package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamModulePurchaseRepository extends JpaRepository<ExamModulePurchase, UUID> {

	Optional<ExamModulePurchase> findByIdAndDeletedAtIsNull(UUID id);

	boolean existsByStudentIdAndModuleIdAndStatusAndDeletedAtIsNull(UUID studentId, UUID moduleId, String status);

	List<ExamModulePurchase> findByStudentIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID studentId);

}
