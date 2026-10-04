package com.coachingcenter.api.files;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.Material;
import com.coachingcenter.api.courses.MaterialRepository;

@RestController
public class FileController {

	private final MaterialRepository materials;

	private final LocalFileStorage fileStorage;

	private final FileAccessService access;

	public FileController(MaterialRepository materials, LocalFileStorage fileStorage, FileAccessService access) {
		this.materials = materials;
		this.fileStorage = fileStorage;
		this.access = access;
	}

	@GetMapping("/api/v1/files/{id}")
	public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws Exception {
		Material material = materials.findByIdAndDeletedAtIsNull(id)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
		access.ensureCanDownload(material);
		Path path = fileStorage.resolve(material.getStoredFileName());
		if (!Files.exists(path)) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		InputStreamResource body = new InputStreamResource(Files.newInputStream(path));
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + material.getOriginalFileName() + "\"")
			.contentType(MediaType.parseMediaType(material.getContentType()))
			.contentLength(material.getByteSize())
			.body(body);
	}

}
