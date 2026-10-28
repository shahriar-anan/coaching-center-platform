package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 7 (R3, D6): a package default instructor is set for the package and, with applyToExams, for every
 * exam in it; a new exam inherits the default; an exam keeps its own instructor when the default changes without applyToExams;
 * and the instructor directory lists each package under the people who hold it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, PackageInstructorIT.ClockConfig.class })
class PackageInstructorIT extends PostgresIntegrationTest {

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
	void thePackageDefaultReachesEveryExamAndTheDirectoryFollowsEachStep() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Tutor" + UUID.randomUUID().toString().substring(0, 8);
		UUID i1 = instructor(token + " One");
		UUID i2 = instructor(token + " Two");
		UUID i3 = instructor(token + " Three");
		UUID module = fx.module(staff, token + " package");
		UUID e1 = fx.createExam(staff, module, "MCQ", "E1 " + token, NOON, 40, null);
		UUID e2 = fx.createExam(staff, module, "MCQ", "E2 " + token, NOON, 40, null);
		mockMvc.perform(post("/api/v1/live-exams/" + e1 + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(i1, null)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(e1)).isEqualTo(i1);
		assertThat(instructorOf(e2)).isNull();

		// Step 1: the package default with applyToExams (the default) sets E1 and E2, and replaces E1's own instructor.
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(i2, null)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(e1)).isEqualTo(i2);
		assertThat(instructorOf(e2)).isEqualTo(i2);
		UUID e3 = fx.createExam(staff, module, "MCQ", "E3 " + token, NOON, 40, null);
		assertThat(instructorOf(e3)).as("a new exam inherits the package default").isEqualTo(i2);
		assertThat(modulesOf(master, token + " One")).as("I1 holds nothing now").isEmpty();
		assertThat(modulesOf(master, token + " Two")).hasSize(1);
		assertThat(modulesOf(master, token + " Two").get(0).get("titleEn").asText()).isEqualTo(token + " package");

		// Step 2: E1 goes back to I1. The default is replaced without touching the exams (applyToExams=false).
		mockMvc.perform(delete("/api/v1/live-exams/" + e1 + "/instructor").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exams/" + e1 + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(i1, null)))
			.andExpect(status().isCreated());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/instructor?applyToExams=false")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(i3, false)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(e1)).as("E1 keeps I1").isEqualTo(i1);
		assertThat(instructorOf(e2)).as("E2 keeps I2").isEqualTo(i2);
		assertThat(instructorOf(e3)).as("E3 keeps I2").isEqualTo(i2);
		assertThat(defaultOf(module)).isEqualTo(i3);
		UUID e4 = fx.createExam(staff, module, "MCQ", "E4 " + token, NOON, 40, null);
		assertThat(instructorOf(e4)).as("a new exam gets the new default").isEqualTo(i3);
		assertThat(modulesOf(master, token + " One")).hasSize(1);
		assertThat(modulesOf(master, token + " Two")).hasSize(1);
		assertThat(modulesOf(master, token + " Three")).hasSize(1);

		// Step 3: the default is cleared (applyToExams=false). The exams keep their instructors, E4 included.
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/instructor?applyToExams=false")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(defaultOf(module)).as("the package default is cleared").isNull();
		assertThat(instructorOf(e1)).isEqualTo(i1);
		assertThat(instructorOf(e2)).isEqualTo(i2);
		assertThat(instructorOf(e4)).isEqualTo(i3);
		UUID e5 = fx.createExam(staff, module, "MCQ", "E5 " + token, NOON, 40, null);
		assertThat(instructorOf(e5)).as("a new exam has no instructor").isNull();
		assertThat(modulesOf(master, token + " Three")).as("I3 still holds E4").hasSize(1);
	}

	private UUID instructor(String name) {
		UUID id = fx.seed("INSTRUCTOR");
		fx.jdbc().update("update admin_profiles set full_name = ? where user_id = ?", name, id);
		return id;
	}

	private static String instructorBody(UUID instructor, Boolean applyToExams) {
		return applyToExams == null ? "{\"instructorId\":\"" + instructor + "\"}"
				: "{\"instructorId\":\"" + instructor + "\",\"applyToExams\":" + applyToExams + "}";
	}

	private UUID instructorOf(UUID exam) {
		return fx.jdbc().queryForObject("select instructor_id from live_exams where id = ?", UUID.class, exam);
	}

	private UUID defaultOf(UUID module) {
		return fx.jdbc().queryForList("select instructor_id from live_exam_instructors where module_id = ?", UUID.class, module)
			.stream().findFirst().orElse(null);
	}

	/** The packages the instructor with this name is listed under in the instructor directory. */
	private JsonNode modulesOf(String master, String name) throws Exception {
		JsonNode content = json.readTree(mockMvc.perform(get("/api/v1/instructors").param("q", name)
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("content");
		assertThat(content).as("the directory finds " + name).hasSize(1);
		return content.get(0).get("modules");
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
