package com.coachingcenter.api.offline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * Reads the first worksheet of an {@code .xlsx} workbook for a result sheet (plan section 2), with the JDK only: the zip's central directory
 * and a namespace-aware DOM. Only the parts the reader needs are inflated: the workbook, its relationships, the shared strings, and the first
 * sheet's part (plan F10). Every other entry is skipped without being read. The parts read in all may expand to at most 50 MB, and the zip may
 * hold at most 1,000 entries; over either cap the file is refused. The reader handles inline strings, numbers, booleans and formula cells
 * (their cached value). Styles, charts and the other parts are ignored. An {@code .xls} file (the old binary format) is refused with the
 * message to save it again as {@code .xlsx}.
 */
final class OfflineResultReader {

	/** The most entries a zip may hold. A result sheet has a few dozen parts. */
	static final int MAX_ENTRIES = 1000;

	/** The most bytes the parts read from the workbook may expand to, in all. A result sheet of 2,000 rows is far below it. */
	static final long MAX_EXPANDED_BYTES = 50L * 1024 * 1024;

	private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

	private static final String RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

	private static final String WORKBOOK = "xl/workbook.xml";

	private static final String WORKBOOK_RELS = "xl/_rels/workbook.xml.rels";

	private static final String SHARED_STRINGS = "xl/sharedStrings.xml";

	private static final String UNREADABLE = "This file is not a readable .xlsx workbook.";

	private static final String TOO_LARGE = "The file is too large to read.";

	private static final byte[] OLE_HEADER = { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

	private static final byte[] ZIP_HEADER = { 'P', 'K', 3, 4 };

	private static final int END_RECORD = 0x06054b50;

	private static final int CENTRAL_HEADER = 0x02014b50;

	private static final int LOCAL_HEADER = 0x04034b50;

	private static final int END_RECORD_SIZE = 22;

	private static final int MAX_COMMENT = 0xFFFF;

	/** One row of the sheet: its Excel row number, and its non-empty cells by column index (column A is 0). */
	record Line(int number, Map<Integer, String> cells) {
	}

	private OfflineResultReader() {
	}

	/** Reads the first worksheet. Refused with a field error on {@code file} when the bytes are not a readable {@code .xlsx}. */
	static List<Line> read(byte[] bytes) {
		if (startsWith(bytes, OLE_HEADER)) {
			throw refuse("Save the file as .xlsx and upload it again.");
		}
		if (!startsWith(bytes, ZIP_HEADER)) {
			throw refuse(UNREADABLE);
		}
		try {
			Archive archive = new Archive(bytes);
			byte[] workbook = archive.part(WORKBOOK);
			byte[] rels = archive.part(WORKBOOK_RELS);
			if (workbook == null || rels == null) {
				throw refuse(UNREADABLE);
			}
			String sheetPath = firstSheetPath(workbook, rels);
			byte[] shared = archive.part(SHARED_STRINGS);
			List<String> strings = shared == null ? List.of() : sharedStrings(shared);
			byte[] sheet = archive.part(sheetPath);
			if (sheet == null) {
				throw refuse(UNREADABLE);
			}
			return lines(sheet, strings);
		}
		catch (ApiException exception) {
			throw exception;
		}
		catch (IOException | RuntimeException exception) {
			throw refuse(UNREADABLE);
		}
	}

	/**
	 * The zip, read through its central directory. Entries are found by name from the directory, and a part is inflated only when it is asked
	 * for, so the parts that are not needed are never read. Every byte inflated counts toward the cap.
	 */
	private static final class Archive {

		private final byte[] zip;

		private final Map<String, Entry> entries = new HashMap<>();

		private long expanded;

		Archive(byte[] zip) throws IOException {
			this.zip = zip;
			int end = endRecord(zip);
			int count = unsigned16(zip, end + 10);
			long directory = unsigned32(zip, end + 16);
			if (count == 0xFFFF || directory == 0xFFFFFFFFL) {
				throw new IOException("Zip64 is not read.");
			}
			if (count > MAX_ENTRIES) {
				throw refuse(TOO_LARGE);
			}
			if (directory > zip.length) {
				throw new IOException("Bad directory.");
			}
			int at = (int) directory;
			for (int index = 0; index < count; index++) {
				if (at + 46 > zip.length || unsigned32(zip, at) != CENTRAL_HEADER) {
					throw new IOException("Bad directory.");
				}
				int nameLength = unsigned16(zip, at + 28);
				int next = at + 46 + nameLength + unsigned16(zip, at + 30) + unsigned16(zip, at + 32);
				if (next > zip.length) {
					throw new IOException("Bad directory.");
				}
				String name = new String(zip, at + 46, nameLength, StandardCharsets.UTF_8);
				entries.put(name, new Entry(unsigned16(zip, at + 10), unsigned32(zip, at + 20), unsigned32(zip, at + 24),
						unsigned32(zip, at + 42)));
				at = next;
			}
		}

		/** The uncompressed bytes of one part, or null when the zip has no part of that name. */
		byte[] part(String name) throws IOException {
			Entry entry = entries.get(name);
			if (entry == null) {
				return null;
			}
			if (entry.size() > MAX_EXPANDED_BYTES - expanded) {
				throw refuse(TOO_LARGE);
			}
			long header = entry.offset();
			if (header + 30 > zip.length || unsigned32(zip, (int) header) != LOCAL_HEADER) {
				throw new IOException("Bad part.");
			}
			int at = (int) header;
			long start = at + 30L + unsigned16(zip, at + 26) + unsigned16(zip, at + 28);
			if (start + entry.compressed() > zip.length) {
				throw new IOException("Bad part.");
			}
			if (entry.method() == 0) {
				count(entry.compressed());
				return Arrays.copyOfRange(zip, (int) start, (int) (start + entry.compressed()));
			}
			if (entry.method() == 8) {
				return inflate((int) start, (int) entry.compressed());
			}
			throw new IOException("Unsupported compression.");
		}

		private byte[] inflate(int start, int length) throws IOException {
			Inflater inflater = new Inflater(true);
			try {
				inflater.setInput(zip, start, length);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				while (!inflater.finished()) {
					int read = inflater.inflate(buffer);
					if (read == 0 && !inflater.finished()) {
						throw new IOException("Truncated part.");
					}
					count(read);
					out.write(buffer, 0, read);
				}
				return out.toByteArray();
			}
			catch (DataFormatException exception) {
				throw new IOException("Bad part.", exception);
			}
			finally {
				inflater.end();
			}
		}

		private void count(long bytes) {
			expanded += bytes;
			if (expanded > MAX_EXPANDED_BYTES) {
				throw refuse(TOO_LARGE);
			}
		}

		private static int endRecord(byte[] zip) throws IOException {
			for (int at = zip.length - END_RECORD_SIZE; at >= 0 && at >= zip.length - END_RECORD_SIZE - MAX_COMMENT; at--) {
				if (unsigned32(zip, at) == END_RECORD) {
					return at;
				}
			}
			throw new IOException("No end of the zip.");
		}

	}

	/** A zip entry from the central directory: its method, its compressed and uncompressed sizes, and where its local header is. */
	private record Entry(int method, long compressed, long size, long offset) {
	}

	/** The part name of the first sheet, from the workbook's first {@code sheet} element and its relationship. */
	private static String firstSheetPath(byte[] workbook, byte[] rels) throws IOException {
		Element sheet = firstElement(document(workbook), "sheet");
		if (sheet == null) {
			throw refuse(UNREADABLE);
		}
		String id = sheet.getAttributeNS(RELATIONSHIPS, "id");
		NodeList relationships = document(rels).getElementsByTagNameNS("*", "Relationship");
		for (int index = 0; index < relationships.getLength(); index++) {
			Element relationship = (Element) relationships.item(index);
			if (id.equals(relationship.getAttribute("Id"))) {
				String target = relationship.getAttribute("Target");
				return target.startsWith("/") ? target.substring(1) : "xl/" + target;
			}
		}
		throw refuse(UNREADABLE);
	}

	/** The text of each shared string: the text runs of a plain or rich string, without the phonetic runs. */
	private static List<String> sharedStrings(byte[] xml) throws IOException {
		List<String> strings = new ArrayList<>();
		NodeList items = document(xml).getElementsByTagNameNS(MAIN, "si");
		for (int index = 0; index < items.getLength(); index++) {
			strings.add(text((Element) items.item(index)));
		}
		return strings;
	}

	/** The text of a string item: its {@code t} children, and the {@code t} children of its runs. Phonetic runs are skipped. */
	private static String text(Element item) {
		StringBuilder text = new StringBuilder();
		for (Node child = item.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (!(child instanceof Element element) || !MAIN.equals(element.getNamespaceURI())) {
				continue;
			}
			if ("t".equals(element.getLocalName())) {
				text.append(element.getTextContent());
			}
			else if ("r".equals(element.getLocalName())) {
				NodeList runs = element.getElementsByTagNameNS(MAIN, "t");
				for (int run = 0; run < runs.getLength(); run++) {
					text.append(runs.item(run).getTextContent());
				}
			}
		}
		return text.toString();
	}

	private static List<Line> lines(byte[] xml, List<String> shared) throws IOException {
		Element data = firstElement(document(xml), "sheetData");
		if (data == null) {
			return List.of();
		}
		List<Line> lines = new ArrayList<>();
		int lineNumber = 0;
		for (Element row : children(data, "row")) {
			lineNumber = row.hasAttribute("r") ? Integer.parseInt(row.getAttribute("r")) : lineNumber + 1;
			Map<Integer, String> cells = new HashMap<>();
			int column = 0;
			for (Element cell : children(row, "c")) {
				column = cell.hasAttribute("r") ? columnIndex(cell.getAttribute("r")) : column;
				String value = value(cell, shared);
				if (value != null && !value.isEmpty()) {
					cells.put(column, value);
				}
				column++;
			}
			lines.add(new Line(lineNumber, cells));
		}
		return lines;
	}

	/** A cell's value as text: the shared string, the inline text, or the stored (or cached formula) value. Null when there is none. */
	private static String value(Element cell, List<String> shared) {
		String type = cell.getAttribute("t");
		if ("inlineStr".equals(type)) {
			Element inline = firstChild(cell, "is");
			return inline == null ? null : text(inline).trim();
		}
		Element value = firstChild(cell, "v");
		if (value == null) {
			return null;
		}
		String raw = value.getTextContent().trim();
		if ("s".equals(type)) {
			int index = Integer.parseInt(raw);
			return index >= 0 && index < shared.size() ? shared.get(index).trim() : null;
		}
		return raw;
	}

	/** The zero-based column of a cell reference such as {@code C6} (column C is 2). */
	static int columnIndex(String reference) {
		int column = 0;
		for (char letter : reference.toCharArray()) {
			if (!Character.isLetter(letter)) {
				break;
			}
			column = column * 26 + (Character.toUpperCase(letter) - 'A' + 1);
		}
		return column - 1;
	}

	private static Document document(byte[] xml) throws IOException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setExpandEntityReferences(false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(new ByteArrayInputStream(xml));
		}
		catch (Exception exception) {
			throw new IOException("Unreadable part.", exception);
		}
	}

	private static Element firstElement(Document document, String localName) {
		NodeList found = document.getElementsByTagNameNS(MAIN, localName);
		return found.getLength() == 0 ? null : (Element) found.item(0);
	}

	private static List<Element> children(Element parent, String localName) {
		List<Element> found = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element element && MAIN.equals(element.getNamespaceURI()) && localName.equals(element.getLocalName())) {
				found.add(element);
			}
		}
		return found;
	}

	private static Element firstChild(Element parent, String localName) {
		List<Element> found = children(parent, localName);
		return found.isEmpty() ? null : found.get(0);
	}

	private static int unsigned16(byte[] bytes, int at) {
		return (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8;
	}

	private static long unsigned32(byte[] bytes, int at) {
		return unsigned16(bytes, at) | (long) unsigned16(bytes, at + 2) << 16;
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		if (bytes.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (bytes[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	/** The refusal of a file that cannot be read, answered as a field error on the file with the code FILE_REFUSED. */
	static ApiException refuse(String message) {
		return refuse("FILE_REFUSED", OfflineResultFile.params(), message);
	}

	/** The refusal of a file, answered as a field error on the file with a code the web translates. */
	static ApiException refuse(String code, Map<String, Object> params, String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
				List.of(new FieldErrorDetail("file", message, code, params)));
	}

}
