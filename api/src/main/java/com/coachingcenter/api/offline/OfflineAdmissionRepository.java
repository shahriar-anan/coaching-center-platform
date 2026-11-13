package com.coachingcenter.api.offline;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OfflineAdmissionRepository extends JpaRepository<OfflineAdmission, UUID> {

	/** The student's open admission, if any. The partial unique index (V82) means at most one row matches. */
	Optional<OfflineAdmission> findFirstByStudentIdAndStatusNot(UUID studentId, String status);

	/**
	 * The admission, locked for the rest of the caller's transaction. Every admin change takes this lock first, so two staff changes
	 * (or a change and an onboarding) run one after the other, and the second one sees the first one's result.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from OfflineAdmission a where a.id = :id")
	Optional<OfflineAdmission> findForUpdate(@Param("id") UUID id);

	/** The admissions of one batch in one state, for the batch's counts and its onboarded students. */
	List<OfflineAdmission> findByBatchIdAndStatus(UUID batchId, String status);

}
