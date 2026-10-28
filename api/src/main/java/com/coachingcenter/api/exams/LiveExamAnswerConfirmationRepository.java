package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamAnswerConfirmationRepository
		extends JpaRepository<LiveExamAnswerConfirmation, LiveExamAnswerConfirmation.Key> {

	List<LiveExamAnswerConfirmation> findByParticipantId(UUID participantId);

}
