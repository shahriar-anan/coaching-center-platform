package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class LiveExamModuleController {

	private final LiveExamModuleService modules;

	private final LiveExamStaffModuleService staff;

	private final PermissionChecker permissions;

	private final ModuleCoverService covers;

	private final PackageRevisionService revisions;

	public LiveExamModuleController(LiveExamModuleService modules, LiveExamStaffModuleService staff,
			PermissionChecker permissions, ModuleCoverService covers, PackageRevisionService revisions) {
		this.modules = modules;
		this.staff = staff;
		this.permissions = permissions;
		this.covers = covers;
		this.revisions = revisions;
	}

	@PostMapping("/api/v1/live-exam-modules")
	public ResponseEntity<LiveExamModuleService.ModuleView> create(@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		requirePricePermission(body);
		return ResponseEntity.status(HttpStatus.CREATED).body(modules.create(body));
	}

	@PatchMapping("/api/v1/live-exam-modules/{id}")
	public LiveExamModuleService.ModuleView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		requirePricePermission(body);
		return modules.update(id, body);
	}

	@PostMapping("/api/v1/live-exam-modules/{id}/final-save")
	public LiveExamModuleService.ModuleView finalSave(@PathVariable UUID id) {
		require(Permissions.EXAM_MANAGE);
		return modules.finalSave(id);
	}

	/**
	 * Body {@code {"revisionId"}}. A staged revision is published by the Master Admin only, and a missing revisionId is
	 * accepted from the Master Admin alone (contracts §7 item 4).
	 */
	@PostMapping("/api/v1/live-exam-modules/{id}/publish")
	public LiveExamModuleService.ModuleView publish(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		if (revisions.hasRevision(id)) {
			require(Permissions.LIVE_EXAM_PUBLISH);
		}
		String revisionId = body != null && body.hasNonNull("revisionId") ? body.get("revisionId").asText() : null;
		return modules.publish(id, revisionId);
	}

	/**
	 * {@code status} and {@code q} apply to the staff read only; a student's list ignores them. {@code popular} keeps the
	 * popular packages for a student (NR-01, B5).
	 */
	@GetMapping("/api/v1/live-exam-modules")
	public PageResponse<LiveExamModuleService.ModuleView> list(@RequestParam(required = false) UUID categoryId,
			@RequestParam(required = false) String examType, @RequestParam(required = false) String status,
			@RequestParam(required = false) String q, @RequestParam(required = false, defaultValue = "false") boolean popular,
			@PageableDefault(size = 20) Pageable pageable) {
		String type = LiveExamTypeFilter.parse(examType);
		if (permissions.has(Permissions.EXAM_READ)) {
			return PageResponse.from(staff.list(categoryId, status, q, pageable));
		}
		if (permissions.has(Permissions.LIVE_EXAM_EVALUATE)) {
			return PageResponse.from(staff.listForInstructor(actorId(), pageable));
		}
		if (permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			return PageResponse.from(modules.list(categoryId, pageable, true, actorId(), type, popular));
		}
		throw denied();
	}

	@GetMapping("/api/v1/live-exam-modules/{id}")
	public LiveExamModuleService.ModuleView get(@PathVariable UUID id) {
		if (permissions.has(Permissions.EXAM_READ)) {
			return staff.get(id);
		}
		if (permissions.has(Permissions.LIVE_EXAM_EVALUATE)) {
			return staff.getAssigned(id, actorId());
		}
		if (permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			return modules.getPublished(id, actorId());
		}
		throw denied();
	}

	/** The cover for staff screens. The public cover route serves published modules only, so a draft shows its cover here. */
	@GetMapping("/api/v1/live-exam-modules/{id}/cover")
	public ResponseEntity<InputStreamResource> staffCover(@PathVariable UUID id) {
		require(Permissions.EXAM_READ);
		ModuleCoverService.CoverFile file = covers.open(id);
		return ResponseEntity.ok()
			.header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
			.contentType(MediaType.parseMediaType(file.contentType()))
			.contentLength(file.byteSize())
			.body(new InputStreamResource(file.body()));
	}

	@PostMapping(value = "/api/v1/live-exam-modules/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public LiveExamModuleService.ModuleView uploadCover(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		require(Permissions.MODULE_COVER_MANAGE);
		covers.upload(id, file);
		return staff.get(id);
	}

	@DeleteMapping("/api/v1/live-exam-modules/{id}/cover")
	public LiveExamModuleService.ModuleView removeCover(@PathVariable UUID id) {
		require(Permissions.MODULE_COVER_MANAGE);
		covers.remove(id);
		return staff.get(id);
	}

	@DeleteMapping("/api/v1/live-exam-modules/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		modules.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/live-exam-modules/{id}/archive")
	public ResponseEntity<Void> archive(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		modules.archive(id);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private void requirePricePermission(JsonNode body) {
		if (body.has("price") && !permissions.has(Permissions.EXAM_PRICE)) {
			throw denied();
		}
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private static UUID actorId() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof com.coachingcenter.api.auth.permission.CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
