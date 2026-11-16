package com.coachingcenter.api.offline;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfflineResultSheetRepository extends JpaRepository<OfflineResultSheet, UUID> {

	/** The sheet of one status for an exam class: the staged one, or the published one. At most one of each exists (V97). */
	Optional<OfflineResultSheet> findByClassIdAndStatus(UUID classId, String status);

	/** The sheets of an exam class: the published one and the staged one, in any order. */
	List<OfflineResultSheet> findByClassIdAndStatusIn(UUID classId, Collection<String> statuses);

	/** Whether an exam class has a sheet of one of these statuses. A routine upload keeps such an exam (rule A1). */
	boolean existsByClassIdAndStatusIn(UUID classId, Collection<String> statuses);

	/** The exam classes of a batch that have a sheet of one of these statuses (rule A1, the routine preview and confirm). */
	@Query("select s.classId from OfflineResultSheet s where s.batchId = :batchId and s.status in :statuses")
	Set<UUID> classIdsOfBatchWith(@Param("batchId") UUID batchId, @Param("statuses") Collection<String> statuses);


	/** True when any result sheet, in any state, belongs to the class. */
	boolean existsByClassId(UUID classId);

}
