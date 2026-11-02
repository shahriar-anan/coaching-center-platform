package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;

/**
 * Replaces an MCQ exam's questions with the ones in a client's Word file (plan: docs/exam-site/plans/mcq-docx-upload). The
 * preview reads the file and saves nothing. The apply replaces the questions in one go: a draft at once, and a published exam
 * as a staged revision that the Master Admin publishes. A file with any blocking problem saves nothing.
 */
@Service
public class LiveExamImportService {

	private final LiveExamRepository exams;

	private final LiveExamQuestionRepository questions;

	private final LiveExamOptionRepository options;

	private final LiveExamRevisionService staging;

	private final Clock clock;

	public LiveExamImportService(LiveExamRepository exams, LiveExamQuestionRepository questions,
			LiveExamOptionRepository options, LiveExamRevisionService staging, Clock clock) {
		this.exams = exams;
		this.questions = questions;
		this.options = options;
		this.staging = staging;
		this.clock = clock;
	}

	/** What staff read before they confirm: the file's header, questions and problems, and how many questions it replaces. */
	@Transactional(readOnly = true)
	public McqImportView preview(UUID examId, byte[] file) {
		LiveExam exam = requireMcq(examId);
		McqDocxParser.ImportResult result = McqDocxParser.parse(file);
		ExamState working = staging.working(exam);
		return new McqImportView(result.header(), result.fullMarks(), result.durationMinutes(), result.questions(),
				problems(result, working), result.warnings(), working.questions.size(), takesStagedEdits(exam));
	}

	/** The file's own problems, and a time in its header that the exam's window cannot hold (D16). */
	private static List<McqDocxParser.ImportProblem> problems(McqDocxParser.ImportResult result, ExamState working) {
		List<McqDocxParser.ImportProblem> problems = new ArrayList<>(result.problems());
		if (result.durationMinutes() != null && !fitsWindow(working, result.durationMinutes())) {
			problems.add(new McqDocxParser.ImportProblem(null, "DURATION_OUTSIDE_WINDOW"));
		}
		return problems;
	}

	private static boolean fitsWindow(ExamState working, int minutes) {
		if (LiveExam.MODE_PRACTICE.equals(working.mode) || working.liveAt == null || working.endsAt == null) {
			return true;
		}
		return !Instant.parse(working.endsAt).isBefore(Instant.parse(working.liveAt).plus(Duration.ofMinutes(minutes)));
	}

	/** Replaces the questions with the file's. Refused, and nothing saved, when the file has a blocking problem. */
	@Transactional
	public ImportApplied apply(UUID examId, byte[] file, UUID actorId) {
		LiveExam exam = requireMcq(examId);
		McqDocxParser.ImportResult result = McqDocxParser.parse(file);
		ExamState working = staging.working(exam);
		List<McqDocxParser.ImportProblem> problems = problems(result, working);
		if (!problems.isEmpty()) {
			List<FieldErrorDetail> details = new ArrayList<>();
			for (McqDocxParser.ImportProblem problem : problems) {
				String where = problem.question() == null ? "file" : "question " + problem.question();
				details.add(new FieldErrorDetail(where, problem.code()));
			}
			throw new ApiException(ErrorCode.QUESTION_FILE_HAS_PROBLEMS, HttpStatus.CONFLICT,
					"The file has problems, so nothing was saved.", details);
		}
		String header = result.header().isEmpty() ? null : String.join("\n", result.header());
		boolean staged = takesStagedEdits(exam);
		if (staged) {
			working.questions = toStates(result.questions());
			working.paperHeader = header;
			working.paperLanguage = "BN";
			if (result.durationMinutes() != null) {
				working.durationMinutes = result.durationMinutes();
			}
			staging.stage(exam, working, actorId);
		}
		else {
			replaceRows(exam, result, header);
		}
		return new ImportApplied(result.questions().size(), staged);
	}

	/** A draft's questions: the old ones are soft-deleted (saved answers still point at them, D12), and the file's are written. */
	private void replaceRows(LiveExam exam, McqDocxParser.ImportResult result, String header) {
		Instant now = clock.instant();
		for (LiveExamQuestion old : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(exam.getId())) {
			for (LiveExamOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(old.getId())) {
				option.setDeletedAt(now);
			}
			old.setDeletedAt(now);
		}
		int position = 0;
		for (McqDocxParser.ImportedQuestion parsed : result.questions()) {
			LiveExamQuestion row = new LiveExamQuestion();
			row.setExamId(exam.getId());
			row.setStem(parsed.stem());
			row.setMarks(1);
			row.setExplanation(parsed.explanation());
			row.setPosition(position++);
			row.setMultipleAnswer(false);
			row = questions.save(row);
			int optionPosition = 0;
			for (McqDocxParser.ImportedOption parsedOption : parsed.options()) {
				LiveExamOption option = new LiveExamOption();
				option.setQuestionId(row.getId());
				option.setOptionText(parsedOption.text());
				option.setCorrect(parsedOption.correct());
				option.setPosition(optionPosition++);
				options.save(option);
			}
		}
		exam.setPaperHeader(header);
		exam.setPaperLanguage("BN");
		if (result.durationMinutes() != null) {
			exam.setDurationMinutes(result.durationMinutes());
		}
		exam.setFinallySaved(false);
	}

	/** The file's questions as the staged state holds them. Each row gets its own id when the revision is published. */
	private static List<ExamState.QuestionState> toStates(List<McqDocxParser.ImportedQuestion> parsed) {
		List<ExamState.QuestionState> states = new ArrayList<>();
		int position = 0;
		for (McqDocxParser.ImportedQuestion question : parsed) {
			ExamState.QuestionState state = new ExamState.QuestionState();
			state.id = UUID.randomUUID();
			state.stem = question.stem();
			state.explanation = question.explanation();
			state.marks = 1;
			state.position = position++;
			state.multipleAnswer = false;
			int optionPosition = 0;
			for (McqDocxParser.ImportedOption option : question.options()) {
				ExamState.OptionState optionState = new ExamState.OptionState();
				optionState.id = UUID.randomUUID();
				optionState.text = option.text();
				optionState.correct = option.correct();
				optionState.position = optionPosition++;
				state.options.add(optionState);
			}
			states.add(state);
		}
		return states;
	}

	/** Staged edits go through a revision for a published exam, and for a withdrawn one that was ever published (D5). */
	static boolean takesStagedEdits(LiveExam exam) {
		return LiveExam.PUBLISHED.equals(exam.getStatus())
				|| (LiveExam.ARCHIVED.equals(exam.getStatus()) && exam.getPublishedAt() != null);
	}

	private LiveExam requireMcq(UUID examId) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
		if (!LiveExam.MCQ.equals(exam.getExamType())) {
			throw new ApiException(ErrorCode.EXAM_TYPE_MISMATCH, HttpStatus.CONFLICT, "Only an MCQ exam has questions.");
		}
		return exam;
	}

	/** The preview of an import: the file as read, and what it would replace. */
	public record McqImportView(List<String> header, Integer fullMarks, Integer durationMinutes,
			List<McqDocxParser.ImportedQuestion> questions, List<McqDocxParser.ImportProblem> problems,
			List<String> warnings, int currentQuestions, boolean staged) {
	}

	/** What an apply saved: how many questions were written, and whether they wait as a staged revision. */
	public record ImportApplied(int questions, boolean staged) {
	}
}
