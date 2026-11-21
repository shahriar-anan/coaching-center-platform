package com.coachingcenter.api.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Where viewing copies live, which file is served, and that they go with their original. */
class PdfViewCopiesTest {

	@TempDir
	Path dir;

	private LocalFileStorage storage() {
		return new LocalFileStorage(new FileStorageProperties(dir.toString(), FileStorageProperties.DEFAULT_MATERIAL_MAX_BYTES,
				FileStorageProperties.DEFAULT_EXAM_IMAGE_MAX_BYTES));
	}

	private PdfViewCopies copies(LocalFileStorage storage) {
		return new PdfViewCopies(storage, new PdfViewCopyMaker(1800, 0.72f, 0.7),
				new StaticListableBeanFactory().getBeanProvider(PdfViewCopies.ViewCopyPause.class), true);
	}

	@Test
	void servesTheCopyOnceMadeAndTheOriginalOnRequest() throws IOException {
		LocalFileStorage storage = storage();
		PdfViewCopies copies = copies(storage);
		PdfViewCopyMakerTest.scan(dir.resolve("abc"), 8, 2400, 3300);
		Path original = storage.resolve("abc");

		assertThat(copies.forViewing(original)).isEqualTo(original);
		assertThat(copies.makeNow("abc").outcome()).isEqualTo(PdfViewCopyMaker.Outcome.MADE);

		Path served = copies.forViewing(original);
		assertThat(served.getFileName().toString()).isEqualTo("abc.view.pdf");
		assertThat(PdfViewCopies.isCopy(served)).isTrue();
		assertThat(Files.size(served)).isLessThan(Files.size(original));
		assertThat(dir.resolve("abc.view.tmp")).doesNotExist();
	}

	@Test
	void deletingTheOriginalDeletesItsCopy() throws IOException {
		LocalFileStorage storage = storage();
		PdfViewCopies copies = copies(storage);
		PdfViewCopyMakerTest.scan(dir.resolve("abc"), 8, 2400, 3300);
		copies.makeNow("abc");

		storage.delete("abc");

		assertThat(dir.resolve("abc")).doesNotExist();
		assertThat(dir.resolve("abc.view.pdf")).doesNotExist();
	}

	@Test
	void marksAFileThatWouldNotShrinkSoItIsNotTriedAgain() throws IOException {
		LocalFileStorage storage = storage();
		PdfViewCopies copies = copies(storage);
		PdfViewCopyMakerTest.scan(dir.resolve("small"), 3, 1200, 1600);

		copies.makeNow("small");

		assertThat(dir.resolve("small.view.none")).exists();
		assertThat(copies.needsCopy("small")).isFalse();
		assertThat(copies.forViewing(storage.resolve("small"))).isEqualTo(storage.resolve("small"));
	}

	@Test
	void doesNotCopySmallFiles() throws IOException {
		LocalFileStorage storage = storage();
		PdfViewCopies copies = copies(storage);
		PdfViewCopyMakerTest.scan(dir.resolve("tiny"), 1, 1000, 1400);
		// One small page is well under the 3 MB floor.
		assertThat(Files.size(dir.resolve("tiny"))).isLessThan(PdfViewCopies.MIN_BYTES);
		assertThat(copies.needsCopy("tiny")).isFalse();
	}

}
