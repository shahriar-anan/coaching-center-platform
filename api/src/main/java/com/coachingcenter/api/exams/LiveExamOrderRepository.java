package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LiveExamOrderRepository extends JpaRepository<LiveExamOrder, UUID> {

	Optional<LiveExamOrder> findByTranId(String tranId);

	boolean existsByExamIdAndStatus(UUID examId, String status);

	boolean existsByStudentIdAndStatus(UUID studentId, String status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<LiveExamOrder> findByExamIdAndStatus(UUID examId, String status);

	List<LiveExamOrder> findByStatusAndCreatedAtBetween(String status, Instant from, Instant to);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from LiveExamOrder o where o.tranId = :tranId")
	Optional<LiveExamOrder> lockByTranId(@Param("tranId") String tranId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from LiveExamOrder o where o.id = :id")
	Optional<LiveExamOrder> lockById(@Param("id") UUID id);

	Optional<LiveExamOrder> findByStudentIdAndScopeAndModuleIdAndExamIdIsNullAndStatus(UUID studentId, String scope,
			UUID moduleId, String status);

	Optional<LiveExamOrder> findByStudentIdAndScopeAndExamIdAndStatus(UUID studentId, String scope, UUID examId,
			String status);

}
