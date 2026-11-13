package com.coachingcenter.api.offline;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import com.coachingcenter.api.offline.OfflineRoutineReader.RawRow;
import com.coachingcenter.api.offline.OfflineRoutineReader.Rect;

/**
 * The student copy of a routine (plan G2): every page is rendered to an image at 150 dpi, the Instructor cell of every row is painted over
 * in its background colour (white when the colour cannot be told), the name boxes are painted too, a light "SYMAM LAW" watermark is
 * stamped on the page, and the pages are written as a new PDF. The copy has no text layer, so no name can be read from it. The painting
 * is done on the image, so the names are gone from the file, not hidden under a box.
 *
 * <p>Rows whose Instructor cell is part of a merged cell that carries other text are not painted: the text stays visible.
 */
final class OfflineRoutineStudentCopy {

	static final int DPI = 150;

	private static final float JPEG_QUALITY = 0.85f;

	private static final String WATERMARK = "SYMAM LAW";

	private static final float WATERMARK_SIZE = 64f;

	private static final float WATERMARK_ANGLE = 35f;

	private static final float WATERMARK_GREY = 0.6f;

	private static final float WATERMARK_ALPHA = 0.12f;

	/** How far inside the cell the background is sampled, in points, so the sample is clear of the rules. */
	private static final float SAMPLE_INSET = 3f;

	private OfflineRoutineStudentCopy() {
	}

	/**
	 * The student copy of the original PDF. {@code rows} are the rows the reader found in that PDF (their geometry). Throws
	 * IllegalStateException when the copy cannot be made, so the confirm fails and nothing is saved.
	 */
	static byte[] render(byte[] original, List<RawRow> rows) {
		Map<Integer, List<RawRow>> byPage = rows.stream().collect(Collectors.groupingBy(RawRow::page));
		try (PDDocument source = Loader.loadPDF(original); PDDocument copy = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDFRenderer renderer = new PDFRenderer(source);
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			for (int index = 0; index < source.getNumberOfPages(); index++) {
				PDRectangle box = source.getPage(index).getMediaBox();
				BufferedImage image = renderer.renderImageWithDPI(index, DPI, ImageType.RGB);
				paint(image, byPage.getOrDefault(index + 1, List.of()));
				PDPage page = new PDPage(box);
				copy.addPage(page);
				PDImageXObject picture = JPEGFactory.createFromImage(copy, image, JPEG_QUALITY);
				try (PDPageContentStream content = new PDPageContentStream(copy, page)) {
					content.drawImage(picture, 0, 0, box.getWidth(), box.getHeight());
					watermark(content, font, box);
				}
			}
			copy.save(out);
			return out.toByteArray();
		}
		catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("The student copy could not be made.", exception);
		}
	}

	/** Paints every row's Instructor cell and name boxes on one page image. */
	private static void paint(BufferedImage image, List<RawRow> rows) {
		double scale = DPI / 72.0;
		Graphics2D graphics = image.createGraphics();
		try {
			for (RawRow row : rows) {
				if (row.cell() == null) {
					continue;
				}
				Color background = background(image, row.cell(), scale);
				graphics.setColor(background);
				fill(graphics, row.cell(), scale);
				for (Rect name : row.names()) {
					fill(graphics, name, scale);
				}
			}
		}
		finally {
			graphics.dispose();
		}
	}

	/** The cell's background, read from its bottom corners and middle. White unless all three agree and the colour is light. */
	private static Color background(BufferedImage image, Rect cell, double scale) {
		int y = clamp((int) Math.round((cell.y1() - SAMPLE_INSET) * scale), image.getHeight());
		int[] xs = {
				clamp((int) Math.round((cell.x0() + SAMPLE_INSET) * scale), image.getWidth()),
				clamp((int) Math.round(((cell.x0() + cell.x1()) / 2) * scale), image.getWidth()),
				clamp((int) Math.round((cell.x1() - SAMPLE_INSET) * scale), image.getWidth()) };
		int first = image.getRGB(xs[0], y);
		for (int x : xs) {
			if (image.getRGB(x, y) != first) {
				return Color.WHITE;
			}
		}
		Color sample = new Color(first, true);
		boolean light = sample.getRed() > 200 && sample.getGreen() > 200 && sample.getBlue() > 200;
		return light ? new Color(sample.getRed(), sample.getGreen(), sample.getBlue()) : Color.WHITE;
	}

	private static void fill(Graphics2D graphics, Rect rect, double scale) {
		int x0 = (int) Math.floor(rect.x0() * scale);
		int y0 = (int) Math.floor(rect.y0() * scale);
		int x1 = (int) Math.ceil(rect.x1() * scale);
		int y1 = (int) Math.ceil(rect.y1() * scale);
		graphics.fillRect(x0, y0, x1 - x0, y1 - y0);
	}

	private static int clamp(int value, int size) {
		return Math.max(0, Math.min(size - 1, value));
	}

	/** The diagonal watermark, grey and transparent, drawn over the picture. */
	private static void watermark(PDPageContentStream content, PDType1Font font, PDRectangle box) throws IOException {
		PDExtendedGraphicsState state = new PDExtendedGraphicsState();
		state.setNonStrokingAlphaConstant(WATERMARK_ALPHA);
		state.setStrokingAlphaConstant(WATERMARK_ALPHA);
		content.saveGraphicsState();
		content.setGraphicsStateParameters(state);
		content.setNonStrokingColor(WATERMARK_GREY);
		content.beginText();
		content.setFont(font, WATERMARK_SIZE);
		content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(WATERMARK_ANGLE), box.getWidth() * 0.18f, box.getHeight() * 0.35f));
		content.showText(WATERMARK);
		content.endText();
		content.restoreGraphicsState();
	}

}
