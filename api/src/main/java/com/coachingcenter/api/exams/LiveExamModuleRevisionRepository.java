package com.coachingcenter.api.exams;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamModuleRevisionRepository extends JpaRepository<LiveExamModuleRevision, UUID> {

	Optional<LiveExamModuleRevision> findByModuleId(UUID moduleId);

	List<LiveExamModuleRevision> findAllByOrderByUpdatedAtDesc();

	/** The packages among {@code moduleIds} that have a staged revision, for the staff reads (NR-01, B4). */
	List<LiveExamModuleRevision> findByModuleIdIn(Collection<UUID> moduleIds);

}
