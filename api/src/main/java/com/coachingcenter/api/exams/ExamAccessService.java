package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.Course;
import com.coachingcenter.api.courses.CourseRepository;
import com.coachingcenter.api.purchases.EnrollmentRepository;

@Service
public class ExamAccessService {

	private final CourseRepository courses;

	private final EnrollmentRepository enrollments;

	private final ExamModulePurchaseRepository examPurchases;

	public ExamAccessService(CourseRepository courses, EnrollmentRepository enrollments,
			ExamModulePurchaseRepository examPurchases) {
		this.courses = courses;
		this.enrollments = enrollments;
		this.examPurchases = examPurchases;
	}

	public boolean accessGranted(ExamModule module, UUID studentId) {
		if (!"PUBLISHED".equals(module.getStatus()) || module.getAccessMode() == null) {
			return false;
		}
		return switch (module.getAccessMode()) {
			case "OPEN" -> true;
			case "PURCHASE_REQUIRED" -> hasActiveExamPurchase(module.getId(), studentId);
			case "ENROLLED_ONLY" -> enrolledOnlyGranted(module.getCourseId(), studentId);
			default -> false;
		};
	}

	public void requireAccess(ExamModule module, UUID studentId) {
		if (!accessGranted(module, studentId)) {
			throw new ApiException(ErrorCode.EXAM_ACCESS_REQUIRED, HttpStatus.FORBIDDEN, "Exam access is required.");
		}
	}

	boolean hasActiveExamPurchase(UUID moduleId, UUID studentId) {
		return examPurchases.existsByStudentIdAndModuleIdAndStatusAndDeletedAtIsNull(studentId, moduleId, "ACTIVE");
	}

	private boolean enrolledOnlyGranted(UUID courseId, UUID studentId) {
		if (courseId == null) {
			return false;
		}
		Course course = courses.findByIdAndDeletedAtIsNull(courseId).orElse(null);
		if (course == null || !"PUBLISHED".equals(course.getStatus()) || course.isClosed()) {
			return false;
		}
		if ("FULL_FREE".equals(course.getAccessType())) {
			return true;
		}
		return enrollments.existsByStudentIdAndCourseIdAndOpeningNumberAndStatusAndDeletedAtIsNull(studentId,
				courseId, course.getOpeningNumber(), "ACTIVE");
	}

}
