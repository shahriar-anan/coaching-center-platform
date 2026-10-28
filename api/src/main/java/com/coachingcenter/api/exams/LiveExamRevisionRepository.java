package com.coachingcenter.api.exams;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveExamRevisionRepository extends JpaRepository<LiveExamRevision, UUID> {

	Optional<LiveExamRevision> findByExamId(UUID examId);

	/** The exams among {@code examIds} that have a staged revision (NR-01, B3). */
	@Query("select r.examId from LiveExamRevision r where r.examId in :examIds")
	List<UUID> findExamIdsIn(@Param("examIds") Collection<UUID> examIds);

}
