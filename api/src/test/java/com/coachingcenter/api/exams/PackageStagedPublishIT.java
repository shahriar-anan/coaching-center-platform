package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The package screen's one publish (NR-01, B4 and E31): the summary lists the package's own staged edit, each staged exam and
 * each teacher change; publish-changes applies them together, and a stale revision id aborts the whole publish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, PackageStagedPublishIT.ClockConfig.class })
class PackageStagedPublishIT extends PostgresIntegrationTest {

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

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		clock.set(TEN_AM);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void theSummaryListsEveryStagedPieceAndOneMasterPublishTakesThemAll() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Bundle" + UUID.randomUUID().toString().substring(0, 8);
		UUID teacher = instructor(token + " Teacher");
		String originalTitle = token + " package";
		UUID module = fx.module(staff, originalTitle);
		UUID examA = fx.createExam(staff, module, "MCQ", "Exam A " + token, NOON, 40, null);
		UUID question = fx.question(staff, examA, "Old stem", 1);
		fx.publishNew(master, examA);
		UUID examB = fx.createExam(staff, module, "WRITTEN", "Exam B " + token, NOON, 40, null);
		fx.uploadPdf(staff, examB);
		fx.publishNew(master, examB);

		// One staged piece for each kind: the package's title, a question in exam A, and a teacher on exam B.
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Bundle title " + token + "\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/live-exams/" + examA + "/questions/" + question).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.questionJson("New stem", 1, "Because", false, "*A", "B")))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + examB + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"instructorId\":\"" + teacher + "\"}"))
			.andExpect(status().isCreated());

		JsonNode summary = summary(staff, module);
		assertThat(summary.get("packageChanges").asBoolean()).isTrue();
		assertThat(summary.get("instructorChanges").asBoolean()).isTrue();
		assertThat(summary.get("exams")).hasSize(2);
		assertThat(row(summary, examA).get("changes").asInt()).isEqualTo(1);
		assertThat(row(summary, examB).get("changes").asInt()).isEqualTo(1);
		assertThat(row(summary, examA).get("titleEn").asText()).isEqualTo("Exam A " + token);

		// Only the Master Admin publishes; the System Admin's attempt changes nothing.
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish-changes").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		// A stale package revision id: nothing is published, not even the exams that were read correctly.
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish-changes").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"%s\"}".formatted(UUID.randomUUID())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PENDING_CHANGES_STALE"));
		assertNothingPublished(staff, module, examA, question, examB, originalTitle);

		// A stale exam revision id on the second exam: the package and the first exam are not published either.
		String packageToken = pendingToken(master, "/api/v1/live-exam-modules/" + module + "/pending-changes");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish-changes").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"%s\",\"examRevisionIds\":{\"%s\":\"%s\"}}".formatted(packageToken, examB,
					UUID.randomUUID())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PENDING_CHANGES_STALE"));
		assertNothingPublished(staff, module, examA, question, examB, originalTitle);

		// The tokens that were read publish everything together.
		String tokenA = pendingToken(master, "/api/v1/live-exams/" + examA + "/pending-changes");
		String tokenB = pendingToken(master, "/api/v1/live-exams/" + examB + "/pending-changes");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish-changes").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"%s\",\"examRevisionIds\":{\"%s\":\"%s\",\"%s\":\"%s\"}}".formatted(packageToken, examA,
					tokenA, examB, tokenB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.packageChanged").value(true))
			.andExpect(jsonPath("$.examsPublished").value(2));

		JsonNode after = summary(staff, module);
		assertThat(after.get("packageChanges").asBoolean()).isFalse();
		assertThat(after.get("instructorChanges").asBoolean()).isFalse();
		assertThat(after.get("exams")).isEmpty();
		assertThat(fx.jdbc().queryForObject("select title_en from live_exam_modules where id = ?", String.class, module))
			.isEqualTo("Bundle title " + token);
		assertThat(fx.jdbc().queryForObject("select stem from live_exam_questions where id = ?", String.class, question))
			.isEqualTo("New stem");
		assertThat(fx.jdbc().queryForObject("select instructor_id from live_exams where id = ?", UUID.class, examB))
			.isEqualTo(teacher);
	}

	@Test
	void aPackageWithNothingStagedPublishesNothingAndSaysSo() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Quiet " + UUID.randomUUID().toString().substring(0, 8));
		UUID exam = fx.createExam(staff, module, "MCQ", "Quiet exam", NOON, 40, null);
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);

		JsonNode summary = summary(staff, module);
		assertThat(summary.get("packageChanges").asBoolean()).isFalse();
		assertThat(summary.get("instructorChanges").asBoolean()).isFalse();
		assertThat(summary.get("exams")).isEmpty();
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish-changes").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.packageChanged").value(false))
			.andExpect(jsonPath("$.examsPublished").value(0));
	}

	/** Nothing staged has been published: the live rows still hold the old title, stem and teacher, and everything is still waiting. */
	private void assertNothingPublished(String staff, UUID module, UUID examA, UUID question, UUID examB, String originalTitle)
			throws Exception {
		assertThat(fx.jdbc().queryForObject("select title_en from live_exam_modules where id = ?", String.class, module))
			.isEqualTo(originalTitle);
		assertThat(fx.jdbc().queryForObject("select stem from live_exam_questions where id = ?", String.class, question))
			.isEqualTo("Old stem");
		assertThat(fx.jdbc().queryForObject("select instructor_id from live_exams where id = ?", UUID.class, examB)).isNull();
		JsonNode summary = summary(staff, module);
		assertThat(summary.get("packageChanges").asBoolean()).isTrue();
		assertThat(summary.get("exams")).hasSize(2);
		assertThat(row(summary, examA).get("changes").asInt()).isEqualTo(1);
		assertThat(row(summary, examB).get("changes").asInt()).isEqualTo(1);
	}

	private JsonNode summary(String token, UUID module) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/staged-summary")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private String pendingToken(String token, String path) throws Exception {
		return json.readTree(mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("revisionId").asText();
	}

	private static JsonNode row(JsonNode summary, UUID exam) {
		for (JsonNode row : summary.get("exams")) {
			if (exam.toString().equals(row.get("id").asText())) {
				return row;
			}
		}
		throw new AssertionError("No waiting changes on exam " + exam);
	}

	private UUID instructor(String name) {
		UUID id = fx.seed("INSTRUCTOR");
		fx.jdbc().update("update admin_profiles set full_name = ? where user_id = ?", name, id);
		return id;
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
