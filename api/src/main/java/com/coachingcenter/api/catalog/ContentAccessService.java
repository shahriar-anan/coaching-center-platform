package com.coachingcenter.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.ContentNode;
import com.coachingcenter.api.courses.ContentNodeRepository;
import com.coachingcenter.api.courses.Course;
import com.coachingcenter.api.courses.CourseRepository;
import com.coachingcenter.api.courses.Material;
import com.coachingcenter.api.courses.MaterialRepository;
import com.coachingcenter.api.purchases.Enrollment;
import com.coachingcenter.api.purchases.EnrollmentRepository;

@Service
public class ContentAccessService {

	private final CourseRepository courses;

	private final ContentNodeRepository nodes;

	private final MaterialRepository materials;

	private final EnrollmentRepository enrollments;

	public ContentAccessService(CourseRepository courses, ContentNodeRepository nodes, MaterialRepository materials,
			EnrollmentRepository enrollments) {
		this.courses = courses;
		this.nodes = nodes;
		this.materials = materials;
		this.enrollments = enrollments;
	}

	@Transactional(readOnly = true)
	public LectureContent getLecture(UUID courseId, UUID lectureId, UUID studentId) {
		Course course = requireOpenStudentCourse(courseId);
		ContentNode lecture = nodes.findByIdAndCourseIdAndDeletedAtIsNull(lectureId, courseId)
			.filter(node -> "LECTURE".equals(node.getNodeType()))
			.orElseThrow(this::notFound);
		requireLectureAccess(course, lecture, studentId);
		List<Material> lectureMaterials = materials.findByLectureNodeIdAndDeletedAtIsNull(lectureId);
		List<UUID> materialIds = lectureMaterials.stream().map(Material::getId).toList();
		List<LectureMaterialSummary> materialViews = lectureMaterials.stream()
			.map(material -> new LectureMaterialSummary(material.getId(), material.getOriginalFileName(),
					material.getContentType(), material.getByteSize()))
			.toList();
		return new LectureContent(lecture.getId(), lecture.getTitle(), lecture.getVideoRef(), lecture.getNotes(),
				materialIds, materialViews);
	}

	@Transactional(readOnly = true)
	public MaterialContent getCourseMaterial(UUID courseId, UUID materialId, UUID studentId) {
		Course course = requireOpenStudentCourse(courseId);
		Material material = materials.findByIdAndCourseIdAndDeletedAtIsNull(materialId, courseId)
			.filter(item -> item.getLectureNodeId() == null)
			.orElseThrow(this::notFound);
		requireMaterialAccess(course, material, studentId);
		return new MaterialContent(material.getId(), material.getOriginalFileName(), material.getByteSize(),
				material.getContentType(), material.isFree());
	}

	@Transactional(readOnly = true)
	public List<MyCourse> listMyCourses(UUID studentId) {
		return enrollments.findByStudentIdAndStatusAndDeletedAtIsNull(studentId, "ACTIVE")
			.stream()
			.map(enrollment -> {
				Course course = courses.findByIdAndDeletedAtIsNull(enrollment.getCourseId()).orElse(null);
				if (course == null || !isOpenForStudents(course)) {
					return null;
				}
				if (enrollment.getOpeningNumber() != course.getOpeningNumber()) {
					return null;
				}
				return new MyCourse(course.getId(), course.getTitleEn(), course.getTitleBn());
			})
			.filter(item -> item != null)
			.toList();
	}

	public void ensureCanDownload(Material material, UUID studentId) {
		if (material.getCourseId() == null) {
			throw materialForbidden();
		}
		Course course = courses.findByIdAndDeletedAtIsNull(material.getCourseId()).orElseThrow(this::notFound);
		if (!isOpenForStudents(course)) {
			throw notFound();
		}
		if (material.getLectureNodeId() != null) {
			ContentNode lecture = nodes.findByIdAndCourseIdAndDeletedAtIsNull(material.getLectureNodeId(),
					material.getCourseId())
				.orElseThrow(this::notFound);
			if (!canOpenLecture(course, lecture, studentId)) {
				throw materialForbidden();
			}
			return;
		}
		if (!canOpenCourseMaterial(course, material, studentId)) {
			throw materialForbidden();
		}
	}

	private void requireLectureAccess(Course course, ContentNode lecture, UUID studentId) {
		if (!canOpenLecture(course, lecture, studentId)) {
			throw enrollmentRequired();
		}
	}

	private void requireMaterialAccess(Course course, Material material, UUID studentId) {
		if (!canOpenCourseMaterial(course, material, studentId)) {
			throw enrollmentRequired();
		}
	}

	private static boolean canOpenLecture(Course course, ContentNode lecture, boolean enrolled) {
		if ("FULL_FREE".equals(course.getAccessType()) || lecture.isFree()) {
			return true;
		}
		return enrolled;
	}

	private static boolean canOpenCourseMaterial(Course course, Material material, boolean enrolled) {
		if ("FULL_FREE".equals(course.getAccessType()) || material.isFree()) {
			return true;
		}
		return enrolled;
	}

	private boolean canOpenLecture(Course course, ContentNode lecture, UUID studentId) {
		return canOpenLecture(course, lecture, hasActiveEnrollmentForOpening(studentId, course));
	}

	private boolean canOpenCourseMaterial(Course course, Material material, UUID studentId) {
		return canOpenCourseMaterial(course, material, hasActiveEnrollmentForOpening(studentId, course));
	}

	private boolean hasActiveEnrollmentForOpening(UUID studentId, Course course) {
		return enrollments.existsByStudentIdAndCourseIdAndOpeningNumberAndStatusAndDeletedAtIsNull(studentId,
				course.getId(), course.getOpeningNumber(), "ACTIVE");
	}

	private Course requireOpenStudentCourse(UUID courseId) {
		Course course = courses.findByIdAndDeletedAtIsNull(courseId).orElseThrow(this::notFound);
		if (!isOpenForStudents(course)) {
			throw notFound();
		}
		return course;
	}

	private static boolean isOpenForStudents(Course course) {
		return "PUBLISHED".equals(course.getStatus()) && !course.isClosed();
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException enrollmentRequired() {
		return new ApiException(ErrorCode.ENROLLMENT_REQUIRED, HttpStatus.FORBIDDEN, "Enrollment is required.");
	}

	private static ApiException materialForbidden() {
		return new ApiException(ErrorCode.MATERIAL_FORBIDDEN, HttpStatus.FORBIDDEN, "Material access is denied.");
	}

	public record LectureMaterialSummary(UUID id, String originalFileName, String contentType, long byteSize) {
	}

	public record LectureContent(UUID id, String title, String videoRef, String notes, List<UUID> materialIds,
			List<LectureMaterialSummary> materials) {
	}

	public record MaterialContent(UUID id, String originalFileName, long byteSize, String contentType, boolean free) {
	}

	public record MyCourse(UUID courseId, String titleEn, String titleBn) {
	}

}
