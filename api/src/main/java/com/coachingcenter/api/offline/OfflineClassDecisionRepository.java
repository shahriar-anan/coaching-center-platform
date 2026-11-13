package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineClassDecisionRepository extends JpaRepository<OfflineClassDecision, UUID> {

	/** The "not taken" requests of one class, newest first. */
	List<OfflineClassDecision> findByClassIdOrderByRequestedAtDesc(UUID classId);

}
