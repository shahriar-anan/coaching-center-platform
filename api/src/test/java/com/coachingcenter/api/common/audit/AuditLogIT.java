package com.coachingcenter.api.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.auth.CapturingEmailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class AuditLogIT extends PostgresIntegrationTest {

	private static final Set<String> API_OPERATIONS = Set.of(
			"POST /api/v1/auth/register",
			"POST /api/v1/auth/activate",
			"POST /api/v1/auth/login",
			"POST /api/v1/auth/refresh",
			"POST /api/v1/auth/logout",
			"POST /api/v1/auth/change-password",
			"POST /api/v1/auth/change-phone",
			"POST /api/v1/auth/forgot-password",
			"POST /api/v1/auth/reset-password",
			"POST /api/v1/auth/verify-email",
			"POST /api/v1/auth/verify-email/resend",
			"GET /api/v1/me",
			"POST /api/v1/instructors",
			"GET /api/v1/instructors",
			"POST /api/v1/instructors/{id}/activation-code",
			"POST /api/v1/admins",
			"GET /api/v1/admins",
			"PATCH /api/v1/admins/{id}/status",
			"DELETE /api/v1/admins/{id}",
			"POST /api/v1/admins/{id}/activation-code",
			"POST /api/v1/students",
			"GET /api/v1/students",
			"GET /api/v1/students/{id}",
			"PATCH /api/v1/students/{id}",
			"PATCH /api/v1/students/{id}/status",
			"POST /api/v1/students/{id}/activation-code",
			"DELETE /api/v1/students/{id}",
			"GET /api/v1/audit-log",
			"POST /api/v1/courses",
			"GET /api/v1/courses",
			"GET /api/v1/courses/{id}",
			"PATCH /api/v1/courses/{id}",
			"DELETE /api/v1/courses/{id}",
			"POST /api/v1/courses/{id}/archive",
			"POST /api/v1/courses/{id}/close",
			"POST /api/v1/courses/{id}/reopen",
			"POST /api/v1/courses/{id}/cover",
			"GET /api/v1/courses/{id}/nodes",
			"POST /api/v1/courses/{id}/nodes",
			"PATCH /api/v1/courses/{id}/nodes/{nodeId}",
			"DELETE /api/v1/courses/{id}/nodes/{nodeId}",
			"POST /api/v1/courses/{id}/nodes/reorder",
			"GET /api/v1/courses/{id}/materials",
			"POST /api/v1/courses/{id}/materials",
			"POST /api/v1/courses/{id}/materials/batch",
			"PUT /api/v1/courses/{id}/materials/{materialId}",
			"PATCH /api/v1/courses/{id}/materials/{materialId}",
			"DELETE /api/v1/courses/{id}/materials/{materialId}",
			"PUT /api/v1/courses/{id}/routine",
			"GET /api/v1/courses/{id}/routine",
			"GET /api/v1/courses/{id}/instructors",
			"POST /api/v1/courses/{id}/instructors",
			"PATCH /api/v1/courses/{id}/instructors/{instructorId}",
			"POST /api/v1/courses/{id}/instructors/{instructorId}/photo",
			"DELETE /api/v1/courses/{id}/instructors/{instructorId}",
			"GET /api/v1/courses/{id}/enrollments",
			"GET /api/v1/course-categories",
			"POST /api/v1/course-categories",
			"PATCH /api/v1/course-categories/{id}",
			"DELETE /api/v1/course-categories/{id}",
			"POST /api/v1/books",
			"GET /api/v1/books",
			"GET /api/v1/books/{id}",
			"PATCH /api/v1/books/{id}",
			"DELETE /api/v1/books/{id}",
			"POST /api/v1/books/{id}/cover",
			"GET /api/v1/book-categories",
			"POST /api/v1/book-categories",
			"PATCH /api/v1/book-categories/{id}",
			"DELETE /api/v1/book-categories/{id}",
			"GET /api/v1/catalog/courses",
			"GET /api/v1/catalog/courses/{id}",
			"GET /api/v1/catalog/courses/{id}/lectures/{lectureId}",
			"GET /api/v1/catalog/courses/{id}/materials/{materialId}",
			"GET /api/v1/catalog/course-categories",
			"GET /api/v1/catalog/books",
			"GET /api/v1/catalog/books/{id}",
			"GET /api/v1/catalog/book-categories",
			"GET /api/v1/me/exam-routine",
			"GET /api/v1/me/courses",
			"POST /api/v1/me/courses/{courseId}/progress",
			"GET /api/v1/me/courses/{courseId}/progress",
			"GET /api/v1/files/{id}",
			"GET /api/v1/catalog/exam-modules",
			"GET /api/v1/catalog/exam-modules/{id}",
			"GET /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}",
			"POST /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts",
			"GET /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts",
			"PUT /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts/answers",
			"POST /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts/submit",
			"POST /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/images",
			"POST /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/images/reorder",
			"DELETE /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/images/{imageId}",
			"POST /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/submit",
			"GET /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission",
			"GET /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/result",
			"GET /api/v1/catalog/exam-modules/{moduleId}/papers/{paperId}/result",
			"GET /api/v1/catalog/exam-modules/{moduleId}/papers/{paperId}/leaderboard",
			"POST /api/v1/exam-modules",
			"PATCH /api/v1/exam-modules/{id}",
			"GET /api/v1/exam-modules",
			"GET /api/v1/exam-modules/{id}",
			"DELETE /api/v1/exam-modules/{id}",
			"POST /api/v1/exam-modules/{id}/archive",
			"PATCH /api/v1/exam-modules/{id}/access",
			"GET /api/v1/exam-modules/{id}/nodes",
			"POST /api/v1/exam-modules/{id}/nodes",
			"PATCH /api/v1/exam-modules/{id}/nodes/{nodeId}",
			"POST /api/v1/exam-modules/{id}/nodes/reorder",
			"DELETE /api/v1/exam-modules/{id}/nodes/{nodeId}",
			"POST /api/v1/exam-modules/{id}/purchases",
			"GET /api/v1/me/exam-module-purchases",
			"POST /api/v1/exam-papers/{paperId}/components",
			"GET /api/v1/exam-components/{componentId}",
			"PATCH /api/v1/exam-components/{componentId}",
			"POST /api/v1/exam-components/{componentId}/schedule",
			"GET /api/v1/exam-papers/{paperId}",
			"PATCH /api/v1/exam-papers/{paperId}",
			"POST /api/v1/exam-components/{componentId}/questions",
			"POST /api/v1/exam-components/{componentId}/questions/{questionId}",
			"PATCH /api/v1/questions/{questionId}",
			"GET /api/v1/questions/{questionId}",
			"POST /api/v1/exam-components/{componentId}/questions/reorder",
			"DELETE /api/v1/exam-components/{componentId}/questions/{questionId}",
			"GET /api/v1/exam-components/{componentId}/attempts",
			"GET /api/v1/exam-components/{componentId}/submissions",
			"POST /api/v1/exam-components/{componentId}/submissions/{submissionId}/evaluation",
			"POST /api/v1/exam-components/{componentId}/results/publish",
			"POST /api/v1/exam-components/{componentId}/results/unpublish",
			"GET /api/v1/exam-components/{componentId}/students/{studentId}/result",
			"GET /api/v1/exam-papers/{paperId}/leaderboard",
			"GET /api/v1/exam-components/{componentId}/question-stats",
			"GET /api/v1/question-bank/questions",
			"POST /api/v1/question-banks",
			"GET /api/v1/question-banks/{id}",
			"DELETE /api/v1/question-banks/{id}/questions/{questionId}",
			"GET /api/v1/exam-files/{id}",
			"GET /api/v1/live-exam-categories",
			"GET /api/v1/live-exam-categories/{id}",
			"POST /api/v1/live-exam-categories",
			"PATCH /api/v1/live-exam-categories/{id}",
			"DELETE /api/v1/live-exam-categories/{id}",
			"POST /api/v1/live-exam-categories/{id}/archive",
			"POST /api/v1/live-exam-modules",
			"PATCH /api/v1/live-exam-modules/{id}",
			"POST /api/v1/live-exam-modules/{id}/final-save",
			"POST /api/v1/live-exam-modules/{id}/publish",
			"GET /api/v1/live-exam-modules",
			"GET /api/v1/live-exam-modules/{id}",
			"DELETE /api/v1/live-exam-modules/{id}",
			"POST /api/v1/live-exam-modules/{id}/archive",
			"GET /api/v1/live-exam-modules/{id}/cover",
			"POST /api/v1/live-exam-modules/{id}/cover",
			"DELETE /api/v1/live-exam-modules/{id}/cover",
			"POST /api/v1/live-exam-participants/{id}/ready",
			"DELETE /api/v1/live-exam-participants/{id}/ready",
			"GET /api/v1/live-exams/{id}/release-status",
			"POST /api/v1/live-exam-modules/{moduleId}/exams",
			"GET /api/v1/live-exam-modules/{moduleId}/exams",
			"GET /api/v1/live-exams/{id}",
			"PATCH /api/v1/live-exams/{id}",
			"POST /api/v1/live-exams/{id}/final-save",
			"POST /api/v1/live-exams/{id}/publish",
			"POST /api/v1/live-exams/{id}/archive",
			"POST /api/v1/live-exams/{id}/questions",
			"PATCH /api/v1/live-exams/{id}/questions/{questionId}",
			"POST /api/v1/live-exams/{id}/question-pdf",
			"GET /api/v1/live-exam-modules/{moduleId}/roster/search",
			"POST /api/v1/live-exam-modules/{moduleId}/roster",
			"POST /api/v1/live-exam-modules/{moduleId}/roster/paste",
			"DELETE /api/v1/live-exam-modules/{moduleId}/roster/{studentId}",
			"POST /api/v1/live-exams/{id}/take",
			"POST /api/v1/live-exams/{id}/retake",
			"PUT /api/v1/live-exams/{id}/answers",
			"POST /api/v1/live-exams/{id}/submit",
			"GET /api/v1/live-exams/{id}/result",
			"GET /api/v1/live-exams/{id}/question-pdf",
			"POST /api/v1/live-exams/{id}/answer-pdf",
			"POST /api/v1/live-exam-modules/{moduleId}/instructor",
			"GET /api/v1/live-exam-modules/{moduleId}/submissions",
			"GET /api/v1/live-exam-participants/{id}",
			"GET /api/v1/live-exam-participants/{id}/answer-pdf",
			"GET /api/v1/live-exam-participants/{id}/evaluated-pdf",
			"POST /api/v1/live-exam-participants/{id}/evaluation",
			"GET /api/v1/live-exams/{id}/leaderboard",
			"GET /api/v1/live-exams/{id}/evaluated-pdf",
			"POST /api/v1/courses/{id}/purchases",
			"POST /api/v1/books/{id}/orders",
			"GET /api/v1/me/purchases",
			"GET /api/v1/me/orders",
			"GET /api/v1/payments",
			"POST /api/v1/payments/{id}/verify",
			"POST /api/v1/courses/{id}/admissions",
			"GET /api/v1/book-orders",
			"PATCH /api/v1/book-orders/{id}",
			"GET /api/v1/auth/google",
			"GET /api/v1/auth/google/callback",
			"POST /api/v1/me/phone",
			"GET /api/v1/public/live-exam-categories",
			"GET /api/v1/public/live-exam-modules",
			"GET /api/v1/public/live-exam-modules/{id}",
			"GET /api/v1/public/live-exam-modules/{id}/cover",
			"GET /api/v1/public/live-exam-modules/{id}/exams",
			"GET /api/v1/public/live-exams/{id}",
			"POST /api/v1/live-exam-orders",
			"GET /api/v1/live-exam-orders",
			"GET /api/v1/live-exam-orders/status",
			"GET /api/v1/live-exam-orders/{id}",
			"POST /api/v1/live-exam-orders/{id}/cancel",
			"GET /api/v1/live-exam-orders/{tranId}/return",
			"POST /api/v1/live-exam-orders/{tranId}/return",
			"POST /api/v1/live-exam-orders/{tranId}/ipn",
			"POST /api/v1/live-exam-participants/{id}/publish",
			"DELETE /api/v1/live-exam-modules/{moduleId}/instructor",
			"DELETE /api/v1/live-exams/{id}",
			"DELETE /api/v1/live-exams/{id}/questions/{questionId}",
			"GET /api/v1/live-exam-participation",
			"GET /api/v1/live-exam-participation/summary",
			"GET /api/v1/me/notices",
			"GET /api/v1/me/notices/unread-count",
			"POST /api/v1/me/notices/{id}/read",
			"POST /api/v1/me/notices/read-all",
			"GET /api/v1/me/live-exam-orders",
			"GET /api/v1/me/live-exam-access",
			"GET /api/v1/public/exam-site/status",
			"GET /api/v1/live-exam-modules/{moduleId}/roster",
			"GET /api/v1/students/{id}/live-exam-access",
			"GET /api/v1/students/{id}/live-exam-results",
			"PATCH /api/v1/me",
			"GET /api/v1/live-exam-submissions",
			"GET /api/v1/live-exams/{id}/results",
			"POST /api/v1/live-exams/{id}/publish-results",
			"POST /api/v1/live-exams/{id}/copy",
			"GET /api/v1/public/live-exams",
			"POST /api/v1/live-exam-grants",
			"DELETE /api/v1/live-exam-grants/{id}",
			"POST /api/v1/live-exam-orders/{id}/confirm",
			"POST /api/v1/live-exam-orders/{id}/reject",
			"PUT /api/v1/live-exams/{id}/answers-released",
			"PATCH /api/v1/instructors/{id}/status",
			"DELETE /api/v1/instructors/{id}",
			"POST /api/v1/announcements",
			"GET /api/v1/announcements",
			"GET /api/v1/system-status",
			// A: NR-01 authoring
			"POST /api/v1/live-exams",
			"GET /api/v1/live-exams/{id}/pending-changes",
			"DELETE /api/v1/live-exams/{id}/pending-changes",
			"GET /api/v1/live-exam-pending-changes",
			"GET /api/v1/live-exams/{id}/preview",
			"GET /api/v1/live-exams/{id}/preview/question-pdf");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private CapturingEmailSender emails;

	@Autowired
	private ObjectMapper json;

	@Test
	void theAuditLogListsNewestFirst() throws Exception {
		String phone = uniquePhone();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", UUID.randomUUID(), phone, uniqueEmail(), passwords.encode("password1"));
		// The table is append-only, so each run's row is dated later than every earlier run's.
		UUID newest = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO audit_log (id, actor_user_id, action, entity_type, entity_id, created_at)
				VALUES (?, NULL, 'TEST_ORDER', 'TEST', ?, now() + interval '100 years')
				""", UUID.randomUUID(), newest);

		mockMvc.perform(get("/api/v1/audit-log").header("Authorization", "Bearer " + token(phone)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].entityId").value(newest.toString()));
	}

	@Test
	void auditedActionsRecordActorActionAndEntity() throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID masterId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MASTER_ADMIN', 'ACTIVE', now(), now())
				""", masterId, phone, email, passwords.encode("password1"));
		String access = token(phone);

		MvcResult created = mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content(account(uniquePhone(), uniqueEmail())))
			.andExpect(status().isCreated())
			.andReturn();
		UUID studentId = UUID.fromString(field(created, "id"));
		assertAudit(jdbc, AuditRecorder.ACTIVATION_CODE_ISSUED, masterId, studentId);

		mockMvc.perform(patch("/api/v1/students/" + studentId + "/status").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"INACTIVE\"}"))
			.andExpect(status().isNoContent());
		assertAudit(jdbc, AuditRecorder.USER_SUSPENDED, masterId, studentId);

		mockMvc.perform(patch("/api/v1/students/" + studentId + "/status").header("Authorization", "Bearer " + access)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"ACTIVE\"}"))
			.andExpect(status().isNoContent());
		assertAudit(jdbc, AuditRecorder.USER_RESTORED, masterId, studentId);

		mockMvc.perform(delete("/api/v1/students/" + studentId).header("Authorization", "Bearer " + access))
			.andExpect(status().isNoContent());
		assertAudit(jdbc, AuditRecorder.USER_DELETED, masterId, studentId);

		String resetEmail = uniqueEmail();
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Reset User","phone":"%s","email":"%s","password":"password1"}
					""".formatted(uniquePhone(), resetEmail)))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + resetEmail + "\"}")).andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"code":"%s","password":"password2"}
					""".formatted(emails.latestReset(resetEmail))))
			.andExpect(status().isNoContent());
		UUID resetUser = jdbc.queryForObject("select id from users where email = ?", UUID.class, resetEmail);
		Integer resets = jdbc.queryForObject("""
				select count(*) from audit_log
				where action = ? and entity_id = ? and actor_user_id is null
				""", Integer.class, AuditRecorder.PASSWORD_RESET, resetUser);
		assertThat(resets).isEqualTo(1);
	}

	@Test
	void openApiMatchesImplementedEndpoints() throws Exception {
		MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		JsonNode paths = json.readTree(result.getResponse().getContentAsString()).get("paths");
		Set<String> operations = new java.util.HashSet<>();
		paths.properties().forEach(path -> path.getValue().properties()
			.forEach(method -> operations.add(method.getKey().toUpperCase() + " " + path.getKey())));
		assertThat(operations).isEqualTo(API_OPERATIONS);
	}

	private static void assertAudit(JdbcTemplate jdbc, String action, UUID actor, UUID entity) {
		Integer count = jdbc.queryForObject("""
				select count(*) from audit_log
				where action = ? and actor_user_id = ? and entity_id = ? and entity_type = 'USER'
				""", Integer.class, action, actor, entity);
		assertThat(count).isEqualTo(1);
	}

	private String token(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		return field(result, "accessToken");
	}

	private String field(MvcResult result, String name) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + name + "\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static String account(String phone, String email) {
		return """
				{"fullName":"Audit User","phone":"%s","email":"%s"}
				""".formatted(phone, email);
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
