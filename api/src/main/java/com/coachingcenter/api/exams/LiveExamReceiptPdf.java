package com.coachingcenter.api.exams;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The receipt of one paid order as an A4 PDF (P4), laid out like an invoice: the company block and logo at the top, the
 * billed-to block, the item table, the totals with a PAID stamp, the payment details and the thank-you footer. PDFBox's
 * standard fonts cannot draw Bangla, so the PDF is in English: the student's name keeps only its Latin letters and spaces,
 * and an item with no English title prints the order id in its place. The web print view keeps the Bangla text. A long item
 * list continues on the next page.
 */
@Component
class LiveExamReceiptPdf {

	// The company block. It matches the footer in web/src/i18n/messages/en/common.ts; change it here only.
	private static final String COMPANY_NAME = "Symam Law";

	private static final String COMPANY_TAGLINE = "ExamPortal: online law exams";

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

	private static final Color RED = new Color(0xb4, 0x23, 0x18);

	private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	/** A4 page size in points, and the content box inside the margins (top-based y, as the layout code uses it). */
	private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

	private static final float CENTRE_X = PDRectangle.A4.getWidth() / 2;

	private static final float LEFT = 50f;

	private static final float RIGHT = 545f;

	private static final float WIDTH = RIGHT - LEFT;

	/** Body content must end above this line (a distance from the top of the page); the footer sits below it. */
	private static final float BODY_LIMIT = 720f;

	private static final float LOGO_WIDTH = 150f;

	private static final float DESC_X = LEFT + 8;

	private static final float DESC_WIDTH = 200f;

	private static final float TYPE_X = 262f;

	private static final float VALIDITY_X = 325f;

	private static final float VALIDITY_WIDTH = 120f;

	private static final float AMOUNT_RIGHT = RIGHT - 8;

	private static final float LABEL_RIGHT = 430f;

	private static final float VALUE_X = 200f;

	private static final float VALUE_WIDTH = 330f;

	private static final float HEADER_ROW_HEIGHT = 22f;

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.ENGLISH)
		.withZone(LiveExamReceipts.DHAKA);

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM, yyyy, HH:mm", Locale.ENGLISH)
		.withZone(LiveExamReceipts.DHAKA);

	byte[] render(LiveExamOrderHistoryService.Receipt receipt) {
		LiveExamOrderHistoryService.OwnOrderRow order = receipt.order();
		try (PDDocument document = new PDDocument()) {
			document.getDocumentInformation().setTitle(text(order.receiptNo()));
			PDImageXObject logo = PDImageXObject.createFromByteArray(document, logoBytes(), "symam-law-logo");
			Sheet sheet = new Sheet(document);
			header(sheet, logo, order);
			billedTo(sheet, receipt);
			items(sheet, receipt);
			totals(sheet, order);
			paymentDetails(sheet, receipt);
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

	/** The company block on the left, and the logo with the receipt title on the right. */
	private static void header(Sheet sheet, PDImageXObject logo, LiveExamOrderHistoryService.OwnOrderRow order)
			throws IOException {
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
		sheet.showRight(RIGHT, titleTop + 16, REGULAR, 10, INK, text(order.receiptNo()));
		sheet.showRight(RIGHT, titleTop + 30, REGULAR, 10, GREY,
				"Date: " + (order.paidAt() == null ? "-" : DATE.format(order.paidAt())));
		sheet.rule(LEFT, RIGHT, titleTop + 46, DIVIDER, 0.8f);
		sheet.y = titleTop + 62;
	}

	/** The student's name (Latin letters only), phone and the email the receipt went to. Empty lines are left out. */
	private static void billedTo(Sheet sheet, LiveExamOrderHistoryService.Receipt receipt) throws IOException {
		float top = sheet.y;
		sheet.show(LEFT, top, BOLD, 8.5f, GREY, "BILLED TO");
		top += 16;
		String name = nameText(receipt.studentName());
		if (!name.isEmpty()) {
			sheet.show(LEFT, top, BOLD, 13, INK, name);
			top += 16;
		}
		String phone = ascii(receipt.studentPhone());
		if (!phone.isEmpty()) {
			sheet.show(LEFT, top, REGULAR, 10, INK, phone);
			top += 14;
		}
		String email = ascii(receipt.receiptEmail());
		if (!email.isEmpty()) {
			sheet.show(LEFT, top, REGULAR, 10, INK, email);
			top += 14;
		}
		sheet.y = top + 12;
	}

	/** The item table: one row per item, and a bundle's exams one row each. A row that does not fit starts a new page. */
	private static void items(Sheet sheet, LiveExamOrderHistoryService.Receipt receipt) throws IOException {
		LiveExamOrderHistoryService.OwnOrderRow order = receipt.order();
		tableHeader(sheet);
		for (LiveExamOrderHistoryService.ReceiptItem item : receipt.items()) {
			String title = ascii(item.titleEn());
			List<String> titleLines = title.isEmpty() ? List.of() : wrap(title, BOLD, 10, DESC_WIDTH);
			List<String> validityLines = wrap(validity(item.accessDays()), REGULAR, 9, VALIDITY_WIDTH);
			int lines = Math.max(Math.max(titleLines.size(), validityLines.size()), 1);
			float height = 27 + (lines - 1) * 13;
			if (!sheet.fits(height)) {
				sheet.newPage();
				tableHeader(sheet);
			}
			float top = sheet.y;
			if (title.isEmpty()) {
				sheet.show(DESC_X, top + 18, REGULAR, 9, GREY, "Order ID: " + text(order.orderId() == null ? null
						: order.orderId().toString()));
			}
			for (int i = 0; i < titleLines.size(); i++) {
				sheet.show(DESC_X, top + 18 + i * 13, BOLD, 10, INK, titleLines.get(i));
			}
			sheet.show(TYPE_X, top + 18, REGULAR, 9, INK, item.type());
			for (int i = 0; i < validityLines.size(); i++) {
				sheet.show(VALIDITY_X, top + 18 + i * 13, REGULAR, 9, INK, validityLines.get(i));
			}
			if (item.listPrice() != null) {
				sheet.showRight(AMOUNT_RIGHT, top + 18, REGULAR, 10, INK, money(item.listPrice()));
			}
			sheet.rule(LEFT, RIGHT, top + height, DIVIDER, 0.6f);
			sheet.y = top + height;
		}
	}

	private static void tableHeader(Sheet sheet) throws IOException {
		float top = sheet.y;
		sheet.fill(LEFT, top, WIDTH, HEADER_ROW_HEIGHT, HEADER_FILL);
		sheet.show(DESC_X, top + 15, BOLD, 9, INK, "Description");
		sheet.show(TYPE_X, top + 15, BOLD, 9, INK, "Type");
		sheet.show(VALIDITY_X, top + 15, BOLD, 9, INK, "Validity");
		sheet.showRight(AMOUNT_RIGHT, top + 15, BOLD, 9, INK, "Amount");
		sheet.y = top + HEADER_ROW_HEIGHT;
	}

	/**
	 * The totals under the table, right aligned, with the PAID stamp at the lower left. The discount line appears only when the
	 * order has a list price, a discount percent, and a paid amount below the list price.
	 */
	private static void totals(Sheet sheet, LiveExamOrderHistoryService.OwnOrderRow order) throws IOException {
		BigDecimal amount = order.amount();
		BigDecimal listPrice = order.listPrice() == null ? amount : order.listPrice();
		boolean discounted = amount != null && listPrice != null && order.listPrice() != null
				&& order.discountPercent() != null && order.discountPercent() > 0 && listPrice.compareTo(amount) > 0;
		BigDecimal discount = discounted ? listPrice.subtract(amount) : null;
		float blockHeight = discounted ? 112 : 94;
		if (!sheet.fits(blockHeight)) {
			sheet.newPage();
		}
		float blockTop = sheet.y + 8;
		float top = blockTop;
		sheet.showRight(LABEL_RIGHT, top + 12, REGULAR, 10, INK, "Subtotal");
		sheet.showRight(AMOUNT_RIGHT, top + 12, REGULAR, 10, INK, money(listPrice));
		top += 18;
		if (discounted) {
			sheet.showRight(LABEL_RIGHT, top + 12, REGULAR, 10, RED, "Discount (" + order.discountPercent() + "%)");
			sheet.showRight(AMOUNT_RIGHT, top + 12, REGULAR, 10, RED, "-" + money(discount));
			top += 18;
		}
		sheet.rule(340, AMOUNT_RIGHT, top + 1, DIVIDER, 0.8f);
		top += 6;
		sheet.showRight(LABEL_RIGHT, top + 12, BOLD, 10, INK, "Total");
		sheet.showRight(AMOUNT_RIGHT, top + 12, BOLD, 10, INK, money(amount));
		top += 18;
		sheet.showRight(LABEL_RIGHT, top + 12, BOLD, 11, GREEN, "Paid");
		sheet.showRight(AMOUNT_RIGHT, top + 12, BOLD, 11, GREEN, money(amount));
		top += 18;
		sheet.paidStamp(LEFT + 95, blockTop + 40);
		sheet.y = top + 16;
	}

	/** The payment details: a two-column list of the label and the value. */
	private static void paymentDetails(Sheet sheet, LiveExamOrderHistoryService.Receipt receipt) throws IOException {
		LiveExamOrderHistoryService.OwnOrderRow order = receipt.order();
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "Status", "Paid" });
		rows.add(new String[] { "Paid on",
				order.paidAt() == null ? "-" : DATE_TIME.format(order.paidAt()) + " (Bangladesh time)" });
		String channel = ascii(order.paymentChannel());
		rows.add(new String[] { "Payment method", channel.isEmpty() ? "Online payment" : channel });
		rows.add(new String[] { "Transaction ID", text(order.tranId()) });
		rows.add(new String[] { "Order ID", text(order.orderId() == null ? null : order.orderId().toString()) });
		rows.add(new String[] { "Receipt number", text(order.receiptNo()) });
		if (LiveExamOrder.SSLCOMMERZ.equals(receipt.method())) {
			rows.add(new String[] { "Gateway", "SSLCommerz" });
		}
		List<List<String>> values = new ArrayList<>();
		float height = 30;
		for (String[] row : rows) {
			List<String> lines = wrap(row[1], REGULAR, 10, VALUE_WIDTH);
			values.add(lines);
			height += lines.size() * 13 + 4;
		}
		if (!sheet.fits(height)) {
			sheet.newPage();
		}
		float top = sheet.y;
		sheet.show(DESC_X, top + 9, BOLD, 8.5f, GREY, "PAYMENT DETAILS");
		sheet.rule(LEFT, RIGHT, top + 16, DIVIDER, 0.8f);
		top += 26;
		for (int i = 0; i < rows.size(); i++) {
			sheet.show(DESC_X, top + 11, REGULAR, 9.5f, GREY, rows.get(i)[0]);
			List<String> lines = values.get(i);
			for (int j = 0; j < lines.size(); j++) {
				sheet.show(VALUE_X, top + 11 + j * 13, REGULAR, 10, INK, lines.get(j));
			}
			top += lines.size() * 13 + 4;
		}
		sheet.y = top;
	}

	/** The footer on the last page: the computer-generated line, a divider, the thank-you line and the support line. */
	private static void footer(Sheet sheet) throws IOException {
		sheet.centre(744, REGULAR, 8.5f, GREY, "This is a computer-generated receipt. No signature is required.");
		sheet.rule(LEFT, RIGHT, 758, DIVIDER, 0.8f);
		sheet.centre(778, BOLD, 11, INK, "Thank you for choosing " + COMPANY_NAME + " ExamPortal!");
		sheet.centre(794, REGULAR, 8.5f, GREY,
				"For support, email " + COMPANY_EMAIL + " or call " + COMPANY_PHONE + ".");
	}

	private static String validity(Integer accessDays) {
		return accessDays == null ? "Lifetime" : accessDays + " days after the exam window ends";
	}

	private static String money(BigDecimal amount) {
		if (amount == null) {
			return "-";
		}
		return "BDT " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH))
			.format(amount.setScale(2, RoundingMode.HALF_UP));
	}

	/** A value for the PDF: the printable ASCII text, or a dash when nothing is left. */
	private static String text(String value) {
		String latin = ascii(value);
		return latin.isEmpty() ? "-" : latin;
	}

	/** A student's name for the PDF: its Latin letters and spaces only, or an empty text when nothing is left. */
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

	/** The words of a text, wrapped to the given width in points. */
	private static List<String> wrap(String value, PDType1Font font, float size, float maxWidth) throws IOException {
		List<String> lines = new ArrayList<>();
		String current = "";
		for (String word : value.split(" ")) {
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (!current.isEmpty() && width(font, size, candidate) > maxWidth) {
				lines.add(current);
				current = word;
			}
			else {
				current = candidate;
			}
		}
		if (!current.isEmpty()) {
			lines.add(current);
		}
		return lines;
	}

	private static float width(PDType1Font font, float size, String value) throws IOException {
		return font.getStringWidth(value) * size / 1000f;
	}

	/**
	 * One page being written. The {@code y} cursor is the distance from the top of the page to the next block; a block that
	 * does not fit above the body limit starts a new page.
	 */
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
			show(right - width(font, size, value), top, font, size, color, value);
		}

		void centre(float top, PDType1Font font, float size, Color color, String value) throws IOException {
			show(CENTRE_X - width(font, size, value) / 2, top, font, size, color, value);
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

		/** A green outline rounded rectangle with PAID inside, turned about its centre by -12 degrees. */
		void paidStamp(float centreX, float centreTop) throws IOException {
			float cx = centreX;
			float cy = PAGE_HEIGHT - centreTop;
			float w = 170f;
			float h = 60f;
			float r = 10f;
			content.saveGraphicsState();
			// The last transform acts first, so the drawing is moved to the origin, turned, and moved back to the centre.
			content.transform(Matrix.getTranslateInstance(cx, cy));
			content.transform(Matrix.getRotateInstance(Math.toRadians(-12), 0, 0));
			content.transform(Matrix.getTranslateInstance(-cx, -cy));
			roundedRect(cx - w / 2, cy - h / 2, w, h, r);
			content.setStrokingColor(GREEN);
			content.setLineWidth(2.5f);
			content.stroke();
			float size = 34f;
			content.beginText();
			content.setFont(BOLD, size);
			content.setNonStrokingColor(GREEN);
			content.newLineAtOffset(cx - width(BOLD, size, "PAID") / 2, cy - size * 0.36f);
			content.showText("PAID");
			content.endText();
			content.restoreGraphicsState();
		}

		private void roundedRect(float x, float y0, float w, float h, float r) throws IOException {
			float k = 0.5523f * r;
			content.moveTo(x + r, y0);
			content.lineTo(x + w - r, y0);
			content.curveTo(x + w - r + k, y0, x + w, y0 + r - k, x + w, y0 + r);
			content.lineTo(x + w, y0 + h - r);
			content.curveTo(x + w, y0 + h - r + k, x + w - r + k, y0 + h, x + w - r, y0 + h);
			content.lineTo(x + r, y0 + h);
			content.curveTo(x + r - k, y0 + h, x, y0 + h - r + k, x, y0 + h - r);
			content.lineTo(x, y0 + r);
			content.curveTo(x, y0 + r - k, x + r - k, y0, x + r, y0);
			content.closePath();
		}

	}

}
