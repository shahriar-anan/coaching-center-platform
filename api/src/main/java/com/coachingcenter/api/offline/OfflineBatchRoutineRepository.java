package com.coachingcenter.api.offline;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineBatchRoutineRepository extends JpaRepository<OfflineBatchRoutine, UUID> {

	/** Every routine of the batch, the newest month first and the latest revision first within a month. */
	List<OfflineBatchRoutine> findByBatchIdOrderByMonthDescRevisionDesc(UUID batchId);

	/** The confirmed routine of one month, if there is one. At most one exists (a partial unique index). */
	Optional<OfflineBatchRoutine> findByBatchIdAndMonthAndStatus(UUID batchId, LocalDate month, String status);

}
