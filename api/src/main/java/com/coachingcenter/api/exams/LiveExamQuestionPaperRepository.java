package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LiveExamQuestionPaperRepository extends JpaRepository<LiveExamQuestionPaper, UUID> {

	Optional<LiveExamQuestionPaper> findTopByExamIdOrderByVersionDesc(UUID examId);

	Optional<LiveExamQuestionPaper> findByExamIdAndVersion(UUID examId, int version);

	/** Every stored paper file, all versions. */
	@Query("select p.storedFileName from LiveExamQuestionPaper p where p.storedFileName is not null")
	List<String> findStoredPaperNames();

	/** The exams' own paper columns, from before paper versions. */
	@Query("select e.questionStoredFileName from LiveExam e where e.questionStoredFileName is not null")
	List<String> findStoredExamPaperNames();

}
