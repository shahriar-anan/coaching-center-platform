package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The editable state of an exam: its settings, its proposed paper and its questions with their options. A published exam's
 * staged edit is stored as this, as JSON, in {@code live_exam_revisions.payload} (NR-01, E31). The same shape is read from
 * the rows to compare a revision with what is published.
 *
 * <p>
 * Instants are ISO-8601 strings so the payload does not depend on the mapper's date settings. Question and option ids are
 * the row ids; a question or option added in a revision carries a temporary id until the revision is published and the
 * row gets its own.
 */
public class ExamState {

	public String titleEn;

	public String titleBn;

	public String descriptionEn;

	public String descriptionBn;

	public String rulesEn;

	public String rulesBn;

	public String paperLanguage;

	/** The category of the exam (NR-01 UI Step 1 fix). Null only in a state staged before the field existed. */
	public UUID categoryId;

	public String mode;

	public String liveAt;

	public String endsAt;

	public int durationMinutes;

	public Integer passPercent;

	public Integer writtenMaximum;

	public BigDecimal price;

	public boolean free;

	public Integer discountPercent;

	public String discountEndsAt;

	public Integer accessDays;

	public BigDecimal negativeMarks;

	public boolean answersChangeable;

	public boolean previewEnabled;

	/** The question paper version this state points at (a written exam). */
	public UUID paperId;

	public List<QuestionState> questions = new ArrayList<>();

	/** The settings and the paper pointer of an exam row. The questions are loaded separately. */
	static ExamState of(LiveExam exam) {
		ExamState state = new ExamState();
		state.readSettingsFrom(exam);
		state.paperId = exam.getQuestionPaperId();
		return state;
	}

	void readSettingsFrom(LiveExam exam) {
		titleEn = exam.getTitleEn();
		titleBn = exam.getTitleBn();
		descriptionEn = exam.getDescriptionEn();
		descriptionBn = exam.getDescriptionBn();
		rulesEn = exam.getRulesEn();
		rulesBn = exam.getRulesBn();
		paperLanguage = exam.getPaperLanguage();
		categoryId = exam.getCategoryId();
		mode = exam.getMode();
		liveAt = text(exam.getLiveAt());
		endsAt = text(exam.getEndsAt());
		durationMinutes = exam.getDurationMinutes();
		passPercent = exam.getPassPercent();
		writtenMaximum = exam.getWrittenMaximum();
		price = exam.getPrice();
		free = exam.isFree();
		discountPercent = exam.getDiscountPercent();
		discountEndsAt = text(exam.getDiscountEndsAt());
		accessDays = exam.getAccessDays();
		negativeMarks = exam.getNegativeMarks();
		answersChangeable = exam.isAnswersChangeable();
		previewEnabled = exam.isPreviewEnabled();
	}

	/** Writes the settings onto an exam. The paper pointer and the questions are applied by the publish step. */
	void applySettingsTo(LiveExam exam) {
		exam.setTitleEn(titleEn);
		exam.setTitleBn(titleBn);
		exam.setDescriptionEn(descriptionEn);
		exam.setDescriptionBn(descriptionBn);
		exam.setRulesEn(rulesEn);
		exam.setRulesBn(rulesBn);
		exam.setPaperLanguage(paperLanguage);
		if (categoryId != null) {
			exam.setCategoryId(categoryId);
		}
		exam.setMode(mode);
		exam.setLiveAt(instant(liveAt));
		exam.setEndsAt(instant(endsAt));
		exam.setDurationMinutes(durationMinutes);
		exam.setPassPercent(passPercent);
		exam.setWrittenMaximum(writtenMaximum);
		exam.setPrice(price);
		exam.setFree(free);
		exam.setDiscountPercent(discountPercent);
		exam.setDiscountEndsAt(instant(discountEndsAt));
		exam.setAccessDays(accessDays);
		exam.setNegativeMarks(negativeMarks);
		exam.setAnswersChangeable(answersChangeable);
		exam.setPreviewEnabled(previewEnabled);
	}

	/** The sum of the question marks (an MCQ exam's maximum). */
	int questionMarks() {
		int sum = 0;
		for (QuestionState question : questions) {
			sum += question.marks;
		}
		return sum;
	}

	QuestionState question(UUID id) {
		return questions.stream().filter(question -> id.equals(question.id)).findFirst().orElse(null);
	}

	private static String text(Instant instant) {
		return instant == null ? null : instant.toString();
	}

	static Instant instant(String text) {
		return text == null ? null : Instant.parse(text);
	}

	public static class QuestionState {

		public UUID id;

		public String stem;

		public int marks;

		public String explanation;

		public int position;

		public boolean multipleAnswer;

		public List<OptionState> options = new ArrayList<>();

	}

	public static class OptionState {

		public UUID id;

		public String text;

		public boolean correct;

		public int position;

	}

}
