package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.files.FileResponses;
import com.coachingcenter.api.offline.OfflineBatchService.BatchView;
import com.coachingcenter.api.offline.OfflineBatchService.PhotoFile;
import com.coachingcenter.api.offline.OfflineBatchService.PublicBatchView;
import com.coachingcenter.api.offline.OfflineBatchService.RoutineFile;

/**
 * The public batch catalogue (V93, public-admission plan section 5): the cards, the batch page, its photo and the student copy of its
 * live routine. Under the {@code GET /api/v1/public/**} rule, so no sign-in is needed, and behind the offline switch like every
 * offline route. An archived batch answers 404 on each of them.
 */
@RestController
public class OfflineBatchPublicController {

	private final OfflineBatchService batches;

	private final OfflineRoutineService routines;

	public OfflineBatchPublicController(OfflineBatchService batches, OfflineRoutineService routines) {
		this.batches = batches;
		this.routines = routines;
	}

	@GetMapping("/api/v1/public/offline-batches")
	public List<PublicBatchView> list() {
		return batches.publicList();
	}

	@GetMapping("/api/v1/public/offline-batches/{id}")
	public PublicBatchView get(@PathVariable UUID id) {
		return batches.publicGet(id);
	}

	/** The batch photo. Public, so a shared cache may keep it for a few minutes; the link carries the upload time. */
	@GetMapping("/api/v1/public/offline-batches/{id}/photo")
	public ResponseEntity<InputStreamResource> photo(@PathVariable UUID id) {
		PhotoFile file = batches.openPhoto(id);
		return FileResponses.inline(file.body(), file.contentType() == null ? MediaType.IMAGE_JPEG_VALUE : file.contentType(),
				file.byteSize(), "batch-photo", "public, max-age=300");
	}

	/** The student copy of the live routine (no instructor names), or 404 when the batch has none. Private: the browser may keep it. */
	@GetMapping("/api/v1/public/offline-batches/{id}/routine.pdf")
	public ResponseEntity<InputStreamResource> routine(@PathVariable UUID id) {
		batches.requirePublic(id);
		RoutineFile file = routines.studentCopy(id, null);
		return FileResponses.inline(file.body(), MediaType.APPLICATION_PDF_VALUE, file.byteSize(), file.fileName(),
				"private, no-cache");
	}

}
