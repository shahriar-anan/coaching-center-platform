package com.coachingcenter.api.exams;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveExamRetakeGrantRepository extends JpaRepository<LiveExamRetakeGrant, UUID> {

	/** The student's grant that is still in force, or empty. A revoked grant is not active. */
	@Query("""
			select g from LiveExamRetakeGrant g
			where g.examId = :examId and g.studentId = :studentId and g.revokedAt is null
			""")
	Optional<LiveExamRetakeGrant> findActive(@Param("examId") UUID examId, @Param("studentId") UUID studentId);

}
