package com.coachingcenter.api.files;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDICCBased;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Makes a smaller copy of a scanned PDF for viewing (22 Nov 2026). Answer scripts arrive as phone photos of about
 * 2400×3300 px per page, 20–25 MB per script, which phones download slowly and struggle to decode. The copy scales each
 * large photo down to {@code longEdge} pixels on its long side and re-encodes it as JPEG. The image keeps its name and
 * the page draws it at the same size, so nothing on the page moves; text and the marker's vector annotations are
 * untouched.
 * <p>
 * Only plain photos are touched: one DCT or Flate filter, 8 bits per component, gray or RGB, no mask. Anything else
 * (JPEG 2000, JBIG2, CCITT, CMYK, masked or undecodable images) is left exactly as it is. The original file is never
 * changed; the caller writes the copy next to it.
 */
@Component
public class PdfViewCopyMaker {

	public enum Outcome {
		/** The copy was written and is enough smaller to serve. */
		MADE,
		/** Nothing worth shrinking, or the copy would not have been enough smaller. */
		NO_GAIN,
		/** The file could not be read or is past the limits; try again only if it changes. */
		SKIPPED
	}

	public record Result(Outcome outcome, long originalBytes, long copyBytes, int imagesScaled, String reason) {
	}

	/** Larger originals are not worth the time and memory; they are served as they are. */
	static final long MAX_ORIGINAL_BYTES = 100L * 1024 * 1024;

	/** A file with more images than this is unusual for a script; it is left alone rather than tying up the worker. */
	static final int MAX_IMAGES = 300;

	private final int longEdge;

	private final float quality;

	private final double keepAtMost;

	public PdfViewCopyMaker(@Value("${app.files.view-copy.long-edge:1800}") int longEdge,
			@Value("${app.files.view-copy.quality:0.72}") float quality,
			@Value("${app.files.view-copy.keep-at-most:0.7}") double keepAtMost) {
		this.longEdge = longEdge;
		this.quality = quality;
		this.keepAtMost = keepAtMost;
	}

	/**
	 * Writes the copy of {@code original} to {@code target} and reports what happened. {@code target} exists afterwards
	 * only when the outcome is {@link Outcome#MADE}. Never throws for a bad PDF.
	 */
	public Result make(Path original, Path target) {
		long size;
		try {
			size = Files.size(original);
		}
		catch (IOException exception) {
			return new Result(Outcome.SKIPPED, 0, 0, 0, "unreadable");
		}
		if (size > MAX_ORIGINAL_BYTES) {
			return new Result(Outcome.SKIPPED, size, 0, 0, "too large");
		}
		try (PDDocument document = Loader.loadPDF(original.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
			if (document.isEncrypted()) {
				// Opened without a password (owner lock only), so its pages are readable by anyone; a copy cannot be saved locked.
				document.setAllSecurityToBeRemoved(true);
			}
			Walk walk = new Walk();
			for (PDPage page : document.getPages()) {
				shrink(document, page.getResources(), walk);
				if (walk.tooMany) {
					return new Result(Outcome.SKIPPED, size, 0, 0, "too many images");
				}
			}
			if (walk.scaled == 0) {
				return new Result(Outcome.NO_GAIN, size, 0, 0, "no large photos");
			}
			document.save(target.toFile());
			long copy = Files.size(target);
			if (copy > size * keepAtMost) {
				Files.deleteIfExists(target);
				return new Result(Outcome.NO_GAIN, size, copy, walk.scaled, "not enough smaller");
			}
			return new Result(Outcome.MADE, size, copy, walk.scaled, null);
		}
		catch (IOException | RuntimeException | OutOfMemoryError failure) {
			try {
				Files.deleteIfExists(target);
			}
			catch (IOException ignored) {
				// The caller writes to a temp name and only renames a MADE copy, so a leftover is never served.
			}
			return new Result(Outcome.SKIPPED, size, 0, 0, failure.getClass().getSimpleName());
		}
	}

	/** What one pass has seen: replacements by original stream (an image shared by pages is scaled once), and visited forms. */
	private static final class Walk {

		final Map<COSStream, PDImageXObject> replaced = new IdentityHashMap<>();

		final Set<COSStream> kept = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

		final Set<COSBase> forms = new HashSet<>();

		int seen;

		int scaled;

		boolean tooMany;

	}

	private void shrink(PDDocument document, PDResources resources, Walk walk) throws IOException {
		if (resources == null) {
			return;
		}
		for (COSName name : resources.getXObjectNames()) {
			PDXObject xobject;
			try {
				xobject = resources.getXObject(name);
			}
			catch (IOException | RuntimeException broken) {
				continue;
			}
			if (xobject instanceof PDFormXObject form) {
				if (walk.forms.add(form.getCOSObject())) {
					shrink(document, form.getResources(), walk);
				}
				continue;
			}
			if (!(xobject instanceof PDImageXObject image)) {
				continue;
			}
			COSStream stream = image.getCOSObject();
			PDImageXObject done = walk.replaced.get(stream);
			if (done != null) {
				resources.put(name, done);
				continue;
			}
			if (walk.kept.contains(stream)) {
				continue;
			}
			if (++walk.seen > MAX_IMAGES) {
				walk.tooMany = true;
				return;
			}
			PDImageXObject smaller = smaller(document, image);
			if (smaller == null) {
				walk.kept.add(stream);
				continue;
			}
			walk.replaced.put(stream, smaller);
			walk.scaled++;
			resources.put(name, smaller);
		}
	}

	/** The scaled replacement, or null when the image is small or not a plain photo. */
	private PDImageXObject smaller(PDDocument document, PDImageXObject image) {
		try {
			int width = image.getWidth();
			int height = image.getHeight();
			if (Math.max(width, height) <= longEdge * 1.1 || image.isStencil() || image.getBitsPerComponent() != 8) {
				return null;
			}
			COSStream stream = image.getCOSObject();
			if (stream.containsKey(COSName.SMASK) || stream.containsKey(COSName.MASK) || stream.containsKey(COSName.DECODE)) {
				return null;
			}
			List<COSName> filters = image.getStream().getFilters();
			if (filters.size() != 1 || !(COSName.DCT_DECODE.equals(filters.get(0)) || COSName.FLATE_DECODE.equals(filters.get(0)))) {
				return null;
			}
			Boolean gray = grayOrRgb(image.getColorSpace());
			if (gray == null) {
				return null;
			}
			BufferedImage decoded = image.getImage();
			if (decoded == null) {
				return null;
			}
			double factor = (double) longEdge / Math.max(width, height);
			int targetWidth = Math.max(1, (int) Math.round(width * factor));
			int targetHeight = Math.max(1, (int) Math.round(height * factor));
			BufferedImage scaled = scale(decoded, targetWidth, targetHeight, gray);
			return JPEGFactory.createFromImage(document, scaled, quality);
		}
		catch (IOException | RuntimeException | OutOfMemoryError failure) {
			return null;
		}
	}

	/** True for gray, false for RGB, null for anything else (CMYK, indexed, Lab, separations). */
	private static Boolean grayOrRgb(PDColorSpace space) {
		if (space instanceof PDDeviceGray) {
			return true;
		}
		if (space instanceof PDDeviceRGB) {
			return false;
		}
		if (space instanceof PDICCBased icc) {
			int components = icc.getNumberOfComponents();
			return components == 1 ? Boolean.TRUE : components == 3 ? Boolean.FALSE : null;
		}
		return null;
	}

	/**
	 * Scales in halving steps and finishes with bicubic, which keeps thin pen strokes far better than one big bilinear
	 * step. The result is plain gray or RGB, as JPEG needs.
	 */
	static BufferedImage scale(BufferedImage source, int targetWidth, int targetHeight, boolean gray) {
		int type = gray ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
		BufferedImage current = source;
		int width = source.getWidth();
		int height = source.getHeight();
		while (width / 2 >= targetWidth && height / 2 >= targetHeight) {
			width /= 2;
			height /= 2;
			current = draw(current, width, height, type, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		return draw(current, targetWidth, targetHeight, type, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	}

	private static BufferedImage draw(BufferedImage source, int width, int height, int type, Object interpolation) {
		BufferedImage target = new BufferedImage(width, height, type);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(source, 0, 0, width, height, null);
		}
		finally {
			graphics.dispose();
		}
		return target;
	}

}
