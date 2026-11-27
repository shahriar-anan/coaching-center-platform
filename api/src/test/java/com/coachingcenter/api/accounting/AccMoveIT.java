package com.coachingcenter.api.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.coachingcenter.api.PostgresIntegrationTest;

/**
 * The move of the old payments (V107_2, plan 5.4). Each test builds its own scratch schema: it migrates it to V107 (the old tables), adds
 * old rows, then migrates it to the latest version. The shared test schema is not touched, so no other test sees these rows.
 */
@SpringBootTest
class AccMoveIT extends PostgresIntegrationTest {

	private static final UUID GROUP = UUID.fromString("a0000000-0000-4000-8000-000000000001");

	private static final UUID TYPE_LECTURE = UUID.fromString("a0000000-0000-4000-8000-000000000111");

	private static final UUID TYPE_EXAM = UUID.fromString("a0000000-0000-4000-8000-000000000112");

	private static final UUID TYPE_OTHER = UUID.fromString("a0000000-0000-4000-8000-000000000117");

	private static final UUID ACCOUNT_CASH = UUID.fromString("a0000000-0000-4000-8000-000000000201");

	private static final UUID ACCOUNT_BKASH = UUID.fromString("a0000000-0000-4000-8000-000000000202");

	private static final UUID ACCOUNT_ROCKET = UUID.fromString("a0000000-0000-4000-8000-000000000204");

	private static final UUID ACCOUNT_BANK = UUID.fromString("a0000000-0000-4000-8000-000000000205");

	@Value("${spring.datasource.url}")
	private String url;

	@Value("${spring.datasource.username}")
	private String user;

	@Value("${spring.datasource.password}")
	private String password;

	@Autowired
	private JdbcTemplate jdbc;

	private final List<String> schemas = new ArrayList<>();

	@AfterEach
	void dropScratchSchemas() {
		for (String schema : schemas) {
			jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
		schemas.clear();
	}

	@Test
	void everyApprovedAndVoidedPaymentMovesWithItsChargeAndTheChecksHold() {
		String schema = newSchema();
		migrate(schema, "107");
		JdbcTemplate old = db(schema);
		UUID admin = seedUser(old);
		UUID batch = UUID.randomUUID();
		seedBatch(old, admin, batch);
		UUID rahim = seedInstructor(old, admin, "Rahim", false);
		UUID karim = seedInstructor(old, admin, "Karim", true);

		UUID examClass = seedClass(old, batch, "EXAM", "Chemistry", LocalDate.parse("2026-10-08"), rahim);
		UUID otherClass = seedClass(old, batch, "OTHER", "Revision", LocalDate.parse("2026-10-09"), rahim);
		UUID voidedClass = seedClass(old, batch, "LECTURE", "Maths", LocalDate.parse("2026-10-11"), rahim);

		// Hand-recorded payments: a lecture with a class count, an exam without one, an other to an archived instructor, a voided one
		// and a rejected one.
		UUID lecture = seedOtherPayment(old, admin, rahim, batch, "LECTURE", 3, "Lecture fee", "1500.00", "BANK",
				LocalDate.parse("2026-10-10"), "APPROVED", null, Instant.parse("2026-10-10T04:00:00Z"), "REF1");
		UUID exam = seedOtherPayment(old, admin, rahim, null, "EXAM", null, "Paper setting", "800.00", "CASH",
				LocalDate.parse("2026-10-12"), "APPROVED", null, Instant.parse("2026-10-12T04:00:00Z"), null);
		UUID printing = seedOtherPayment(old, admin, karim, null, "OTHER", null, "Printing", "250.00", "ROCKET",
				LocalDate.parse("2026-10-13"), "APPROVED", null, Instant.parse("2026-10-13T04:00:00Z"), null);
		UUID voidedOther = seedOtherPayment(old, admin, rahim, null, "OTHER", null, "Old bonus", "600.00", "BKASH",
				LocalDate.parse("2026-10-14"), "VOIDED", "Entered twice", Instant.parse("2026-10-14T04:00:00Z"), null);
		UUID rejectedOther = seedOtherPayment(old, admin, rahim, null, "LECTURE", 2, "Not paid", "999.00", "BKASH",
				LocalDate.parse("2026-10-15"), "REJECTED", null, Instant.parse("2026-10-15T04:00:00Z"), null);

		// Class payments: one to Karim for a class of Rahim's (the payee is the payment's instructor), one for an other class, and one
		// voided.
		UUID classExam = seedClassPayment(old, admin, examClass, karim, "500.00", "BKASH", LocalDate.parse("2026-10-06"),
				"APPROVED", null, Instant.parse("2026-10-06T04:00:00Z"));
		UUID classOther = seedClassPayment(old, admin, otherClass, rahim, "300.00", "CASH", LocalDate.parse("2026-10-07"),
				"APPROVED", null, Instant.parse("2026-10-07T04:00:00Z"));
		UUID classVoided = seedClassPayment(old, admin, voidedClass, rahim, "400.00", "NAGAD", LocalDate.parse("2026-10-11"),
				"VOIDED", "Wrong amount", Instant.parse("2026-10-11T04:00:00Z"));

		migrate(schema, "latest");
		JdbcTemplate moved = db(schema);

		// Payees: the same ids and names; an archived instructor stays archived.
		assertThat(moved.queryForObject("select count(*) from acc_payees where group_id = ? and id in (?, ?)", Integer.class,
				GROUP, rahim, karim)).isEqualTo(2);
		assertThat(moved.queryForObject("select archived_at is not null from acc_payees where id = ?", Boolean.class, karim)).isTrue();
		assertThat(moved.queryForObject("select archived_at is null from acc_payees where id = ?", Boolean.class, rahim)).isTrue();

		// Hand-recorded payment: the payment and its charge, paired, with the old values.
		Map<String, Object> payment = movedPayment(moved, "OTHER", lecture);
		assertThat(payment.get("kind")).isEqualTo("PAYMENT");
		assertThat(payment.get("status")).isEqualTo("ACTIVE");
		assertThat(payment.get("payee_id")).isEqualTo(rahim);
		assertThat(payment.get("account_id")).isEqualTo(ACCOUNT_BANK);
		assertThat(money(payment.get("amount"))).isEqualTo("1500.00");
		assertThat(payment.get("reference")).isEqualTo("REF1");
		assertThat(payment.get("batch_id")).isEqualTo(batch);
		assertThat(payment.get("legacy_source")).isEqualTo("OTHER");
		assertThat(payment.get("corrected_at")).isNull();
		Map<String, Object> charge = entry(moved, payment.get("paired_entry_id"));
		assertThat(charge.get("kind")).isEqualTo("CHARGE");
		assertThat(charge.get("type_id")).isEqualTo(TYPE_LECTURE);
		assertThat(charge.get("payee_id")).isEqualTo(rahim);
		assertThat(money(charge.get("amount"))).isEqualTo("1500.00");
		assertThat(charge.get("quantity")).isEqualTo(new BigDecimal("3.00"));
		assertThat(charge.get("unit_price")).isNull();
		assertThat(charge.get("account_id")).isNull();
		assertThat(charge.get("batch_id")).isEqualTo(batch);
		assertThat(charge.get("note")).isEqualTo("Moved from instructor payments");
		assertThat(charge.get("entry_date").toString()).isEqualTo("2026-10-10");
		assertThat((Long) charge.get("no")).isLessThan((Long) payment.get("no"));

		// Recorded by and at come from the old payment; the created time of the moved rows is their recorded time.
		assertThat(payment.get("recorded_by")).isEqualTo(admin);
		assertThat(charge.get("recorded_by")).isEqualTo(admin);
		assertThat(instant(payment.get("recorded_at"))).isEqualTo(Instant.parse("2026-10-10T04:00:00Z"));
		assertThat(instant(payment.get("created_at"))).isEqualTo(instant(payment.get("recorded_at")));

		// The exam payment has no class count: its charge has no quantity, and the type is Exam.
		Map<String, Object> examPayment = movedPayment(moved, "OTHER", exam);
		Map<String, Object> examCharge = entry(moved, examPayment.get("paired_entry_id"));
		assertThat(examCharge.get("type_id")).isEqualTo(TYPE_EXAM);
		assertThat(examCharge.get("quantity")).isNull();
		assertThat(examCharge.get("description")).isEqualTo("Paper setting");
		assertThat(examPayment.get("account_id")).isEqualTo(ACCOUNT_CASH);

		// An other payment to an archived instructor: the other type, the rocket account, and the payee is the archived one.
		Map<String, Object> printingPayment = movedPayment(moved, "OTHER", printing);
		assertThat(printingPayment.get("payee_id")).isEqualTo(karim);
		assertThat(printingPayment.get("account_id")).isEqualTo(ACCOUNT_ROCKET);
		assertThat(entry(moved, printingPayment.get("paired_entry_id")).get("type_id")).isEqualTo(TYPE_OTHER);

		// A voided hand payment: both entries voided with the old reason; a rejected one is not moved at all.
		Map<String, Object> voidedPayment = movedPayment(moved, "OTHER", voidedOther);
		assertThat(voidedPayment.get("status")).isEqualTo("VOIDED");
		assertThat(voidedPayment.get("void_reason")).isEqualTo("Entered twice");
		assertThat(voidedPayment.get("voided_by")).isEqualTo(admin);
		assertThat(entry(moved, voidedPayment.get("paired_entry_id")).get("status")).isEqualTo("VOIDED");
		assertThat(moved.queryForObject("select count(*) from acc_entries where legacy_id = ?", Integer.class, rejectedOther))
				.isZero();

		// Class payments: the payee is the payment's instructor; the charge takes the class kind and the class is named.
		Map<String, Object> classPayment = movedPayment(moved, "CLASS", classExam);
		assertThat(classPayment.get("payee_id")).isEqualTo(karim);
		assertThat(classPayment.get("account_id")).isEqualTo(ACCOUNT_BKASH);
		assertThat(classPayment.get("legacy_class_id")).isEqualTo(examClass);
		Map<String, Object> classCharge = entry(moved, classPayment.get("paired_entry_id"));
		assertThat(classCharge.get("type_id")).isEqualTo(TYPE_EXAM);
		assertThat(classCharge.get("quantity")).isEqualTo(new BigDecimal("1.00"));
		assertThat(classCharge.get("description")).isEqualTo("Class: Chemistry on 2026-10-08");
		assertThat(classCharge.get("payee_id")).isEqualTo(karim);

		Map<String, Object> classOtherPayment = movedPayment(moved, "CLASS", classOther);
		assertThat(entry(moved, classOtherPayment.get("paired_entry_id")).get("type_id")).isEqualTo(TYPE_OTHER);
		assertThat(classOtherPayment.get("account_id")).isEqualTo(ACCOUNT_CASH);

		Map<String, Object> classVoidedPayment = movedPayment(moved, "CLASS", classVoided);
		assertThat(classVoidedPayment.get("status")).isEqualTo("VOIDED");
		assertThat(classVoidedPayment.get("void_reason")).isEqualTo("Wrong amount");
		assertThat(entry(moved, classVoidedPayment.get("paired_entry_id")).get("status")).isEqualTo("VOIDED");

		// Totals: the active payments equal the old approved ones (1500 + 800 + 250 + 500 + 300), and every balance is zero.
		assertThat(money(moved.queryForObject("select sum(amount) from acc_entries where group_id = ? and kind = 'PAYMENT'"
				+ " and status = 'ACTIVE'", BigDecimal.class, GROUP))).isEqualTo("3350.00");
		assertThat(balance(moved, rahim)).isEqualByComparingTo("0");
		assertThat(balance(moved, karim)).isEqualByComparingTo("0");
	}

	@Test
	void theMoveRefusesToRunWhileAnOldPaymentIsStillPending() {
		String schema = newSchema();
		migrate(schema, "107");
		JdbcTemplate old = db(schema);
		UUID admin = seedUser(old);
		UUID rahim = seedInstructor(old, admin, "Rahim", false);
		UUID batch = UUID.randomUUID();
		seedBatch(old, admin, batch);

		// A pending hand payment.
		UUID pendingOther = seedOtherPayment(old, admin, rahim, null, "OTHER", null, "Pending", "100.00", "CASH",
				LocalDate.parse("2026-10-20"), "PENDING", null, Instant.parse("2026-10-20T04:00:00Z"), null);
		assertThrows(FlywayException.class, () -> migrate(schema, "latest"));
		old.update("UPDATE offline_instructor_other_payments SET status = 'REJECTED' WHERE id = ?", pendingOther);

		// A pending class payment.
		UUID cls = seedClass(old, batch, "OTHER", "Pending class", LocalDate.parse("2026-10-21"), rahim);
		UUID pendingClass = seedClassPayment(old, admin, cls, rahim, "100.00", "CASH", LocalDate.parse("2026-10-21"),
				"PENDING", null, Instant.parse("2026-10-21T04:00:00Z"));
		assertThrows(FlywayException.class, () -> migrate(schema, "latest"));
		old.update("UPDATE offline_class_payments SET status = 'REJECTED' WHERE id = ?", pendingClass);

		// A pending correction of an other payment, made by a System Admin and waiting for the Master Admin.
		old.update("INSERT INTO payment_edits (id, kind, payment_id, status, before_values, after_values, reason, requested_by,"
				+ " requested_at) VALUES (?, 'OTHER', ?, 'PENDING', '{}'::jsonb, '{}'::jsonb, 'Fix', ?, now())",
				UUID.randomUUID(), UUID.randomUUID(), admin);
		assertThrows(FlywayException.class, () -> migrate(schema, "latest"));
		old.update("UPDATE payment_edits SET status = 'REJECTED' WHERE status = 'PENDING'");

		// With nothing pending, the move runs and moves nothing (every old row was rejected).
		migrate(schema, "latest");
		assertThat(db(schema).queryForObject("select count(*) from acc_entries", Integer.class)).isZero();
	}

	private String newSchema() {
		String schema = "acc_move_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		schemas.add(schema);
		return schema;
	}

	/** Migrates a scratch schema to the given target ("107" for the old tables, "latest" for everything). */
	private void migrate(String schema, String target) {
		Flyway.configure()
				.dataSource(url, user, password)
				.schemas(schema)
				.createSchemas(true)
				.locations("classpath:db/migration")
				.target(target)
				.load()
				.migrate();
	}

	/** A JDBC template whose connections use the scratch schema first. */
	private JdbcTemplate db(String schema) {
		String withSchema = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
		return new JdbcTemplate(new DriverManagerDataSource(withSchema, user, password));
	}

	private BigDecimal balance(JdbcTemplate moved, UUID payee) {
		return moved.queryForObject("select coalesce(sum(case kind when 'CHARGE' then amount when 'PAYMENT' then -amount end), 0)"
				+ " from acc_entries where payee_id = ? and status = 'ACTIVE'", BigDecimal.class, payee);
	}

	private Map<String, Object> movedPayment(JdbcTemplate moved, String source, UUID oldId) {
		return moved.queryForMap("select * from acc_entries where kind = 'PAYMENT' and legacy_source = ? and legacy_id = ?",
				source, oldId);
	}

	private Map<String, Object> entry(JdbcTemplate moved, Object id) {
		return moved.queryForMap("select * from acc_entries where id = ?", id);
	}

	private UUID seedUser(JdbcTemplate old) {
		UUID id = UUID.randomUUID();
		old.update("INSERT INTO users (id, phone, email, role, status, created_at, updated_at)"
				+ " VALUES (?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())",
				id, "+8801" + ThreadLocalRandom.current().nextInt(100000000, 1000000000), "move-" + id + "@example.com");
		return id;
	}

	private void seedBatch(JdbcTemplate old, UUID admin, UUID batch) {
		old.update("INSERT INTO offline_batches (id, name_en, name_bn, ends_on, default_monthly_fee, course_fee,"
				+ " default_installments, status, created_by, created_at, updated_at)"
				+ " VALUES (?, ?, ?, DATE '2026-12-31', 0, 0, 1, 'ACTIVE', ?, now(), now())",
				batch, "Batch " + UUID.randomUUID(), "Batch", admin);
	}

	private UUID seedInstructor(JdbcTemplate old, UUID admin, String name, boolean archived) {
		UUID id = UUID.randomUUID();
		old.update("INSERT INTO offline_instructors (id, name, phone, note, archived_at, created_by, created_at, updated_at)"
				+ " VALUES (?, ?, '01700000002', 'A note', ?, ?, now(), now())", id, name + " " + UUID.randomUUID(),
				archived ? Timestamp.from(Instant.now()) : null, admin);
		return id;
	}

	private UUID seedClass(JdbcTemplate old, UUID batch, String kind, String subject, LocalDate heldOn, UUID instructor) {
		UUID id = UUID.randomUUID();
		old.update("INSERT INTO offline_classes (id, batch_id, held_on, subject, kind, instructor_id, status, created_at, updated_at)"
				+ " VALUES (?, ?, ?, ?, ?, ?, 'PAID', now(), now())", id, batch, heldOn, subject, kind, instructor);
		return id;
	}

	private UUID seedClassPayment(JdbcTemplate old, UUID admin, UUID classId, UUID instructor, String amount, String method,
			LocalDate paidOn, String status, String voidReason, Instant recordedAt) {
		UUID id = UUID.randomUUID();
		boolean voided = "VOIDED".equals(status);
		old.update("INSERT INTO offline_class_payments (id, class_id, instructor_id, amount, method, reference, paid_on, note, status,"
				+ " recorded_by, recorded_at, voided_by, voided_at, void_reason)"
				+ " VALUES (?, ?, ?, CAST(? AS NUMERIC), ?, NULL, ?, NULL, ?, ?, ?, ?, ?, ?)",
				id, classId, instructor, amount, method, paidOn, status, admin, Timestamp.from(recordedAt),
				voided ? admin : null, voided ? Timestamp.from(recordedAt) : null, voidReason);
		return id;
	}

	private UUID seedOtherPayment(JdbcTemplate old, UUID admin, UUID instructor, UUID batch, String kind, Integer classCount,
			String reason, String amount, String method, LocalDate paidOn, String status, String voidReason, Instant recordedAt,
			String reference) {
		UUID id = UUID.randomUUID();
		boolean voided = "VOIDED".equals(status);
		old.update("INSERT INTO offline_instructor_other_payments (id, instructor_id, batch_id, kind, class_count, reason, amount,"
				+ " method, reference, paid_on, note, status, recorded_by, recorded_at, voided_by, voided_at, void_reason)"
				+ " VALUES (?, ?, ?, ?, ?, ?, CAST(? AS NUMERIC), ?, ?, ?, 'A note', ?, ?, ?, ?, ?, ?)",
				id, instructor, batch, kind, classCount, reason, amount, method, reference, paidOn, status, admin,
				Timestamp.from(recordedAt), voided ? admin : null, voided ? Timestamp.from(recordedAt) : null, voidReason);
		return id;
	}

	private static String money(Object value) {
		return ((BigDecimal) value).setScale(2).toPlainString();
	}

	private static Instant instant(Object value) {
		return ((Timestamp) value).toInstant();
	}

}
