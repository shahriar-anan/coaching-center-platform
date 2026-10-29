package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;
import com.coachingcenter.api.users.StudentProfile;
import com.coachingcenter.api.users.StudentProfileRepository;

/** Results report for one exam, and one student's exam history. Scores follow the leaderboard's publish rule. */
@Service
public class LiveExamResultsService {

	private static final Set<String> KINDS = Set.of(LiveExamParticipant.LIVE, LiveExamParticipant.ARCHIVED,
			LiveExamParticipant.RETAKE, LiveExamParticipant.PRACTICE);

	private static final Set<String> STATUSES = Set.of(LiveExamParticipant.IN_PROGRESS, LiveExamParticipant.SUBMITTED,
			LiveExamParticipant.FAILED_TO_SUBMIT);

	private static final Set<String> SORTS = Set.of("submittedAt,desc", "submittedAt,asc", "score,desc", "score,asc",
			"studentName");

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final LiveExamParticipantRepository participants;

	private final LiveExamQuestionRepository questions;

	private final LiveExamRetakeGrantRepository retakeGrants;

	private final StudentProfileRepository students;

	private final LiveExamMemberships memberships;

	public LiveExamResultsService(LiveExamRepository exams, LiveExamModuleRepository modules,
			LiveExamParticipantRepository participants, LiveExamQuestionRepository questions,
			LiveExamRetakeGrantRepository retakeGrants, StudentProfileRepository students, LiveExamMemberships memberships) {
		this.exams = exams;
		this.modules = modules;
		this.participants = participants;
		this.questions = questions;
		this.retakeGrants = retakeGrants;
		this.memberships = memberships;
		this.students = students;
	}

	/**
	 * Master Admin and System Admin read every exam. An instructor reads a written exam assigned to them; anything else is
	 * 404. {@code kind} is LIVE, or RETAKE for every attempt that is not the live one (the value the current web sends), and
	 * also ARCHIVED or PRACTICE; {@code attemptKind} matches one full value exactly. {@code status} is a participant status.
	 */
	@Transactional
	public ResultsReport report(UUID examId, CurrentActor actor, String kind, String attemptKind, String status, String q,
			String sort, Pageable pageable) {
		String kindFilter = filter(kind, KINDS);
		String attemptFilter = filter(attemptKind, KINDS);
		String statusFilter = filter(status, STATUSES);
		String order = sort == null || sort.isBlank() ? "submittedAt,desc" : sort.trim();
		if (!SORTS.contains(order)) {
			throw validationFailed();
		}
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (actor.role() == Role.INSTRUCTOR) {
			if (!LiveExam.WRITTEN.equals(exam.getExamType()) || !actor.userId().equals(exam.getInstructorId())) {
				throw notFound();
			}
		}
		else if (actor.role() != Role.MASTER_ADMIN && actor.role() != Role.SYSTEM_ADMIN) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		int maximum = maximum(exam);
		List<LiveExamParticipant> all = participants.findByExamIdAndDeletedAtIsNull(examId);
		Map<UUID, StudentProfile> profiles = profiles(all);
		Map<UUID, LiveExamRetakeGrant> grants = new HashMap<>();
		retakeGrants.findAllActive(examId).forEach(grant -> grants.put(grant.getStudentId(), grant));
		String needle = q == null ? "" : q.trim().toLowerCase();
		List<Row> rows = all.stream()
			.filter(p -> kindFilter == null || kindMatches(kindFilter, p.getKind()))
			.filter(p -> attemptFilter == null || attemptFilter.equals(p.getKind()))
			.filter(p -> statusFilter == null || statusFilter.equals(p.getStatus()))
			.map(p -> row(p, maximum, profiles.get(p.getStudentId()), grants))
			.filter(row -> needle.isEmpty() || row.studentName().toLowerCase().contains(needle)
					|| row.studentCode().toLowerCase().contains(needle))
			.sorted(comparator(order))
			.toList();
		int from = (int) Math.min(pageable.getOffset(), rows.size());
		int to = Math.min(from + pageable.getPageSize(), rows.size());
		Page<Row> page = new PageImpl<>(rows.subList(from, to), pageable, rows.size());
		return new ResultsReport(summary(all, exam), PageResponse.from(page));
	}

	/** One student's sittings across exams, newest first. Master Admin and System Admin only (checked by the caller). */
	@Transactional(readOnly = true)
	public PageResponse<HistoryRow> history(UUID studentId, Pageable pageable) {
		if (!students.existsById(studentId)) {
			throw notFound();
		}
		Page<LiveExamParticipant> page = participants.findHistory(studentId,
				PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
		Map<UUID, LiveExam> examById = new HashMap<>();
		Set<UUID> examIds = page.stream().map(LiveExamParticipant::getExamId).collect(Collectors.toSet());
		for (UUID examId : examIds) {
			exams.findByIdAndDeletedAtIsNull(examId).ifPresent(exam -> examById.put(exam.getId(), exam));
		}
		Map<UUID, LiveExamModule> moduleByExam = new HashMap<>();
		for (LiveExam exam : examById.values()) {
			memberships.primaryModuleId(exam.getId())
				.flatMap(modules::findByIdAndDeletedAtIsNull)
				.ifPresent(module -> moduleByExam.put(exam.getId(), module));
		}
		Map<UUID, Integer> maximumByExam = new HashMap<>();
		examById.values().forEach(exam -> maximumByExam.put(exam.getId(), maximum(exam)));
		return PageResponse.from(page.map(participant -> {
			LiveExam exam = examById.get(participant.getExamId());
			LiveExamModule module = exam == null ? null : moduleByExam.get(exam.getId());
			return new HistoryRow(participant.getId(), exam == null ? null : exam.getId(),
					exam == null ? null : exam.getTitleEn(), exam == null ? null : exam.getTitleBn(),
					module == null ? null : module.getId(), module == null ? null : module.getTitleEn(),
					module == null ? null : module.getTitleBn(), LiveExamParticipant.legacyKind(participant.getKind()),
					participant.getKind(), participant.getStatus(),
					McqScoring.plain(participant.getScore()), maximumByExam.getOrDefault(participant.getExamId(), 0), participant.getPassed(),
					participant.getSubmittedAt(), published(participant, exam), participant.getAttemptNo());
		}));
	}

	/** The legacy RETAKE filter means every attempt that is not the live one; the other values match exactly. */
	private static boolean kindMatches(String filter, String kind) {
		return LiveExamParticipant.RETAKE.equals(filter) ? !LiveExamParticipant.LIVE.equals(kind) : filter.equals(kind);
	}

	private Map<UUID, StudentProfile> profiles(List<LiveExamParticipant> all) {
		Map<UUID, StudentProfile> byId = new HashMap<>();
		List<UUID> ids = all.stream().map(LiveExamParticipant::getStudentId).distinct().toList();
		if (!ids.isEmpty()) {
			students.findAllById(ids).forEach(profile -> byId.put(profile.getUserId(), profile));
		}
		return byId;
	}

	private Row row(LiveExamParticipant participant, int maximum, StudentProfile profile, Map<UUID, LiveExamRetakeGrant> grants) {
		LiveExamRetakeGrant grant = grants.get(participant.getStudentId());
		return new Row(participant.getId(), participant.getStudentId(), profile == null ? "" : profile.getFullName(),
				profile == null || profile.getStudentCode() == null ? "" : profile.getStudentCode(), participant.getStatus(),
				LiveExamParticipant.legacyKind(participant.getKind()), participant.getKind(),
				McqScoring.plain(participant.getScore()), maximum, participant.getPassed(), participant.getStartedAt(),
				participant.getSubmittedAt(), participant.getPublishedAt(), participant.hasPendingRevision() ? Boolean.TRUE : null,
				participant.getAttemptNo(),
				grant == null ? null : new LiveExamEvaluationService.RetakeGrantView(grant.getId(), grant.getUsedParticipantId() != null));
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

	/**
	 * Board attempts only (the live attempt, or a practice exam's first attempt), plus a count per kind. A score counts once
	 * it is final: an MCQ submit, or a written publish, or a no-submit zero.
	 */
	private static Summary summary(List<LiveExamParticipant> all, LiveExam exam) {
		List<LiveExamParticipant> live = all.stream()
			.filter(p -> LiveExamParticipant.LIVE.equals(p.getKind()) || LiveExamParticipant.PRACTICE.equals(p.getKind()))
			.toList();
		List<LiveExamParticipant> finals = live.stream().filter(p -> counted(p, exam)).toList();
		List<BigDecimal> scores = finals.stream().map(p -> McqScoring.plain(p.getScore())).toList();
		int submitted = (int) live.stream().filter(p -> LiveExamParticipant.SUBMITTED.equals(p.getStatus())).count();
		int failed = (int) live.stream().filter(p -> LiveExamParticipant.FAILED_TO_SUBMIT.equals(p.getStatus())).count();
		int passCount = (int) finals.stream().filter(p -> Boolean.TRUE.equals(p.getPassed())).count();
		int practice = (int) all.stream()
			.filter(p -> LiveExamParticipant.RETAKE.equals(LiveExamParticipant.legacyKind(p.getKind()))).count();
		Double average = null;
		BigDecimal highest = null;
		BigDecimal lowest = null;
		Double passRate = null;
		if (!scores.isEmpty()) {
			average = Math.round(scores.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0) * 10) / 10.0;
			highest = scores.stream().max(Comparator.naturalOrder()).orElse(null);
			lowest = scores.stream().min(Comparator.naturalOrder()).orElse(null);
			passRate = Math.round((double) passCount * 1000 / scores.size()) / 10.0;
		}
		return new Summary(live.size(), submitted, failed, average, highest, lowest, passCount, passRate, practice,
				count(all, LiveExamParticipant.LIVE), count(all, LiveExamParticipant.ARCHIVED), count(all, LiveExamParticipant.RETAKE),
				count(all, LiveExamParticipant.PRACTICE));
	}

	private static int count(List<LiveExamParticipant> all, String kind) {
		return (int) all.stream().filter(p -> kind.equals(p.getKind())).count();
	}

	private static boolean counted(LiveExamParticipant participant, LiveExam exam) {
		if (participant.getScore() == null) {
			return false;
		}
		if (LiveExam.MCQ.equals(exam.getExamType())) {
			return LiveExamParticipant.SUBMITTED.equals(participant.getStatus());
		}
		return LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus()) || participant.getPublishedAt() != null;
	}

	private static boolean published(LiveExamParticipant participant, LiveExam exam) {
		if (exam != null && LiveExam.WRITTEN.equals(exam.getExamType())) {
			return participant.getPublishedAt() != null || LiveExamParticipant.FAILED_TO_SUBMIT.equals(participant.getStatus());
		}
		return LiveExamParticipant.SUBMITTED.equals(participant.getStatus());
	}

	private static Comparator<Row> comparator(String sort) {
		Comparator<Row> order = switch (sort) {
			case "submittedAt,asc" -> Comparator.comparing(Row::submittedAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()));
			case "score,desc" -> Comparator.comparing(Row::score, Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()));
			case "score,asc" -> Comparator.comparing(Row::score, Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()));
			case "studentName" -> Comparator.comparing(Row::studentName).thenComparing(Row::studentCode);
			default -> Comparator.comparing(Row::submittedAt, Comparator.nullsLast(Comparator.<Instant>reverseOrder()));
		};
		return order.thenComparing(Row::participantId);
	}

	private static String filter(String value, Set<String> allowed) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String upper = value.trim().toUpperCase();
		if (!allowed.contains(upper)) {
			throw validationFailed();
		}
		return upper;
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	/**
	 * The averages and pass figures cover board attempts only (live, or a practice exam's first attempt). {@code passRate} is
	 * a percentage of the counted results. {@code live}, {@code archived}, {@code retake} and {@code practice} count every
	 * attempt of that kind; {@code practiceParticipants} is the legacy count of every attempt that is not the live one.
	 */
	public record Summary(int participants, int submitted, int failedToSubmit,
			@JsonInclude(JsonInclude.Include.ALWAYS) Double averageScore, @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal highestScore,
			@JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal lowestScore, int passCount,
			@JsonInclude(JsonInclude.Include.ALWAYS) Double passRate, int practiceParticipants, int live, int archived,
			int retake, int practice) {
	}

	public record Row(UUID participantId, UUID studentId, String studentName, String studentCode, String status,
			String kind, String attemptKind, BigDecimal score, int maximum, Boolean passed, Instant startedAt, Instant submittedAt,
			Instant publishedAt, Boolean releasePending, int attemptNo, LiveExamEvaluationService.RetakeGrantView retakeGrant) {
	}

	public record ResultsReport(Summary summary, PageResponse<Row> rows) {
	}

	public record HistoryRow(UUID participantId, UUID examId, String examTitleEn, String examTitleBn, UUID moduleId,
			String moduleTitleEn, String moduleTitleBn, String kind, String attemptKind, String status, BigDecimal score,
			int maximum,
			Boolean passed, Instant submittedAt, boolean published, int attemptNo) {
	}

}
