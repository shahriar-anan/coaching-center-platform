package com.coachingcenter.api.files;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.stereotype.Component;

@Component
public class ExamImageProcessor {

	static {
		ImageIO.scanForPlugins();
	}

	private static final int MAX_LONGEST_SIDE = 1600;

	private static final float JPEG_QUALITY = 0.72f;

	public ProcessedImage process(InputStream input) throws IOException {
		byte[] bytes = input.readAllBytes();
		int orientation = readOrientation(bytes);
		try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
			if (!readers.hasNext()) {
				throw new IOException("Unsupported image.");
			}
			ImageReader reader = readers.next();
			reader.setInput(imageInput);
			BufferedImage image = reader.read(0);
			reader.dispose();
			if (image == null) {
				throw new IOException("Could not decode image.");
			}
			image = applyOrientation(image, orientation);
			image = scaleDown(image);
			byte[] jpeg = encodeJpeg(image);
			return new ProcessedImage(jpeg, "image/jpeg");
		}
	}

	private static BufferedImage applyOrientation(BufferedImage image, int orientation) {
		if (orientation <= 1) {
			return image;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		AffineTransform transform = new AffineTransform();
		int newWidth = width;
		int newHeight = height;
		switch (orientation) {
			case 2 -> {
				transform.scale(-1, 1);
				transform.translate(-width, 0);
			}
			case 3 -> {
				transform.translate(width, height);
				transform.rotate(Math.PI);
			}
			case 4 -> {
				transform.scale(1, -1);
				transform.translate(0, -height);
			}
			case 5 -> {
				transform.rotate(Math.PI / 2);
				transform.scale(1, -1);
				newWidth = height;
				newHeight = width;
			}
			case 6 -> {
				transform.translate(height, 0);
				transform.rotate(Math.PI / 2);
				newWidth = height;
				newHeight = width;
			}
			case 7 -> {
				transform.scale(-1, 1);
				transform.translate(-height, 0);
				transform.translate(height, width);
				transform.rotate(Math.PI);
				newWidth = height;
				newHeight = width;
			}
			case 8 -> {
				transform.translate(0, width);
				transform.rotate(3 * Math.PI / 2);
				newWidth = height;
				newHeight = width;
			}
			default -> {
				return image;
			}
		}
		BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = rotated.createGraphics();
		graphics.drawImage(image, transform, null);
		graphics.dispose();
		return rotated;
	}

	private static BufferedImage scaleDown(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int longest = Math.max(width, height);
		if (longest <= MAX_LONGEST_SIDE) {
			return toRgb(image);
		}
		double scale = MAX_LONGEST_SIDE / (double) longest;
		int targetWidth = (int) Math.round(width * scale);
		int targetHeight = (int) Math.round(height * scale);
		BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = scaled.createGraphics();
		graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
		graphics.dispose();
		return scaled;
	}

	private static BufferedImage toRgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_RGB) {
			return image;
		}
		BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = rgb.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return rgb;
	}

	private static byte[] encodeJpeg(BufferedImage image) throws IOException {
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			throw new IOException("JPEG encoder unavailable.");
		}
		ImageWriter writer = writers.next();
		JPEGImageWriteParam param = new JPEGImageWriteParam(null);
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(JPEG_QUALITY);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
			writer.setOutput(stream);
			writer.write(null, new IIOImage(image, null, null), param);
			writer.dispose();
		}
		return output.toByteArray();
	}

	private static int readOrientation(byte[] bytes) {
		if (bytes.length < 4 || bytes[0] != (byte) 0xFF || bytes[1] != (byte) 0xD8) {
			return 1;
		}
		int offset = 2;
		while (offset + 4 < bytes.length) {
			if (bytes[offset] != (byte) 0xFF) {
				break;
			}
			int marker = bytes[offset + 1] & 0xFF;
			if (marker == 0xE1) {
				int length = ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
				if (offset + length > bytes.length) {
					break;
				}
				int start = offset + 10;
				int end = offset + 2 + length - 2;
				return readExifOrientation(bytes, start, end);
			}
			if (marker == 0xD9 || marker == 0xDA) {
				break;
			}
			int segmentLength = ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
			if (segmentLength < 2) {
				break;
			}
			offset += 2 + segmentLength;
		}
		return 1;
	}

	private static int readExifOrientation(byte[] bytes, int start, int end) {
		if (start + 8 > end) {
			return 1;
		}
		if (!"Exif".equals(new String(bytes, start, 4))) {
			return 1;
		}
		int tiff = start + 6;
		if (tiff + 8 > end) {
			return 1;
		}
		boolean littleEndian = bytes[tiff] == 'I' && bytes[tiff + 1] == 'I';
		int ifdOffset = readInt(bytes, tiff + 4, littleEndian);
		int ifd = tiff + ifdOffset;
		if (ifd + 2 > end) {
			return 1;
		}
		int entries = readShort(bytes, ifd, littleEndian);
		for (int i = 0; i < entries; i++) {
			int entry = ifd + 2 + i * 12;
			if (entry + 12 > end) {
				break;
			}
			int tag = readShort(bytes, entry, littleEndian);
			if (tag == 0x0112) {
				return readShort(bytes, entry + 8, littleEndian);
			}
		}
		return 1;
	}

	private static int readShort(byte[] bytes, int offset, boolean littleEndian) {
		int b0 = bytes[offset] & 0xFF;
		int b1 = bytes[offset + 1] & 0xFF;
		return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
	}

	private static int readInt(byte[] bytes, int offset, boolean littleEndian) {
		int b0 = bytes[offset] & 0xFF;
		int b1 = bytes[offset + 1] & 0xFF;
		int b2 = bytes[offset + 2] & 0xFF;
		int b3 = bytes[offset + 3] & 0xFF;
		return littleEndian ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
				: (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
	}

	public record ProcessedImage(byte[] bytes, String contentType) {
	}

}
