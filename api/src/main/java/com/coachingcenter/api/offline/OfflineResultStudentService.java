package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.notFound;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The results a student sees for their own admission (results-api-contract, the student routes; rules R2, R6, R12 and A2). An open admission
 * sees the published sheet of each exam of its batch. A closed admission sees, for each exam, the latest sheet that was published before it was
 * closed (rule R12), even when a newer one has since replaced it. An exam shows only when the student has a row in its sheet: a present row,
 * or an absent one for an expected student (rule A2). Every other student is hidden.
 */
@Service
public class OfflineResultStudentService {

	private static final String OPEN_SHEETS = "s.status = 'PUBLISHED'";

	private static final String CLOSED_SHEETS = "s.status in ('PUBLISHED', 'REPLACED') and s.published_at < ?";

	private final OfflineAdmissionService admissions;

	private final JdbcTemplate jdbc;

	public OfflineResultStudentService(OfflineAdmissionService admissions, JdbcTemplate jdbc) {
		this.admissions = admissions;
		this.jdbc = jdbc;
	}

	/** The results of the student's admission, newest exam first, each with the student's own row. 404 for another student's admission. */
	@Transactional(readOnly = true)
	public StudentResults results(UUID studentId, UUID admissionId) {
		OfflineAdmission admission = admissions.ownById(studentId, admissionId);
		if (admission.getBatchId() == null) {
			throw notFound();
		}
		String[] batch = jdbc.queryForObject("select name_en, name_bn from offline_batches where id = ?",
				(rs, row) -> new String[] { rs.getString("name_en"), rs.getString("name_bn") }, admission.getBatchId());
		List<ExamResult> exams = new ArrayList<>();
		if (afterOnboarding(admission)) {
			Instant cutoff = cutoff(admission);
			List<Object> args = new ArrayList<>(List.of(admission.getId(), admission.getBatchId()));
			if (cutoff != null) {
				args.add(Timestamp.from(cutoff));
			}
			exams.addAll(jdbc.query("""
					select distinct on (s.class_id) s.id as sheet_id, c.id as class_id, c.held_on, c.subject, c.topic, s.exam_type,
					  s.full_marks, s.pass_mark, s.published_at, r.absent, r.right_count, r.wrong_count, r.mcq, r.written, r.total,
					  r.rank, r.passed,
					  (select count(*) from offline_result_rows x where x.sheet_id = s.id and x.rank is not null) as ranked
					from offline_result_rows r
					join offline_result_sheets s on s.id = r.sheet_id
					join offline_classes c on c.id = s.class_id
					where r.admission_id = ? and s.batch_id = ? and
					""" + (cutoff == null ? OPEN_SHEETS : CLOSED_SHEETS) + " " + """
					order by s.class_id, s.published_at desc
					""", (rs, row) -> examOf(rs), args.toArray()));
			exams.sort(NEWEST_EXAM);
		}
		return new StudentResults(batch[0], batch[1], admission.getRollNo(), exams);
	}

	/** One exam's leaderboard: the rows of the sheet by rank then roll, absent last. 404 unless the student has a row in it. */
	@Transactional(readOnly = true)
	public Leaderboard leaderboard(UUID studentId, UUID admissionId, UUID classId) {
		OfflineAdmission admission = admissions.ownById(studentId, admissionId);
		if (!afterOnboarding(admission)) {
			throw notFound();
		}
		Instant cutoff = cutoff(admission);
		List<Object> args = new ArrayList<>(List.of(classId, admission.getBatchId()));
		if (cutoff != null) {
			args.add(Timestamp.from(cutoff));
		}
		List<Sheet> sheets = jdbc.query("""
				select s.id, c.subject, c.topic, c.held_on, s.exam_type, s.full_marks, s.pass_mark, s.published_at
				from offline_result_sheets s join offline_classes c on c.id = s.class_id
				where s.class_id = ? and s.batch_id = ? and
				""" + (cutoff == null ? OPEN_SHEETS : CLOSED_SHEETS) + " " + """
				order by s.published_at desc
				limit 1
				""", (rs, row) -> new Sheet(rs.getObject("id", UUID.class), rs.getString("subject"), rs.getString("topic"),
				rs.getObject("held_on", LocalDate.class), rs.getString("exam_type"), rs.getBigDecimal("full_marks"),
				rs.getBigDecimal("pass_mark"), instant(rs.getTimestamp("published_at"))), args.toArray());
		if (sheets.isEmpty()) {
			throw notFound();
		}
		Sheet sheet = sheets.get(0);
		Integer mine = jdbc.queryForObject("select count(*) from offline_result_rows where sheet_id = ? and admission_id = ?", Integer.class,
				sheet.id(), admissionId);
		if (mine == null || mine == 0) {
			throw notFound();
		}
		List<LeaderRow> rows = new ArrayList<>(jdbc.query("""
				select r.admission_id, r.roll_no, coalesce(sp.full_name, a.full_name) as name, r.absent, r.right_count, r.wrong_count,
				  r.mcq, r.written, r.total, r.rank, r.passed
				from offline_result_rows r
				left join offline_admissions a on a.id = r.admission_id
				left join student_profiles sp on sp.user_id = a.student_id
				where r.sheet_id = ?
				""", (rs, row) -> leaderRow(rs, admissionId), sheet.id()));
		rows.sort(LEADERBOARD);
		String myRoll = rows.stream().filter(LeaderRow::mine).map(LeaderRow::roll).findFirst().orElse(null);
		return new Leaderboard(classId, sheet.subject(), sheet.topic(), sheet.heldOn(), sheet.examType(), sheet.fullMarks(), sheet.passMark(),
				sheet.publishedAt(), myRoll, rows);
	}

	private static ExamResult examOf(ResultSet rs) throws SQLException {
		boolean absent = rs.getBoolean("absent");
		Integer right = rs.getObject("right_count", Integer.class);
		Integer wrong = rs.getObject("wrong_count", Integer.class);
		BigDecimal written = rs.getBigDecimal("written");
		boolean maybeAbsent = !absent && isZero(right) && isZero(wrong) && isZero(written);
		return new ExamResult(rs.getObject("class_id", UUID.class), rs.getObject("held_on", LocalDate.class), rs.getString("subject"),
				rs.getString("topic"), rs.getString("exam_type"), rs.getBigDecimal("full_marks"), rs.getBigDecimal("pass_mark"),
				instant(rs.getTimestamp("published_at")), absent, maybeAbsent, absent ? null : rs.getBigDecimal("total"),
				rs.getBigDecimal("mcq"), written, absent ? null : rs.getObject("rank", Integer.class), rs.getLong("ranked"),
				absent ? null : rs.getObject("passed", Boolean.class));
	}

	private static LeaderRow leaderRow(ResultSet rs, UUID myAdmission) throws SQLException {
		boolean absent = rs.getBoolean("absent");
		Integer right = rs.getObject("right_count", Integer.class);
		Integer wrong = rs.getObject("wrong_count", Integer.class);
		BigDecimal written = rs.getBigDecimal("written");
		boolean maybeAbsent = !absent && isZero(right) && isZero(wrong) && isZero(written);
		UUID admission = rs.getObject("admission_id", UUID.class);
		return new LeaderRow(rs.getString("roll_no"), rs.getString("name"), absent ? null : rs.getObject("rank", Integer.class),
				absent ? null : rs.getBigDecimal("total"), rs.getBigDecimal("mcq"), written,
				absent ? null : rs.getObject("passed", Boolean.class), absent, maybeAbsent, myAdmission.equals(admission));
	}

	/**
	 * The cut-off of a closed admission (rule R12): the sheets published before it was closed. Null for an open admission, which sees the
	 * published sheets.
	 */
	private static Instant cutoff(OfflineAdmission admission) {
		return OfflineAdmission.CLOSED.equals(admission.getStatus()) ? admission.getClosedAt() : null;
	}

	/** An admission that was onboarded, now or before it was closed. One that never was has no results. */
	private static boolean afterOnboarding(OfflineAdmission admission) {
		return (OfflineAdmission.ONBOARDED.equals(admission.getStatus()) || OfflineAdmission.CLOSED.equals(admission.getStatus()))
				&& admission.getOnboardedAt() != null && admission.getBatchId() != null;
	}

	private static Instant instant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	private static boolean isZero(Number value) {
		return value == null || (value instanceof BigDecimal decimal ? decimal.signum() == 0 : value.intValue() == 0);
	}

	/** Newest exam first; the same day, the later published first. */
	private static final Comparator<ExamResult> NEWEST_EXAM = Comparator.comparing(ExamResult::heldOn, Comparator.reverseOrder())
		.thenComparing(ExamResult::publishedAt, Comparator.nullsLast(Comparator.reverseOrder()));

	/** Absent last, then rank, then roll (numbers compare as numbers). */
	private static final Comparator<LeaderRow> LEADERBOARD = Comparator.comparing(LeaderRow::absent)
		.thenComparing(LeaderRow::rank, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(LeaderRow::roll, Comparator.nullsLast(OfflineResultService::compareRolls));

	/** The sheet of an exam, as the leaderboard needs it. */
	private record Sheet(UUID id, String subject, String topic, LocalDate heldOn, String examType, BigDecimal fullMarks, BigDecimal passMark,
			Instant publishedAt) {
	}

	/** One exam of the student's admission. {@code rankOf} counts the ranked students of the sheet (absent ones excluded). */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record ExamResult(UUID classId, LocalDate heldOn, String subject, String topic, String examType, BigDecimal fullMarks,
			BigDecimal passMark, Instant publishedAt, boolean absent, boolean maybeAbsent, BigDecimal total, BigDecimal mcq,
			BigDecimal written, Integer rank, long rankOf, Boolean passed) {
	}

	/** The student's results: the batch names, the roll, and the exams. */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record StudentResults(String batchNameEn, String batchNameBn, String roll, List<ExamResult> exams) {
	}

	/** One row of a leaderboard. Roll numbers and names are visible to every student of the batch (rule R6). */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record LeaderRow(String roll, String name, Integer rank, BigDecimal total, BigDecimal mcq, BigDecimal written, Boolean passed,
			boolean absent, boolean maybeAbsent, boolean mine) {
	}

	/** One exam's leaderboard. {@code myRoll} is the student's own roll in it. */
	@JsonInclude(JsonInclude.Include.ALWAYS)
	public record Leaderboard(UUID classId, String subject, String topic, LocalDate heldOn, String examType, BigDecimal fullMarks,
			BigDecimal passMark, Instant publishedAt, String myRoll, List<LeaderRow> rows) {
	}

}
