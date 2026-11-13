package com.coachingcenter.api.offline;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineClassRepository extends JpaRepository<OfflineClass, UUID> {

	/** The classes of a batch in date and start order. */
	List<OfflineClass> findByBatchIdOrderByHeldOnAscStartsAtAsc(UUID batchId);

	/** The classes with one of the stored statuses (the unsettled list reads the computed status from these). */
	List<OfflineClass> findByStatusIn(Collection<String> statuses);

	/** How many classes of an instructor are not cancelled. An instructor with any of them cannot be archived. */
	long countByInstructorIdAndStatusNot(UUID instructorId, String status);

}
