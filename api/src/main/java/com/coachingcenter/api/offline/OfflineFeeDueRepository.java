package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineFeeDueRepository extends JpaRepository<OfflineFeeDue, UUID> {

	/** The dues that still count, in month order. */
	List<OfflineFeeDue> findByAdmissionIdAndCancelledAtIsNullOrderBySeqAsc(UUID admissionId);

	/** Every due of the admission, cancelled ones too, in month order. The terms change reads it to restore a cancelled month. */
	List<OfflineFeeDue> findByAdmissionIdOrderBySeqAsc(UUID admissionId);

}
