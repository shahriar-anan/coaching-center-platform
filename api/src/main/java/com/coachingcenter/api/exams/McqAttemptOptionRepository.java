package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface McqAttemptOptionRepository extends JpaRepository<McqAttemptOption, UUID> {

	List<McqAttemptOption> findByAttemptQuestionIdAndDeletedAtIsNullOrderByPositionAsc(UUID attemptQuestionId);

}
