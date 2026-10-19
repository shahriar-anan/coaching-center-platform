package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamAnswerRepository extends JpaRepository<LiveExamAnswer, LiveExamAnswer.Key> {

	List<LiveExamAnswer> findByParticipantId(UUID participantId);

	void deleteByParticipantId(UUID participantId);

}
