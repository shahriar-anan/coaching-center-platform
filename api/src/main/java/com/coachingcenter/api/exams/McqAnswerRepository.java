package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface McqAnswerRepository extends JpaRepository<McqAnswer, UUID> {

	List<McqAnswer> findByAttemptIdAndDeletedAtIsNull(UUID attemptId);

	Optional<McqAnswer> findByAttemptIdAndAttemptQuestionIdAndDeletedAtIsNull(UUID attemptId, UUID attemptQuestionId);

}
