package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Prices, discounts and re-buys of exam orders (NR-01, B5, B6, B7, A2' and R19), against real PostgreSQL. The test rolls
 * back, so the shared database keeps nothing it wrote.
 */
@SpringBootTest
@Transactional
@Import(DiscountOrderIT.ClockConfig.class)
class DiscountOrderIT extends PostgresIntegrationTest {

	private static final Instant WINDOW_START = Instant.parse("2026-10-29T04:00:00Z");

	private static final Instant WINDOW_END = WINDOW_START.plusSeconds(3600);

	private static final Instant NOW = WINDOW_START.minus(Duration.ofHours(1));

	@Autowired
	private LiveExamOrderService orders;

	@Autowired
	private LiveExamRepository exams;

	@Autowired
	private LiveExamAccess access;

	@Autowired
	private FakeLiveExamPaymentGateway gateway;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper json;

	@BeforeEach
	void reset() {
		clock.set(NOW);
		gateway.reset();
	}

	@Test
	void aDiscountIsAppliedAndTheOrderRecordsWhatTheStudentSaw() {
		UUID exam = exam(1000, 20, null, 30);
		UUID student = student();

		LiveExamOrderService.OrderView order = initiate(student, exam);

		assertThat(order.amount()).isEqualByComparingTo("800.00");
		assertThat(order.listPrice()).isEqualByComparingTo("1000.00");
		assertThat(order.discountPercent()).isEqualTo(20);
		assertThat(order.examId()).isEqualTo(exam);
		assertThat(order.moduleId()).as("an exam order names no package").isNull();
	}

	@Test
	void aDiscountThatEndedBeforeNowIsNotApplied() {
		UUID exam = exam(1000, 20, NOW.minusSeconds(1), 30);

		LiveExamOrderService.OrderView order = initiate(student(), exam);

		assertThat(order.amount()).isEqualByComparingTo("1000.00");
		assertThat(order.discountPercent()).as("no percent is shown once the discount has ended").isNull();
	}

	@Test
	void aDiscountEndingNowIsNotAppliedAndOneEndingALaterSecondIs() {
		UUID ending = exam(1000, 20, NOW, null);
		assertThat(initiate(student(), ending).amount()).isEqualByComparingTo("1000.00");

		UUID later = exam(1000, 20, NOW.plusSeconds(1), null);
		assertThat(initiate(student(), later).amount()).isEqualByComparingTo("800.00");
	}

	@Test
	void anOpenOrderKeepsItsAmountAfterAPriceChange() {
		UUID exam = exam(1000, 20, null, 30);
		UUID student = student();
		LiveExamOrderService.OrderView first = initiate(student, exam);

		jdbc.update("update live_exams set price = 2000, discount_percent = null where id = ?", exam);
		LiveExamOrderService.InitiateResult again = orders.initiate(student, body(exam));

		assertThat(again.created()).isFalse();
		assertThat(again.order().orderId()).isEqualTo(first.orderId());
		assertThat(again.order().amount()).isEqualByComparingTo("800.00");
	}

	@Test
	void aFreeExamIsNotForSale() {
		UUID exam = freeExam();
		UUID student = student();

		assertThatThrownBy(() -> orders.initiate(student, body(exam)))
			.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.code()).isEqualTo(ErrorCode.EXAM_NOT_FOR_SALE));
		assertThat(orderCount(student)).isZero();
	}

	@Test
	void anExamWithNoPriceIsNotForSaleOnItsOwn() {
		UUID exam = exam(null, null, null, null);
		UUID student = student();

		assertThatThrownBy(() -> orders.initiate(student, body(exam)))
			.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.code()).isEqualTo(ErrorCode.EXAM_NOT_FOR_SALE));
		assertThat(orderCount(student)).isZero();
	}

	@Test
	void anActiveHolderIsRefused() {
		UUID exam = exam(1000, null, null, 30);
		UUID student = student();
		grant(student, exam, WINDOW_START.minus(Duration.ofDays(1)));
		clock.set(WINDOW_END.plus(Duration.ofDays(5)));

		assertThatThrownBy(() -> orders.initiate(student, body(exam)))
			.isInstanceOfSatisfying(ApiException.class,
					error -> assertThat(error.code()).isEqualTo(ErrorCode.ALREADY_PURCHASED));
		assertThat(orderCount(student)).isZero();
	}

	@Test
	void anExpiredHolderMayBuyAgainAndTheNewGrantRestartsTheClock() {
		UUID exam = exam(1000, null, null, 30);
		UUID student = student();
		grant(student, exam, WINDOW_START.minus(Duration.ofDays(1)));
		Instant lapsed = WINDOW_END.plus(Duration.ofDays(30)).plusSeconds(60);
		clock.set(lapsed);
		LiveExam row = exams.findById(exam).orElseThrow();
		assertThat(access.canSit(student, row, lapsed)).isFalse();

		LiveExamOrderService.OrderView order = initiate(student, exam);
		orders.ipn(order.tranId(), null, "val-1");

		assertThat(access.canSit(student, row, lapsed)).isTrue();
		AccessState state = access.state(student, row, lapsed);
		assertThat(state.grantStart()).as("the new grant starts at the purchase").isEqualTo(lapsed);
		assertThat(state.endsAt()).isEqualTo(lapsed.plus(Duration.ofDays(30)));
		assertThat(jdbc.queryForObject("select count(*) from live_exam_grants where student_id = ? and revoked_at is null",
				Integer.class, student)).as("the expired grant was retired, not kept alongside").isEqualTo(1);
	}

	private LiveExamOrderService.OrderView initiate(UUID student, UUID exam) {
		LiveExamOrderService.InitiateResult result = orders.initiate(student, body(exam));
		assertThat(result.created()).isTrue();
		return result.order();
	}

	private JsonNode body(UUID exam) {
		return json.readTree("{\"examId\":\"" + exam + "\"}");
	}

	/** A published exam, in a published package, sold on its own. Null price means no price. */
	private UUID exam(Integer price, Integer discountPercent, Instant discountEndsAt, Integer accessDays) {
		return insertExam(price, false, discountPercent, discountEndsAt, accessDays);
	}

	private UUID freeExam() {
		return insertExam(null, true, null, null, null);
	}

	private UUID insertExam(Integer price, boolean free, Integer discountPercent, Instant discountEndsAt, Integer accessDays) {
		UUID category = UUID.randomUUID();
		jdbc.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", category);
		UUID module = UUID.randomUUID();
		jdbc.update("insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,"
				+ " access_mode, status, finally_saved, position, created_at, updated_at)"
				+ " values (?, ?, 'Package', 'প্যাকেজ', '', '', 'MANUAL', 'PUBLISHED', true, 0, now(), now())", module,
				category);
		UUID exam = UUID.randomUUID();
		jdbc.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, pass_percent, answers_changeable, price, is_free, discount_percent, discount_ends_at,"
				+ " access_days, published_at, created_at, updated_at)"
				+ " values (?, null, ?, 'MCQ', 'Paper', 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', 'PUBLISHED',"
				+ " true, 50, true, ?, ?, ?, ?, ?, ?, now(), now())", exam, category, Timestamp.from(WINDOW_START),
				Timestamp.from(WINDOW_END), price == null ? null : BigDecimal.valueOf(price), free, discountPercent,
				ts(discountEndsAt), accessDays, Timestamp.from(WINDOW_START));
		jdbc.update("insert into live_exam_package_exams (id, module_id, exam_id, position, added_at, removed_at)"
				+ " values (?, ?, ?, 0, ?, null)", UUID.randomUUID(), module, exam, Timestamp.from(WINDOW_START));
		return exam;
	}

	private void grant(UUID student, UUID exam, Instant createdAt) {
		jdbc.update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source)"
				+ " values (?, ?, 'EXAM', null, ?, null, ?, 'ORDER')", UUID.randomUUID(), student, exam,
				Timestamp.from(createdAt));
	}

	private UUID student() {
		UUID id = UUID.randomUUID();
		long digits = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L);
		jdbc.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, 'STUDENT', 'ACTIVE', now(), now())", id, "+8801" + String.format("%09d", digits),
				id + "@example.com");
		return id;
	}

	private int orderCount(UUID student) {
		return jdbc.queryForObject("select count(*) from live_exam_orders where student_id = ?", Integer.class, student);
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

		@Bean
		@Primary
		FakeLiveExamPaymentGateway fakeGateway() {
			return new FakeLiveExamPaymentGateway();
		}

	}

}
