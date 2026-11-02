package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.StudentProfileRepository;

/**
 * The leaderboard and a student's position on it (NR-01, D21 to D24, R11 to R13). The board holds the board attempts: the
 * live attempt of a live exam, or the first attempt of a practice exam. Only results at or above the pass mark are listed.
 * Archived attempts and retakes are never on the board; they get a relative position among its rows.
 */
@Service
public class LiveExamLeaderboardService {

	/** A live attempt, ranked among the board attempts posted so far while the window is open. */
	public static final String CURRENT = "CURRENT";

	/** An archived attempt or a retake: the rank its score would take among the board's rows. */
	public static final String RELATIVE = "RELATIVE";

	/** A live attempt after the window, or a practice first attempt: its place on the board. */
	public static final String BOARD = "BOARD";

	private final LiveExamRepository exams;

	private final LiveExamParticipantRepository participants;

	private final LiveExamQuestionRepository questions;

	private final StudentProfileRepository students;

	private final LiveExamService examViews;

	private final Clock clock;

	private final McqAttemptSnapshot snapshots;

	public LiveExamLeaderboardService(LiveExamRepository exams, LiveExamParticipantRepository participants,
			LiveExamQuestionRepository questions, StudentProfileRepository students, LiveExamService examViews, Clock clock,
			McqAttemptSnapshot snapshots) {
		this.exams = exams;
		this.participants = participants;
		this.questions = questions;
		this.students = students;
		this.examViews = examViews;
		this.clock = clock;
		this.snapshots = snapshots;
	}

	/**
	 * The board. Staff who may mark the exam read the rows at any time, with the count of results below the pass mark. An
	 * instructor gets 404 on a written exam until a paper of it is released (see {@link #resultsOpen}); Master Admin and
	 * System Admin are not held to that. A student sees the rows only once the window has ended (or for a practice exam),
	 * and always their own position in {@code me}; during the window the rows are empty (R12).
	 */
	@Transactional(readOnly = true)
	public Leaderboard board(UUID examId, CurrentActor actor, boolean marker) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (marker) {
			if (!LiveExamEvaluationService.reads(exam, actor)) {
				throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
			}
			if (actor.role() == Role.INSTRUCTOR && !resultsOpen(exam)) {
				throw notFound();
			}
		}
		else {
			examViews.get(examId, false, actor.userId());
		}
		Instant now = clock.instant();
		String phase = ExamWindow.phase(exam, now);
		boolean student = !marker && actor.role() == Role.STUDENT;
		List<LiveExamParticipant> attempts = participants.findBoardAttempts(examId);
		List<LiveExamParticipant> board = attempts.stream().filter(p -> onBoard(p, exam)).toList();
		Position me = student ? mine(exam, actor.userId(), board, now) : null;
		boolean showRows = !student || ExamWindow.ENDED.equals(phase) || ExamWindow.PRACTICE.equals(phase);
		List<LeaderboardRow> rows = new ArrayList<>();
		if (showRows) {
			int liveMaximum = maximum(exam);
			Map<UUID, String> names = names(board);
			Map<UUID, Standing> standings = new HashMap<>();
			for (LiveExamParticipant row : board) {
				standings.put(row.getId(), standing(exam, row, liveMaximum));
			}
			// Ranked by raw marks (D15, changed 2 November 2026). Ties: earlier submit, then name. Each row still shows its own maximum.
			List<LiveExamParticipant> ordered = board.stream()
				.sorted(Comparator.comparing(LiveExamParticipant::getScore, Comparator.reverseOrder())
					.thenComparing(LiveExamParticipant::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing(p -> names.getOrDefault(p.getStudentId(), "")))
				.toList();
			int rank = 1;
			BigDecimal previous = null;
			for (int index = 0; index < ordered.size(); index++) {
				LiveExamParticipant row = ordered.get(index);
				Standing standing = standings.get(row.getId());
				if (previous != null && row.getScore().compareTo(previous) != 0) {
					rank = index + 1;
				}
				previous = row.getScore();
				rows.add(new LeaderboardRow(names.getOrDefault(row.getStudentId(), ""), rank, McqScoring.plain(row.getScore()),
						standing.maximum(), true, student && actor.userId().equals(row.getStudentId()), standing.percent()));
			}
		}
		Integer belowPass = student ? null
				: (int) attempts.stream().filter(p -> ready(p, exam) && !Boolean.TRUE.equals(p.getPassed())).count();
		return new Leaderboard(rows, me, belowPass);
	}

	/**
	 * Where an attempt stands, or null while it has no result to rank: an MCQ attempt that is not posted, or a written one
	 * that is not released. The board is read as it is now, so a live attempt's {@code CURRENT} rank can move.
	 */
	@Transactional(readOnly = true)
	public Position positionOf(LiveExam exam, LiveExamParticipant attempt) {
		List<LiveExamParticipant> board = participants.findBoardAttempts(exam.getId()).stream()
			.filter(p -> onBoard(p, exam))
			.toList();
		return position(exam, attempt, board, clock.instant());
	}

	private Position mine(LiveExam exam, UUID studentId, List<LiveExamParticipant> board, Instant now) {
		List<LiveExamParticipant> own = participants
			.findByExamIdAndStudentIdAndDeletedAtIsNullOrderByAttemptNoDesc(exam.getId(), studentId);
		LiveExamParticipant chosen = own.stream().filter(p -> isBoardKind(p) && ready(p, exam)).findFirst()
			.orElseGet(() -> own.stream().filter(p -> ready(p, exam)).findFirst().orElse(null));
		return chosen == null ? null : position(exam, chosen, board, now);
	}

	private Position position(LiveExam exam, LiveExamParticipant attempt, List<LiveExamParticipant> board, Instant now) {
		if (!ready(attempt, exam)) {
			return null;
		}
		String type = !isBoardKind(attempt) ? RELATIVE
				: LiveExamParticipant.LIVE.equals(attempt.getKind()) && ExamWindow.LIVE.equals(ExamWindow.phase(exam, now))
						? CURRENT : BOARD;
		if (!Boolean.TRUE.equals(attempt.getPassed())) {
			return new Position(type, null, board.size(), true);
		}
		int ahead = (int) board.stream().filter(p -> p.getScore().compareTo(attempt.getScore()) > 0).count();
		return new Position(type, ahead + 1, board.size(), false);
	}

	/** An attempt's own maximum and its percent of it (D15). The percent is null when that maximum is 0. */
	private Standing standing(LiveExam exam, LiveExamParticipant attempt, int liveMaximum) {
		int maximum = liveMaximum;
		if (LiveExam.MCQ.equals(exam.getExamType()) && attempt.getQuestionSnapshot() != null) {
			maximum = McqScoring.maximum(snapshots.read(attempt).key());
		}
		// A written attempt is ranked on the maximum it was sat on (V67), not the exam's current one.
		if (LiveExam.WRITTEN.equals(exam.getExamType())) {
			maximum = attempt.writtenMaximum(exam);
		}
		BigDecimal percent = maximum == 0 ? null
				: attempt.getScore().multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(maximum), 2, RoundingMode.HALF_UP);
		return new Standing(maximum, percent);
	}

	/** Has a result a student may see ranked: posted for an MCQ, released (or a no-submit zero) for a written paper. */
	private static boolean ready(LiveExamParticipant participant, LiveExam exam) {
		if (participant.getScore() == null) {
			return false;
		}
		if (LiveExam.MCQ.equals(exam.getExamType())) {
			return LiveExamParticipant.SUBMITTED.equals(participant.getStatus());
		}
		return LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus()) || participant.getPublishedAt() != null;
	}

	/**
	 * Whether a written exam's results are released for staff who are not Master or System Admins: a paper is released once
	 * the Master Admin publishes it (publish-results sets its {@code publishedAt}). An MCQ exam has no release step.
	 */
	private boolean resultsOpen(LiveExam exam) {
		return !LiveExam.WRITTEN.equals(exam.getExamType())
				|| participants.existsByExamIdAndDeletedAtIsNullAndPublishedAtIsNotNull(exam.getId());
	}

	/** A listed row: a board attempt with a result, at or above the pass mark (D24). */
	private static boolean onBoard(LiveExamParticipant participant, LiveExam exam) {
		return ready(participant, exam) && Boolean.TRUE.equals(participant.getPassed());
	}

	private static boolean isBoardKind(LiveExamParticipant participant) {
		return LiveExamParticipant.LIVE.equals(participant.getKind()) || LiveExamParticipant.PRACTICE.equals(participant.getKind());
	}

	private Map<UUID, String> names(List<LiveExamParticipant> rows) {
		Map<UUID, String> byId = new HashMap<>();
		List<UUID> ids = rows.stream().map(LiveExamParticipant::getStudentId).distinct().toList();
		if (!ids.isEmpty()) {
			students.findAllById(ids).forEach(profile -> byId.put(profile.getUserId(), profile.getFullName()));
		}
		return byId;
	}

	private int maximum(LiveExam exam) {
		if (LiveExam.WRITTEN.equals(exam.getExamType())) {
			return exam.getWrittenMaximum() == null ? 0 : exam.getWrittenMaximum();
		}
		int sum = 0;
		for (LiveExamQuestion question : questions.findByExamIdAndDeletedAtIsNullOrderByPositionAscIdAsc(exam.getId())) {
			sum += question.getMarks();
		}
		return sum;
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** {@code me} is a student's own position and is null for staff. {@code belowPass} counts results under the pass mark, for staff. */
	public record Leaderboard(List<LeaderboardRow> rows, Position me, Integer belowPass) {
	}

	/** {@code percent} is the score over {@code maximum}, 2 decimal places; null when the maximum is 0. */
	public record LeaderboardRow(String name, int rank, BigDecimal score, int maximum, boolean passed, boolean mine,
			BigDecimal percent) {
	}

	/** An attempt's own maximum and its percent of it. */
	private record Standing(int maximum, BigDecimal percent) {
	}

	/** {@code rank} is null when {@code belowPass}; {@code of} is the number of rows on the board. */
	public record Position(String type, Integer rank, int of, boolean belowPass) {
	}

}
