package com.coachingcenter.api.purchases;

import java.util.UUID;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

	Optional<Enrollment> findByIdAndDeletedAtIsNull(UUID id);

	boolean existsByStudentIdAndCourseIdAndOpeningNumberAndStatusAndDeletedAtIsNull(UUID studentId, UUID courseId,
			int openingNumber, String status);

	List<Enrollment> findByStudentIdAndStatusAndDeletedAtIsNull(UUID studentId, String status);

}
