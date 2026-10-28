package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

/**
 * The field-by-field change list that the Master Admin reads before publishing a staged edit (E31): what the published
 * exam says now and what it will say after, and what publishing does besides (re-score, notify). It compares two
 * {@link ExamState}s and reads nothing else, apart from the paper version numbers.
 */
@Component
class ExamChangeListBuilder {

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final DateTimeFormatter DHAKA_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final LiveExamQuestionPaperRepository papers;

	ExamChangeListBuilder(LiveExamQuestionPaperRepository papers) {
		this.papers = papers;
	}

	/** What an edit changes. {@code submittedMcq} and {@code recipients} come from the caller, who counts the rows. */
	ChangeList build(String revisionId, ExamState before, ExamState after, int submittedMcq, int recipients) {
		List<FieldChange> fields = fields(before, after);
		List<QuestionChange> questions = questions(before, after);
		PaperChange paper = paper(before, after);
		boolean scoring = scoringChanged(fields, questions);
		boolean notifies = timeChanged(fields) || paper != null || !questions.isEmpty();
		Impact impact = new Impact(scoring ? submittedMcq : 0, notifies, notifies ? recipients : 0);
		return new ChangeList(revisionId, fields, questions, paper, List.of(), impact);
	}

	/** True when the diff is empty, so a revision that was edited back to the published state can be dropped. */
	static boolean isEmpty(ChangeList changes) {
		return changes.fields().isEmpty() && changes.questions().isEmpty() && changes.paper() == null;
	}

	/** True when a re-score is due: the answer key, the marks, a multi-answer flag, negative marks or the pass percent changed. */
	static boolean scoringChanged(ChangeList changes) {
		return scoringChanged(changes.fields(), changes.questions());
	}

	private static boolean scoringChanged(List<FieldChange> fields, List<QuestionChange> questions) {
		for (FieldChange field : fields) {
			if ("negativeMarks".equals(field.field()) || "passPercent".equals(field.field())) {
				return true;
			}
		}
		for (QuestionChange question : questions) {
			if (Set.of("CORRECT", "MARKS", "MULTIPLE", "ADDED", "REMOVED").contains(question.change())) {
				return true;
			}
		}
		return false;
	}

	private static boolean timeChanged(List<FieldChange> fields) {
		for (FieldChange field : fields) {
			if (Set.of("liveAt", "endAt", "durationMinutes").contains(field.field())) {
				return true;
			}
		}
		return false;
	}

	private static List<FieldChange> fields(ExamState before, ExamState after) {
		List<FieldChange> changes = new ArrayList<>();
		add(changes, "titleEn", before.titleEn, after.titleEn);
		add(changes, "titleBn", before.titleBn, after.titleBn);
		add(changes, "descriptionEn", before.descriptionEn, after.descriptionEn);
		add(changes, "descriptionBn", before.descriptionBn, after.descriptionBn);
		add(changes, "rulesEn", before.rulesEn, after.rulesEn);
		add(changes, "rulesBn", before.rulesBn, after.rulesBn);
		add(changes, "paperLanguage", before.paperLanguage, after.paperLanguage);
		add(changes, "mode", before.mode, after.mode);
		add(changes, "liveAt", dhaka(before.liveAt), dhaka(after.liveAt));
		add(changes, "endAt", dhaka(before.endsAt), dhaka(after.endsAt));
		add(changes, "durationMinutes", before.durationMinutes, after.durationMinutes);
		add(changes, "passPercent", before.passPercent, after.passPercent);
		add(changes, "maximum", before.writtenMaximum, after.writtenMaximum);
		add(changes, "price", before.price, after.price);
		add(changes, "free", before.free, after.free);
		add(changes, "discountPercent", before.discountPercent, after.discountPercent);
		add(changes, "discountEndsAt", dhaka(before.discountEndsAt), dhaka(after.discountEndsAt));
		add(changes, "accessDays", before.accessDays, after.accessDays);
		add(changes, "negativeMarks", before.negativeMarks, after.negativeMarks);
		add(changes, "answersChangeable", before.answersChangeable, after.answersChangeable);
		add(changes, "previewEnabled", before.previewEnabled, after.previewEnabled);
		return changes;
	}

	private static void add(List<FieldChange> changes, String field, Object before, Object after) {
		if (!same(before, after)) {
			changes.add(new FieldChange(field, before, after));
		}
	}

	private static boolean same(Object before, Object after) {
		if (before instanceof BigDecimal left && after instanceof BigDecimal right) {
			return left.compareTo(right) == 0;
		}
		return Objects.equals(before, after);
	}

	private static String dhaka(String instant) {
		return instant == null ? null : DHAKA_TIME.format(Instant.parse(instant).atZone(DHAKA));
	}

	private static List<QuestionChange> questions(ExamState before, ExamState after) {
		List<QuestionChange> changes = new ArrayList<>();
		Set<UUID> kept = new HashSet<>();
		for (ExamState.QuestionState question : after.questions) {
			ExamState.QuestionState earlier = before.question(question.id);
			if (earlier == null) {
				changes.add(new QuestionChange(question.id, question.position, "ADDED", null, question.stem));
				continue;
			}
			kept.add(question.id);
			compare(changes, earlier, question);
		}
		for (ExamState.QuestionState question : before.questions) {
			if (!kept.contains(question.id)) {
				changes.add(new QuestionChange(question.id, question.position, "REMOVED", question.stem, null));
			}
		}
		return changes;
	}

	private static void compare(List<QuestionChange> changes, ExamState.QuestionState before, ExamState.QuestionState after) {
		if (!Objects.equals(before.stem, after.stem)) {
			changes.add(new QuestionChange(after.id, after.position, "STEM", before.stem, after.stem));
		}
		List<String> beforeText = list(before, option -> option.text);
		List<String> afterText = list(after, option -> option.text);
		if (!beforeText.equals(afterText)) {
			changes.add(new QuestionChange(after.id, after.position, "OPTIONS_TEXT", beforeText, afterText));
		}
		List<Integer> beforeKey = correct(before);
		List<Integer> afterKey = correct(after);
		if (!beforeKey.equals(afterKey)) {
			changes.add(new QuestionChange(after.id, after.position, "CORRECT", beforeKey, afterKey));
		}
		if (before.marks != after.marks) {
			changes.add(new QuestionChange(after.id, after.position, "MARKS", before.marks, after.marks));
		}
		if (!Objects.equals(before.explanation, after.explanation)) {
			changes.add(new QuestionChange(after.id, after.position, "EXPLANATION", before.explanation, after.explanation));
		}
		if (before.multipleAnswer != after.multipleAnswer) {
			changes.add(new QuestionChange(after.id, after.position, "MULTIPLE", before.multipleAnswer, after.multipleAnswer));
		}
	}

	private static <T> List<T> list(ExamState.QuestionState question, Function<ExamState.OptionState, T> field) {
		return question.options.stream().sorted(java.util.Comparator.comparingInt(option -> option.position)).map(field).toList();
	}

	/** The positions of the correct options, so the key is comparable even when option ids are temporary. */
	private static List<Integer> correct(ExamState.QuestionState question) {
		List<Integer> positions = new ArrayList<>();
		List<ExamState.OptionState> sorted = question.options.stream()
			.sorted(java.util.Comparator.comparingInt(option -> option.position))
			.toList();
		for (int index = 0; index < sorted.size(); index++) {
			if (sorted.get(index).correct) {
				positions.add(index);
			}
		}
		return positions;
	}

	private PaperChange paper(ExamState before, ExamState after) {
		if (Objects.equals(before.paperId, after.paperId)) {
			return null;
		}
		Integer beforeVersion = before.paperId == null ? null : papers.findById(before.paperId).map(LiveExamQuestionPaper::getVersion).orElse(null);
		LiveExamQuestionPaper next = after.paperId == null ? null : papers.findById(after.paperId).orElse(null);
		return new PaperChange(beforeVersion, next == null ? null : next.getVersion(),
				next == null ? null : next.getOriginalFileName());
	}

}
