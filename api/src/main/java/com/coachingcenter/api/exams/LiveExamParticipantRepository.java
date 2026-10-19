package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamParticipantRepository extends JpaRepository<LiveExamParticipant, UUID> {

	Optional<LiveExamParticipant> findByExamIdAndStudentIdAndDeletedAtIsNull(UUID examId, UUID studentId);

	List<LiveExamParticipant> findByExamIdAndDeletedAtIsNull(UUID examId);

}
