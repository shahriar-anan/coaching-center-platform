package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveGrantAdminIT.ClockConfig.class })
class LiveGrantAdminIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	@Test
	void masterAndSystemAdminGrantAModuleOrAnExamByHandAndTheRowIsAudited() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Granted Student");
		UUID module = module(system, "By hand");
		UUID exam = exam(system, module);

		MvcResult moduleGrant = mockMvc.perform(post("/api/v1/live-exam-grants")
			.header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Paid by cash at the office")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.source").value("MANUAL"))
			.andExpect(jsonPath("$.moduleId").value(module.toString()))
			.andExpect(jsonPath("$.examId").doesNotExist())
			.andExpect(jsonPath("$.reason").value("Paid by cash at the office"))
			.andExpect(jsonPath("$.grantedBy").isNotEmpty())
			.andReturn();
		UUID grant = UUID.fromString(json.readTree(moduleGrant.getResponse().getContentAsString()).get("id").asText());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select source from live_exam_grants where id = ?", String.class, grant)).isEqualTo("MANUAL");
		assertThat(jdbc.queryForObject("select order_id from live_exam_grants where id = ?", UUID.class, grant)).isNull();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ACCESS_GRANTED' and entity_id = ?",
				Integer.class, grant)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "EXAM", null, exam, "Retake for a sick day")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scope").value("EXAM"))
			.andExpect(jsonPath("$.examId").value(exam.toString()))
			.andExpect(jsonPath("$.moduleId").value(module.toString()));

		mockMvc.perform(get("/api/v1/students/" + student + "/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content[0].via").value("MANUAL"))
			.andExpect(jsonPath("$.content[1].via").value("MANUAL"));
	}

	@Test
	void instructorsAndStudentsCannotGrant() throws Exception {
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Refused Student");
		UUID module = module(system, "Refused");
		String instructor = token(seed("INSTRUCTOR", "Teacher"));
		String learner = token(student);
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + instructor)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Trying")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + learner)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Trying")))
			.andExpect(status().isForbidden());
	}

	@Test
	void aSecondActiveGrantOnTheSameTargetIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID student = seed("STUDENT", "Twice Student");
		UUID module = module(master, "Twice");
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "First")))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Second")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GRANT_ALREADY_ACTIVE"));
	}

	@Test
	void unknownStudentsAndTargetsAreNotFoundAndBadInputIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID student = seed("STUDENT", "Missing Targets");
		UUID teacher = seed("INSTRUCTOR", "Not A Student");
		UUID module = module(master, "Missing targets");
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(UUID.randomUUID(), "MODULE", module, null, "Unknown student")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(teacher, "MODULE", module, null, "Not a student")))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", UUID.randomUUID(), null, "Unknown module")))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "ALL", module, null, "Bad scope")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "   ")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "a".repeat(501))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aRevokedGrantStopsAccessAndANewGrantRestoresIt() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Revoked Student");
		String learner = token(student);
		UUID module = module(system, "Revoke");
		publish(system, module);
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isNotFound());

		MvcResult first = mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "First grant")))
			.andExpect(status().isCreated())
			.andReturn();
		UUID grant = UUID.fromString(json.readTree(first.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).param("reason", "Entered by mistake")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revokedAt").isNotEmpty())
			.andExpect(jsonPath("$.revokedBy").isNotEmpty())
			.andExpect(jsonPath("$.revokeReason").value("Entered by mistake"));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/students/" + student + "/live-exam-access").header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.totalElements").value(0));
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ACCESS_REVOKED' and entity_id = ?",
				Integer.class, grant)).isEqualTo(1);

		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).param("reason", "Again")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grant).param("reason", "Trying")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(student, "MODULE", module, null, "Granted again")))
			.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isOk());
	}

	@Test
	void masterAdminConfirmsOrRejectsOnlyPendingOrders() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Master");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID buyer = seed("STUDENT", "Stuck Buyer");
		UUID other = seed("STUDENT", "Second Buyer");
		String learner = token(buyer);
		UUID module = module(system, "Stuck");
		publish(system, module);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID confirmed = pendingOrder(jdbc, buyer, module);
		UUID rejected = pendingOrder(jdbc, other, module);

		mockMvc.perform(post("/api/v1/live-exam-orders/" + confirmed + "/confirm").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Checked\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/live-exam-orders/" + confirmed + "/confirm").header("Authorization", "Bearer " + learner)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Checked\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/live-exam-orders/" + confirmed + "/confirm").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/live-exam-orders/" + confirmed + "/confirm").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Checked at SSLCommerz\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUCCEEDED"));
		assertThat(jdbc.queryForObject("select status from live_exam_orders where id = ?", String.class, confirmed))
			.isEqualTo("SUCCEEDED");
		assertThat(jdbc.queryForObject("select payment_channel from live_exam_orders where id = ?", String.class, confirmed))
			.isEqualTo("MANUAL-CONFIRMED");
		assertThat(jdbc.queryForObject("select confirmed_by from live_exam_orders where id = ?", UUID.class, confirmed))
			.isEqualTo(masterId);
		assertThat(jdbc.queryForObject("select source from live_exam_grants where order_id = ?", String.class, confirmed))
			.isEqualTo("ORDER");
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + learner))
			.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ORDER_CONFIRMED' and entity_id = ?",
				Integer.class, confirmed)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/live-exam-orders/" + confirmed + "/confirm").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Again\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));
		mockMvc.perform(post("/api/v1/live-exam-orders/" + confirmed + "/reject").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Too late\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ORDER_NOT_PENDING"));

		mockMvc.perform(post("/api/v1/live-exam-orders/" + rejected + "/reject").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No payment found\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"));
		assertThat(jdbc.queryForObject("select rejected_reason from live_exam_orders where id = ?", String.class, rejected))
			.isEqualTo("No payment found");
		assertThat(jdbc.queryForObject("select count(*) from live_exam_grants where order_id = ?", Integer.class, rejected))
			.isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ORDER_REJECTED' and entity_id = ?",
				Integer.class, rejected)).isEqualTo(1);

		mockMvc.perform(post("/api/v1/live-exam-orders/" + UUID.randomUUID() + "/confirm").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing\"}"))
			.andExpect(status().isNotFound());
	}

	private UUID pendingOrder(JdbcTemplate jdbc, UUID student, UUID module) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id,
				  status, created_at, updated_at)
				VALUES (?, ?, 'MODULE', ?, NULL, 500.00, 'BDT', 'SSLCOMMERZ', ?, 'INITIATED', now(), now())
				""", id, student, module, "P" + id.toString().replace("-", "").substring(0, 20));
		return id;
	}

	@Test
	void masterAdminAndSystemAdminReleaseOrWithholdTheMcqAnswers() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Master");
		String master = token(masterId);
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		String instructor = token(seed("INSTRUCTOR", "Teacher"));
		String learner = token(seed("STUDENT", "Reader"));
		UUID module = module(system, "Answers");
		UUID exam = exam(system, module);
		String path = "/api/v1/live-exams/" + exam + "/answers-released";
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		mockMvc.perform(put(path).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.released").value(true))
			.andExpect(jsonPath("$.releasedAt").isNotEmpty());
		assertThat(jdbc.queryForObject("select answers_released_by from live_exams where id = ?", UUID.class, exam))
			.isEqualTo(masterId);
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ANSWERS_RELEASED' and entity_id = ?",
				Integer.class, exam)).isEqualTo(1);
		mockMvc.perform(put(path).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":true}"))
			.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ANSWERS_RELEASED' and entity_id = ?",
				Integer.class, exam)).isEqualTo(1);

		mockMvc.perform(put(path).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.released").value(false))
			.andExpect(jsonPath("$.releasedAt").doesNotExist());
		assertThat(jdbc.queryForObject("select count(*) from live_exams where id = ? and answers_released_at is null", Integer.class, exam)).isOne();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ANSWERS_WITHHELD' and entity_id = ?",
				Integer.class, exam)).isEqualTo(1);
		mockMvc.perform(put(path).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.released").value(true));

		mockMvc.perform(put(path).header("Authorization", "Bearer " + instructor)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":false}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(put(path).header("Authorization", "Bearer " + learner)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":false}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(put(path).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/live-exams/" + UUID.randomUUID() + "/answers-released")
			.header("Authorization", "Bearer " + master).contentType(MediaType.APPLICATION_JSON).content("{\"released\":true}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void aStudentWithPaidOrGrantedAccessCannotBeDeleted() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = module(system, "Deletion");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		UUID paid = seed("STUDENT", "Paid Student");
		jdbc.update("""
				INSERT INTO live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id,
				  status, created_at, updated_at)
				VALUES (?, ?, 'MODULE', ?, NULL, 500.00, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now())
				""", UUID.randomUUID(), paid, module, "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
		mockMvc.perform(delete("/api/v1/students/" + paid).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCOUNT_HAS_PAID_ORDERS"));

		UUID granted = seed("STUDENT", "Granted Student");
		MvcResult grant = mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(granted, "MODULE", module, null, "Trial access")))
			.andExpect(status().isCreated())
			.andReturn();
		UUID grantId = UUID.fromString(json.readTree(grant.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(delete("/api/v1/students/" + granted).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCOUNT_HAS_PAID_ORDERS"));
		mockMvc.perform(delete("/api/v1/live-exam-grants/" + grantId).param("reason", "Trial over")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/students/" + granted).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict());

		UUID plain = seed("STUDENT", "Plain Student");
		mockMvc.perform(delete("/api/v1/students/" + plain).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/students/" + plain).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from users where id = ? and deleted_at is not null", Integer.class, plain))
			.isOne();
	}

	@Test
	void masterAdminSuspendsAndDeletesAnInstructorUnlessTheyMarkedWork() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID teacher = seed("INSTRUCTOR", "Suspended Teacher");
		UUID marker = seed("INSTRUCTOR", "Marking Teacher");
		UUID student = seed("STUDENT", "Marked Student");
		UUID module = module(system, "Instructor");
		UUID exam = exam(system, module);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("""
				INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, status,
				  evaluated_by, created_at, updated_at)
				VALUES (?, ?, ?, 'LIVE', now(), now(), 'SUBMITTED', ?, now(), now())
				""", UUID.randomUUID(), exam, student, marker);

		mockMvc.perform(patch("/api/v1/instructors/" + teacher + "/status").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/instructors/" + teacher + "/status").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, teacher)).isEqualTo("INACTIVE");
		mockMvc.perform(patch("/api/v1/instructors/" + teacher + "/status").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/v1/instructors/" + marker).header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCOUNT_HAS_MARKED_WORK"));
		mockMvc.perform(delete("/api/v1/instructors/" + teacher).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/instructors/" + teacher).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from users where id = ? and deleted_at is not null", Integer.class, teacher))
			.isOne();
	}

	@Test
	void masterAdminSendsAnnouncementsToAModuleOrToEveryStudentOnce() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID rostered = seed("STUDENT", "Rostered Reader");
		UUID granted = seed("STUDENT", "Granted Reader");
		UUID outside = seed("STUDENT", "Outside Reader");
		UUID module = module(system, "Announced");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":\"" + rostered + "\"}"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(granted, "MODULE", module, null, "Granted for the announcement test")))
			.andExpect(status().isCreated());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		MvcResult moduleSent = mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("Module news", "মডিউলের খবর", "Read this", "", "MODULE", module)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.audience").value("MODULE"))
			.andExpect(jsonPath("$.recipients").value(2))
			.andReturn();
		UUID moduleAnnouncement = UUID.fromString(json.readTree(moduleSent.getResponse().getContentAsString()).get("id").asText());
		assertThat(jdbc.queryForObject("select count(*) from student_notices where announcement_id = ? and kind = 'ANNOUNCEMENT'"
				+ " and exam_id is null and module_id = ? and user_id in (?, ?)", Integer.class, moduleAnnouncement, module, rostered,
				granted)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from student_notices where announcement_id = ? and user_id = ?",
				Integer.class, moduleAnnouncement, outside)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ANNOUNCEMENT_SENT' and entity_id = ?",
				Integer.class, moduleAnnouncement)).isEqualTo(1);

		int active = jdbc.queryForObject("select count(*) from users where role = 'STUDENT' and status = 'ACTIVE'"
				+ " and deleted_at is null", Integer.class);
		MvcResult allSent = mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("School closed", "", "", "স্কুল বন্ধ", "ALL", null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.audience").value("ALL"))
			.andExpect(jsonPath("$.recipients").value(active))
			.andReturn();
		UUID allAnnouncement = UUID.fromString(json.readTree(allSent.getResponse().getContentAsString()).get("id").asText());
		assertThat(jdbc.queryForObject("select count(*) from student_notices where announcement_id = ? and module_id is null",
				Integer.class, allAnnouncement)).isEqualTo(active);
		assertThat(jdbc.queryForObject("select count(distinct user_id) from student_notices where announcement_id = ?",
				Integer.class, allAnnouncement)).isEqualTo(active);
	}

	@Test
	void announcementsAreMasterAdminOnlyAndBadInputIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		String teacher = token(seed("INSTRUCTOR", "Teacher"));
		String learner = token(seed("STUDENT", "Listener"));
		UUID module = module(system, "Refused news");
		String allJson = announcementJson("Hello", "", "", "", "ALL", null);
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON).content(allJson)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + teacher)
			.contentType(MediaType.APPLICATION_JSON).content(allJson)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + learner)
			.contentType(MediaType.APPLICATION_JSON).content(allJson)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("", " ", "", "", "ALL", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("Hello", "", "", "", "MODULE", null)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("Hello", "", "", "", "ALL", module)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("Hello", "", "", "", "ROSTER", null)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("Hello", "", "", "", "MODULE", UUID.randomUUID())))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("a".repeat(501), "", "", "", "ALL", null)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(announcementJson("", "", "", "b".repeat(5001), "ALL", null)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void announcementHistoryIsMasterAdminOnlyNewestFirstWithoutBodiesAndCountsRecipients() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		String teacher = token(seed("INSTRUCTOR", "Teacher"));
		String learner = token(seed("STUDENT", "Listener"));
		UUID empty = module(system, "No readers yet");
		UUID rostered = module(system, "One reader");
		UUID reader = seed("STUDENT", "History Reader");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + rostered + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":\"" + reader + "\"}"))
			.andExpect(status().isCreated());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		// Each send is dated a minute apart, so the newest-first order is fixed. The dates start at the real time now,
		// which is later than every other test's clock and than earlier runs against a kept database. The clock goes
		// back to its start afterwards.
		Instant start = clock.instant();
		Instant now = Instant.now();
		UUID emptyAnnouncement;
		UUID rosteredAnnouncement;
		UUID allAnnouncement;
		try {
			clock.set(now);
			emptyAnnouncement = sendAnnouncement(master,
					announcementJson("Nobody yet", "", "Hidden body", "", "MODULE", empty));
			clock.set(now.plusSeconds(60));
			rosteredAnnouncement = sendAnnouncement(master,
					announcementJson("Module news", "মডিউলের খবর", "Read this", "", "MODULE", rostered));
			clock.set(now.plusSeconds(120));
			allAnnouncement = sendAnnouncement(master,
					announcementJson("School closed", "", "", "স্কুল বন্ধ", "ALL", null));
		}
		finally {
			clock.set(start);
		}
		int allRecipients = jdbc.queryForObject("select count(*) from student_notices where announcement_id = ?", Integer.class,
				allAnnouncement);
		int total = jdbc.queryForObject("select count(*) from announcements", Integer.class);

		mockMvc.perform(get("/api/v1/announcements").param("size", "3").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(3))
			.andExpect(jsonPath("$.totalElements").value(total))
			.andExpect(jsonPath("$.content[0].id").value(allAnnouncement.toString()))
			.andExpect(jsonPath("$.content[0].audience").value("ALL"))
			.andExpect(jsonPath("$.content[0].titleEn").value("School closed"))
			.andExpect(jsonPath("$.content[0].moduleId").doesNotExist())
			.andExpect(jsonPath("$.content[0].moduleTitleEn").doesNotExist())
			.andExpect(jsonPath("$.content[0].recipients").value(allRecipients))
			.andExpect(jsonPath("$.content[0].bodyEn").doesNotExist())
			.andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.content[1].id").value(rosteredAnnouncement.toString()))
			.andExpect(jsonPath("$.content[1].audience").value("MODULE"))
			.andExpect(jsonPath("$.content[1].moduleId").value(rostered.toString()))
			.andExpect(jsonPath("$.content[1].moduleTitleEn").value("One reader"))
			.andExpect(jsonPath("$.content[1].moduleTitleBn").value("মডিউল"))
			.andExpect(jsonPath("$.content[1].recipients").value(1))
			.andExpect(jsonPath("$.content[2].id").value(emptyAnnouncement.toString()))
			.andExpect(jsonPath("$.content[2].recipients").value(0))
			.andExpect(jsonPath("$.content[2].moduleTitleEn").value("No readers yet"));
		mockMvc.perform(get("/api/v1/announcements").param("page", "1").param("size", "1")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(rosteredAnnouncement.toString()));

		mockMvc.perform(get("/api/v1/announcements").header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/announcements").header("Authorization", "Bearer " + teacher))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/announcements").header("Authorization", "Bearer " + learner))
			.andExpect(status().isForbidden());
	}

	private UUID sendAnnouncement(String master, String payload) throws Exception {
		MvcResult sent = mockMvc.perform(post("/api/v1/announcements").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(payload))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(sent.getResponse().getContentAsString()).get("id").asText());
	}

	private static String announcementJson(String titleEn, String titleBn, String bodyEn, String bodyBn, String audience,
			UUID module) {
		return """
				{"titleEn":"%s","titleBn":"%s","bodyEn":"%s","bodyBn":"%s","audience":"%s","moduleId":%s}
				""".formatted(titleEn, titleBn, bodyEn, bodyBn, audience, quoted(module));
	}

	private void publish(String token, UUID module) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
	}

	private UUID module(String token, String title) throws Exception {
		MvcResult category = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s cat","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID categoryId = UUID.fromString(json.readTree(category.getResponse().getContentAsString()).get("id").asText());
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID exam(String token, UUID module) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "examType":"MCQ","paperLanguage":"BN","titleEn":"Grant quiz","titleBn":"কুইজ",
					  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
					  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private static String body(UUID student, String scope, UUID module, UUID exam, String reason) {
		return """
				{"studentId":"%s","scope":"%s","moduleId":%s,"examId":%s,"reason":"%s"}
				""".formatted(student, scope, quoted(module), quoted(exam), reason);
	}

	private static String quoted(UUID id) {
		return id == null ? "null" : "\"" + id + "\"";
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "grant-" + id + "@example.com", passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"G" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class, userId);
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
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
