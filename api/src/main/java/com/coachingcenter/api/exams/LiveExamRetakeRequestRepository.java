package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamRetakeRequestRepository extends JpaRepository<LiveExamRetakeRequest, UUID> {

	/** The student's latest request on the exam, whatever its status. */
	Optional<LiveExamRetakeRequest> findTopByExamIdAndStudentIdOrderByRequestedAtDesc(UUID examId, UUID studentId);

	boolean existsByExamIdAndStudentIdAndStatus(UUID examId, UUID studentId, String status);

	/** The requests with a status, oldest first, so the staff work through them in the order they came. */
	List<LiveExamRetakeRequest> findByStatusOrderByRequestedAtAsc(String status);

}
