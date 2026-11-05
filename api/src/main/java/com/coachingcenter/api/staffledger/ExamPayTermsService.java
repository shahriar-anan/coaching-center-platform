package com.coachingcenter.api.staffledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.exams.LiveExamInstructorService;
import com.coachingcenter.api.exams.LiveExamService;
import com.coachingcenter.api.staffledger.StaffMarkingService.ExamMarking;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

import tools.jackson.databind.JsonNode;

/**
 * Pay per written exam and the marker on it (staff pay plan, phase 8, decision P18).
 *
 * <p>
 * A written exam may carry its own rate per sheet ({@code live_exam_pay_terms}). A sheet earns at the exam's rate, else the
 * instructor's own rate, else the default; {@link #effectiveRate} states that order and the earnings job's query repeats it in
 * SQL. A change to the exam's rate takes effect for sheets published from now: existing MARKING lines are never touched.
 *
 * <p>
 * Every change to the rate is written to {@code live_exam_assignment_log} with the actor, and the audit log. A change that
 * alters what the assigned instructor earns is told to that instructor, and every other active Master Admin is told of any
 * change. Only the Master Admin sets rates or assigns markers (open question O1). The marker is optional when a written exam
 * is created, and a written exam with no marker can still be published (O2).
 */
@Service
public class ExamPayTermsService {

	static final String WRITTEN = "WRITTEN";

	/** The exam page for the Master Admin, where the change notice links. */
	static final String EXAM_LINK = "/staff/exams/";

	/** The instructor's own pay page, where the rate notice links. */
	static final String MY_PAY_LINK = StaffEarningsService.EARNED_LINK;

	/** NUMERIC(12,2): at most ten digits before the point. */
	private static final int MAX_INTEGER_DIGITS = 10;

	private static final String NOT_WRITTEN = "Pay per sheet and a marker apply to a written exam only.";

	private static final String RATE_RULE = "Pay per sheet must be a number above zero, with at most two decimals.";

	private static final String EXAM = """
			select exam_type, instructor_id, title_en, title_bn from live_exams where id = :id and deleted_at is null
			""";

	private static final String OWN_RATE = "select sheet_rate from live_exam_pay_terms where exam_id = :exam";

	private static final String INSTRUCTOR_RATE = "select sheet_rate from staff_pay_settings where user_id = :user";

	private static final String DEFAULT_RATE = "select sheet_rate from staff_pay_defaults where id = 1";

	private static final String FULL_NAME = "select full_name from admin_profiles where user_id = :user";

	private static final String MASTER_ADMINS = """
			select id from users where role = 'MASTER_ADMIN' and status = 'ACTIVE' and deleted_at is null
			""";

	private static final String UPSERT_TERMS = """
			insert into live_exam_pay_terms (exam_id, sheet_rate, updated_by, updated_at)
			values (:exam, :rate, :by, :now)
			on conflict (exam_id) do update set sheet_rate = excluded.sheet_rate, updated_by = excluded.updated_by,
			  updated_at = excluded.updated_at
			""";

	private static final String DELETE_TERMS = "delete from live_exam_pay_terms where exam_id = :exam";

	private static final String INSERT_LOG = """
			insert into live_exam_assignment_log (id, exam_id, kind, instructor_id, previous_instructor_id, sheet_rate,
			  previous_sheet_rate, changed_by, created_at)
			values (:id, :exam, :kind, :instructor, null, :rate, :previous, :by, :now)
			""";

	private static final String LOG = """
			select l.kind, l.sheet_rate, l.previous_sheet_rate, l.created_at,
			  i.full_name as instructor_name, p.full_name as previous_name, c.full_name as changed_by_name
			from live_exam_assignment_log l
			left join admin_profiles i on i.user_id = l.instructor_id
			left join admin_profiles p on p.user_id = l.previous_instructor_id
			left join admin_profiles c on c.user_id = l.changed_by
			where l.exam_id = :exam
			order by l.created_at desc, l.id desc
			""";

	private static final String MY_EXAMS = """
			select e.id, e.title_en, e.title_bn, t.sheet_rate as exam_rate
			from live_exams e
			left join live_exam_pay_terms t on t.exam_id = e.id
			where e.instructor_id = :user and e.exam_type = 'WRITTEN' and e.deleted_at is null
			order by e.title_en, e.id
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final StaffNotificationWriter notifications;

	private final AuditRecorder audit;

	private final StaffMarkingService marking;

	private final LiveExamService exams;

	private final LiveExamInstructorService instructors;

	private final Clock clock;

	private final PermissionChecker permissions;

	public ExamPayTermsService(JdbcTemplate jdbc, StaffNotificationWriter notifications, AuditRecorder audit,
			StaffMarkingService marking, LiveExamService exams, LiveExamInstructorService instructors, Clock clock,
			PermissionChecker permissions) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.notifications = notifications;
		this.audit = audit;
		this.marking = marking;
		this.exams = exams;
		this.instructors = instructors;
		this.clock = clock;
		this.permissions = permissions;
	}

	/**
	 * One exam's pay terms. The Master Admin reads any exam; anyone else reads only the exam they are assigned to, and is
	 * refused otherwise.
	 */
	@Transactional(readOnly = true)
	public PayTerms view(UUID examId, UUID actorId, boolean manager) {
		ExamRow exam = exam(examId);
		if (!manager && (exam.instructorId() == null || !exam.instructorId().equals(actorId))) {
			throw denied();
		}
		return build(exam);
	}

	/**
	 * Sets or clears (null) the exam's own rate per sheet. Only a change is written: the log row, the audit entry and the
	 * notices. The change applies to sheets published from now on.
	 */
	@Transactional
	public PayTerms setRate(UUID examId, JsonNode body, UUID actorId) {
		ExamRow exam = exam(examId);
		if (!WRITTEN.equals(exam.examType())) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("sheetRate", "A multiple-choice exam has no pay per sheet.")));
		}
		BigDecimal next = clearOrRate(body);
		BigDecimal previous = ownRate(examId);
		if (sameRate(previous, next)) {
			return build(exam);
		}
		BigDecimal instructorRate = instructorRate(exam.instructorId());
		BigDecimal defaults = defaultRate();
		BigDecimal before = effectiveRate(previous, instructorRate, defaults);
		BigDecimal after = effectiveRate(next, instructorRate, defaults);

		UUID logId = saveRate(examId, exam.instructorId(), previous, next, actorId);

		String titleEn = StaffNotificationWriter.fitText(exam.titleEn());
		String titleBn = StaffNotificationWriter.fitText(exam.titleBn());
		if (exam.instructorId() != null && !sameRate(before, after)) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("examTitleEn", titleEn);
			payload.put("examTitleBn", titleBn);
			payload.put("fromRate", before);
			payload.put("toRate", after);
			notifications.notify(exam.instructorId(), StaffNotificationKind.EXAM_RATE_CHANGED, payload, MY_PAY_LINK,
					"EXAM_RATE_CHANGED:" + exam.instructorId() + ":" + logId);
		}
		String actorName = StaffNotificationWriter.fitText(fullName(actorId));
		String markerName = StaffNotificationWriter.fitText(fullName(exam.instructorId()));
		for (UUID master : masterAdmins()) {
			if (master.equals(actorId)) {
				continue;
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("personName", actorName);
			payload.put("examTitleEn", titleEn);
			payload.put("examTitleBn", titleBn);
			payload.put("previousMarkerName", markerName);
			payload.put("markerName", markerName);
			payload.put("fromRate", before);
			payload.put("toRate", after);
			notifications.notify(master, StaffNotificationKind.EXAM_PAY_TERMS_CHANGED, payload, EXAM_LINK + examId,
					"EXAM_PAY_TERMS_CHANGED:" + master + ":" + logId);
		}
		return build(exam);
	}

	/**
	 * Creates an exam exactly as the create routes do, then applies the optional marker and pay per sheet (phase 8). A body
	 * with neither goes through unchanged. The marker goes through the existing assign service, so its rules apply. The
	 * whole creation is one transaction: a refused marker or rate refuses the exam too.
	 */
	@Transactional
	public LiveExamService.ExamView create(UUID moduleId, JsonNode body) {
		JsonNode rateNode = present(body, "sheetRate");
		JsonNode markerNode = present(body, "instructorId");
		List<FieldErrorDetail> errors = new ArrayList<>();
		BigDecimal rate = null;
		UUID marker = null;
		if (rateNode != null) {
			rate = rateOf(rateNode);
			if (rate == null) {
				errors.add(new FieldErrorDetail("sheetRate", RATE_RULE));
			}
		}
		if (markerNode != null) {
			marker = uuidOf(markerNode);
			if (marker == null) {
				errors.add(new FieldErrorDetail("instructorId", "Instructor must be an id."));
			}
		}
		if ((rateNode != null || markerNode != null) && !WRITTEN.equals(body.path("examType").asText())) {
			if (rateNode != null) {
				errors.add(new FieldErrorDetail("sheetRate", NOT_WRITTEN));
			}
			if (markerNode != null) {
				errors.add(new FieldErrorDetail("instructorId", NOT_WRITTEN));
			}
		}
		if (!errors.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
		}
		// Only the Master Admin sets a rate or assigns a marker (decision O1). A System Admin may create exams, so the create
		// route must refuse these two fields from anyone without the same permissions the pay-terms and assign routes require.
		if ((rate != null && !permissions.has(Permissions.STAFF_PAY_MANAGE))
				|| (marker != null && !permissions.has(Permissions.LIVE_EXAM_ASSIGN))) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}

		LiveExamService.ExamView created = exams.create(moduleId, body);
		if (rate == null && marker == null) {
			return created;
		}
		if (marker != null) {
			instructors.assign(created.id(), marker);
		}
		if (rate != null) {
			saveRate(created.id(), marker, null, rate, currentActorId());
		}
		// Read again, so the response shows the marker that was just assigned.
		return marker == null ? created : exams.get(created.id(), true, null);
	}

	/** The signed-in instructor's assigned written exams, with the rate each sheet earns at and the marking counts. */
	@Transactional(readOnly = true)
	public List<MyExam> myExams(UUID me) {
		Map<UUID, ExamMarking> figures = new HashMap<>();
		for (ExamMarking row : marking.self(me).exams()) {
			figures.put(row.examId(), row);
		}
		BigDecimal instructorRate = instructorRate(me);
		BigDecimal defaults = defaultRate();
		return jdbc.query(MY_EXAMS, new MapSqlParameterSource("user", me), (rs, number) -> {
			UUID examId = rs.getObject("id", UUID.class);
			BigDecimal own = rs.getBigDecimal("exam_rate");
			ExamMarking row = figures.get(examId);
			return new MyExam(examId, rs.getString("title_en"), rs.getString("title_bn"), money(own),
					money(effectiveRate(own, instructorRate, defaults)), row == null ? 0 : row.marked(),
					row == null ? 0 : row.published());
		});
	}

	/**
	 * The rate a sheet earns at (staff pay plan, decision P18): the exam's own rate, else the instructor's rate, else the
	 * default. Null only when the default is missing, which the table does not allow.
	 */
	static BigDecimal effectiveRate(BigDecimal examRate, BigDecimal instructorRate, BigDecimal defaultRate) {
		if (examRate != null) {
			return examRate;
		}
		return instructorRate != null ? instructorRate : defaultRate;
	}

	/**
	 * A rate typed by a person or sent by a client: a number above zero, at most two decimals, and no more than ten digits
	 * before the point (NUMERIC(12,2)). Returns it to two places, or null when it breaks one of those rules.
	 */
	static BigDecimal rateOf(JsonNode node) {
		if (node == null || !node.isNumber()) {
			return null;
		}
		BigDecimal value = node.decimalValue();
		if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) {
			return null;
		}
		value = value.setScale(2, RoundingMode.UNNECESSARY);
		if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
			return null;
		}
		return value;
	}

	/** The PUT body's sheetRate: a rate, or null to clear the exam's own rate. The key must be present. */
	private static BigDecimal clearOrRate(JsonNode body) {
		JsonNode node = body == null ? null : body.get("sheetRate");
		if (node == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("sheetRate", "Send a rate, or null to clear the exam's own rate.")));
		}
		if (node.isNull()) {
			return null;
		}
		BigDecimal rate = rateOf(node);
		if (rate == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("sheetRate", RATE_RULE)));
		}
		return rate;
	}

	private static boolean sameRate(BigDecimal a, BigDecimal b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.compareTo(b) == 0;
	}

	private static BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

	/** Writes the exam's own rate (or clears it) and one log row, and audits the change. Returns the log row's id. */
	private UUID saveRate(UUID examId, UUID instructorId, BigDecimal previous, BigDecimal next, UUID actorId) {
		Instant now = clock.instant();
		if (next == null) {
			jdbc.update(DELETE_TERMS, new MapSqlParameterSource("exam", examId));
		}
		else {
			jdbc.update(UPSERT_TERMS, new MapSqlParameterSource().addValue("exam", examId)
				.addValue("rate", next, Types.NUMERIC)
				.addValue("by", actorId, Types.OTHER)
				.addValue("now", Timestamp.from(now)));
		}
		UUID logId = UUID.randomUUID();
		jdbc.update(INSERT_LOG, new MapSqlParameterSource().addValue("id", logId)
			.addValue("exam", examId)
			.addValue("kind", previous == null ? "RATE_SET" : "RATE_CHANGED")
			.addValue("instructor", instructorId, Types.OTHER)
			.addValue("rate", next, Types.NUMERIC)
			.addValue("previous", previous, Types.NUMERIC)
			.addValue("by", actorId, Types.OTHER)
			.addValue("now", Timestamp.from(now)));
		audit.record(AuditRecorder.LIVE_EXAM_PAY_TERMS_SET, AuditRecorder.LIVE_EXAM_ENTITY, examId);
		return logId;
	}

	private PayTerms build(ExamRow exam) {
		BigDecimal own = ownRate(exam.id());
		BigDecimal effective = effectiveRate(own, instructorRate(exam.instructorId()), defaultRate());
		List<LogEntry> log = jdbc.query(LOG, new MapSqlParameterSource("exam", exam.id()), (rs, number) -> new LogEntry(
				rs.getString("kind"), rs.getString("instructor_name"), rs.getString("previous_name"),
				money(rs.getBigDecimal("sheet_rate")), money(rs.getBigDecimal("previous_sheet_rate")),
				rs.getString("changed_by_name"), rs.getTimestamp("created_at").toInstant()));
		Person instructor = exam.instructorId() == null ? null : new Person(exam.instructorId(), fullName(exam.instructorId()));
		return new PayTerms(instructor, money(own), money(effective), log);
	}

	private ExamRow exam(UUID examId) {
		List<ExamRow> rows = jdbc.query(EXAM, new MapSqlParameterSource("id", examId), (rs, number) -> new ExamRow(examId,
				rs.getString("exam_type"), rs.getObject("instructor_id", UUID.class), rs.getString("title_en"),
				rs.getString("title_bn")));
		if (rows.isEmpty()) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		return rows.get(0);
	}

	private BigDecimal ownRate(UUID examId) {
		return firstRate(OWN_RATE, new MapSqlParameterSource("exam", examId));
	}

	private BigDecimal instructorRate(UUID instructorId) {
		return instructorId == null ? null : firstRate(INSTRUCTOR_RATE, new MapSqlParameterSource("user", instructorId));
	}

	private BigDecimal defaultRate() {
		return jdbc.queryForObject(DEFAULT_RATE, new MapSqlParameterSource(), BigDecimal.class);
	}

	private BigDecimal firstRate(String sql, MapSqlParameterSource params) {
		List<BigDecimal> rows = jdbc.query(sql, params, (rs, number) -> rs.getBigDecimal("sheet_rate"));
		return rows.isEmpty() ? null : rows.get(0);
	}

	private String fullName(UUID userId) {
		if (userId == null) {
			return null;
		}
		List<String> names = jdbc.query(FULL_NAME, new MapSqlParameterSource("user", userId),
				(rs, number) -> rs.getString("full_name"));
		return names.isEmpty() ? null : names.get(0);
	}

	/** Every active Master Admin, to be told of a change. */
	private List<UUID> masterAdmins() {
		return jdbc.queryForList(MASTER_ADMINS, new MapSqlParameterSource(), UUID.class);
	}

	private static JsonNode present(JsonNode body, String field) {
		JsonNode node = body == null ? null : body.get(field);
		return node == null || node.isNull() ? null : node;
	}

	private static UUID uuidOf(JsonNode node) {
		if (!node.isTextual()) {
			return null;
		}
		try {
			return UUID.fromString(node.asText());
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static UUID currentActorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		return null;
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	private record ExamRow(UUID id, String examType, UUID instructorId, String titleEn, String titleBn) {
	}

	/** A person named in the pay terms: the id and the name on the admin profile (null when there is none). */
	public record Person(UUID id, String name) {
	}

	/**
	 * An exam's pay terms. {@code examRate} is the exam's own rate (null when it has none). {@code effectiveRate} is the rate a
	 * sheet earns at now. {@code log} is the change history, newest first.
	 */
	public record PayTerms(Person instructor, BigDecimal examRate, BigDecimal effectiveRate, List<LogEntry> log) {
	}

	/** One change in the history. Names are null when the person has no profile. */
	public record LogEntry(String kind, String instructorName, String previousInstructorName, BigDecimal sheetRate,
			BigDecimal previousSheetRate, String changedByName, Instant createdAt) {
	}

	/** One of the instructor's written exams, with its own rate, the rate sheets earn at, and the marking counts. */
	public record MyExam(UUID examId, String titleEn, String titleBn, BigDecimal examRate, BigDecimal effectiveRate,
			int marked, int published) {
	}

}
