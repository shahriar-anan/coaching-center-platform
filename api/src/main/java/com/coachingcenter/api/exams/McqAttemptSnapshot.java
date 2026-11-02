package com.coachingcenter.api.exams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * The question set an MCQ attempt is sat and scored on (NR-01 D9). When the attempt starts, the live questions and options are
 * copied into it as JSON in {@code live_exam_participants.question_snapshot}. Every read, answer check and score of that attempt
 * uses the copy, so a later edit of the exam never reaches a running or submitted attempt. An attempt with no snapshot started
 * before snapshots existed, and reads the live rows as it always did.
 *
 * <p>
 * The copy keeps the live row ids, so an answer still points at a real row (D12).
 */
@Component
public class McqAttemptSnapshot {

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final ObjectMapper json;

	public McqAttemptSnapshot(LiveExamQuestionRepository questions, LiveExamOptionRepository options, ObjectMapper json) {
		this.questions = questions;
		this.options = options;
		this.json = json;
	}

	/** The JSON to store on a new attempt: the exam's live question set as it is now. */
	public String capture(UUID examId) {
		return json.writeValueAsString(live(examId));
	}

	/** The question set an attempt answers: its stored copy, or the live rows when it has none. */
	public Snapshot of(LiveExamParticipant attempt) {
		if (attempt.getQuestionSnapshot() == null) {
			return live(attempt.getExamId());
		}
		return json.readValue(attempt.getQuestionSnapshot(), Snapshot.class);
	}

	/** A stored copy, read. The caller has checked that the attempt has one. */
	public Snapshot read(LiveExamParticipant attempt) {
		return json.readValue(attempt.getQuestionSnapshot(), Snapshot.class);
	}

	/** The live question set: non-deleted questions in position order, each with its non-deleted options in position order. */
	private Snapshot live(UUID examId) {
		Snapshot snapshot = new Snapshot();
		List<LiveExamQuestion> rows = questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId);
		Map<UUID, List<LiveExamOption>> optionsByQuestion = new HashMap<>();
		if (!rows.isEmpty()) {
			for (LiveExamOption option : options.findByQuestionIdInAndDeletedAtIsNullOrderByPositionAscIdAsc(
					rows.stream().map(LiveExamQuestion::getId).toList())) {
				optionsByQuestion.computeIfAbsent(option.getQuestionId(), ignored -> new ArrayList<>()).add(option);
			}
		}
		for (LiveExamQuestion row : rows) {
			SnapshotQuestion question = new SnapshotQuestion();
			question.id = row.getId();
			question.stem = row.getStem();
			question.marks = row.getMarks();
			question.explanation = row.getExplanation();
			question.position = row.getPosition();
			question.multipleAnswer = row.isMultipleAnswer();
			for (LiveExamOption option : optionsByQuestion.getOrDefault(row.getId(), List.of())) {
				SnapshotOption item = new SnapshotOption();
				item.id = option.getId();
				item.text = option.getOptionText();
				item.correct = option.isCorrect();
				item.position = option.getPosition();
				question.options.add(item);
			}
			snapshot.questions.add(question);
		}
		return snapshot;
	}

	/** An attempt's question set, in position order. */
	public static class Snapshot {

		public List<SnapshotQuestion> questions = new ArrayList<>();

		/** The answer key for scoring, one entry per question in position order. */
		public List<McqScoring.QuestionKey> key() {
			List<McqScoring.QuestionKey> key = new ArrayList<>();
			for (SnapshotQuestion question : questions) {
				Set<UUID> correct = new HashSet<>();
				for (SnapshotOption option : question.options) {
					if (option.correct) {
						correct.add(option.id);
					}
				}
				key.add(new McqScoring.QuestionKey(question.id, question.marks, correct));
			}
			return key;
		}

	}

	public static class SnapshotQuestion {

		public UUID id;

		public String stem;

		public int marks;

		public String explanation;

		public int position;

		public boolean multipleAnswer;

		public List<SnapshotOption> options = new ArrayList<>();

	}

	public static class SnapshotOption {

		public UUID id;

		public String text;

		public boolean correct;

		public int position;

	}

}
