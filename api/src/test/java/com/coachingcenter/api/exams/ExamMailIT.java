package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest(properties = "app.exam-mail.hour-before.enabled=true")
@Import({ ExamMailIT.ClockConfig.class, ExamMailIT.MailConfig.class })
class ExamMailIT extends PostgresIntegrationTest {

	private static final Instant START = Instant.parse("2026-10-20T04:00:00Z");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MutableClock clock;

	@Autowired
	private CapturingExamMailSender sender;

	@Autowired
	private ExamMailService examMail;

	@Autowired
	private ExamMailDelivery delivery;

	@Autowired
	private LiveExamService exams;

	@Autowired
	private LiveExamModuleService modules;

	@Autowired
	private LiveExamRosterService roster;

	@Autowired
	private LiveExamOrderTx orders;

	@BeforeEach
	void reset() {
		jdbc.update("update live_exams set status = 'DRAFT'");
		jdbc.update("delete from exam_outbound_mail");
		sender.clear();
		clock.set(START);
	}

	@Test
	void aDatedExamMailsActiveStudentsWhenTheModuleIsPublished() {
		Instant liveAt = START.plus(Duration.ofHours(2));
		Fixture fixture = seed("DRAFT", liveAt, "CPC Written", "সিপিসি");
		Student active = student("ACTIVE");
		Student inactive = student("INACTIVE");
		Student stranger = student("ACTIVE");
		roster.mark(fixture.moduleId, active.id);
		roster.mark(fixture.moduleId, inactive.id);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isZero();

		exams.publish(fixture.examId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isZero();

		modules.publish(fixture.moduleId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(1);
		assertThat(count(inactive.id, ExamOutboundMail.EXAM_TIME)).isZero();
		assertThat(count(stranger.id, ExamOutboundMail.EXAM_TIME)).isZero();
		assertThat(mailStatus(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(ExamOutboundMail.SENT);
		String text = sender.sentTo(active.email).get(0).text();
		assertThat(text).contains("সিপিসি");
		assertThat(text).contains("CPC Written");
		assertThat(text).contains("2026-10-20 12:00");
		assertThat(text).doesNotContain(active.phone);
		assertThat(text).doesNotContain(stranger.email);

		examMail.onExamPublished(fixture.examId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(1);
	}

	@Test
	void aNullLiveAtOrADraftSendsNothing() {
		Fixture dated = seed("PUBLISHED", null, "Undated", "তারিখ নেই");
		Student active = student("ACTIVE");
		roster.mark(dated.moduleId, active.id);
		exams.publish(dated.examId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isZero();

		examMail.onExamPublished(dated.examId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isZero();
	}

	@Test
	void republishWithANewInstantSendsOnceForThatInstant() {
		Instant first = START.plus(Duration.ofHours(3));
		Fixture fixture = seed("PUBLISHED", first, "Second sitting", "দ্বিতীয়");
		Student active = student("ACTIVE");
		roster.mark(fixture.moduleId, active.id);
		exams.publish(fixture.examId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(1);

		Instant second = first.plus(Duration.ofDays(1));
		jdbc.update("update live_exams set live_at = ? where id = ?", Timestamp.from(second), fixture.examId);
		examMail.onExamPublished(fixture.examId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(2);
		examMail.onExamPublished(fixture.examId);
		assertThat(count(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(2);
	}

	@Test
	void theHourJobUsesBothBandsAndSkipsTheEdges() {
		Instant liveAt = START.plus(Duration.ofHours(3));
		Fixture fixture = seed("PUBLISHED", liveAt, "Timed", "সময়");
		Student active = student("ACTIVE");
		roster.mark(fixture.moduleId, active.id);
		exams.publish(fixture.examId);
		assertThat(count(active.id, ExamOutboundMail.HOUR_BEFORE)).isZero();

		clock.set(liveAt.minus(Duration.ofMinutes(80)));
		examMail.scan();
		clock.set(liveAt.minus(Duration.ofMinutes(70)));
		examMail.scan();
		clock.set(liveAt.minus(Duration.ofMinutes(10)));
		examMail.scan();
		clock.set(liveAt.minus(Duration.ofMinutes(15)));
		examMail.scan();
		assertThat(count(active.id, ExamOutboundMail.HOUR_BEFORE)).isZero();

		clock.set(liveAt.minus(Duration.ofMinutes(40)));
		examMail.scan();
		assertThat(count(active.id, ExamOutboundMail.HOUR_BEFORE)).isEqualTo(1);
		assertThat(mailStatus(active.id, ExamOutboundMail.HOUR_BEFORE)).isEqualTo(ExamOutboundMail.SENT);
		String text = sender.sentTo(active.email).stream()
			.filter(message -> message.subject().contains("Exam starting soon"))
			.findFirst()
			.orElseThrow()
			.text();
		assertThat(text).contains("about one hour");
		assertThat(text).doesNotContain("score");
		examMail.scan();
		assertThat(count(active.id, ExamOutboundMail.HOUR_BEFORE)).isEqualTo(1);
	}

	@Test
	void publishInsideTheHourSendsBothAndInsideFifteenMinutesSendsOnlyTheTime() {
		Student early = student("ACTIVE");
		Instant soon = clock.instant().plus(Duration.ofMinutes(40));
		Fixture earlyFixture = seed("PUBLISHED", soon, "Soon", "শীঘ্রই");
		roster.mark(earlyFixture.moduleId, early.id);
		exams.publish(earlyFixture.examId);
		assertThat(count(early.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(1);
		assertThat(count(early.id, ExamOutboundMail.HOUR_BEFORE)).isEqualTo(1);

		Student late = student("ACTIVE");
		Instant almost = clock.instant().plus(Duration.ofMinutes(10));
		Fixture lateFixture = seed("PUBLISHED", almost, "Almost", "প্রায়");
		roster.mark(lateFixture.moduleId, late.id);
		exams.publish(lateFixture.examId);
		assertThat(count(late.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(1);
		assertThat(count(late.id, ExamOutboundMail.HOUR_BEFORE)).isZero();
		examMail.scan();
		assertThat(count(late.id, ExamOutboundMail.HOUR_BEFORE)).isZero();
	}

	@Test
	void lostAccessIsSkippedAndAStudentWithoutAccessIsAbsent() {
		Instant liveAt = START.plus(Duration.ofHours(2));
		Fixture fixture = seed("PUBLISHED", liveAt, "Closed", "বন্ধ");
		Student active = student("ACTIVE");
		roster.mark(fixture.moduleId, active.id);
		exams.publish(fixture.examId);
		jdbc.update("delete from live_exam_roster where module_id = ? and student_id = ?", fixture.moduleId, active.id);
		jdbc.update("delete from live_exam_grants where student_id = ?", active.id);
		jdbc.update(
				"update exam_outbound_mail set status = 'PENDING', attempt_count = 0 where user_id = ? and kind = 'EXAM_TIME'",
				active.id);
		UUID mailId = jdbc.queryForObject(
				"select id from exam_outbound_mail where user_id = ? and kind = 'EXAM_TIME'", UUID.class, active.id);
		sender.clear();
		delivery.deliverOne(mailId);
		assertThat(mailStatus(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(ExamOutboundMail.SKIPPED);
		assertThat(sender.sentTo(active.email)).isEmpty();
	}

	@Test
	void aQueuedMailForADeletedExamIsSkipped() {
		Instant liveAt = START.plus(Duration.ofHours(2));
		Fixture fixture = seed("PUBLISHED", liveAt, "Removed", "মুছে ফেলা");
		Student active = student("ACTIVE");
		roster.mark(fixture.moduleId, active.id);
		exams.publish(fixture.examId);
		jdbc.update("update exam_outbound_mail set status = 'PENDING', attempt_count = 0 where user_id = ? and kind = 'EXAM_TIME'",
				active.id);
		UUID mailId = jdbc.queryForObject(
				"select id from exam_outbound_mail where user_id = ? and kind = 'EXAM_TIME'", UUID.class, active.id);
		jdbc.update("update live_exams set deleted_at = now() where id = ?", fixture.examId);
		sender.clear();
		delivery.deliverOne(mailId);
		assertThat(mailStatus(active.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(ExamOutboundMail.SKIPPED);
		assertThat(sender.sentTo(active.email)).isEmpty();
	}

	@Test
	void aSucceededOrderSendsOneReceiptAndASecondValidationDoesNot() {
		Instant liveAt = START.plus(Duration.ofHours(2));
		Fixture fixture = seed("PUBLISHED", liveAt, "Paid paper", "পেইড");
		Student buyer = student("ACTIVE");
		exams.publish(fixture.examId);
		BigDecimal amount = new BigDecimal("250.00");
		LiveExamOrder order = orders.insert(buyer.id, LiveExamOrder.EXAM, fixture.moduleId, fixture.examId, amount);
		orders.verify(order.getTranId(), LiveExamPaymentGateway.Verification.valid(order.getTranId(), amount, "BDT",
				"VAL-SECRET-99", "BKASH-BKash"));
		orders.verify(order.getTranId(),
				LiveExamPaymentGateway.Verification.valid(order.getTranId(), amount, "BDT", "VAL-SECRET-99"));

		assertThat(noticeCount(order.getId())).isEqualTo(1);
		UUID adminId = noticeRecipient(order.getId());
		assertThat(jdbc.queryForObject("select role || '/' || status from users where id = ?", String.class, adminId))
			.isEqualTo("MASTER_ADMIN/ACTIVE");
		String adminEmail = jdbc.queryForObject("select email from users where id = ?", String.class, adminId);
		String notice = sender.sentTo(adminEmail).stream()
			.filter(message -> message.subject().contains("New purchase"))
			.findFirst()
			.orElseThrow()
			.text();
		assertThat(notice).contains(buyer.phone, buyer.email, "Paid paper", "পেইড", "250.00 BDT", "BKASH-BKash",
				order.getTranId(), "2026-10-20 10:00");
		assertThat(notice).doesNotContain("VAL-SECRET-99");
		assertThat(mailStatus(adminId, ExamOutboundMail.PURCHASE_NOTICE)).isEqualTo(ExamOutboundMail.SENT);

		assertThat(count(buyer.id, ExamOutboundMail.PAYMENT_RECEIPT)).isEqualTo(1);
		assertThat(count(buyer.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(1);
		assertThat(orderStatus(order.getId())).isEqualTo(LiveExamOrder.SUCCEEDED);
		String text = sender.sentTo(buyer.email).stream()
			.filter(message -> message.subject().contains("Payment receipt"))
			.findFirst()
			.orElseThrow()
			.text();
		assertThat(text).contains("পেইড");
		assertThat(text).contains("Paid paper");
		assertThat(text).contains("250.00");
		assertThat(text).contains("BDT");
		assertThat(text).contains(order.getTranId());
		assertThat(text).doesNotContain("VAL-SECRET-99");
		assertThat(text).doesNotContain(buyer.phone);

		roster.mark(fixture.moduleId, buyer.id);
		assertThat(count(buyer.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(1);
	}

	@Test
	void aModulePurchaseMailsTheReceiptAndEachDatedExamOnce() {
		Instant first = START.plus(Duration.ofHours(4));
		Fixture fixture = seed("PUBLISHED", first, "First paper", "প্রথম");
		jdbc.update("""
				insert into live_exams (
				    id, module_id, exam_type, title_en, title_bn, description_en, description_bn, rules_en, rules_bn,
				    paper_language, live_at, duration_minutes, pass_percent, status, finally_saved, created_at, updated_at)
				values (?, ?, 'MCQ', 'Second paper', 'দ্বিতীয়', 'd', 'd', 'r', 'r', 'BN', ?, 40, 40, 'PUBLISHED', true, now(), now())
				""", UUID.randomUUID(), fixture.moduleId, Timestamp.from(first.plus(Duration.ofHours(3))));
		exams.publish(fixture.examId);
		Student buyer = student("ACTIVE");
		BigDecimal amount = new BigDecimal("2500.00");
		LiveExamOrder order = orders.insert(buyer.id, LiveExamOrder.MODULE, fixture.moduleId, null, amount);
		orders.verify(order.getTranId(),
				LiveExamPaymentGateway.Verification.valid(order.getTranId(), amount, "BDT", "VAL-SECRET-99"));
		assertThat(count(buyer.id, ExamOutboundMail.PAYMENT_RECEIPT)).isEqualTo(1);
		assertThat(count(buyer.id, ExamOutboundMail.EXAM_TIME)).isEqualTo(2);
		assertThat(sender.sentTo(buyer.email).stream().filter(message -> message.subject().contains("Payment receipt")).findFirst()
			.orElseThrow()
			.text()).contains("মডিউল");
	}

	@Test
	void failedAndCancelledOrdersSendNoMail() {
		Fixture fixture = seed("PUBLISHED", START.plus(Duration.ofHours(2)), "Unpaid", "অপেইড");
		Student buyer = student("ACTIVE");
		BigDecimal amount = new BigDecimal("100.00");
		LiveExamOrder failed = orders.insert(buyer.id, LiveExamOrder.EXAM, fixture.moduleId, fixture.examId, amount);
		orders.verify(failed.getTranId(), LiveExamPaymentGateway.Verification.invalid());
		LiveExamOrder cancelled = orders.insert(buyer.id, LiveExamOrder.MODULE, fixture.moduleId, null, amount);
		orders.cancel(cancelled.getId(), buyer.id);
		assertThat(count(buyer.id, ExamOutboundMail.PAYMENT_RECEIPT)).isZero();
		assertThat(count(buyer.id, ExamOutboundMail.EXAM_TIME)).isZero();
		assertThat(noticeCount(failed.getId())).isZero();
		assertThat(noticeCount(cancelled.getId())).isZero();
	}

	@Test
	void thePurchaseNoticeIsSkippedWhenItsRecipientIsNoLongerAnActiveMasterAdmin() {
		Fixture fixture = seed("PUBLISHED", START.plus(Duration.ofDays(2)), "Skip notice", "স্কিপ");
		Student buyer = student("ACTIVE");
		BigDecimal amount = new BigDecimal("60.00");
		sender.failTimes(2);
		LiveExamOrder order = orders.insert(buyer.id, LiveExamOrder.EXAM, fixture.moduleId, fixture.examId, amount);
		orders.verify(order.getTranId(),
				LiveExamPaymentGateway.Verification.valid(order.getTranId(), amount, "BDT", "VAL-SECRET-99"));
		UUID adminId = noticeRecipient(order.getId());
		assertThat(mailStatus(adminId, ExamOutboundMail.PURCHASE_NOTICE)).isEqualTo(ExamOutboundMail.PENDING);

		jdbc.update("update users set status = 'INACTIVE' where id = ?", adminId);
		try {
			examMail.scan();
		}
		finally {
			jdbc.update("update users set status = 'ACTIVE' where id = ?", adminId);
		}

		assertThat(mailStatus(adminId, ExamOutboundMail.PURCHASE_NOTICE)).isEqualTo(ExamOutboundMail.SKIPPED);
		assertThat(mailStatus(buyer.id, ExamOutboundMail.PAYMENT_RECEIPT)).isEqualTo(ExamOutboundMail.SENT);
	}

	@Test
	void aSendFailureLeavesTheSucceededOrderAndRetriesTheSameRow() {
		Fixture fixture = seed("PUBLISHED", START.plus(Duration.ofDays(2)), "Retry", "পুনরায়");
		Student buyer = student("ACTIVE");
		BigDecimal amount = new BigDecimal("80.00");
		sender.failTimes(5);
		LiveExamOrder order = orders.insert(buyer.id, LiveExamOrder.EXAM, fixture.moduleId, fixture.examId, amount);
		orders.verify(order.getTranId(),
				LiveExamPaymentGateway.Verification.valid(order.getTranId(), amount, "BDT", "VAL-SECRET-99"));
		examMail.scan();
		examMail.scan();
		assertThat(orderStatus(order.getId())).isEqualTo(LiveExamOrder.SUCCEEDED);
		assertThat(grantCount(order.getId())).isEqualTo(1);
		assertThat(mailStatus(buyer.id, ExamOutboundMail.PAYMENT_RECEIPT)).isEqualTo(ExamOutboundMail.FAILED);
		assertThat(attemptCount(buyer.id, ExamOutboundMail.PAYMENT_RECEIPT)).isEqualTo(3);
		int attempts = sender.attempts();
		examMail.scan();
		assertThat(sender.attempts()).isEqualTo(attempts);
	}

	@Test
	void theScanIsSingleFlight() throws Exception {
		Fixture fixture = seed("PUBLISHED", START.plus(Duration.ofDays(2)), "Lock", "লক");
		Student buyer = student("ACTIVE");
		UUID orderId = UUID.randomUUID();
		String tranId = UUID.randomUUID().toString().replace("-", "").substring(0, 30);
		jdbc.update("""
				insert into live_exam_orders (
				    id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id, status, created_at, updated_at)
				values (?, ?, 'EXAM', ?, ?, 15.00, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now())
				""", orderId, buyer.id, fixture.moduleId, fixture.examId, tranId);
		jdbc.update("""
				insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, source, created_at)
				values (?, ?, 'EXAM', ?, ?, ?, 'ORDER', now())
				""", UUID.randomUUID(), buyer.id, fixture.moduleId, fixture.examId, orderId);
		jdbc.update("""
				insert into exam_outbound_mail (id, kind, message_key, user_id, attempt_count, status, created_at, updated_at)
				values (?, 'PAYMENT_RECEIPT', ?, ?, 0, 'PENDING', now(), now())
				""", UUID.randomUUID(), orderId.toString(), buyer.id);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		sender.blockNext(entered, release);
		Thread first = new Thread(examMail::scan);
		first.start();
		assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
		examMail.scan();
		release.countDown();
		first.join(5000);
		assertThat(sender.sentCount()).isEqualTo(1);
	}

	private int count(UUID userId, String kind) {
		Integer count = jdbc.queryForObject("select count(*) from exam_outbound_mail where user_id = ? and kind = ?",
				Integer.class, userId, kind);
		return count == null ? 0 : count;
	}

	private String mailStatus(UUID userId, String kind) {
		List<String> rows = jdbc.queryForList(
				"select status from exam_outbound_mail where user_id = ? and kind = ? order by created_at", String.class, userId,
				kind);
		return rows.isEmpty() ? null : rows.get(rows.size() - 1);
	}

	private int attemptCount(UUID userId, String kind) {
		Integer count = jdbc.queryForObject(
				"select attempt_count from exam_outbound_mail where user_id = ? and kind = ?", Integer.class, userId, kind);
		return count == null ? 0 : count;
	}

	private int noticeCount(UUID orderId) {
		Integer count = jdbc.queryForObject(
				"select count(*) from exam_outbound_mail where kind = 'PURCHASE_NOTICE' and message_key = ?", Integer.class,
				orderId.toString());
		return count == null ? 0 : count;
	}

	private UUID noticeRecipient(UUID orderId) {
		return jdbc.queryForObject(
				"select user_id from exam_outbound_mail where kind = 'PURCHASE_NOTICE' and message_key = ?", UUID.class,
				orderId.toString());
	}

	private String orderStatus(UUID orderId) {
		return jdbc.queryForObject("select status from live_exam_orders where id = ?", String.class, orderId);
	}

	private int grantCount(UUID orderId) {
		Integer count = jdbc.queryForObject("select count(*) from live_exam_grants where order_id = ?", Integer.class, orderId);
		return count == null ? 0 : count;
	}

	private Fixture seed(String moduleStatus, Instant liveAt, String titleEn, String titleBn) {
		UUID categoryId = UUID.randomUUID();
		UUID moduleId = UUID.randomUUID();
		UUID examId = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				values (?, 'Category', 'ক্যাটাগরি', 1, now(), now())
				""", categoryId);
		jdbc.update("""
				insert into live_exam_modules (
				    id, category_id, title_en, title_bn, description_en, description_bn, access_mode, status, finally_saved,
				    position, created_at, updated_at)
				values (?, ?, 'Module EN', 'মডিউল', 'd', 'd', 'MANUAL', ?, true, 1, now(), now())
				""", moduleId, categoryId, moduleStatus);
		jdbc.update("""
				insert into live_exams (
				    id, module_id, exam_type, title_en, title_bn, description_en, description_bn, rules_en, rules_bn,
				    paper_language, live_at, duration_minutes, pass_percent, status, finally_saved, created_at, updated_at)
				values (?, ?, 'MCQ', ?, ?, 'd', 'd', 'r', 'r', 'BN', ?, 40, 40, 'DRAFT', true, now(), now())
				""", examId, moduleId, titleEn, titleBn, liveAt == null ? null : Timestamp.from(liveAt));
		jdbc.update("""
				insert into live_exam_questions (id, exam_id, stem, marks, position, created_at, updated_at)
				values (?, ?, 'stem', 1, 1, now(), now())
				""", UUID.randomUUID(), examId);
		return new Fixture(moduleId, examId);
	}

	private Student student(String status) {
		String phone = "+8801" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
		UUID id = UUID.randomUUID();
		String email = "exam-" + id + "@example.com";
		jdbc.update("""
				insert into users (id, phone, email, role, status, created_at, updated_at)
				values (?, ?, ?, 'STUDENT', ?, now(), now())
				""", id, phone, email, status);
		return new Student(id, email, phone);
	}

	private record Fixture(UUID moduleId, UUID examId) {
	}

	private record Student(UUID id, String email, String phone) {
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(START);
		}

	}

	@TestConfiguration
	static class MailConfig {

		@Bean
		@Primary
		CapturingExamMailSender capturingExamMailSender() {
			return new CapturingExamMailSender();
		}

	}

}
