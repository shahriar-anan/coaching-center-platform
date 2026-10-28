package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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

	boolean existsByScopeAndExamId(String scope, UUID examId);

}
