package com.coachingcenter.api.offline;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfflineClassRepository extends JpaRepository<OfflineClass, UUID> {

	/**
	 * The class, locked for the rest of the caller's transaction. A result save, publish or discard takes it first, so two of them for one
	 * exam run one after the other (F2), and a date change waits for a result that is being saved (F7).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from OfflineClass c where c.id = :id")
	Optional<OfflineClass> findForUpdate(@Param("id") UUID id);

	/** The classes of a batch in date and start order. */
	List<OfflineClass> findByBatchIdOrderByHeldOnAscStartsAtAsc(UUID batchId);

	/** The classes with one of the stored statuses (the unsettled list reads the computed status from these). */
	List<OfflineClass> findByStatusIn(Collection<String> statuses);

	/** How many classes of an instructor are not cancelled. An instructor with any of them cannot be archived. */
	long countByInstructorIdAndStatusNot(UUID instructorId, String status);

}
