package com.coachingcenter.api.courses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class CourseInstructorService {

	private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final CourseRepository courses;

	private final CourseInstructorRepository instructors;

	private final CourseMaterialService materials;

	private final AuditRecorder audit;

	public CourseInstructorService(CourseRepository courses, CourseInstructorRepository instructors,
			CourseMaterialService materials, AuditRecorder audit) {
		this.courses = courses;
		this.instructors = instructors;
		this.materials = materials;
		this.audit = audit;
	}

	@Transactional
	public InstructorView create(UUID courseId, JsonNode body) {
		requireCourse(courseId);
		CourseInstructor instructor = new CourseInstructor();
		instructor.setCourseId(courseId);
		instructor.setName(requiredName(body));
		if (body.has("description") && !body.get("description").isNull()) {
			instructor.setDescription(body.get("description").asText());
		}
		List<CourseInstructor> existing = instructors.findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(courseId);
		instructor.setPosition(existing.size());
		instructor = instructors.save(instructor);
		if (body.has("position") && !body.get("position").isNull()) {
			place(instructor, body.get("position").asInt());
		}
		return view(instructor);
	}

	@Transactional
	public InstructorView update(UUID courseId, UUID instructorId, JsonNode body) {
		requireCourse(courseId);
		CourseInstructor instructor = requireInstructor(courseId, instructorId);
		if (body.has("name") && !body.get("name").isNull()) {
			instructor.setName(requiredText(body.get("name")));
		}
		if (body.has("description")) {
			if (body.get("description").isNull()) {
				instructor.setDescription(null);
			}
			else {
				instructor.setDescription(body.get("description").asText());
			}
		}
		if (body.has("position") && !body.get("position").isNull()) {
			place(instructor, body.get("position").asInt());
		}
		return view(instructor);
	}

	@Transactional
	public InstructorView uploadPhoto(UUID courseId, UUID instructorId, MultipartFile file) {
		requireCourse(courseId);
		CourseInstructor instructor = requireInstructor(courseId, instructorId);
		if (file == null || file.isEmpty() || file.getContentType() == null || !PHOTO_TYPES.contains(file.getContentType())) {
			throw validationFailed();
		}
		UUID previous = instructor.getPhotoFileId();
		Material stored = materials.storeCourseFile(courseId, file);
		instructor.setPhotoFileId(stored.getId());
		if (previous != null) {
			materials.discard(courseId, previous);
		}
		return view(instructor);
	}

	@Transactional
	public void delete(UUID courseId, UUID instructorId) {
		requireCourse(courseId);
		CourseInstructor instructor = requireInstructor(courseId, instructorId);
		if (instructor.getPhotoFileId() != null) {
			materials.discard(courseId, instructor.getPhotoFileId());
		}
		instructor.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.COURSE_INSTRUCTOR_DELETE, AuditRecorder.COURSE_INSTRUCTOR_ENTITY, instructor.getId());
	}

	@Transactional(readOnly = true)
	public List<InstructorView> list(UUID courseId) {
		requireCourse(courseId);
		return views(courseId);
	}

	@Transactional(readOnly = true)
	public List<InstructorView> views(UUID courseId) {
		return instructors.findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(courseId)
			.stream()
			.map(CourseInstructorService::view)
			.toList();
	}

	private void place(CourseInstructor instructor, int position) {
		if (position < 0) {
			throw validationFailed();
		}
		List<CourseInstructor> ordered = new ArrayList<>(
				instructors.findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(instructor.getCourseId()));
		ordered.removeIf(item -> item.getId().equals(instructor.getId()));
		int index = Math.min(position, ordered.size());
		ordered.add(index, instructor);
		for (int i = 0; i < ordered.size(); i++) {
			ordered.get(i).setPosition(i);
		}
	}

	private Course requireCourse(UUID courseId) {
		return courses.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseInstructorService::notFound);
	}

	private CourseInstructor requireInstructor(UUID courseId, UUID instructorId) {
		return instructors.findByIdAndCourseIdAndDeletedAtIsNull(instructorId, courseId)
			.orElseThrow(CourseInstructorService::notFound);
	}

	private static String requiredName(JsonNode body) {
		if (body == null || !body.has("name")) {
			throw validationFailed();
		}
		return requiredText(body.get("name"));
	}

	private static String requiredText(JsonNode node) {
		if (node == null || node.isNull() || node.asText().isBlank() || node.asText().trim().length() > 500) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static InstructorView view(CourseInstructor instructor) {
		return new InstructorView(instructor.getId(), instructor.getName(), instructor.getDescription(),
				instructor.getPhotoFileId(), instructor.getPosition());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private static ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record InstructorView(UUID id, String name, String description, UUID photoFileId, int position) {
	}

}
