package com.coachingcenter.api.purchases;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.Course;
import com.coachingcenter.api.courses.CourseRepository;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;
import com.coachingcenter.api.users.User;
import com.coachingcenter.api.users.UserRepository;

@Service
public class CourseRosterService {

	private final CourseRepository courses;

	private final EnrollmentRepository enrollments;

	private final UserRepository users;

	private final StudentProfileRepository profiles;

	public CourseRosterService(CourseRepository courses, EnrollmentRepository enrollments, UserRepository users,
			StudentProfileRepository profiles) {
		this.courses = courses;
		this.enrollments = enrollments;
		this.users = users;
		this.profiles = profiles;
	}

	@Transactional(readOnly = true)
	public Page<RosterItem> list(UUID courseId, Integer opening, Pageable pageable) {
		Course course = courses.findByIdAndDeletedAtIsNull(courseId).orElseThrow(this::notFound);
		int openingNumber = opening == null ? course.getOpeningNumber() : opening;
		return enrollments.findByCourseIdAndOpeningNumberAndDeletedAtIsNull(courseId, openingNumber, pageable)
			.map(this::item);
	}

	private RosterItem item(Enrollment enrollment) {
		User user = users.findById(enrollment.getStudentId()).orElseThrow(this::notFound);
		StudentProfile profile = profiles.findById(enrollment.getStudentId()).orElseThrow(this::notFound);
		return new RosterItem(enrollment.getId(), enrollment.getStudentId(), profile.getFullName(), user.getPhone(),
				enrollment.getSource(), enrollment.getStatus(), enrollment.getDeliveryMode(), enrollment.getOpeningNumber(),
				enrollment.getStartedAt());
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record RosterItem(UUID enrollmentId, UUID studentId, String fullName, String phone, String source,
			String status, String deliveryMode, int openingNumber, Instant startedAt) {
	}

}
