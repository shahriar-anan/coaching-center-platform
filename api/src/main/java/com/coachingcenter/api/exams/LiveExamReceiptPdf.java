package com.coachingcenter.api.exams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
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
import org.springframework.stereotype.Component;

/**
 * The receipt of one paid order as a one-page A4 PDF (P4). PDFBox's standard fonts cannot draw Bangla, so the PDF is in
 * English: labels, the English title, and the student's name with any non-Latin letters left out. An item that has only a
 * Bangla title prints its type without a title (the order id is always printed). The web print view keeps the Bangla text.
 */
@Component
class LiveExamReceiptPdf {

	private static final String SITE_NAME = "Exam Portal";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH)
		.withZone(LiveExamReceipts.DHAKA);

	private static final float LEFT = 60f;

	private static final float VALUE_X = 230f;

	private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

	byte[] render(LiveExamOrderHistoryService.Receipt receipt) {
		LiveExamOrderHistoryService.OwnOrderRow order = receipt.order();
		List<String[]> rows = new ArrayList<>();
		rows.add(new String[] { "Receipt number", text(order.receiptNo()) });
		rows.add(new String[] { "Paid on", order.paidAt() == null ? "-" : WHEN.format(order.paidAt()) + " (Bangladesh time)" });
		rows.add(new String[] { "Student", text(receipt.studentName()) });
		rows.add(new String[] { "Phone", text(receipt.studentPhone()) });
		boolean exam = LiveExamOrder.EXAM.equals(order.scope());
		rows.add(new String[] { "Item", itemType(order.scope()) });
		String title = ascii(exam ? order.examTitleEn() : order.moduleTitleEn());
		if (!title.isEmpty()) {
			rows.add(new String[] { "Title", title });
		}
		BigDecimal listPrice = order.listPrice() == null ? order.amount() : order.listPrice();
		rows.add(new String[] { "List price", money(listPrice) });
		rows.add(new String[] { "Discount", order.discountPercent() == null || order.discountPercent() == 0 ? "None"
				: order.discountPercent() + "%" });
		rows.add(new String[] { "Amount paid", money(order.amount()) });
		rows.add(new String[] { "Payment method", text(order.paymentChannel()) });
		rows.add(new String[] { "Transaction ID", text(order.tranId()) });
		rows.add(new String[] { "Order ID", text(order.orderId() == null ? null : order.orderId().toString()) });
		try (PDDocument document = new PDDocument()) {
			document.getDocumentInformation().setTitle(text(order.receiptNo()));
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				line(content, LEFT, 780, BOLD, 20, SITE_NAME);
				line(content, LEFT, 752, REGULAR, 14, "Payment receipt");
				float y = 700;
				for (String[] row : rows) {
					line(content, LEFT, y, REGULAR, 11, row[0]);
					line(content, VALUE_X, y, BOLD, 11, row[1]);
					y -= 26;
				}
				line(content, LEFT, 90, REGULAR, 9,
						"Quote the receipt number when you contact us about this payment.");
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static void line(PDPageContentStream content, float x, float y, PDType1Font font, float size, String value)
			throws IOException {
		content.beginText();
		content.setFont(font, size);
		content.newLineAtOffset(x, y);
		content.showText(value);
		content.endText();
	}

	private static String itemType(String scope) {
		if (LiveExamOrder.EXAM.equals(scope)) {
			return "Exam";
		}
		return LiveExamOrder.BUNDLE.equals(scope) ? "Package (all its exams)" : "Package";
	}

	private static String money(BigDecimal amount) {
		return amount == null ? "-" : "BDT " + amount.setScale(2).toPlainString();
	}

	/** A value for the PDF: the Latin text only, or a dash when nothing is left. */
	private static String text(String value) {
		String latin = ascii(value);
		return latin.isEmpty() ? "-" : latin;
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

}
