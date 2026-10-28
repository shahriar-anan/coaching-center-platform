package com.coachingcenter.api.exams;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveExamQuestionRepository extends JpaRepository<LiveExamQuestion, UUID> {

	List<LiveExamQuestion> findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(UUID examId);

	Optional<LiveExamQuestion> findByIdAndExamIdAndDeletedAtIsNull(UUID id, UUID examId);

	/** Rows of exam id and its number of live questions, for the staff list (NR-01, B3). */
	@Query("""
			select q.examId, count(q) from LiveExamQuestion q
			where q.examId in :examIds and q.deletedAt is null
			group by q.examId
			""")
	List<Object[]> countByExamIds(@Param("examIds") Collection<UUID> examIds);

}
