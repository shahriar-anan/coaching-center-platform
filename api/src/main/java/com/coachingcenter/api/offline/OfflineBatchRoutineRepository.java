package com.coachingcenter.api.offline;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineBatchRoutineRepository extends JpaRepository<OfflineBatchRoutine, UUID> {

	/** Every routine of the batch, the newest first: by the day it counts from, then by version. */
	List<OfflineBatchRoutine> findByBatchIdOrderByEffectiveFromDescRevisionDesc(UUID batchId);

	/** The live routine of the batch: the one CONFIRMED routine (a partial unique index allows at most one). */
	Optional<OfflineBatchRoutine> findByBatchIdAndStatus(UUID batchId, String status);

}
