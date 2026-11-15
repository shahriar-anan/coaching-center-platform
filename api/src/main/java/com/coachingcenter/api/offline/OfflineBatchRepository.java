package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineBatchRepository extends JpaRepository<OfflineBatch, UUID> {

	List<OfflineBatch> findByStatusOrderByNameEnAsc(String status);

	/** The batches on the public cards: not archived, the lowest display order first (V93). */
	List<OfflineBatch> findByStatusOrderByDisplayOrderAscNameEnAsc(String status);

}
