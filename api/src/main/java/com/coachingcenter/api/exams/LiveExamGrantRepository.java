package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamGrantRepository extends JpaRepository<LiveExamGrant, UUID> {

	boolean existsByStudentIdAndScopeAndModuleId(UUID studentId, String scope, UUID moduleId);

	boolean existsByStudentIdAndScopeAndExamId(UUID studentId, String scope, UUID examId);

	boolean existsByStudentIdAndModuleId(UUID studentId, UUID moduleId);

	boolean existsByOrderId(UUID orderId);

	boolean existsByStudentIdAndScopeAndModuleIdAndRevokedAtIsNull(UUID studentId, String scope, UUID moduleId);

	boolean existsByStudentIdAndScopeAndExamIdAndRevokedAtIsNull(UUID studentId, String scope, UUID examId);

	boolean existsByScopeAndExamId(String scope, UUID examId);

}
