package com.coachingcenter.api.offline;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfflineAdmissionRepository extends JpaRepository<OfflineAdmission, UUID> {

	/**
	 * The student's newest open admission (V94: a student may have one open admission per batch, so this answers the newest of them).
	 * The routes that predate several admissions use it.
	 */
	Optional<OfflineAdmission> findFirstByStudentIdAndStatusNotOrderByCreatedAtDesc(UUID studentId, String status);

	/** The student's open admission for one batch, if any (P11). */
	Optional<OfflineAdmission> findFirstByStudentIdAndBatchIdAndStatusNot(UUID studentId, UUID batchId, String status);

	/** Every open admission of the student, newest first. */
	List<OfflineAdmission> findByStudentIdAndStatusNotOrderByCreatedAtDesc(UUID studentId, String status);

	/** Every admission of the student in any state, newest first (the signed-in list, P11). */
	List<OfflineAdmission> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

	/** The student's latest admission in one state. The student's own read routes use it for a closed admission (plan 6.4). */
	Optional<OfflineAdmission> findFirstByStudentIdAndStatusOrderByCreatedAtDesc(UUID studentId, String status);

	/** The public applicant's application by its token hash (V94). */
	Optional<OfflineAdmission> findFirstByAccessTokenHash(String accessTokenHash);

	/** The newest onboarded admission whose form has this email (case ignored), for a student asking for an activation code again. */
	Optional<OfflineAdmission> findFirstByEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String email, String status);

	/** The reopen link's application by the hash of its token (V94). */
	Optional<OfflineAdmission> findFirstByReopenTokenHash(String reopenTokenHash);

	/** The open public application of one email for one batch, if any (the same email applying again opens it, P11). */
	@Query("select a from OfflineAdmission a where a.studentId is null and a.batchId = :batchId and a.status <> :closed"
			+ " and lower(a.email) = lower(:email)")
	Optional<OfflineAdmission> findOpenPublicApplication(@Param("email") String email, @Param("batchId") UUID batchId,
			@Param("closed") String closed);

	/** The student's open admissions that carry a join code, newest first. */
	List<OfflineAdmission> findByStudentIdAndStatusAndJoinCodeHashIsNotNullOrderByCreatedAtDesc(UUID studentId, String status);

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
