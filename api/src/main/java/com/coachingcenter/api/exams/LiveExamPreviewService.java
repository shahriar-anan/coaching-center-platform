package com.coachingcenter.api.exams;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;

/**
 * The preview of an exam, for promotion (C10', P-b): a signed-in student sees the paper without sitting it and without
 * buying. It opens only after the live window has ended, or at any time for a practice exam, and only when staff switched
 * preview on. It never carries the correct options or the explanations.
 */
@Service
public class LiveExamPreviewService {

	private final LiveExamRepository exams;

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final LiveExamQuestionPaperRepository papers;

	private final LiveExamCatalogVisibility visibility;

	private final LocalFileStorage files;

	private final Clock clock;

	public LiveExamPreviewService(LiveExamRepository exams, LiveExamQuestionRepository questions,
			LiveExamOptionRepository options, LiveExamQuestionPaperRepository papers,
			LiveExamCatalogVisibility visibility, LocalFileStorage files, Clock clock) {
		this.exams = exams;
		this.questions = questions;
		this.options = options;
		this.papers = papers;
		this.visibility = visibility;
		this.files = files;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PreviewView preview(UUID examId) {
		LiveExam exam = previewable(examId);
		Instant now = clock.instant();
		boolean mcq = LiveExam.MCQ.equals(exam.getExamType());
		List<PreviewQuestion> views = null;
		int maximum = exam.getWrittenMaximum() == null ? 0 : exam.getWrittenMaximum();
		if (mcq) {
			views = questionViews(examId);
			maximum = views.stream().mapToInt(PreviewQuestion::marks).sum();
		}
		return new PreviewView(exam.getId(), exam.getExamType(), exam.getPaperLanguage(), exam.getTitleEn(),
				exam.getTitleBn(), exam.getDescriptionEn(), exam.getDescriptionBn(), exam.getRulesEn(), exam.getRulesBn(),
				exam.getDurationMinutes(), exam.getPassPercent(), maximum, exam.getMode(), ExamWindow.phase(exam, now),
				exam.getNegativeMarks(), ExamPricing.view(exam, now, false), mcq ? null : exam.getQuestionPaperId() != null,
				views);
	}

	/** The published paper of a written exam. */
	@Transactional(readOnly = true)
	public byte[] paperPdf(UUID examId) {
		LiveExam exam = previewable(examId);
		if (!LiveExam.WRITTEN.equals(exam.getExamType()) || exam.getQuestionPaperId() == null) {
			throw notFound();
		}
		LiveExamQuestionPaper paper = papers.findById(exam.getQuestionPaperId()).orElseThrow(this::notFound);
		try {
			return java.nio.file.Files.readAllBytes(files.resolve(paper.getStoredFileName()));
		}
		catch (IOException exception) {
			throw notFound();
		}
	}

	/** A published exam with preview on, whose window has ended or which is practice, in an open category. Else not found. */
	private LiveExam previewable(UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		String phase = ExamWindow.phase(exam, clock.instant());
		boolean open = ExamWindow.ENDED.equals(phase) || ExamWindow.PRACTICE.equals(phase);
		if (!LiveExam.PUBLISHED.equals(exam.getStatus()) || !exam.isPreviewEnabled() || !open
				|| visibility.branchHidden(exam.getCategoryId())) {
			throw notFound();
		}
		return exam;
	}

	private List<PreviewQuestion> questionViews(UUID examId) {
		List<LiveExamQuestion> rows = questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(examId);
		Map<UUID, List<PreviewOption>> byQuestion = new HashMap<>();
		if (!rows.isEmpty()) {
			List<UUID> ids = rows.stream().map(LiveExamQuestion::getId).toList();
			for (LiveExamOption option : options.findByQuestionIdInAndDeletedAtIsNullOrderByPositionAscIdAsc(ids)) {
				byQuestion.computeIfAbsent(option.getQuestionId(), ignored -> new ArrayList<>())
					.add(new PreviewOption(option.getId(), option.getOptionText(), option.getPosition()));
			}
		}
		List<PreviewQuestion> views = new ArrayList<>();
		for (LiveExamQuestion question : rows) {
			views.add(new PreviewQuestion(question.getId(), question.getStem(), question.getMarks(), question.getPosition(),
					question.isMultipleAnswer(), byQuestion.getOrDefault(question.getId(), List.of())));
		}
		return views;
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** {@code hasPaper} is set for a written exam and {@code questions} for an MCQ exam. */
	public record PreviewView(UUID id, String examType, String paperLanguage, String titleEn, String titleBn,
			String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, int durationMinutes,
			Integer passPercent, int maximum, String mode, String phase, BigDecimal negativeMarks, PriceView pricing,
			Boolean hasPaper, List<PreviewQuestion> questions) {
	}

	public record PreviewQuestion(UUID id, String stem, int marks, int position, boolean multipleAnswer,
			List<PreviewOption> options) {
	}

	public record PreviewOption(UUID id, String text, int position) {
	}

}
