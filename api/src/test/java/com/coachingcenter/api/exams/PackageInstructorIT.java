package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
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
 * NR-01 final check, case 7 (D6): a package has no instructor of its own. A package counts for a teacher only through an
 * exam assigned to that teacher, and the instructor directory lists it under that teacher.
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

	/**
	 * A package with an exam assigned to a teacher shows under that teacher in the directory. The package does not appear for a
	 * teacher who holds no exam in it.
	 */
	@Test
	void aPackageShowsUnderTheTeacherOfItsExamsOnly() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		String token = "Tutor" + UUID.randomUUID().toString().substring(0, 8);
		UUID i1 = instructor(token + " One");
		UUID i2 = instructor(token + " Two");
		UUID module = fx.module(staff, token + " package");
		UUID e1 = fx.createExam(staff, module, "WRITTEN", "E1 " + token, NOON, 40, null);
		mockMvc.perform(post("/api/v1/live-exams/" + e1 + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content(instructorBody(i1)))
			.andExpect(status().isCreated());
		assertThat(instructorOf(e1)).isEqualTo(i1);

		assertThat(modulesOf(master, token + " One")).as("I1 holds E1 in the package").hasSize(1);
		assertThat(modulesOf(master, token + " One").get(0).get("titleEn").asText()).isEqualTo(token + " package");
		assertThat(modulesOf(master, token + " Two")).as("I2 holds no exam in the package").isEmpty();
	}

	private UUID instructor(String name) {
		UUID id = fx.seed("INSTRUCTOR");
		fx.jdbc().update("update admin_profiles set full_name = ? where user_id = ?", name, id);
		return id;
	}

	private static String instructorBody(UUID instructor) {
		return "{\"instructorId\":\"" + instructor + "\"}";
	}

	private UUID instructorOf(UUID exam) {
		return fx.jdbc().queryForObject("select instructor_id from live_exams where id = ?", UUID.class, exam);
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
