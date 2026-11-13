package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The student's own My admission reads (plan 5 and 6.4): the view after onboarding, a closed admission kept as history, the live
 * exams and packages, and the routine of the student's own batch. The clock is fixed at 10:00 in Dhaka on 20 October 2026.
 *
 * The helpers are copied from {@code OfflineAdmissionFormIT} and {@code AccessExpiryIT} rather than inherited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineMyAdmissionIT.ClockConfig.class })
class OfflineMyAdmissionIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant EARLIER = Instant.parse("2026-10-01T00:00:00Z");

	private static final Instant COURSE_CAP = Instant.parse("2027-03-31T17:59:59Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	/** The Master Admin who creates this test's batches and uploads routines. JUnit makes a new instance per test method. */
	private UUID master;

	/** The staff user who created this test's admission rows. */
	private UUID deskStaff;

	@Test
	void theViewCarriesTheBatchFeeAndDatesAfterOnboarding() throws Exception {
		UUID student = seed("STUDENT", "Onboarded Student");
		UUID batch = batch("Evening Batch A", "সন্ধ্যা ব্যাচ এ");
		admission(student, OfflineAdmission.ONBOARDED, batch, "2500", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);

		JsonNode view = view(token(student));

		assertThat(view.get("state").asText()).isEqualTo("ONBOARDED");
		assertThat(view.get("batchNameEn").asText()).isEqualTo("Evening Batch A");
		assertThat(view.get("batchNameBn").asText()).isEqualTo("সন্ধ্যা ব্যাচ এ");
		assertThat(view.get("monthlyFee").decimalValue()).isEqualByComparingTo("2500");
		assertThat(view.get("admittedOn").asText()).isEqualTo("2026-10-01");
		assertThat(view.get("courseEndsOn").asText()).isEqualTo("2027-03-31");
		assertThat(view.get("hasRoutine").asBoolean()).isFalse();
	}

	@Test
	void theBatchFeeAndDatesStayHiddenBeforeOnboarding() throws Exception {
		UUID student = seed("STUDENT", "Pending Setup Student");
		UUID batch = batch("Morning Batch", "সকালের ব্যাচ");
		admission(student, OfflineAdmission.SUBMITTED, batch, "3000", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);

		JsonNode view = view(token(student));

		assertThat(view.get("state").asText()).isEqualTo("SUBMITTED");
		assertThat(view.hasNonNull("batchNameEn")).isFalse();
		assertThat(view.hasNonNull("monthlyFee")).isFalse();
		assertThat(view.hasNonNull("admittedOn")).isFalse();
		assertThat(view.hasNonNull("courseEndsOn")).isFalse();
		assertThat(view.get("hasRoutine").asBoolean()).isFalse();
	}

	@Test
	void aClosedAdmissionIsReadableAsHistoryAndDoesNotGateTheStudent() throws Exception {
		UUID student = seed("STUDENT", "Closed Student");
		UUID batch = batch("Closed Batch", "বন্ধ ব্যাচ");
		admission(student, OfflineAdmission.CLOSED, batch, "2500", LocalDate.parse("2026-01-01"),
				LocalDate.parse("2026-06-30"), Instant.parse("2025-12-01T00:00:00Z"));
		UUID latest = admission(student, OfflineAdmission.CLOSED, batch, "2500", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);

		JsonNode view = view(token(student));

		assertThat(view.get("id").asText()).isEqualTo(latest.toString());
		assertThat(view.get("state").asText()).isEqualTo("CLOSED");
		assertThat(view.get("batchNameEn").asText()).isEqualTo("Closed Batch");
		assertThat(view.get("courseEndsOn").asText()).isEqualTo("2027-03-31");

		MvcResult take = mockMvc.perform(post("/api/v1/live-exams/" + UUID.randomUUID() + "/take")
			.header("Authorization", "Bearer " + token(student))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andReturn();
		assertThat(take.getResponse().getContentAsString()).doesNotContain("ADMISSION_PENDING");
	}

	@Test
	void theItemsListLiveOfflineAccessWithTitlesAndEnd() throws Exception {
		UUID student = seed("STUDENT", "Items Student");
		UUID batch = batch("Items Batch", "আইটেম ব্যাচ");
		UUID admission = admission(student, OfflineAdmission.ONBOARDED, batch, "2500", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);
		UUID category = category();
		UUID pkg = module(category, "Admission Package", "ভর্তি প্যাকেজ");
		UUID liveExam = exam(category, "Live Exam One", "লাইভ পরীক্ষা এক");
		UUID packageExam = exam(category, "Package Exam", "প্যাকেজ পরীক্ষা");
		UUID expiredExam = exam(category, "Expired Exam", "মেয়াদোত্তীর্ণ পরীক্ষা");
		UUID removedExam = exam(category, "Removed Exam", "বাদ পরীক্ষা");
		UUID removedMember = exam(category, "Left The Package", "প্যাকেজ ছাড়া");
		member(pkg, packageExam, null);
		member(pkg, expiredExam, null);
		member(pkg, removedMember, Instant.parse("2026-10-10T00:00:00Z"));

		UUID examGrant = offlineGrant(student, "EXAM", null, liveExam, COURSE_CAP);
		UUID moduleGrant = offlineGrant(student, "MODULE", pkg, null, null);
		UUID expiredGrant = offlineGrant(student, "EXAM", null, expiredExam, EARLIER);
		UUID removedGrant = offlineGrant(student, "EXAM", null, removedExam, COURSE_CAP);
		item(admission, "EXAM", liveExam, null, examGrant, null);
		item(admission, "MODULE", null, pkg, moduleGrant, null);
		item(admission, "EXAM", expiredExam, null, expiredGrant, null);
		item(admission, "EXAM", removedExam, null, removedGrant, Instant.parse("2026-10-15T00:00:00Z"));

		MvcResult result = mockMvc.perform(get("/api/v1/me/admission/items").header("Authorization", "Bearer " + token(student)))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode items = json.readTree(result.getResponse().getContentAsString());

		assertThat(items).hasSize(2);
		JsonNode exam = find(items, "EXAM");
		assertThat(exam.get("examId").asText()).isEqualTo(liveExam.toString());
		assertThat(exam.hasNonNull("moduleId")).isFalse();
		assertThat(exam.get("titleEn").asText()).isEqualTo("Live Exam One");
		assertThat(exam.get("titleBn").asText()).isEqualTo("লাইভ পরীক্ষা এক");
		assertThat(exam.hasNonNull("packageExamCount")).isFalse();
		assertThat(Instant.parse(exam.get("accessEndsAt").asText())).isEqualTo(COURSE_CAP);

		JsonNode module = find(items, "MODULE");
		assertThat(module.get("moduleId").asText()).isEqualTo(pkg.toString());
		assertThat(module.hasNonNull("examId")).isFalse();
		assertThat(module.get("titleEn").asText()).isEqualTo("Admission Package");
		assertThat(module.get("titleBn").asText()).isEqualTo("ভর্তি প্যাকেজ");
		assertThat(module.get("packageExamCount").asInt()).isEqualTo(2);
		assertThat(module.hasNonNull("accessEndsAt")).isFalse();
	}

	@Test
	void aStudentWithNoAdmissionGetsNotFoundOnTheNewRoutes() throws Exception {
		String student = token(seed("STUDENT", "Plain Student"));

		mockMvc.perform(get("/api/v1/me/admission/items").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/me/admission/batch-routine.pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void theRoutineIsServedOnlyOnceOnboardedAndWhenUploaded() throws Exception {
		UUID batch = batch("Routine Batch", "রুটিন ব্যাচ");
		UUID student = seed("STUDENT", "Routine Student");
		admission(student, OfflineAdmission.ONBOARDED, batch, "2500", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);
		String studentToken = token(student);

		mockMvc.perform(get("/api/v1/me/admission/batch-routine.pdf").header("Authorization", "Bearer " + studentToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

		uploadRoutine(batch, "%PDF-1.4\nroutine with no students yet\n");

		assertThat(view(studentToken).get("hasRoutine").asBoolean()).isTrue();
		assertThat(routineBytes(studentToken)).isEqualTo("%PDF-1.4\nroutine with no students yet\n".getBytes());
	}

	@Test
	void aStudentReadsTheRoutineOfTheirOwnBatchOnly() throws Exception {
		UUID batchA = batch("Batch A", "ব্যাচ এ");
		UUID batchB = batch("Batch B", "ব্যাচ বি");
		UUID studentA = seed("STUDENT", "Student In A");
		UUID studentB = seed("STUDENT", "Student In B");
		admission(studentA, OfflineAdmission.ONBOARDED, batchA, "2500", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);
		admission(studentB, OfflineAdmission.ONBOARDED, batchB, "2700", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);
		String routineA = "%PDF-1.4\nroutine for batch A\n";
		String routineB = "%PDF-1.4\nroutine for batch B\n";
		uploadRoutine(batchA, routineA);
		uploadRoutine(batchB, routineB);

		assertThat(routineBytes(token(studentA))).isEqualTo(routineA.getBytes());
		assertThat(routineBytes(token(studentB))).isEqualTo(routineB.getBytes());

		MvcResult staff = mockMvc.perform(get("/api/v1/offline-batches/" + batchA + "/routine.pdf")
			.header("Authorization", "Bearer " + token(master())))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(staff.getResponse().getContentAsByteArray()).isEqualTo(routineA.getBytes());
	}

	@Test
	void theRoutineResponseIsAPrivatePdfWithTheStaffFileName() throws Exception {
		UUID batch = batch("Header Batch", "হেডার ব্যাচ");
		UUID student = seed("STUDENT", "Header Student");
		admission(student, OfflineAdmission.ONBOARDED, batch, "2500", LocalDate.parse("2026-10-01"),
				LocalDate.parse("2027-03-31"), EARLIER);
		uploadRoutine(batch, "%PDF-1.4\nheaders\n");

		MvcResult result = mockMvc.perform(get("/api/v1/me/admission/batch-routine.pdf")
			.header("Authorization", "Bearer " + token(student)))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_PDF))
			.andReturn();

		assertThat(result.getResponse().getHeader("Cache-Control")).contains("private");
		assertThat(result.getResponse().getHeader("Content-Disposition")).contains("routine.pdf");
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------------

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private UUID master() {
		if (master == null) {
			master = seed("MASTER_ADMIN", "Master");
		}
		return master;
	}

	private UUID deskStaff() {
		if (deskStaff == null) {
			deskStaff = seed("MASTER_ADMIN", "Desk Staff");
		}
		return deskStaff;
	}

	/** A batch with its routine not yet uploaded. The fee and the end date are the batch defaults. */
	private UUID batch(String nameEn, String nameBn) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_batches (id, name_en, name_bn, ends_on, default_monthly_fee, status, created_by, created_at,
				  updated_at) values (?, ?, ?, ?, ?, 'ACTIVE', ?, now(), now())
				""", id, nameEn, nameBn, java.sql.Date.valueOf("2027-03-31"), new BigDecimal("2500"), master());
		return id;
	}

	/** An admission row in one state, as the desk and onboarding would leave it. */
	private UUID admission(UUID studentId, String status, UUID batch, String fee, LocalDate admittedOn, LocalDate courseEndsOn,
			Instant createdAt) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count, batch_id,
				  monthly_fee, admitted_on, course_ends_on, first_due_on) values (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
				""", id, studentId, status, deskStaff(), Timestamp.from(createdAt), batch,
				fee == null ? null : new BigDecimal(fee), admittedOn == null ? null : java.sql.Date.valueOf(admittedOn),
				courseEndsOn == null ? null : java.sql.Date.valueOf(courseEndsOn),
				admittedOn == null ? null : java.sql.Date.valueOf(admittedOn.plusMonths(1)));
		return id;
	}

	private UUID category() {
		UUID id = UUID.randomUUID();
		jdbc().update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", id);
		return id;
	}

	private UUID module(UUID category, String titleEn, String titleBn) {
		UUID id = UUID.randomUUID();
		jdbc().update("insert into live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn,"
				+ " access_mode, status, finally_saved, position, created_at, updated_at)"
				+ " values (?, ?, ?, ?, '', '', 'MANUAL', 'PUBLISHED', true, 0, now(), now())", id, category, titleEn, titleBn);
		return id;
	}

	private UUID exam(UUID category, String titleEn, String titleBn) {
		UUID id = UUID.randomUUID();
		Timestamp live = Timestamp.from(Instant.parse("2026-10-29T04:00:00Z"));
		Timestamp ends = Timestamp.from(Instant.parse("2026-10-29T05:00:00Z"));
		jdbc().update("insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,"
				+ " description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,"
				+ " finally_saved, pass_percent, answers_changeable, is_free, access_days, published_at, created_at,"
				+ " updated_at) values (?, null, ?, 'MCQ', ?, ?, '', '', '', '', 'BN', ?, ?, 30, 'LIVE', 'PUBLISHED',"
				+ " true, 50, true, false, 30, ?, now(), now())", id, category, titleEn, titleBn, live, ends, live);
		return id;
	}

	private void member(UUID module, UUID exam, Instant removedAt) {
		jdbc().update("insert into live_exam_package_exams (id, module_id, exam_id, position, added_at, removed_at)"
				+ " values (?, ?, ?, 0, ?, ?)", UUID.randomUUID(), module, exam, Timestamp.from(EARLIER),
				removedAt == null ? null : Timestamp.from(removedAt));
	}

	/** An OFFLINE grant, for an exam ({@code exam} set) or a package ({@code module} set), with its course-end cap. */
	private UUID offlineGrant(UUID student, String scope, UUID module, UUID exam, Instant capEnd) {
		UUID id = UUID.randomUUID();
		jdbc().update("insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source,"
				+ " access_ends_at) values (?, ?, ?, ?, ?, null, ?, 'OFFLINE', ?)", id, student, scope, module, exam,
				Timestamp.from(EARLIER), capEnd == null ? null : Timestamp.from(capEnd));
		return id;
	}

	/** An admission item linked to its grant. A removed item is one with {@code removedAt} set. */
	private void item(UUID admission, String scope, UUID exam, UUID module, UUID grant, Instant removedAt) {
		jdbc().update("insert into offline_admission_items (id, admission_id, scope, exam_id, module_id, grant_id, added_by,"
				+ " added_at, removed_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), admission, scope, exam, module,
				grant, deskStaff(), Timestamp.from(EARLIER), removedAt == null ? null : Timestamp.from(removedAt));
	}

	private void uploadRoutine(UUID batch, String pdf) throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/offline-batches/" + batch + "/routine-pdf")
			.file(new MockMultipartFile("file", "routine.pdf", "application/pdf", pdf.getBytes()))
			.header("Authorization", "Bearer " + token(master())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routineUploaded").value(true));
	}

	private byte[] routineBytes(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/me/admission/batch-routine.pdf").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getContentAsByteArray();
	}

	private JsonNode view(String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/me/admission").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString());
	}

	/** The one item of the given scope in the list. */
	private static JsonNode find(JsonNode items, String scope) {
		List<JsonNode> matches = new ArrayList<>();
		items.forEach(item -> {
			if (scope.equals(item.get("scope").asText())) {
				matches.add(item);
			}
		});
		assertThat(matches).hasSize(1);
		return matches.get(0);
	}

	/** A Bangladesh mobile in the +880 form, unique enough for one test run. */
	private static String uniquePhone() {
		return "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999);
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "offline-mine-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"M" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

}
