package com.coachingcenter.api.staffledger;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.staffnotifications.StaffNotificationKind;
import com.coachingcenter.api.staffnotifications.StaffNotificationWriter;

/**
 * Watches the instructor of each written exam (staff pay plan, phase 8, decision P18). Every two minutes it compares
 * {@code live_exams.instructor_id} with the instructor after the newest ASSIGNED or UNASSIGNED row of
 * {@code live_exam_assignment_log}, and writes the rows for any difference. It catches every path (a draft assignment, a
 * staged one taking effect on publish, an unassignment) without touching the assignment or publishing code.
 *
 * <p>
 * First run: when the log holds no assignment row at all, every written exam is recorded as it stands, with no notice, so
 * release does not flood anyone. After that, a difference notifies the instructors concerned: {@code EXAM_ASSIGNED} to the new
 * one and {@code EXAM_UNASSIGNED} to the previous one. A reassignment is two rows (UNASSIGNED, then ASSIGNED).
 *
 * <p>
 * It reads {@code live_exams} and {@code audit_log}, and writes only {@code live_exam_assignment_log} and staff notifications.
 * Each tick is one transaction, so a failure rolls back the whole tick, leaves no half-written baseline, and the next tick
 * tries again. The job never changes marking or assignment.
 *
 * <p>
 * Switch it off with {@code STAFF_PAY_JOBS_ENABLED=false} (property {@code app.staff-pay.jobs-enabled}), as the pay jobs.
 */
@Component
public class ExamAssignmentWatcher {

	static final String ASSIGNED = "ASSIGNED";

	static final String UNASSIGNED = "UNASSIGNED";

	/** Staff pay page of the instructor who is told. */
	static final String MY_PAY_LINK = "/staff/my-pay";

	private static final Logger log = LoggerFactory.getLogger(ExamAssignmentWatcher.class);

	private static final String WRITTEN_EXAMS = """
			select e.id, e.instructor_id, e.title_en, e.title_bn
			from live_exams e
			where e.exam_type = 'WRITTEN' and e.deleted_at is null
			order by e.id
			""";

	/** The newest assignment row per exam. A tie in time goes to the ASSIGNED row, which a reassignment writes last. */
	private static final String LATEST_ROWS = """
			select distinct on (exam_id) exam_id, instructor_id
			from live_exam_assignment_log
			where kind in ('ASSIGNED', 'UNASSIGNED')
			order by exam_id, created_at desc, case when kind = 'ASSIGNED' then 0 else 1 end
			""";

	private static final String ACTOR = """
			select actor_user_id from audit_log
			where entity_type = :entityType and entity_id = :examId and action in (:actions)
			order by created_at desc
			limit 1
			""";

	private static final String INSERT_ROW = """
			insert into live_exam_assignment_log (id, exam_id, kind, instructor_id, previous_instructor_id, changed_by, created_at)
			values (?, ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbc;

	private final NamedParameterJdbcTemplate named;

	private final StaffNotificationWriter notifications;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	@Value("${app.staff-pay.jobs-enabled:${STAFF_PAY_JOBS_ENABLED:true}}")
	private boolean enabled = true;

	public ExamAssignmentWatcher(JdbcTemplate jdbc, StaffNotificationWriter notifications,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.jdbc = jdbc;
		this.named = new NamedParameterJdbcTemplate(jdbc);
		this.notifications = notifications;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 2, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void run() {
		if (enabled) {
			watch();
		}
	}

	/** Returns how many log rows were written. Zero when another run is still going or when it failed. */
	int watch() {
		if (!running.compareAndSet(false, true)) {
			log.info("staff exam assignment watcher already running");
			return 0;
		}
		try {
			Integer written = transactions.execute(status -> pass());
			return written == null ? 0 : written;
		}
		catch (RuntimeException exception) {
			log.warn("staff exam assignment watcher failed error={}", exception.getClass().getSimpleName());
			return 0;
		}
		finally {
			running.set(false);
		}
	}

	private int pass() {
		Map<UUID, UUID> logged = new HashMap<>();
		jdbc.query(LATEST_ROWS, rs -> {
			logged.put(rs.getObject("exam_id", UUID.class), rs.getObject("instructor_id", UUID.class));
		});
		boolean firstRun = logged.isEmpty();
		List<Exam> exams = jdbc.query(WRITTEN_EXAMS, (rs, row) -> new Exam(rs.getObject("id", UUID.class),
				rs.getObject("instructor_id", UUID.class), rs.getString("title_en"), rs.getString("title_bn")));
		int written = 0;
		for (Exam exam : exams) {
			List<Change> changes = changesFor(logged.containsKey(exam.id()), logged.get(exam.id()), exam.instructorId(), firstRun);
			if (changes.isEmpty()) {
				continue;
			}
			UUID changedBy = changedBy(exam.id());
			for (Change change : changes) {
				written += record(exam, change, changedBy);
			}
		}
		if (written > 0) {
			log.info("staff exam assignment watcher wrote {} assignment row(s)", written);
		}
		return written;
	}

	/** Writes one row and, when the change calls for it, the notice. Returns 1. */
	private int record(Exam exam, Change change, UUID changedBy) {
		UUID id = UUID.randomUUID();
		jdbc.update(INSERT_ROW, id, exam.id(), change.kind(), change.instructorId(), change.previousInstructorId(), changedBy,
				Timestamp.from(clock.instant()));
		if (change.sendNotice()) {
			boolean assigned = ASSIGNED.equals(change.kind());
			StaffNotificationKind kind = assigned ? StaffNotificationKind.EXAM_ASSIGNED : StaffNotificationKind.EXAM_UNASSIGNED;
			UUID recipient = assigned ? change.instructorId() : change.previousInstructorId();
			notifications.notify(recipient, kind, Map.of("examTitleEn", StaffNotificationWriter.fitText(exam.titleEn()),
					"examTitleBn", StaffNotificationWriter.fitText(exam.titleBn())), MY_PAY_LINK, kind.name() + ":" + id);
		}
		return 1;
	}

	/** The actor of the newest instructor or publish entry for the exam, or null when none is known. */
	private UUID changedBy(UUID examId) {
		List<UUID> actors = named.query(ACTOR, new MapSqlParameterSource()
			.addValue("entityType", AuditRecorder.LIVE_EXAM_ENTITY)
			.addValue("examId", examId)
			.addValue("actions", List.of(AuditRecorder.LIVE_EXAM_INSTRUCTOR_ASSIGN, AuditRecorder.LIVE_EXAM_INSTRUCTOR_UNASSIGN,
					AuditRecorder.LIVE_EXAM_REVISION_PUBLISH)),
				(rs, row) -> rs.getObject("actor_user_id", UUID.class));
		return actors.isEmpty() ? null : actors.get(0);
	}

	/**
	 * The rows to write for one written exam. Pure: no database, so it is unit tested.
	 *
	 * @param logged whether the log holds an ASSIGNED or UNASSIGNED row for the exam
	 * @param loggedInstructor the instructor after the newest such row (null when that row is UNASSIGNED)
	 * @param current the exam's live instructor (null when none)
	 * @param firstRun whether the log holds no assignment row for any exam yet
	 * @return the rows to write, in order; empty when nothing changed
	 */
	static List<Change> changesFor(boolean logged, UUID loggedInstructor, UUID current, boolean firstRun) {
		List<Change> changes = new ArrayList<>();
		if (!logged) {
			if (firstRun) {
				// Baseline: record the exam as it stands, with no notice.
				changes.add(current == null ? new Change(UNASSIGNED, null, null, false) : new Change(ASSIGNED, current, null, false));
			}
			else if (current != null) {
				// An exam the watcher has not seen before, assigned after release: a real change.
				changes.add(new Change(ASSIGNED, current, null, true));
			}
			return changes;
		}
		if (Objects.equals(loggedInstructor, current)) {
			return changes;
		}
		if (loggedInstructor != null) {
			changes.add(new Change(UNASSIGNED, null, loggedInstructor, true));
		}
		if (current != null) {
			changes.add(new Change(ASSIGNED, current, null, true));
		}
		return changes;
	}

	/** A written exam as the watcher reads it. */
	record Exam(UUID id, UUID instructorId, String titleEn, String titleBn) {
	}

	/**
	 * One row to write. {@code instructorId} is the instructor after the row (null on UNASSIGNED); {@code previousInstructorId}
	 * is the instructor before it. A notice goes to the new instructor for ASSIGNED and to the previous one for UNASSIGNED.
	 */
	record Change(String kind, UUID instructorId, UUID previousInstructorId, boolean sendNotice) {
	}

}
