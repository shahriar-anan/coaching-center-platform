package com.coachingcenter.api.offline;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.offline.OfflineFeeLedger.Payment;

/**
 * The receipt of one approved monthly fee as an A4 PDF (plan section 6.1). It follows the direct payment receipt: the company block
 * and logo, the billed-to block, the dues the payment covered, the totals, the payment details and the footer. PDFBox's standard
 * fonts cannot draw Bangla, so the PDF is in English: the student's name keeps only its Latin letters, and a batch with no English
 * name prints none.
 */
@Component
public class OfflineFeeReceiptPdf {

	// The company block. It matches the direct payment receipt in exams/LiveExamReceiptPdf; change it in both places.
	private static final String COMPANY_NAME = "SYMAM LAW";

	private static final String COMPANY_TAGLINE = "Online law exams";

	private static final String COMPANY_WEBSITE = "symamlaw.com";

	private static final String COMPANY_EMAIL = "info@symamlaw.com";

	private static final String COMPANY_PHONE = "+8801601561225";

	private static final String COMPANY_ADDRESS = "Binodpur Bazar, Motihar, Rajshahi, Bangladesh";

	private static final String COMPANY_HOURS = "10:00 AM - 6:00 PM";

	private static final String LOGO = "receipt/logo.png";

	private static final Color INK = new Color(0x1c, 0x24, 0x30);

	private static final Color GREY = new Color(0x5d, 0x65, 0x78);

	private static final Color DIVIDER = new Color(0xd8, 0xdc, 0xe3);

	private static final Color HEADER_FILL = new Color(0xf3, 0xf5, 0xf8);

	private static final Color GREEN = new Color(0x1f, 0x7a, 0x4d);

	private static final Color AMBER = new Color(0x8a, 0x5f, 0x12);

	private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

	private static final float LEFT = 50f;

	private static final float RIGHT = 545f;

	private static final float LOGO_WIDTH = 150f;

	private static final float DESC_X = LEFT + 8;

	private static final float DATE_X = 300f;

	private static final float AMOUNT_RIGHT = RIGHT - 8;

	private static final float LABEL_RIGHT = 430f;

	private static final float VALUE_X = 200f;

	/** Body content must end above this line; the footer sits below it. */
	private static final float BODY_LIMIT = 700f;

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.ENGLISH);

	/** The receipt of one approved payment, with the dues it covered and the balance the admission still owes. */
	public byte[] render(OfflineFeeService.Receipt receipt) {
		Payment payment = receipt.payment();
		try (PDDocument document = new PDDocument()) {
			document.getDocumentInformation().setTitle(text(payment.receiptNo()));
			PDImageXObject logo = PDImageXObject.createFromByteArray(document, logoBytes(), "symam-law-logo");
			Sheet sheet = new Sheet(document);
			header(sheet, logo, payment.receiptNo(), DAY.format(payment.paidOn()));
			billedTo(sheet, payment.personName());
			table(sheet, receipt.lines());
			totals(sheet, payment.amount(), receipt.owedNow());
			details(sheet, receipt);
			footer(sheet);
			sheet.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static byte[] logoBytes() throws IOException {
		try (var in = new ClassPathResource(LOGO).getInputStream()) {
			return in.readAllBytes();
		}
	}

	private static void header(Sheet sheet, PDImageXObject logo, String receiptNo, String date) throws IOException {
		sheet.show(LEFT, 62, BOLD, 22, INK, COMPANY_NAME);
		sheet.show(LEFT, 80, REGULAR, 10, GREY, COMPANY_TAGLINE);
		float top = 98;
		for (String contact : List.of("Website: " + COMPANY_WEBSITE, "Email: " + COMPANY_EMAIL, "Phone: " + COMPANY_PHONE,
				"Address: " + COMPANY_ADDRESS, "Hours: " + COMPANY_HOURS)) {
			sheet.show(LEFT, top, REGULAR, 9, GREY, contact);
			top += 13;
		}
		float logoHeight = LOGO_WIDTH * logo.getHeight() / logo.getWidth();
		sheet.image(logo, RIGHT - LOGO_WIDTH, 40, LOGO_WIDTH, logoHeight);
		float titleTop = 40 + logoHeight + 16;
		sheet.showRight(RIGHT, titleTop, BOLD, 13, INK, "PAYMENT RECEIPT");
		sheet.showRight(RIGHT, titleTop + 16, REGULAR, 10, INK, text(receiptNo));
		sheet.showRight(RIGHT, titleTop + 30, REGULAR, 10, GREY, "Date: " + date);
		sheet.rule(LEFT, RIGHT, titleTop + 46, DIVIDER, 0.8f);
		sheet.y = titleTop + 62;
	}

	/** The student's Latin name, and the batch with its English name when it has one. */
	private static void billedTo(Sheet sheet, String personName) throws IOException {
		float top = sheet.y;
		sheet.show(LEFT, top, BOLD, 8.5f, GREY, "BILLED TO");
		top += 16;
		String name = nameText(personName);
		if (!name.isEmpty()) {
			sheet.show(LEFT, top, BOLD, 13, INK, name);
			top += 16;
		}
		sheet.y = top + 12;
	}

	/** One row per due the payment covered, with the due date and the amount applied to it. */
	private static void table(Sheet sheet, List<OfflineFeeService.ReceiptLine> lines) throws IOException {
		tableHeader(sheet);
		for (OfflineFeeService.ReceiptLine line : lines) {
			if (!sheet.fits(28)) {
				sheet.newPage();
				tableHeader(sheet);
			}
			float top = sheet.y;
			sheet.show(DESC_X, top + 18, BOLD, 10, INK, "Monthly fee, month " + line.seq());
			sheet.show(DATE_X, top + 18, REGULAR, 9, INK, "Due " + DAY.format(line.dueOn()));
			sheet.showRight(AMOUNT_RIGHT, top + 18, REGULAR, 10, INK, money(line.amount()));
			sheet.rule(LEFT, RIGHT, top + 27, DIVIDER, 0.6f);
			sheet.y = top + 27;
		}
	}

	private static void tableHeader(Sheet sheet) throws IOException {
		float top = sheet.y;
		sheet.fill(LEFT, top, RIGHT - LEFT, 22, HEADER_FILL);
		sheet.show(DESC_X, top + 15, BOLD, 9, INK, "Description");
		sheet.show(DATE_X, top + 15, BOLD, 9, INK, "Due date");
		sheet.showRight(AMOUNT_RIGHT, top + 15, BOLD, 9, INK, "Applied");
		sheet.y = top + 22;
	}

	private static void totals(Sheet sheet, BigDecimal paid, BigDecimal owedNow) throws IOException {
		if (!sheet.fits(90)) {
			sheet.newPage();
		}
		float top = sheet.y + 8;
		sheet.showRight(LABEL_RIGHT, top + 12, BOLD, 11, GREEN, "Paid on this receipt");
		sheet.showRight(AMOUNT_RIGHT, top + 12, BOLD, 11, GREEN, money(paid));
		top += 18;
		sheet.showRight(LABEL_RIGHT, top + 12, REGULAR, 10, INK, "Still owed on the admission");
		sheet.showRight(AMOUNT_RIGHT, top + 12, REGULAR, 10, INK, money(owedNow));
		sheet.y = top + 30;
	}

	/** The payment details: status, date, method, reference, receipt number and batch. */
	private static void details(Sheet sheet, OfflineFeeService.Receipt receipt) throws IOException {
		Payment payment = receipt.payment();
		String batch = receipt.batchName() == null ? "" : ascii(receipt.batchName());
		List<String[]> rows = new java.util.ArrayList<>(List.of(
				new String[] { "Status", "Paid" },
				new String[] { "Paid on", DAY.format(payment.paidOn()) },
				new String[] { "Payment method", OfflineFeeRules.methodLabel(payment.method()) },
				new String[] { "Reference", text(payment.reference()) },
				new String[] { "Receipt number", text(payment.receiptNo()) },
				new String[] { "Batch", batch.isEmpty() ? "-" : batch }));
		// A corrected payment keeps its receipt number and says when it was corrected (payment-records-edit-plan.md, P4).
		if (payment.correctedAt() != null) {
			rows.add(new String[] { "Corrected on", DAY.format(payment.correctedAt().atZone(OfflineFeeSchedule.DHAKA).toLocalDate()) });
		}
		float height = 30 + rows.size() * 17;
		if (!sheet.fits(height)) {
			sheet.newPage();
		}
		float top = sheet.y;
		sheet.show(DESC_X, top + 9, BOLD, 8.5f, GREY, "PAYMENT DETAILS");
		sheet.rule(LEFT, RIGHT, top + 16, DIVIDER, 0.8f);
		top += 26;
		for (String[] row : rows) {
			sheet.show(DESC_X, top + 11, REGULAR, 9.5f, GREY, row[0]);
			sheet.show(VALUE_X, top + 11, REGULAR, 10, INK, row[1]);
			top += 17;
		}
		sheet.y = top;
	}

	private static void footer(Sheet sheet) throws IOException {
		sheet.centre(744, REGULAR, 8.5f, GREY, "This is a computer-generated receipt. No signature is required.");
		sheet.rule(LEFT, RIGHT, 758, DIVIDER, 0.8f);
		sheet.centre(778, BOLD, 11, INK, "Thank you for choosing " + COMPANY_NAME + "!");
		sheet.centre(794, REGULAR, 8.5f, GREY, "For support, email " + COMPANY_EMAIL + " or call " + COMPANY_PHONE + ".");
	}

	private static String money(BigDecimal amount) {
		return amount == null ? "-" : "BDT " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	/** A value for the PDF: printable ASCII only, or a dash when nothing is left. */
	private static String text(String value) {
		String latin = ascii(value);
		return latin.isEmpty() ? "-" : latin;
	}

	/** A student's name for the PDF: its Latin letters and spaces only. */
	private static String nameText(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("[^A-Za-z ]", "").replaceAll(" +", " ").trim();
	}

	/** Keeps the printable ASCII characters, which the standard font can draw, and trims the spaces around them. */
	private static String ascii(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (char c : value.toCharArray()) {
			if (c >= 32 && c < 127) {
				out.append(c);
			}
		}
		return out.toString().replaceAll(" +", " ").trim();
	}

	/** One page being written. {@code y} is the distance from the top of the page to the next block. */
	private static final class Sheet {

		private final PDDocument document;

		private PDPageContentStream content;

		private float y;

		Sheet(PDDocument document) throws IOException {
			this.document = document;
			newPage();
		}

		void newPage() throws IOException {
			if (content != null) {
				content.close();
			}
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			content = new PDPageContentStream(document, page);
			y = 40f;
		}

		boolean fits(float height) {
			return y + height <= BODY_LIMIT;
		}

		void finish() throws IOException {
			content.close();
		}

		void show(float x, float top, PDType1Font font, float size, Color color, String value) throws IOException {
			if (value == null || value.isEmpty()) {
				return;
			}
			content.beginText();
			content.setFont(font, size);
			content.setNonStrokingColor(color);
			content.newLineAtOffset(x, PAGE_HEIGHT - top);
			content.showText(value);
			content.endText();
		}

		void showRight(float right, float top, PDType1Font font, float size, Color color, String value) throws IOException {
			show(right - font.getStringWidth(value) * size / 1000f, top, font, size, color, value);
		}

		void centre(float top, PDType1Font font, float size, Color color, String value) throws IOException {
			show((RIGHT + LEFT) / 2 - font.getStringWidth(value) * size / 2000f, top, font, size, color, value);
		}

		void rule(float fromX, float toX, float top, Color color, float weight) throws IOException {
			content.setStrokingColor(color);
			content.setLineWidth(weight);
			content.moveTo(fromX, PAGE_HEIGHT - top);
			content.lineTo(toX, PAGE_HEIGHT - top);
			content.stroke();
		}

		void fill(float x, float top, float w, float h, Color color) throws IOException {
			content.setNonStrokingColor(color);
			content.addRect(x, PAGE_HEIGHT - top - h, w, h);
			content.fill();
		}

		void image(PDImageXObject image, float x, float top, float w, float h) throws IOException {
			content.drawImage(image, x, PAGE_HEIGHT - top - h, w, h);
		}

	}

}
