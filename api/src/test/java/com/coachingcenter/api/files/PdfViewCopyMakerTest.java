package com.coachingcenter.api.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The smaller viewing copy of a scanned script (PDF viewer plan, Stage 3). */
class PdfViewCopyMakerTest {

	@TempDir
	Path dir;

	private final PdfViewCopyMaker maker = new PdfViewCopyMaker(1800, 0.72f, 0.7);

	@Test
	void shrinksCameraScansAndKeepsThePages() throws IOException {
		Path original = scan(dir.resolve("script.pdf"), 6, 2400, 3300);
		Path copy = dir.resolve("script.view.pdf");

		PdfViewCopyMaker.Result result = maker.make(original, copy);

		assertThat(result.outcome()).isEqualTo(PdfViewCopyMaker.Outcome.MADE);
		assertThat(result.imagesScaled()).isEqualTo(6);
		assertThat(Files.size(copy)).isLessThanOrEqualTo((long) (Files.size(original) * 0.7));
		try (PDDocument before = Loader.loadPDF(original.toFile()); PDDocument after = Loader.loadPDF(copy.toFile())) {
			assertThat(after.getNumberOfPages()).isEqualTo(before.getNumberOfPages());
			for (int index = 0; index < after.getNumberOfPages(); index++) {
				PDPage page = after.getPage(index);
				assertThat(page.getMediaBox().getWidth()).isEqualTo(before.getPage(index).getMediaBox().getWidth());
				PDImageXObject image = (PDImageXObject) page.getResources().getXObject(COSName.getPDFName("Im1"));
				assertThat(Math.max(image.getWidth(), image.getHeight())).isEqualTo(1800);
			}
		}
	}

	@Test
	void leavesSmallImagesAlone() throws IOException {
		Path original = scan(dir.resolve("small.pdf"), 2, 1000, 1400);

		PdfViewCopyMaker.Result result = maker.make(original, dir.resolve("small.view.pdf"));

		assertThat(result.outcome()).isEqualTo(PdfViewCopyMaker.Outcome.NO_GAIN);
		assertThat(dir.resolve("small.view.pdf")).doesNotExist();
	}

	@Test
	void skipsAFileThatIsNotAPdfWithoutThrowing() throws IOException {
		Path original = Files.writeString(dir.resolve("broken.pdf"), "%PDF-1.7 not really");

		PdfViewCopyMaker.Result result = maker.make(original, dir.resolve("broken.view.pdf"));

		assertThat(result.outcome()).isEqualTo(PdfViewCopyMaker.Outcome.SKIPPED);
		assertThat(dir.resolve("broken.view.pdf")).doesNotExist();
	}

	@Test
	void scalesGrayAsGray() {
		BufferedImage gray = new BufferedImage(2400, 3300, BufferedImage.TYPE_BYTE_GRAY);
		BufferedImage scaled = PdfViewCopyMaker.scale(gray, 1309, 1800, true);
		assertThat(scaled.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
		assertThat(scaled.getWidth()).isEqualTo(1309);
		assertThat(scaled.getHeight()).isEqualTo(1800);
	}

	/** A PDF like a phone scan: one noisy JPEG photo per A4 page, drawn over the whole page, named Im1. */
	static Path scan(Path target, int pages, int width, int height) throws IOException {
		Random random = new Random(7);
		try (PDDocument document = new PDDocument()) {
			for (int index = 0; index < pages; index++) {
				BufferedImage photo = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				Graphics2D graphics = photo.createGraphics();
				graphics.setColor(new Color(245, 243, 236));
				graphics.fillRect(0, 0, width, height);
				graphics.setColor(new Color(30, 40, 120));
				for (int line = 0; line < 400; line++) {
					int y = 80 + line * (height - 160) / 400;
					graphics.drawLine(60 + random.nextInt(40), y, width - 60 - random.nextInt(200), y + random.nextInt(6));
				}
				graphics.dispose();
				// Paper grain, so the JPEG is about as large as a real camera page.
				for (int i = 0; i < width * height / 8; i++) {
					int x = random.nextInt(width);
					int y = random.nextInt(height);
					int shade = 200 + random.nextInt(55);
					photo.setRGB(x, y, new Color(shade, shade, shade - 10).getRGB());
				}
				PDPage page = new PDPage(PDRectangle.A4);
				document.addPage(page);
				PDImageXObject image = JPEGFactory.createFromImage(document, photo, 0.92f);
				try (PDPageContentStream content = new PDPageContentStream(document, page)) {
					content.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
				}
			}
			document.save(target.toFile());
		}
		return target;
	}

}
