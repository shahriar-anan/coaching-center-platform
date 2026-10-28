package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamRevisionRepository extends JpaRepository<LiveExamRevision, UUID> {

	Optional<LiveExamRevision> findByExamId(UUID examId);

	List<LiveExamRevision> findAllByOrderByUpdatedAtDesc();

}
