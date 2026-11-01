package com.coachingcenter.api.exams;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveExamParticipantRepository extends JpaRepository<LiveExamParticipant, UUID> {

	@Query("""
			select p.id from LiveExamParticipant p
			where p.deletedAt is null and p.submittedAt is null and p.deadlineAt <= :now
			  and exists (select e.id from LiveExam e where e.id = p.examId and e.deletedAt is null)
			order by p.deadlineAt asc
			""")
	List<UUID> findDueIds(@Param("now") Instant now, Pageable limit);

	Optional<LiveExamParticipant> findByExamIdAndStudentIdAndDeletedAtIsNull(UUID examId, UUID studentId);

	List<LiveExamParticipant> findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(UUID examId, UUID studentId);

	/** The student's attempt that is still open, or empty. At most one exists (V46). */
	@Query("""
			select p from LiveExamParticipant p
			where p.examId = :examId and p.studentId = :studentId and p.status = 'IN_PROGRESS' and p.deletedAt is null
			""")
	Optional<LiveExamParticipant> findOpen(@Param("examId") UUID examId, @Param("studentId") UUID studentId);

	/** The attempts that can sit on the leaderboard: first live attempts and first practice attempts. */
	@Query("""
			select p from LiveExamParticipant p
			where p.examId = :examId and p.deletedAt is null and p.kind in ('LIVE', 'PRACTICE')
			""")
	List<LiveExamParticipant> findBoardAttempts(@Param("examId") UUID examId);

	List<LiveExamParticipant> findByExamIdAndDeletedAtIsNull(UUID examId);

	boolean existsByExamId(UUID examId);

	boolean existsByEvaluatedBy(UUID evaluatedBy);

	boolean existsByExamIdAndStudentIdAndDeletedAtIsNull(UUID examId, UUID studentId);

	/** Has at least one paper of this exam been released to its student (a written publish sets {@code publishedAt}). */
	boolean existsByExamIdAndDeletedAtIsNullAndPublishedAtIsNotNull(UUID examId);

	/** The exams, among these, that have at least one paper released. One query for a whole page of the staff list. */
	@Query("""
			select distinct p.examId from LiveExamParticipant p
			where p.examId in :examIds and p.deletedAt is null and p.publishedAt is not null
			""")
	List<UUID> findReleasedExamIds(@Param("examIds") Collection<UUID> examIds);

	/** The student's attempts on the given exams, oldest first, so a caller that keeps one per exam keeps the latest. */
	@Query("""
			select p from LiveExamParticipant p
			where p.studentId = :studentId and p.examId in :examIds and p.deletedAt is null
			order by p.examId, p.attemptNo asc
			""")
	List<LiveExamParticipant> findByStudentIdAndExamIdInAndDeletedAtIsNull(@Param("studentId") UUID studentId,
			@Param("examIds") java.util.Collection<UUID> examIds);

	/** One student's sittings, newest first. A sitting whose exam is deleted is left out, as if missing. */
	@Query(value = """
			select p from LiveExamParticipant p
			where p.studentId = :studentId and p.deletedAt is null
			  and exists (select e.id from LiveExam e where e.id = p.examId and e.deletedAt is null)
			order by p.startedAt desc, p.id
			""", countQuery = """
			select count(p) from LiveExamParticipant p
			where p.studentId = :studentId and p.deletedAt is null
			  and exists (select e.id from LiveExam e where e.id = p.examId and e.deletedAt is null)
			""")
	Page<LiveExamParticipant> findHistory(@Param("studentId") UUID studentId, Pageable pageable);

	/**
	 * The exams one student has sat, ordered by the start of their latest attempt, newest first. A deleted attempt or exam is
	 * left out; an exam in no package is included. {@code examType} is {@link LiveExamTypeFilter#ANY} for every type.
	 */
	@Query(value = """
			select p.examId from LiveExamParticipant p
			where p.studentId = :studentId and p.deletedAt is null
			  and exists (select e.id from LiveExam e where e.id = p.examId and e.deletedAt is null
			    and (:examType = '' or e.examType = :examType))
			group by p.examId
			order by max(p.startedAt) desc, p.examId
			""", countQuery = """
			select count(distinct p.examId) from LiveExamParticipant p
			where p.studentId = :studentId and p.deletedAt is null
			  and exists (select e.id from LiveExam e where e.id = p.examId and e.deletedAt is null
			    and (:examType = '' or e.examType = :examType))
			""")
	Page<UUID> findSatExamIds(@Param("studentId") UUID studentId, @Param("examType") String examType, Pageable pageable);

}
