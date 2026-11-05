package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.common.audit.AuditRecorder;

/**
 * The assignment watcher (staff pay plan, phase 8): the baseline on the first run with no notice, a later assignment,
 * unassignment and reassignment writing rows and notices once each, the actor taken from the audit log, and a run with
 * nothing new writing nothing. The watcher is driven by {@code watch} directly; the scheduled tick is off in tests.
 */
@SpringBootTest
@Import(CapturingEmailConfig.class)
class StaffAssignmentWatcherIT extends PostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ExamAssignmentWatcher watcher;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
		// The first run is the one that records the baseline, so every test starts with an empty log.
		jdbc.update("delete from live_exam_assignment_log");
	}

	@Test
	void theFirstRunRecordsTheMarkersAsTheyStandWithoutNotices() {
		UUID marker = seed("INSTRUCTOR");
		UUID exam = writtenExam("Baseline exam " + UUID.randomUUID());
		jdbc.update("update live_exams set instructor_id = ? where id = ?", marker, exam);
		UUID unassigned = writtenExam("Baseline unassigned " + UUID.randomUUID());

		watcher.watch();

		List<Map<String, Object>> rows = log(exam);
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0)).containsEntry("kind", "ASSIGNED").containsEntry("instructor_id", marker)
			.containsEntry("previous_instructor_id", null);
		assertThat(log(unassigned)).hasSize(1);
		assertThat(log(unassigned).get(0)).containsEntry("kind", "UNASSIGNED").containsEntry("instructor_id", null);
		assertThat(notices(marker, "EXAM_ASSIGNED")).isZero();

		// A second run with nothing changed writes nothing.
		watcher.watch();
		assertThat(log(exam)).hasSize(1);
		assertThat(log(unassigned)).hasSize(1);
	}

	@Test
	void aLaterChangeWritesItsRowsAndNoticesOnceWithTheActor() {
		UUID master = seed("MASTER_ADMIN");
		UUID first = seed("INSTRUCTOR");
		UUID second = seed("INSTRUCTOR");
		String title = "Muslim Law " + UUID.randomUUID();
		UUID exam = writtenExam(title);
		watcher.watch();

		// Assigned by the Master Admin through the existing route, which writes the audit entry.
		jdbc.update("update live_exams set instructor_id = ? where id = ?", first, exam);
		audit(master, AuditRecorder.LIVE_EXAM_INSTRUCTOR_ASSIGN, exam);
		watcher.watch();

		assertThat(log(exam)).hasSize(2);
		Map<String, Object> assigned = log(exam).get(1);
		assertThat(assigned).containsEntry("kind", "ASSIGNED").containsEntry("instructor_id", first)
			.containsEntry("changed_by", master);
		assertThat(notices(first, "EXAM_ASSIGNED", title)).isEqualTo(1);

		// Reassigned straight to the second instructor, as a publish of staged changes can do: one unassignment, then one assignment.
		jdbc.update("update live_exams set instructor_id = ? where id = ?", second, exam);
		audit(master, AuditRecorder.LIVE_EXAM_REVISION_PUBLISH, exam);
		watcher.watch();

		// Four rows: the baseline (unassigned), the first assignment, the removal of the first, the assignment of the second.
		assertThat(log(exam)).hasSize(4);
		Map<String, Object> removed = removal(exam, first);
		assertThat(removed).containsEntry("instructor_id", null).containsEntry("changed_by", master);
		Map<String, Object> added = log(exam, "ASSIGNED", second);
		assertThat(added).containsEntry("previous_instructor_id", null).containsEntry("changed_by", master);
		assertThat(notices(first, "EXAM_UNASSIGNED", title)).isEqualTo(1);
		assertThat(notices(second, "EXAM_ASSIGNED", title)).isEqualTo(1);
		assertThat(notices(first, "EXAM_ASSIGNED", title)).isEqualTo(1);

		// Nothing new: no rows and no further notices.
		watcher.watch();
		assertThat(log(exam)).hasSize(4);
		assertThat(notices(second, "EXAM_ASSIGNED", title)).isEqualTo(1);
	}

	@Test
	void anUnassignmentNotifiesThePreviousInstructor() {
		UUID master = seed("MASTER_ADMIN");
		UUID marker = seed("INSTRUCTOR");
		String title = "Unassigned exam " + UUID.randomUUID();
		UUID exam = writtenExam(title);
		watcher.watch();
		jdbc.update("update live_exams set instructor_id = ? where id = ?", marker, exam);
		watcher.watch();

		jdbc.update("update live_exams set instructor_id = null where id = ?", exam);
		audit(master, AuditRecorder.LIVE_EXAM_INSTRUCTOR_UNASSIGN, exam);
		watcher.watch();

		Map<String, Object> removed = removal(exam, marker);
		assertThat(removed).containsEntry("instructor_id", null).containsEntry("changed_by", master);
		assertThat(notices(marker, "EXAM_UNASSIGNED", title)).isEqualTo(1);
		assertThat(notices(marker, "EXAM_ASSIGNED", title)).isEqualTo(1);
	}

	private List<Map<String, Object>> log(UUID exam) {
		return jdbc.queryForList("""
				select kind, instructor_id, previous_instructor_id, changed_by from live_exam_assignment_log
				where exam_id = ? order by created_at, kind
				""", exam);
	}

	private Map<String, Object> log(UUID exam, String kind, UUID instructor) {
		return jdbc.queryForMap("""
				select kind, instructor_id, previous_instructor_id, changed_by from live_exam_assignment_log
				where exam_id = ? and kind = ? and instructor_id = ?
				""", exam, kind, instructor);
	}

	/** The UNASSIGNED row that removed {@code previous}. */
	private Map<String, Object> removal(UUID exam, UUID previous) {
		return jdbc.queryForMap("""
				select kind, instructor_id, previous_instructor_id, changed_by from live_exam_assignment_log
				where exam_id = ? and kind = 'UNASSIGNED' and previous_instructor_id = ?
				""", exam, previous);
	}

	private long notices(UUID recipient, String kind) {
		Long value = jdbc.queryForObject("select count(*) from staff_notifications where recipient_id = ? and kind = ?", Long.class,
				recipient, kind);
		return value == null ? 0 : value;
	}

	private long notices(UUID recipient, String kind, String title) {
		Long value = jdbc.queryForObject("""
				select count(*) from staff_notifications
				where recipient_id = ? and kind = ? and payload->>'examTitleEn' = ?
				""", Long.class, recipient, kind, title);
		return value == null ? 0 : value;
	}

	private void audit(UUID actor, String action, UUID exam) {
		jdbc.update("""
				insert into audit_log (id, actor_user_id, action, entity_type, entity_id, metadata, ip, created_at)
				values (?, ?, ?, ?, ?, null, null, now())
				""", UUID.randomUUID(), actor, action, AuditRecorder.LIVE_EXAM_ENTITY, exam);
	}

	private UUID writtenExam(String title) {
		UUID category = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				values (?, 'Marking', 'Marking', 0, now(), now())
				""", category);
		UUID exam = UUID.randomUUID();
		jdbc.update("""
				insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,
				  description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,
				  finally_saved, answers_changeable, created_at, updated_at)
				values (?, null, ?, 'WRITTEN', ?, 'Written', 'd', 'd', 'r', 'r', 'BN', now(), now() + make_interval(mins => 40),
				  40, 'LIVE', 'PUBLISHED', true, true, now(), now())
				""", exam, category, title);
		return exam;
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "watch-" + id + "@example.com", passwords.encode("password1"), role);
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Watch Test')", id);
		return id;
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
