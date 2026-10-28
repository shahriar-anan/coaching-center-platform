package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.Role;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * How long a student's access lasts (NR-01, A2', R4 and B7), against real PostgreSQL. The test rolls back, so the shared
 * database keeps nothing it wrote.
 */
@SpringBootTest
@Transactional
@Import(AccessExpiryIT.ClockConfig.class)
class AccessExpiryIT extends PostgresIntegrationTest {

	private static final Instant WINDOW_START = Instant.parse("2026-10-29T04:00:00Z");

	private static final Instant WINDOW_END = WINDOW_START.plusSeconds(3600);

	private static final Instant EARLY = WINDOW_START.minus(Duration.ofDays(2));

	private static final Instant FAR_FUTURE = WINDOW_START.plus(Duration.ofDays(3650));

	@Autowired
	private LiveExamAccess access;

	@Autowired
	private LiveExamRepository exams;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private StudentOwnAccessService ownAccess;

	@Autowired
	private StudentLiveExamAccessService staffAccess;

	@Autowired
	private LiveExamGrantAdminService grants;

	@Autowired
	private MutableClock clock;

	@Test
	void theAccessListsShowWhenAccessEndsAndHideFreeExams() {
		clock.set(WINDOW_START);
		UUID category = category();
		LiveExam standalone = exam(category, "LIVE", WINDOW_START, WINDOW_END, 30, false);
		LiveExam free = exam(category, "LIVE", WINDOW_START, WINDOW_END, 30, true);
		UUID module = module(category);
		LiveExam packaged = exam(category, "LIVE", WINDOW_START, WINDOW_END, 10, false);
		member(module, packaged, EARLY, null);
		UUID student = student();
		manualGrant(student, "EXAM", null, standalone, EARLY);
		manualGrant(student, "EXAM", null, free, EARLY);
		manualGrant(student, "MODULE", module, null, EARLY);

		StudentOwnAccessService.MyAccess mine = ownAccess.mine(student);
		assertThat(mine.exams()).as("a free exam is not a grant, so it is not listed").hasSize(1);
		StudentOwnAccessService.MyExam examRow = mine.exams().get(0);
		assertThat(examRow.examId()).isEqualTo(standalone.getId());
		assertThat(examRow.moduleId()).as("a standalone exam is in no package").isNull();
		assertThat(examRow.endsAt()).isEqualTo(WINDOW_END.plus(Duration.ofDays(30)));
		assertThat(examRow.expired()).isFalse();
		StudentOwnAccessService.MyModule moduleRow = mine.modules().get(0);
		assertThat(moduleRow.endsAt()).as("the package ends with its paid exam").isEqualTo(WINDOW_END.plus(Duration.ofDays(10)));

		clock.set(WINDOW_END.plus(Duration.ofDays(11)));
		mine = ownAccess.mine(student);
		assertThat(mine.modules().get(0).expired()).as("expired rows stay listed").isTrue();
		assertThat(mine.exams().get(0).expired()).isFalse();

		var staff = staffAccess.list(student, PageRequest.of(0, 20)).getContent();
		assertThat(staff).as("staff see the same rows, without the free exam").hasSize(2);
		StudentLiveExamAccessService.AccessRow staffExam = staff.stream()
			.filter(row -> !row.exams().isEmpty())
			.findFirst()
			.orElseThrow();
		assertThat(staffExam.endsAt()).isEqualTo(WINDOW_END.plus(Duration.ofDays(30)));
		assertThat(staffExam.expired()).isFalse();
		assertThat(staff.stream().filter(row -> row.exams().isEmpty()).findFirst().orElseThrow().expired()).isTrue();
	}

	@Test
	void aManualGrantReplacesALapsedOneAndIsRefusedWhileTheOneBeforeIsLive() {
		clock.set(WINDOW_START);
		LiveExam exam = exam(category(), "LIVE", WINDOW_START, WINDOW_END, 30, false);
		UUID student = student();
		UUID master = UUID.randomUUID();
		jdbc.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())", master,
				"+8801" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L)),
				master + "@example.com");
		CurrentActor actor = new CurrentActor(master, Role.MASTER_ADMIN);
		manualGrant(student, "EXAM", null, exam, EARLY);

		assertThatThrownBy(() -> grants.grant(actor,
				new LiveExamGrantAdminService.GrantRequest(student, "EXAM", null, exam.getId(), "Early retake")))
			.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.code()).isEqualTo(ErrorCode.GRANT_ALREADY_ACTIVE));

		clock.set(WINDOW_END.plus(Duration.ofDays(31)));
		grants.grant(actor, new LiveExamGrantAdminService.GrantRequest(student, "EXAM", null, exam.getId(), "Second chance"));

		assertThat(jdbc.queryForObject("select count(*) from live_exam_grants where student_id = ? and revoked_at is null",
				Integer.class, student)).as("one live grant").isEqualTo(1);
		assertThat(jdbc.queryForObject("select revoke_reason from live_exam_grants where student_id = ? and revoked_at is not null",
				String.class, student)).isEqualTo(LiveExamAccess.RETIRED);
		assertThat(access.canSit(student, exam, WINDOW_END.plus(Duration.ofDays(31)))).isTrue();
	}

	@Test
	void aLifetimeGrantNeverEnds() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", WINDOW_START, WINDOW_END, null, false);
		UUID student = student();
		grantExam(student, exam, EARLY);

		AccessState state = access.state(student, exam, FAR_FUTURE);
		assertThat(state.granted()).isTrue();
		assertThat(state.endsAt()).isNull();
		assertThat(state.expired()).isFalse();
		assertThat(access.canSit(student, exam, FAR_FUTURE)).isTrue();
	}

	@Test
	void thirtyDaysBoughtBeforeTheWindowRunsFromTheWindowEnd() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", WINDOW_START, WINDOW_END, 30, false);
		UUID student = student();
		grantExam(student, exam, EARLY);

		Instant ends = WINDOW_END.plus(Duration.ofDays(30));
		assertThat(access.state(student, exam, WINDOW_START).endsAt()).isEqualTo(ends);
		assertThat(access.canSit(student, exam, ends.minusSeconds(1))).isTrue();
		assertThat(access.canSit(student, exam, ends)).as("the end is exclusive").isFalse();
		assertThat(access.state(student, exam, ends).expired()).isTrue();
		assertThat(access.hasEver(student, exam)).isTrue();
	}

	@Test
	void aPurchaseAfterTheWindowRunsFromThePurchase() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", WINDOW_START, WINDOW_END, 30, false);
		UUID student = student();
		Instant bought = WINDOW_END.plus(Duration.ofDays(5));
		grantExam(student, exam, bought);

		assertThat(access.state(student, exam, bought).endsAt()).isEqualTo(bought.plus(Duration.ofDays(30)));
	}

	@Test
	void anUndatedExamHasNotStartedCountingAndNeverExpires() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", null, null, 30, false);
		UUID student = student();
		grantExam(student, exam, EARLY);

		AccessState state = access.state(student, exam, FAR_FUTURE);
		assertThat(state.granted()).isTrue();
		assertThat(state.endsAt()).isNull();
		assertThat(state.expired()).isFalse();
	}

	@Test
	void aPracticeExamRunsFromItsPublishTime() {
		UUID category = category();
		Instant published = WINDOW_START;
		LiveExam exam = exam(category, "PRACTICE", null, null, 30, false, published);
		UUID student = student();
		grantExam(student, exam, EARLY);

		assertThat(access.state(student, exam, published).endsAt()).isEqualTo(published.plus(Duration.ofDays(30)));
	}

	@Test
	void theLongerOfTwoRoutesWins() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", WINDOW_START, WINDOW_END, 10, false);
		UUID module = module(category);
		member(module, exam, EARLY, null);
		UUID student = student();
		grantExam(student, exam, EARLY);
		// Bought a day after the window ended: its 10 days run from the purchase, so it outlasts the exam grant.
		Instant packageBought = WINDOW_END.plus(Duration.ofDays(2));
		grantModule(student, module, packageBought);

		AccessState state = access.state(student, exam, WINDOW_START);
		assertThat(state.source()).isEqualTo(LiveExamAccess.MODULE);
		assertThat(state.endsAt()).isEqualTo(packageBought.plus(Duration.ofDays(10)));
	}

	@Test
	void aFreeExamIsOpenToAnyStudentWithNoGrant() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", WINDOW_START, WINDOW_END, 30, true);
		UUID student = student();

		AccessState state = access.state(student, exam, WINDOW_START);
		assertThat(state.granted()).isTrue();
		assertThat(state.source()).isEqualTo(LiveExamAccess.FREE);
		assertThat(state.endsAt()).isNull();
		assertThat(access.canSit(student, exam, FAR_FUTURE)).isTrue();
		assertThat(access.hasEver(student, exam)).as("free is not a grant").isFalse();
	}

	@Test
	void aPackageRemovedAfterPurchaseStillCoversTheStudentWhoHeldIt() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", WINDOW_START, WINDOW_END, null, false);
		UUID module = module(category);
		Instant removed = WINDOW_START.plus(Duration.ofDays(1));
		member(module, exam, EARLY, removed);
		UUID before = student();
		grantModule(before, module, EARLY);
		UUID after = student();
		grantModule(after, module, removed.plusSeconds(60));

		assertThat(access.canSit(before, exam, WINDOW_START)).as("bought before the removal").isTrue();
		assertThat(access.canSit(after, exam, WINDOW_START)).as("bought after the removal").isFalse();
	}

	@Test
	void aRevokedGrantCountsForReadingOnlyThroughAnAttempt() {
		UUID category = category();
		LiveExam exam = exam(category, "LIVE", WINDOW_START, WINDOW_END, null, false);
		UUID student = student();
		UUID grant = grantExam(student, exam, EARLY);
		jdbc.update("update live_exam_grants set revoked_at = ? where id = ?", Timestamp.from(EARLY.plusSeconds(60)), grant);

		assertThat(access.canSit(student, exam, WINDOW_START)).isFalse();
		assertThat(access.hasEver(student, exam)).as("a revoked grant alone").isFalse();

		jdbc.update("insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at,"
				+ " submitted_at, status, attempt_no, created_at, updated_at)"
				+ " values (?, ?, ?, 'LIVE', ?, ?, ?, 'SUBMITTED', 1, now(), now())", UUID.randomUUID(), exam.getId(), student,
				Timestamp.from(WINDOW_START), Timestamp.from(WINDOW_END), Timestamp.from(WINDOW_END));
		assertThat(access.hasEver(student, exam)).as("with an attempt").isTrue();
	}

	@Test
	void aModuleEndsWhenItsPaidExamsHaveAllRunOut() {
		UUID category = category();
		LiveExam thirty = exam(category, "LIVE", WINDOW_START, WINDOW_END, 30, false);
		LiveExam tenDays = exam(category, "LIVE", WINDOW_START, WINDOW_END, 10, false);
		LiveExam free = exam(category, "LIVE", WINDOW_START, WINDOW_END, 1, true);
		UUID module = module(category);
		member(module, thirty, EARLY, null);
		member(module, tenDays, EARLY, null);
		member(module, free, EARLY, null);
		UUID student = student();
		grantModule(student, module, EARLY);

		Instant ends = WINDOW_END.plus(Duration.ofDays(30));
		AccessState state = access.moduleState(student, module, WINDOW_START);
		assertThat(state.granted()).isTrue();
		assertThat(state.endsAt()).as("the latest paid exam ends last, and the free one is ignored").isEqualTo(ends);
		assertThat(access.canSit(student, thirty, WINDOW_START)).isTrue();
		assertThat(access.canSit(student, free, WINDOW_START)).isTrue();
		assertThat(access.moduleState(student, module, ends).expired()).isTrue();
	}

	@Test
	void aPackageWithNoPaidExamNeverEnds() {
		UUID category = category();
		LiveExam free = exam(category, "LIVE", WINDOW_START, WINDOW_END, 30, true);
		UUID module = module(category);
		member(module, free, EARLY, null);
		UUID student = student();
		UUID roster = student();
		jdbc.update("insert into live_exam_roster (module_id, student_id, created_at) values (?, ?, ?)", module, roster,
				Timestamp.from(EARLY));

		assertThat(access.moduleState(student, module, FAR_FUTURE).granted()).isFalse();
		AccessState state = access.moduleState(roster, module, FAR_FUTURE);
		assertThat(state.source()).isEqualTo(LiveExamAccess.ROSTER);
		assertThat(state.endsAt()).isNull();
		assertThat(state.expired()).isFalse();
	}

	private UUID category() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", id);
		return id;
	}

	private UUID module(UUID category) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,"
				+ " access_mode, status, finally_saved, position, created_at, updated_at)"
				+ " values (?, ?, 'Package', 'প্যাকেজ', '', '', 'MANUAL', 'PUBLISHED', true, 0, now(), now())", id, category);
		return id;
	}

	private LiveExam exam(UUID category, String mode, Instant liveAt, Instant endsAt, Integer accessDays, boolean free) {
		return exam(category, mode, liveAt, endsAt, accessDays, free, WINDOW_START);
	}

	private LiveExam exam(UUID category, String mode, Instant liveAt, Instant endsAt, Integer accessDays, boolean free,
			Instant published) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, pass_percent, answers_changeable, is_free, access_days, published_at, created_at,"
				+ " updated_at) values (?, null, ?, 'MCQ', 'Exam', 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, ?, 'PUBLISHED',"
				+ " true, 50, true, ?, ?, ?, now(), now())", id, category, ts(liveAt), ts(endsAt), mode, free, accessDays,
				ts(published));
		return exams.findByIdAndDeletedAtIsNull(id).orElseThrow();
	}

	private void member(UUID module, LiveExam exam, Instant addedAt, Instant removedAt) {
		jdbc.update("insert into live_exam_package_exams (id, module_id, exam_id, position, added_at, removed_at)"
				+ " values (?, ?, ?, 0, ?, ?)", UUID.randomUUID(), module, exam.getId(), ts(addedAt), ts(removedAt));
	}

	private UUID grantExam(UUID student, LiveExam exam, Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source)"
				+ " values (?, ?, 'EXAM', null, ?, null, ?, 'ORDER')", id, student, exam.getId(), ts(createdAt));
		return id;
	}

	private void grantModule(UUID student, UUID module, Instant createdAt) {
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source)"
				+ " values (?, ?, 'MODULE', ?, null, null, ?, 'ORDER')", UUID.randomUUID(), student, module, ts(createdAt));
	}

	/** A manual grant, which the staff list shows without an order. {@code module} is set for a package, {@code exam} for an exam. */
	private void manualGrant(UUID student, String scope, UUID module, LiveExam exam, Instant createdAt) {
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source)"
				+ " values (?, ?, ?, ?, ?, null, ?, 'MANUAL')", UUID.randomUUID(), student, scope, module,
				exam == null ? null : exam.getId(), ts(createdAt));
	}

	private UUID student() {
		UUID id = UUID.randomUUID();
		long digits = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L);
		jdbc.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, 'STUDENT', 'ACTIVE', now(), now())", id, "+8801" + String.format("%09d", digits),
				id + "@example.com");
		return id;
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(WINDOW_START);
		}

	}

}
