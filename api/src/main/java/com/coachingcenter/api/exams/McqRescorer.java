package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Scores every submitted MCQ attempt of an exam again, from the current answer key (NR-01, E28). A change to a key or to the
 * marks re-scores the papers already submitted. Attempts still in progress are left alone: they are scored at submit.
 */
@Component
class McqRescorer {

	private final LiveExamRepository exams;

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final LiveExamParticipantRepository participants;

	private final LiveExamAnswerRepository answers;

	McqRescorer(LiveExamRepository exams, LiveExamQuestionRepository questions, LiveExamOptionRepository options,
			LiveExamParticipantRepository participants, LiveExamAnswerRepository answers) {
		this.exams = exams;
		this.questions = questions;
		this.options = options;
		this.participants = participants;
		this.answers = answers;
	}

	/** Returns how many attempts' scores changed. Their pass result is updated too, but it is not counted. */
	@Transactional
	int rescore(UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(() -> new ApiException(
				ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
		List<McqScoring.QuestionKey> key = key(examId);
		int maximum = McqScoring.maximum(key);
		int changed = 0;
		for (LiveExamParticipant participant : participants.findByExamIdAndDeletedAtIsNull(examId)) {
			if (!LiveExamParticipant.SUBMITTED.equals(participant.getStatus())) {
				continue;
			}
			Map<UUID, Set<UUID>> chosen = chosen(participant.getId());
			BigDecimal score = McqScoring.total(key, chosen, exam.getNegativeMarks()).setScale(2, RoundingMode.HALF_UP);
			if (participant.getScore() == null || participant.getScore().compareTo(score) != 0) {
				changed++;
			}
			participant.setScore(score);
			participant.setPassed(!chosen.isEmpty() && McqScoring.passed(score, maximum, exam.getPassPercent()));
		}
		return changed;
	}

	private List<McqScoring.QuestionKey> key(UUID examId) {
		List<McqScoring.QuestionKey> key = new ArrayList<>();
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId)) {
			Set<UUID> correct = new HashSet<>();
			for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(question.getId())) {
				if (option.isCorrect()) {
					correct.add(option.getId());
				}
			}
			key.add(new McqScoring.QuestionKey(question.getId(), question.getMarks(), correct));
		}
		return key;
	}

	private Map<UUID, Set<UUID>> chosen(UUID participantId) {
		Map<UUID, Set<UUID>> chosen = new HashMap<>();
		for (LiveExamAnswer answer : answers.findByParticipantId(participantId)) {
			chosen.computeIfAbsent(answer.getQuestionId(), id -> new HashSet<>()).add(answer.getOptionId());
		}
		return chosen;
	}

}
