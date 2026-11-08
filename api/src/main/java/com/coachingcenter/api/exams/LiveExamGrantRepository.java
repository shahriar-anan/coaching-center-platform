package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The access checks ignore revoked grants: a revoked grant is history, not access (R-2). {@link #existsByStudentId} is
 * the exception, used for account deletion, which counts revoked grants too (R-10).
 */
public interface LiveExamGrantRepository extends JpaRepository<LiveExamGrant, UUID> {

	boolean existsByStudentId(UUID studentId);

	/** Any grant of the exam, revoked or not: a student who ever held access may still read their own results (R-2). */
	boolean existsByStudentIdAndExamId(UUID studentId, UUID examId);

	boolean existsByStudentIdAndModuleId(UUID studentId, UUID moduleId);

	List<LiveExamGrant> findByStudentIdAndScopeAndModuleId(UUID studentId, String scope, UUID moduleId);

	List<LiveExamGrant> findByStudentIdAndScopeAndExamId(UUID studentId, String scope, UUID examId);

	boolean existsByStudentIdAndScopeAndModuleIdAndRevokedAtIsNull(UUID studentId, String scope, UUID moduleId);

	boolean existsByStudentIdAndScopeAndExamIdAndRevokedAtIsNull(UUID studentId, String scope, UUID examId);

	boolean existsByStudentIdAndModuleIdAndRevokedAtIsNull(UUID studentId, UUID moduleId);

	boolean existsByOrderId(UUID orderId);

	/** The grants an order wrote that still give access; a refund with access removed takes these back (plan 5.2). */
	List<LiveExamGrant> findByOrderIdAndRevokedAtIsNull(UUID orderId);

	/** Whether a refund took access away from the order (the grants carry the refund's revoke reason). */
	boolean existsByOrderIdAndRevokeReason(UUID orderId, String revokeReason);

	boolean existsByScopeAndExamId(String scope, UUID examId);

	/**
	 * Whether a package has a payment plan whose access is still given (plan section 4, C9). A package with one cannot be deleted.
	 * Only a package grant counts: an exam grant names its exam, and its access does not depend on the package.
	 */
	@Query(value = """
			select exists (
			  select 1 from live_exam_payment_plans p
			  join live_exam_grants g on g.id = p.grant_id
			  where g.scope = 'MODULE' and g.module_id = :moduleId and g.revoked_at is null)
			""", nativeQuery = true)
	boolean hasLivePaymentPlanForModule(@Param("moduleId") UUID moduleId);

}
