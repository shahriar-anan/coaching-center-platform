package com.coachingcenter.api.exams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Component
public class PdfCompressor {

	public byte[] compress(byte[] pdf) {
		try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			for (PDPage page : document.getPages()) {
				try (InputStream contents = page.getContents()) {
					if (contents == null) {
						continue;
					}
					byte[] raw = contents.readAllBytes();
					PDStream compressed = new PDStream(document);
					try (OutputStream encoded = compressed.createOutputStream(COSName.FLATE_DECODE)) {
						encoded.write(raw);
					}
					page.setContents(compressed);
				}
			}
			document.save(output);
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

}
