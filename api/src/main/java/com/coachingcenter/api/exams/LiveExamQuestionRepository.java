package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamQuestionRepository extends JpaRepository<LiveExamQuestion, UUID> {

	List<LiveExamQuestion> findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(UUID examId);

	Optional<LiveExamQuestion> findByIdAndExamIdAndDeletedAtIsNull(UUID id, UUID examId);

}
