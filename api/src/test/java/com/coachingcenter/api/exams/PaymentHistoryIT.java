package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
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
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Payment history (P1 to P4) against real PostgreSQL: the paid time and receipt number, the list filters, the summary, the
 * receipt's access rule, and the PDF text. The test rolls back, so the shared database keeps nothing it wrote.
 */
@SpringBootTest
@Transactional
@Import(PaymentHistoryIT.ClockConfig.class)
class PaymentHistoryIT extends PostgresIntegrationTest {

	private static final Instant WINDOW_START = Instant.parse("2026-10-29T04:00:00Z");

	private static final Instant WINDOW_END = WINDOW_START.plusSeconds(3600);

	private static final Instant NOW = WINDOW_START.minus(Duration.ofHours(1));

	@Autowired
	private LiveExamOrderService orders;

	@Autowired
	private LiveExamOrderHistoryService history;

	@Autowired
	private LiveExamReceiptPdf receiptPdf;

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
	void aPaidOrderGetsItsPaidTimeAndAReceiptNumberOnce() {
		UUID student = student();
		UUID exam = exam(1000);
		LiveExamOrderService.OrderView order = initiate(student, exam);

		orders.ipn(order.tranId(), null, "val-1");

		Instant paidAt = paidAt(order.orderId());
		assertThat(paidAt).as("paid time is set when the order is paid").isEqualTo(NOW);
		String receipt = receiptNo(order.orderId());
		assertThat(receipt).matches("RCPT-\\d{4}-\\d{6}");
		assertThat(receipt).startsWith("RCPT-2026-");

		clock.set(NOW.plusSeconds(600));
		orders.ipn(order.tranId(), null, "val-1");
		assertThat(paidAt(order.orderId())).as("a repeat notice does not move the paid time").isEqualTo(paidAt);
		assertThat(receiptNo(order.orderId())).isEqualTo(receipt);
	}

	@Test
	void aConfirmedOrderGetsItsOwnNumberTooAndTheNumbersCountUp() {
		UUID student = student();
		UUID master = insertUser("MASTER_ADMIN");
		LiveExamOrderService.OrderView first = initiate(student, exam(1000));
		LiveExamOrderService.OrderView second = initiate(student, exam(500));

		orders.ipn(first.tranId(), null, "val-1");
		orders.confirm(second.orderId(), master, "Checked at SSLCommerz");

		assertThat(paidAt(second.orderId())).isNotNull();
		assertThat(receiptNo(second.orderId())).matches("RCPT-\\d{4}-\\d{6}");
		assertThat(sequence(receiptNo(second.orderId()))).isGreaterThan(sequence(receiptNo(first.orderId())));
	}

	@Test
	void theListFiltersByStatusScopeExamAndPaidDate() {
		UUID student = student();
		UUID paidExam = exam(1000);
		UUID pendingExam = exam(500);
		LiveExamOrderService.OrderView paid = initiate(student, paidExam);
		LiveExamOrderService.OrderView pending = initiate(student, pendingExam);
		orders.ipn(paid.tranId(), null, "val-1");
		// Paid at 23:59:59 on 28 October, Bangladesh time. The pending order was created at 09:00 on 29 October.
		jdbc.update("update live_exam_orders set paid_at = ? where id = ?",
				Timestamp.from(Instant.parse("2026-10-28T17:59:59Z")), paid.orderId());

		assertThat(ids(filter(student, List.of("PAID"), null, null, null, null, null))).containsExactly(paid.orderId());
		assertThat(ids(filter(student, List.of("PENDING"), null, null, null, null, null))).containsExactly(pending.orderId());
		assertThat(ids(filter(student, List.of("PAID", "PENDING"), null, null, null, null, null)))
			.containsExactlyInAnyOrder(paid.orderId(), pending.orderId());
		assertThat(ids(filter(student, null, "EXAM", null, null, null, null))).hasSize(2);
		assertThat(ids(filter(student, null, "MODULE", null, null, null, null))).isEmpty();
		assertThat(ids(filter(student, null, null, paidExam, null, null, null))).containsExactly(paid.orderId());
		assertThat(ids(filter(student, null, null, null, null, null, paid.orderId()))).containsExactly(paid.orderId());

		LocalDate day = LocalDate.parse("2026-10-29");
		LocalDate eve = day.minusDays(1);
		assertThat(ids(filter(student, null, null, null, eve, eve, null))).as("23:59:59 on the 28th belongs to the 28th")
			.containsExactly(paid.orderId());
		assertThat(ids(filter(student, null, null, null, day, day, null))).containsExactly(pending.orderId());
		assertThat(ids(filter(student, null, null, null, eve, day, null)))
			.containsExactlyInAnyOrder(paid.orderId(), pending.orderId());
		// Midnight, Bangladesh time, is the first moment of the 29th.
		jdbc.update("update live_exam_orders set paid_at = ? where id = ?",
				Timestamp.from(Instant.parse("2026-10-28T18:00:00Z")), paid.orderId());
		assertThat(ids(filter(student, null, null, null, day, day, null)))
			.as("midnight belongs to the new day").containsExactlyInAnyOrder(paid.orderId(), pending.orderId());
		assertThat(ids(filter(student, null, null, null, eve, eve, null))).isEmpty();
	}

	@Test
	void anotherStudentsOrderNeverMatchesAFilterOrTheSummary() {
		UUID student = student();
		UUID other = student();
		LiveExamOrderService.OrderView theirs = initiate(other, exam(1000));
		orders.ipn(theirs.tranId(), null, "val-1");
		LiveExamOrderService.OrderView mine = initiate(student, exam(300));

		assertThat(ids(filter(student, null, null, null, null, null, theirs.orderId()))).isEmpty();
		assertThat(ids(filter(student, null, null, null, null, null, null))).containsExactly(mine.orderId());
		LiveExamOrderHistoryService.OrderSummary summary = history.summary(student);
		assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(summary.paidCount()).isZero();
		assertThat(summary.pendingCount()).isEqualTo(1);
		assertThat(summary.currency()).isEqualTo("BDT");
	}

	@Test
	void theSummaryTotalsPaidAndPendingOrders() {
		UUID student = student();
		LiveExamOrderService.OrderView paid = initiate(student, exam(1000));
		initiate(student, exam(500));
		orders.ipn(paid.tranId(), null, "val-1");

		LiveExamOrderHistoryService.OrderSummary summary = history.summary(student);

		assertThat(summary.totalPaid()).isEqualByComparingTo(new BigDecimal("1000"));
		assertThat(summary.paidCount()).isEqualTo(1);
		assertThat(summary.pendingCount()).isEqualTo(1);
	}

	@Test
	void theReceiptIsOnlyForTheOwnPaidOrderAndItsPdfCarriesTheNumber() throws Exception {
		UUID student = student();
		UUID other = student();
		LiveExamOrderService.OrderView pending = initiate(student, exam(700));
		LiveExamOrderService.OrderView paid = initiate(student, exam(1000));
		orders.ipn(paid.tranId(), null, "val-1");
		LiveExamOrderService.OrderView theirs = initiate(other, exam(900));
		orders.ipn(theirs.tranId(), null, "val-2");

		assertNotFound(() -> history.receipt(student, pending.orderId()));
		assertNotFound(() -> history.receipt(student, theirs.orderId()));
		assertNotFound(() -> history.receipt(student, UUID.randomUUID()));

		LiveExamOrderHistoryService.Receipt receipt = history.receipt(student, paid.orderId());
		byte[] pdf = receiptPdf.render(receipt);
		assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		String text = text(pdf);
		assertThat(text).contains(receiptNo(paid.orderId()), "PAYMENT RECEIPT", "Symam Law", "BDT 1,000.00",
				paid.tranId(), paid.orderId().toString());
	}

	@Test
	void aPdfNameWithBanglaLettersPrintsTheLatinPartOnly() throws Exception {
		UUID student = student();
		jdbc.update("insert into student_profiles (user_id, full_name, student_code) values (?, ?, ?)", student,
				"Rahim শেখ", "PAYHIST" + student.toString().substring(0, 6));
		LiveExamOrderService.OrderView paid = initiate(student, exam(1000));
		orders.ipn(paid.tranId(), null, "val-1");

		String text = text(receiptPdf.render(history.receipt(student, paid.orderId())));

		assertThat(text).contains("Rahim");
		assertThat(text).doesNotContain("শেখ");
	}

	private List<LiveExamOrderHistoryService.OwnOrderRow> filter(UUID student, List<String> status, String scope, UUID exam,
			LocalDate from, LocalDate to, UUID orderId) {
		return history.list(student, new LiveExamOrderHistoryService.OrderFilter(status, scope, null, exam, from, to,
				orderId), PageRequest.of(0, 50)).getContent();
	}

	private static List<UUID> ids(List<LiveExamOrderHistoryService.OwnOrderRow> rows) {
		return rows.stream().map(LiveExamOrderHistoryService.OwnOrderRow::orderId).toList();
	}

	private static void assertNotFound(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
				exception -> assertThat(exception.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
	}

	private static long sequence(String receiptNo) {
		return Long.parseLong(receiptNo.substring(receiptNo.lastIndexOf('-') + 1));
	}

	private static String text(byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private Instant paidAt(UUID orderId) {
		Timestamp paid = jdbc.queryForObject("select paid_at from live_exam_orders where id = ?", Timestamp.class, orderId);
		return paid == null ? null : paid.toInstant();
	}

	private String receiptNo(UUID orderId) {
		return jdbc.queryForObject("select receipt_no from live_exam_orders where id = ?", String.class, orderId);
	}

	private LiveExamOrderService.OrderView initiate(UUID student, UUID exam) {
		LiveExamOrderService.InitiateResult result = orders.initiate(student, examBody(exam));
		assertThat(result.created()).isTrue();
		return result.order();
	}

	private JsonNode examBody(UUID exam) {
		return json.readTree("{\"examId\":\"" + exam + "\"}");
	}

	/** A published exam, sold on its own, in a published package. */
	private UUID exam(Integer price) {
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
				+ " true, 50, true, ?, false, null, null, 30, ?, now(), now())", exam, category, Timestamp.from(WINDOW_START),
				Timestamp.from(WINDOW_END), BigDecimal.valueOf(price), Timestamp.from(WINDOW_START));
		jdbc.update("insert into live_exam_package_exams (id, module_id, exam_id, position, added_at, removed_at)"
				+ " values (?, ?, ?, 0, ?, null)", UUID.randomUUID(), module, exam, Timestamp.from(WINDOW_START));
		return exam;
	}

	private UUID student() {
		return insertUser("STUDENT");
	}

	private UUID insertUser(String role) {
		UUID id = UUID.randomUUID();
		long digits = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L);
		jdbc.update("insert into users (id, phone, email, role, status, created_at, updated_at)"
				+ " values (?, ?, ?, ?, 'ACTIVE', now(), now())", id, "+8801" + String.format("%09d", digits),
				id + "@example.com", role);
		return id;
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
