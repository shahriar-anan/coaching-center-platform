package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface McqAttemptQuestionRepository extends JpaRepository<McqAttemptQuestion, UUID> {

	List<McqAttemptQuestion> findByAttemptIdAndDeletedAtIsNullOrderByPositionAsc(UUID attemptId);

}
