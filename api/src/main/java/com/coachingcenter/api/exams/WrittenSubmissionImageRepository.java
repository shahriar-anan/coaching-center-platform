package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WrittenSubmissionImageRepository extends JpaRepository<WrittenSubmissionImage, UUID> {

	List<WrittenSubmissionImage> findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(UUID submissionId);

	Optional<WrittenSubmissionImage> findByIdAndSubmissionIdAndDeletedAtIsNull(UUID id, UUID submissionId);

}
