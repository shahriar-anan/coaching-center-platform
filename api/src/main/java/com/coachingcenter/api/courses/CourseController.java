package com.coachingcenter.api.courses;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class CourseController {

	private final CourseService courses;

	private final ContentNodeService contentNodes;

	private final CourseMaterialService courseMaterials;

	private final CourseRoutineService routine;

	private final CourseInstructorService instructors;

	private final PermissionChecker permissions;

	public CourseController(CourseService courses, ContentNodeService contentNodes,
			CourseMaterialService courseMaterials, CourseRoutineService routine, CourseInstructorService instructors,
			PermissionChecker permissions) {
		this.courses = courses;
		this.contentNodes = contentNodes;
		this.courseMaterials = courseMaterials;
		this.routine = routine;
		this.instructors = instructors;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/courses")
	public ResponseEntity<CourseService.CourseView> create(@RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(courses.create(body, canSetPrice()));
	}

	@PatchMapping("/api/v1/courses/{id}")
	public CourseService.CourseView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return courses.update(id, body, canSetPrice());
	}

	@GetMapping("/api/v1/courses")
	public PageResponse<CourseService.CourseView> list(@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.COURSE_READ);
		return PageResponse.from(courses.list(pageable));
	}

	@GetMapping("/api/v1/courses/{id}")
	public CourseService.CourseView get(@PathVariable UUID id) {
		require(Permissions.COURSE_READ);
		return courses.get(id);
	}

	@PutMapping("/api/v1/courses/{id}/routine")
	public java.util.List<CourseRoutineService.SlotView> replaceRoutine(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return routine.replace(id, body);
	}

	@GetMapping("/api/v1/courses/{id}/routine")
	public java.util.List<CourseRoutineService.SlotView> getRoutine(@PathVariable UUID id) {
		require(Permissions.COURSE_READ);
		return routine.list(id);
	}

	@GetMapping("/api/v1/courses/{id}/instructors")
	public java.util.List<CourseInstructorService.InstructorView> listInstructors(@PathVariable UUID id) {
		require(Permissions.COURSE_READ);
		return instructors.list(id);
	}

	@PostMapping("/api/v1/courses/{id}/instructors")
	public ResponseEntity<CourseInstructorService.InstructorView> createInstructor(@PathVariable UUID id,
			@RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(instructors.create(id, body));
	}

	@PatchMapping("/api/v1/courses/{id}/instructors/{instructorId}")
	public CourseInstructorService.InstructorView updateInstructor(@PathVariable UUID id, @PathVariable UUID instructorId,
			@RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return instructors.update(id, instructorId, body);
	}

	@PostMapping(path = "/api/v1/courses/{id}/instructors/{instructorId}/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public CourseInstructorService.InstructorView uploadInstructorPhoto(@PathVariable UUID id,
			@PathVariable UUID instructorId, @RequestPart("file") MultipartFile file) {
		require(Permissions.COURSE_MANAGE);
		return instructors.uploadPhoto(id, instructorId, file);
	}

	@DeleteMapping("/api/v1/courses/{id}/instructors/{instructorId}")
	public ResponseEntity<Void> deleteInstructor(@PathVariable UUID id, @PathVariable UUID instructorId) {
		require(Permissions.COURSE_DELETE);
		instructors.delete(id, instructorId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/v1/courses/{id}/nodes")
	public java.util.List<ContentNodeService.NodeView> listNodes(@PathVariable UUID id) {
		require(Permissions.COURSE_READ);
		return contentNodes.list(id);
	}

	@GetMapping("/api/v1/courses/{id}/materials")
	public java.util.List<CourseMaterialService.MaterialView> listMaterials(@PathVariable UUID id) {
		require(Permissions.COURSE_READ);
		return courseMaterials.list(id);
	}

	@DeleteMapping("/api/v1/courses/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.COURSE_DELETE);
		courses.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/courses/{id}/archive")
	public ResponseEntity<Void> archive(@PathVariable UUID id) {
		require(Permissions.COURSE_DELETE);
		courses.archive(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/courses/{id}/nodes")
	public ResponseEntity<ContentNodeService.NodeView> createNode(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(contentNodes.create(id, body));
	}

	@PatchMapping("/api/v1/courses/{id}/nodes/{nodeId}")
	public ContentNodeService.NodeView updateNode(@PathVariable UUID id, @PathVariable UUID nodeId,
			@RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return contentNodes.update(id, nodeId, body);
	}

	@PostMapping("/api/v1/courses/{id}/nodes/reorder")
	public ResponseEntity<Void> reorderNodes(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		contentNodes.reorder(id, body);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/courses/{id}/nodes/{nodeId}")
	public ResponseEntity<Void> deleteNode(@PathVariable UUID id, @PathVariable UUID nodeId) {
		require(Permissions.COURSE_DELETE);
		contentNodes.delete(id, nodeId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/courses/{id}/close")
	public CourseService.CourseView close(@PathVariable UUID id) {
		require(Permissions.COURSE_CLOSE);
		return courses.close(id);
	}

	@PostMapping("/api/v1/courses/{id}/reopen")
	public CourseService.CourseView reopen(@PathVariable UUID id) {
		require(Permissions.COURSE_CLOSE);
		return courses.reopen(id);
	}

	@PostMapping(path = "/api/v1/courses/{id}/cover", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CourseMaterialService.MaterialView> uploadCover(@PathVariable UUID id,
			@RequestPart("file") MultipartFile file) {
		require(Permissions.COURSE_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(courseMaterials.uploadCover(id, file));
	}

	@PostMapping(path = "/api/v1/courses/{id}/materials", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CourseMaterialService.MaterialView> uploadMaterial(@PathVariable UUID id,
			@RequestPart("file") MultipartFile file, @RequestParam(required = false) UUID lectureNodeId,
			@RequestParam(required = false) UUID sectionNodeId, @RequestParam(defaultValue = "false") boolean free,
			@RequestParam(required = false) String title, @RequestParam(required = false) String description,
			@RequestParam(required = false) String kind) {
		require(Permissions.COURSE_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(courseMaterials.uploadMaterial(id, lectureNodeId, sectionNodeId, free, title, description, kind, file));
	}

	@PostMapping(path = "/api/v1/courses/{id}/materials/batch", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<List<CourseMaterialService.MaterialView>> uploadMaterials(@PathVariable UUID id,
			@RequestParam("files") List<MultipartFile> files, @RequestParam(required = false) UUID lectureNodeId,
			@RequestParam(required = false) UUID sectionNodeId, @RequestParam(required = false) List<String> titles,
			@RequestParam(required = false) List<String> descriptions, @RequestParam(required = false) List<String> kinds) {
		require(Permissions.COURSE_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(courseMaterials.uploadBatch(id, lectureNodeId, sectionNodeId, files, titles, descriptions, kinds));
	}

	@PutMapping(path = "/api/v1/courses/{id}/materials/{materialId}", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
	public CourseMaterialService.MaterialView replaceMaterial(@PathVariable UUID id, @PathVariable UUID materialId,
			@RequestPart("file") MultipartFile file) {
		require(Permissions.COURSE_MANAGE);
		return courseMaterials.replace(id, file, materialId);
	}

	@DeleteMapping("/api/v1/courses/{id}/materials/{materialId}")
	public ResponseEntity<Void> deleteMaterial(@PathVariable UUID id, @PathVariable UUID materialId) {
		require(Permissions.COURSE_DELETE);
		courseMaterials.delete(id, materialId);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/api/v1/courses/{id}/materials/{materialId}")
	public CourseMaterialService.MaterialView updateMaterial(@PathVariable UUID id, @PathVariable UUID materialId,
			@RequestBody JsonNode body) {
		require(Permissions.COURSE_MANAGE);
		return courseMaterials.update(id, materialId, body);
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private boolean canSetPrice() {
		return permissions.has(Permissions.COURSE_PRICE);
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

}
