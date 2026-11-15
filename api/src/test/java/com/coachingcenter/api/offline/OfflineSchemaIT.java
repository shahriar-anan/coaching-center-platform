package com.coachingcenter.api.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coachingcenter.api.PostgresIntegrationTest;

@SpringBootTest
class OfflineSchemaIT extends PostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private OfflineBatchRepository batches;

	@Autowired
	private OfflineAdmissionRepository admissions;

	@Autowired
	private OfflineAdmissionItemRepository items;

	@Autowired
	private OfflineFeeDueRepository dues;

	@Autowired
	private OfflineFeePaymentRepository payments;

	@Test
	void theSevenOfflineTablesExist() {
		Integer count = jdbc.queryForObject("""
				select count(*) from information_schema.tables
				where table_schema = current_schema() and table_name in (
				  'offline_batches', 'offline_admissions', 'offline_admission_submissions', 'offline_admission_items',
				  'offline_fee_dues', 'offline_fee_payments', 'offline_fee_allocations')
				""", Integer.class);
		assertEquals(7, count);
	}

	@Test
	void batchAdmissionItemsDueAndPaymentAreStored() {
		UUID creator = seedUser("MASTER_ADMIN");
		UUID student = seedUser("STUDENT");
		UUID category = seedCategory();
		UUID examA = seedExam(category);
		UUID examB = seedExam(category);

		OfflineBatch batch = newBatch(creator, "Batch A");
		batches.saveAndFlush(batch);

		OfflineAdmission admission = newAdmission(student, creator, OfflineAdmission.AWAITING_FORM);
		admission.setBatchId(batch.getId());
		admissions.saveAndFlush(admission);

		items.saveAndFlush(examItem(admission.getId(), examA, creator));
		items.saveAndFlush(examItem(admission.getId(), examB, creator));

		OfflineFeeDue due = new OfflineFeeDue();
		due.setId(UUID.randomUUID());
		due.setAdmissionId(admission.getId());
		due.setSeq((short) 1);
		due.setDueOn(LocalDate.now().plusMonths(1));
		due.setAmount(new BigDecimal("1500.00"));
		due.setCreatedAt(Instant.now());
		dues.saveAndFlush(due);

		OfflineFeePayment payment = new OfflineFeePayment();
		payment.setId(UUID.randomUUID());
		payment.setAdmissionId(admission.getId());
		payment.setAmount(new BigDecimal("500.00"));
		payment.setMethod(OfflineFeePayment.METHOD_CASH);
		payment.setReference("CASH-1");
		payment.setPaidOn(LocalDate.now());
		payment.setStatus(OfflineFeePayment.STATUS_PENDING);
		payment.setRecordedBy(creator);
		payment.setRecordedAt(Instant.now());
		payments.saveAndFlush(payment);

		assertEquals(2, items.findByAdmissionIdAndRemovedAtIsNull(admission.getId()).size());
		assertEquals(1, dues.findByAdmissionIdAndCancelledAtIsNullOrderBySeqAsc(admission.getId()).size());
		assertEquals(1, payments.findByAdmissionIdOrderByRecordedAtAsc(admission.getId()).size());
		OfflineAdmission open = admissions.findFirstByStudentIdAndStatusNotOrderByCreatedAtDesc(student, OfflineAdmission.CLOSED).orElseThrow();
		assertEquals(OfflineAdmission.AWAITING_FORM, open.getStatus());
	}

	@Test
	void unknownStatusIsRejectedByAConstraint() {
		UUID creator = seedUser("MASTER_ADMIN");
		OfflineBatch batch = newBatch(creator, "Batch B");
		batch.setStatus("FOO");
		assertThrows(DataIntegrityViolationException.class, () -> batches.saveAndFlush(batch));

		UUID student = seedUser("STUDENT");
		OfflineAdmission admission = newAdmission(student, creator, "FOO");
		assertThrows(DataIntegrityViolationException.class, () -> admissions.saveAndFlush(admission));
	}

	@Test
	void onlyOneOpenAdmissionPerStudent() {
		UUID creator = seedUser("MASTER_ADMIN");
		UUID student = seedUser("STUDENT");

		OfflineAdmission first = admissions.saveAndFlush(newAdmission(student, creator, OfflineAdmission.AWAITING_FORM));
		assertThrows(DataIntegrityViolationException.class,
				() -> admissions.saveAndFlush(newAdmission(student, creator, OfflineAdmission.SUBMITTED)));

		first.setStatus(OfflineAdmission.CLOSED);
		first.setClosedAt(Instant.now());
		first.setClosedReason("Test close");
		admissions.saveAndFlush(first);

		OfflineAdmission again = admissions.saveAndFlush(newAdmission(student, creator, OfflineAdmission.AWAITING_FORM));
		assertNotNull(again.getId());
	}

	private UUID seedUser(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(100000000, 1000000000),
				"offline-" + id + "@example.com", role);
		return id;
	}

	private UUID seedCategory() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", id);
		return id;
	}

	private UUID seedExam(UUID category) {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		jdbc.update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, pass_percent, answers_changeable, is_free, access_days, published_at, created_at,"
				+ " updated_at) values (?, null, ?, 'MCQ', 'Exam', 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', 'PUBLISHED',"
				+ " true, 50, true, false, null, ?, now(), now())", id, category,
				Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now.plusSeconds(7200)), Timestamp.from(now));
		return id;
	}

	private OfflineBatch newBatch(UUID creator, String name) {
		Instant now = Instant.now();
		OfflineBatch batch = new OfflineBatch();
		batch.setId(UUID.randomUUID());
		batch.setNameEn(name);
		batch.setNameBn("ব্যাচ ১");
		batch.setEndsOn(LocalDate.now().plusMonths(6));
		batch.setDefaultMonthlyFee(new BigDecimal("1500.00"));
		batch.setCourseFee(new BigDecimal("9000.00"));
		batch.setDefaultInstallments(6);
		batch.setStatus(OfflineBatch.STATUS_ACTIVE);
		batch.setCreatedBy(creator);
		batch.setCreatedAt(now);
		batch.setUpdatedAt(now);
		return batch;
	}

	private OfflineAdmission newAdmission(UUID student, UUID creator, String status) {
		OfflineAdmission admission = new OfflineAdmission();
		admission.setId(UUID.randomUUID());
		admission.setStudentId(student);
		admission.setStatus(status);
		admission.setCreatedBy(creator);
		admission.setCreatedAt(Instant.now());
		admission.setSubmitCount(0);
		return admission;
	}

	private OfflineAdmissionItem examItem(UUID admission, UUID exam, UUID addedBy) {
		OfflineAdmissionItem item = new OfflineAdmissionItem();
		item.setId(UUID.randomUUID());
		item.setAdmissionId(admission);
		item.setScope(OfflineAdmissionItem.SCOPE_EXAM);
		item.setExamId(exam);
		item.setAddedBy(addedBy);
		item.setAddedAt(Instant.now());
		return item;
	}

}
