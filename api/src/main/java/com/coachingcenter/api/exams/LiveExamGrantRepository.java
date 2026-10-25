package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Every check here ignores revoked grants: a revoked grant is history, not access (R-2). */
public interface LiveExamGrantRepository extends JpaRepository<LiveExamGrant, UUID> {

	boolean existsByStudentIdAndScopeAndModuleIdAndRevokedAtIsNull(UUID studentId, String scope, UUID moduleId);

	boolean existsByStudentIdAndScopeAndExamIdAndRevokedAtIsNull(UUID studentId, String scope, UUID examId);

	boolean existsByStudentIdAndModuleIdAndRevokedAtIsNull(UUID studentId, UUID moduleId);

	boolean existsByOrderId(UUID orderId);

	boolean existsByScopeAndExamId(String scope, UUID examId);

}
