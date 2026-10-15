package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WrittenSubmissionQuestionRepository extends JpaRepository<WrittenSubmissionQuestion, UUID> {

	List<WrittenSubmissionQuestion> findBySubmissionIdAndDeletedAtIsNullOrderByPositionAsc(UUID submissionId);

}
