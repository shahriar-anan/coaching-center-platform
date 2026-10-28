package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.common.audit.AuditRecorder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 15 (W2 part, contracts section 5): each staff action on the authoring side writes one audit row
 * with the right action, actor and entity. The package and popular actions are in {@code CatalogAuditIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, AuthoringAuditIT.ClockConfig.class })
class AuthoringAuditIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	private static final String NOON = "2026-10-20T12:00:00+06:00";

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

	@Autowired
	private LiveExamDeadlineJob deadlineJob;

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		clock.set(TEN_AM);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void aRevisionDiscardAndAPublishEachWriteOneRow() throws Exception {
		UUID staffId = fx.seed("SYSTEM_ADMIN");
		String staff = fx.token(staffId);
		UUID masterId = fx.seed("MASTER_ADMIN");
		String master = fx.token(masterId);
		UUID module = fx.module(staff, "Audit revision");
		UUID exam = fx.createExam(staff, module, "MCQ", "Audited", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON).content("{\"titleEn\":\"Dropped\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/pending-changes").header("Authorization", "Bearer " + staff))
			.andExpect(status().isNoContent());
		assertOneRow(AuditRecorder.LIVE_EXAM_REVISION_DISCARD, staffId, AuditRecorder.LIVE_EXAM_ENTITY, exam);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON).content("{\"titleEn\":\"Published\"}"))
			.andExpect(status().isOk());
		JsonNode changes = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/pending-changes")
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk());
		assertOneRow(AuditRecorder.LIVE_EXAM_REVISION_PUBLISH, masterId, AuditRecorder.LIVE_EXAM_ENTITY, exam);
	}

	@Test
	void anInstructorAssignAndUnassignOnAnExamEachWriteOneRow() throws Exception {
		UUID staffId = fx.seed("SYSTEM_ADMIN");
		String staff = fx.token(staffId);
		UUID masterId = fx.seed("MASTER_ADMIN");
		String master = fx.token(masterId);
		UUID module = fx.module(staff, "Audit instructor");
		UUID exam = fx.createExam(staff, module, "MCQ", "Instructed", NOON, 40, null);
		UUID instructor = fx.seed("INSTRUCTOR");

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"instructorId\":\"" + instructor + "\"}"))
			.andExpect(status().isCreated());
		assertOneRow(AuditRecorder.LIVE_EXAM_INSTRUCTOR_ASSIGN, masterId, AuditRecorder.LIVE_EXAM_ENTITY, exam);

		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/instructor").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertOneRow(AuditRecorder.LIVE_EXAM_INSTRUCTOR_UNASSIGN, masterId, AuditRecorder.LIVE_EXAM_ENTITY, exam);
	}

	@Test
	void aWrittenRetakeGrantAndRevokeEachWriteOneRowOnTheStudentsAttempt() throws Exception {
		UUID staffId = fx.seed("SYSTEM_ADMIN");
		String staff = fx.token(staffId);
		UUID masterId = fx.seed("MASTER_ADMIN");
		String master = fx.token(masterId);
		UUID module = fx.module(staff, "Audit retake");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Retake paper", NOON, 40, null);
		fx.uploadPdf(staff, exam);
		fx.publishNew(master, exam);
		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);
		fx.grantModule(module, studentId);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		UUID attempt = fx.jdbc().queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class, exam, studentId);
		// The deadline (06:50) passes with no PDF: the attempt is finished, so a grant is allowed.
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		deadlineJob.settleDue();

		JsonNode grant = json.readTree(mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake-grants")
			.header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + studentId + "\",\"reason\":\"Lost the sheet\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertOneRow(AuditRecorder.LIVE_EXAM_RETAKE_GRANT, masterId, AuditRecorder.LIVE_EXAM_PARTICIPANT_ENTITY, attempt);

		mockMvc.perform(delete("/api/v1/live-exams/" + exam + "/retake-grants/" + grant.get("id").asText())
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertOneRow(AuditRecorder.LIVE_EXAM_RETAKE_REVOKE, masterId, AuditRecorder.LIVE_EXAM_PARTICIPANT_ENTITY, attempt);
	}

	/** Exactly one audit row for the action on this entity, written by this actor under this entity type. */
	private void assertOneRow(String action, UUID actor, String entityType, UUID entity) {
		Integer rows = fx.jdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?",
				Integer.class, action, entity);
		assertThat(rows).as(action + " rows for " + entity).isOne();
		Integer matching = fx.jdbc().queryForObject(
				"select count(*) from audit_log where action = ? and entity_id = ? and actor_user_id = ? and entity_type = ?",
				Integer.class, action, entity, actor, entityType);
		assertThat(matching).as(action + " by " + actor + " on " + entityType).isOne();
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(TEN_AM);
		}

	}

}
