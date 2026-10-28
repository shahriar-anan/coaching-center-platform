package com.coachingcenter.api.exams;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamQuestionPaperRepository extends JpaRepository<LiveExamQuestionPaper, UUID> {

	Optional<LiveExamQuestionPaper> findTopByExamIdOrderByVersionDesc(UUID examId);

	Optional<LiveExamQuestionPaper> findByExamIdAndVersion(UUID examId, int version);

}
