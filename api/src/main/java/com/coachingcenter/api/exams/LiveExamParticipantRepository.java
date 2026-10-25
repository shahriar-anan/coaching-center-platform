package com.coachingcenter.api.exams;

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

	List<LiveExamParticipant> findByExamIdAndDeletedAtIsNull(UUID examId);

	boolean existsByExamId(UUID examId);

	List<LiveExamParticipant> findByStudentIdAndExamIdInAndDeletedAtIsNull(UUID studentId,
			java.util.Collection<UUID> examIds);

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

}
