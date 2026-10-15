package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WrittenSubmissionRepository extends JpaRepository<WrittenSubmission, UUID> {

	Optional<WrittenSubmission> findByComponentIdAndStudentIdAndDeletedAtIsNull(UUID componentId, UUID studentId);

	Optional<WrittenSubmission> findByIdAndDeletedAtIsNull(UUID id);

	Page<WrittenSubmission> findByComponentIdAndStatusAndDeletedAtIsNull(UUID componentId, String status,
			Pageable pageable);

	List<WrittenSubmission> findByComponentIdAndDeletedAtIsNull(UUID componentId);

	long countByComponentIdAndStatusAndDeletedAtIsNull(UUID componentId, String status);

	boolean existsByComponentIdAndStudentIdNotAndDeletedAtIsNull(UUID componentId, UUID studentId);

}
