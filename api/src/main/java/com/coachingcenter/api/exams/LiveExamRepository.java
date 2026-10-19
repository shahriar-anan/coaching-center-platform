package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LiveExamRepository extends JpaRepository<LiveExam, UUID> {

	Optional<LiveExam> findByIdAndDeletedAtIsNull(UUID id);

	Page<LiveExam> findByModuleIdAndDeletedAtIsNull(UUID moduleId, Pageable pageable);

	Page<LiveExam> findByModuleIdAndStatusAndDeletedAtIsNull(UUID moduleId, String status, Pageable pageable);

	List<LiveExam> findByModuleIdAndStatusAndDeletedAtIsNull(UUID moduleId, String status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from LiveExam e where e.id = :id and e.deletedAt is null")
	Optional<LiveExam> lockById(@Param("id") UUID id);

}
