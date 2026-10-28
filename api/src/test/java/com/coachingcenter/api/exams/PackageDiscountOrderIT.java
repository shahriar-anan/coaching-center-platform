package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Package and bundle prices with a discount (NR-01, B5, B6 and R19), against real PostgreSQL. A package is priced once and
 * its discount applies to the whole package; a bundle is the sum of its exams, each with its own discount. The test rolls
 * back, so the shared database keeps nothing it wrote.
 */
@SpringBootTest
@Transactional
@Import(PackageDiscountOrderIT.ClockConfig.class)
class PackageDiscountOrderIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

	private static final Instant WINDOW_START = Instant.parse("2026-10-29T04:00:00Z");

	private static final Instant WINDOW_END = WINDOW_START.plusSeconds(3600);

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
	void aPackageDiscountAppliesOnceToTheWholePackageAndTheGrantCoversEveryMemberExam() {
		UUID category = category();
		UUID module = module(category, 1000, 25, null);
		UUID first = member(module, category, null, null, null);
		UUID second = member(module, category, null, null, null);
		UUID student = student();

		LiveExamOrderService.OrderView order = initiate(student, moduleBody(module));

		assertThat(order.amount()).isEqualByComparingTo("750.00");
		assertThat(order.listPrice()).isEqualByComparingTo("1000.00");
		assertThat(order.discountPercent()).isEqualTo(25);
		assertThat(order.moduleId()).isEqualTo(module);
		assertThat(order.examId()).as("a package order names no exam").isNull();

		orders.ipn(order.tranId(), null, "val-1");

		assertThat(access.canSit(student, exams.findById(first).orElseThrow(), WINDOW_START)).isTrue();
		assertThat(access.canSit(student, exams.findById(second).orElseThrow(), WINDOW_START)).isTrue();
	}

	@Test
	void aPackageDiscountThatEndedYesterdayIsNotApplied() {
		UUID category = category();
		UUID module = module(category, 1000, 25, NOW.minus(Duration.ofDays(1)));
		member(module, category, null, null, null);

		LiveExamOrderService.OrderView order = initiate(student(), moduleBody(module));

		assertThat(order.amount()).isEqualByComparingTo("1000.00");
		assertThat(order.listPrice()).isEqualByComparingTo("1000.00");
		assertThat(order.discountPercent()).as("no percent is shown once the discount has ended").isNull();
	}

	@Test
	void aBundleIsTheSumOfItsExamsEachWithItsOwnDiscount() {
		UUID category = category();
		UUID module = module(category, null, null, null);
		UUID discounted = member(module, category, 200, 50, null);
		UUID full = member(module, category, 300, null, null);
		UUID student = student();

		LiveExamOrderService.OrderView order = initiate(student, bundleBody(discounted, full));

		assertThat(order.scope()).isEqualTo(LiveExamOrderBundle.SCOPE);
		assertThat(order.amount()).isEqualByComparingTo("400.00");
		assertThat(order.listPrice()).isEqualByComparingTo("500.00");
		assertThat(order.discountPercent()).as("the two exams had different discounts").isNull();
		assertThat(order.examIds()).containsExactlyInAnyOrder(discounted, full);

		orders.ipn(order.tranId(), null, "val-1");

		assertThat(access.canSit(student, exams.findById(discounted).orElseThrow(), WINDOW_START)).isTrue();
		assertThat(access.canSit(student, exams.findById(full).orElseThrow(), WINDOW_START)).isTrue();
	}

	@Test
	void aPackageDiscountChangedWhileTheOrderIsOpenLeavesTheOpenOrderAtItsAmount() {
		UUID category = category();
		UUID module = module(category, 1000, 25, null);
		member(module, category, null, null, null);
		UUID student = student();
		LiveExamOrderService.OrderView first = initiate(student, moduleBody(module));

		jdbc.update("update live_exam_modules set price = 2000, discount_percent = 50 where id = ?", module);
		LiveExamOrderService.InitiateResult again = orders.initiate(student, moduleBody(module));

		assertThat(again.created()).isFalse();
		assertThat(again.order().orderId()).isEqualTo(first.orderId());
		assertThat(again.order().amount()).isEqualByComparingTo("750.00");
		assertThat(again.order().listPrice()).isEqualByComparingTo("1000.00");
		assertThat(again.order().discountPercent()).isEqualTo(25);
	}

	private LiveExamOrderService.OrderView initiate(UUID student, JsonNode body) {
		LiveExamOrderService.InitiateResult result = orders.initiate(student, body);
		assertThat(result.created()).isTrue();
		return result.order();
	}

	private JsonNode moduleBody(UUID module) {
		return json.readTree("{\"moduleId\":\"" + module + "\"}");
	}

	private JsonNode bundleBody(UUID... examIds) {
		StringBuilder ids = new StringBuilder();
		for (UUID id : examIds) {
			if (ids.length() > 0) {
				ids.append(',');
			}
			ids.append('"').append(id).append('"');
		}
		return json.readTree("{\"examIds\":[" + ids + "]}");
	}

	private UUID category() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", id);
		return id;
	}

	/** A published package with a price, or with none (a bundle is priced by its exams). */
	private UUID module(UUID category, Integer price, Integer discountPercent, Instant discountEndsAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,"
				+ " access_mode, status, finally_saved, position, price, discount_percent, discount_ends_at, created_at,"
				+ " updated_at) values (?, ?, 'Package', 'প্যাকেজ', '', '', 'MANUAL', 'PUBLISHED', true, 0, ?, ?, ?, now(),"
				+ " now())", id, category, price == null ? null : BigDecimal.valueOf(price), discountPercent, ts(discountEndsAt));
		return id;
	}

	/** A published MCQ with a price, in the package when it is given (its home category is the package's). */
	private UUID member(UUID module, UUID category, Integer price, Integer discountPercent, Instant discountEndsAt) {
		UUID exam = UUID.randomUUID();
		jdbc.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, pass_percent, answers_changeable, price, is_free, discount_percent, discount_ends_at,"
				+ " access_days, published_at, created_at, updated_at)"
				+ " values (?, null, ?, 'MCQ', 'Paper', 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', 'PUBLISHED',"
				+ " true, 50, true, ?, false, ?, ?, null, ?, now(), now())", exam, category, ts(WINDOW_START), ts(WINDOW_END),
				price == null ? null : BigDecimal.valueOf(price), discountPercent, ts(discountEndsAt), ts(WINDOW_START));
		if (module != null) {
			jdbc.update("insert into live_exam_package_exams (id, module_id, exam_id, position, added_at, removed_at)"
					+ " values (?, ?, ?, 0, ?, null)", UUID.randomUUID(), module, exam, ts(WINDOW_START));
		}
		return exam;
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
			return new MutableClock(NOW);
		}

		@Bean
		@Primary
		FakeLiveExamPaymentGateway fakeGateway() {
			return new FakeLiveExamPaymentGateway();
		}

	}

}
