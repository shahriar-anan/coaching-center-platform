package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineBatchInstructorRateRepository extends JpaRepository<OfflineBatchInstructorRate, OfflineBatchInstructorRate.Key> {

	List<OfflineBatchInstructorRate> findByBatchId(UUID batchId);

}
