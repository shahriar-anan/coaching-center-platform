package com.coachingcenter.api.exams;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.PdfUploads;

/**
 * The optional routine PDF of a package (notice media plan, F3). The Master Admin uploads it with the cover's permission;
 * anyone can view it once the package is public. The upload applies at once and is not part of the package revision.
 */
@Service
public class ModuleRoutinePdfService {

	/** The largest routine PDF that may be uploaded. */
	public static final long MAX_ROUTINE_BYTES = 20L * 1024 * 1024;

	public static final String CONTENT_TYPE = "application/pdf";

	private static final String FALLBACK_NAME = "routine.pdf";

	private static final int MAX_NAME_LENGTH = 255;

	private final ModuleRoutinePdfRepository routines;

	private final LiveExamModuleRepository modules;

	private final PdfUploads uploads;

	private final LocalFileStorage fileStorage;

	private final AuditRecorder audit;

	public ModuleRoutinePdfService(ModuleRoutinePdfRepository routines, LiveExamModuleRepository modules,
			PdfUploads uploads, LocalFileStorage fileStorage, AuditRecorder audit) {
		this.routines = routines;
		this.modules = modules;
		this.uploads = uploads;
		this.fileStorage = fileStorage;
		this.audit = audit;
	}

	/**
	 * Replaces the routine PDF. The file is registered for commit before the row is saved, so a rolled-back upload deletes
	 * its own file, and the previous file is deleted only after the new row commits.
	 */
	@Transactional
	public void upload(UUID moduleId, MultipartFile file, UUID actorId) {
		requireModule(moduleId);
		LocalFileStorage.StoredFile stored = uploads.store(file, MAX_ROUTINE_BYTES);
		Optional<ModuleRoutinePdf> existing = routines.findById(moduleId);
		String previous = existing.map(ModuleRoutinePdf::getStoredName).orElse(null);
		fileStorage.commitReplacement(stored.storedFileName(), previous);
		ModuleRoutinePdf row = existing.orElseGet(ModuleRoutinePdf::new);
		row.setModuleId(moduleId);
		row.setStoredName(stored.storedFileName());
		row.setOriginalName(originalName(stored.originalFileName()));
		row.setByteSize(stored.byteSize());
		row.setUploadedBy(actorId);
		row.setUploadedAt(Instant.now());
		routines.save(row);
		audit.record(AuditRecorder.MODULE_ROUTINE_UPLOAD, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
	}

	/** Clears the routine PDF. A package with none is left unchanged. */
	@Transactional
	public void remove(UUID moduleId) {
		requireModule(moduleId);
		Optional<ModuleRoutinePdf> existing = routines.findById(moduleId);
		if (existing.isEmpty()) {
			return;
		}
		String previous = existing.get().getStoredName();
		routines.delete(existing.get());
		fileStorage.commitReplacement(null, previous);
		audit.record(AuditRecorder.MODULE_ROUTINE_REMOVE, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
	}

	/** The bytes of a package's routine PDF, or 404 when it has none. The caller checks the package is visible to it. */
	@Transactional(readOnly = true)
	public RoutinePdfFile open(UUID moduleId) {
		requireModule(moduleId);
		ModuleRoutinePdf row = routines.findById(moduleId).orElseThrow(ModuleRoutinePdfService::notFound);
		Path path = fileStorage.resolve(row.getStoredName());
		if (!Files.exists(path)) {
			throw notFound();
		}
		try {
			InputStream body = Files.newInputStream(path);
			return new RoutinePdfFile(body, fileName(row), row.getByteSize());
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/**
	 * The routine PDFs of a page of packages, keyed by package id, in one query. A package with no PDF is absent from the map,
	 * so the caller reads null for it.
	 */
	public Map<UUID, RoutinePdfView> viewsFor(Collection<UUID> moduleIds) {
		Map<UUID, RoutinePdfView> byModule = new HashMap<>();
		if (moduleIds.isEmpty()) {
			return byModule;
		}
		for (ModuleRoutinePdf row : routines.findAllById(moduleIds)) {
			byModule.put(row.getModuleId(), view(row));
		}
		return byModule;
	}

	/** One package's routine PDF as the public read shows it, or null when it has none. */
	public RoutinePdfView viewFor(UUID moduleId) {
		return viewsFor(List.of(moduleId)).get(moduleId);
	}

	/**
	 * The routine PDF of a package. {@code url} is the public path, with the upload time as a cache-busting version. A
	 * package with no PDF carries null in its response, not an absent field.
	 */
	public record RoutinePdfView(String url, String fileName, long byteSize, Instant updatedAt) {
	}

	/** The stored bytes of a routine PDF. The response writes the stream and closes it. */
	public record RoutinePdfFile(InputStream body, String fileName, long byteSize) {
	}

	private static RoutinePdfView view(ModuleRoutinePdf row) {
		long version = row.getUploadedAt().toEpochMilli();
		return new RoutinePdfView("/api/v1/public/live-exam-modules/" + row.getModuleId() + "/routine.pdf?v=" + version,
				fileName(row), row.getByteSize(), row.getUploadedAt());
	}

	private static String fileName(ModuleRoutinePdf row) {
		return row.getOriginalName() == null ? FALLBACK_NAME : row.getOriginalName();
	}

	/** The browser's name without its folders, trimmed to the column's length. Blank becomes null. */
	private static String originalName(String name) {
		if (name == null) {
			return null;
		}
		String base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).trim();
		if (base.isEmpty()) {
			return null;
		}
		return base.length() > MAX_NAME_LENGTH ? base.substring(0, MAX_NAME_LENGTH) : base;
	}

	private LiveExamModule requireModule(UUID id) {
		return modules.findByIdAndDeletedAtIsNull(id).orElseThrow(ModuleRoutinePdfService::notFound);
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

}
