package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineClassDecisionRepository extends JpaRepository<OfflineClassDecision, UUID> {

	List<OfflineClassDecision> findByClassIdOrderByRequestedAtDesc(UUID classId);

	/** How many not-taken requests have this status (the digest counts the ones waiting for approval). */
	long countByStatus(String status);

}
