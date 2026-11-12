package com.coachingcenter.api.offline;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineAdmissionRepository extends JpaRepository<OfflineAdmission, UUID> {

	/** The student's open admission, if any. The partial unique index (V82) means at most one row matches. */
	Optional<OfflineAdmission> findFirstByStudentIdAndStatusNot(UUID studentId, String status);

}
