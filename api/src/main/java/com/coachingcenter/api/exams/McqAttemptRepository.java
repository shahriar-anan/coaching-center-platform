package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface McqAttemptRepository extends JpaRepository<McqAttempt, UUID> {

	Optional<McqAttempt> findByComponentIdAndStudentIdAndDeletedAtIsNull(UUID componentId, UUID studentId);

	boolean existsByComponentIdAndStudentIdNotAndDeletedAtIsNull(UUID componentId, UUID studentId);

	List<McqAttempt> findByComponentIdAndDeletedAtIsNullOrderByStartedAtAsc(UUID componentId);

}
