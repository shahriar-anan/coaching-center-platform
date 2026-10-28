package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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

import tools.jackson.databind.ObjectMapper;

/**
 * An archived package gives no access through itself, but an exam grant the student holds on one of its exams still counts
 * (NR-01 ruling: "An archived, draft or deleted package gives no access through it; other routes still count and results
 * stay readable."). The exam runs 06:00 to 06:40 UTC on 20 October 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ArchivedPackageAccessIT.ClockConfig.class })
class ArchivedPackageAccessIT extends PostgresIntegrationTest {

	private static final Instant BEFORE_WINDOW = Instant.parse("2026-10-20T04:00:00Z");

	private static final String NOON = "2026-10-20T12:00:00+06:00";

	private static final Instant SITTING = Instant.parse("2026-10-20T06:10:00Z");

	private static final Instant AFTER_WINDOW = Instant.parse("2026-10-20T07:00:00Z");

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
	private LiveExamRepository exams;

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		clock.set(BEFORE_WINDOW);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void anExamGrantStillOpensAnExamInAnArchivedPackageAndAPackageGrantDoesNot() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Archived package");
		UUID exam = fx.createExam(staff, module, "MCQ", "Member exam", NOON, 40, null);
		fx.question(staff, exam, "Two plus two", 1);
		fx.publishNew(master, exam);

		UUID both = fx.seed("STUDENT");
		String withExam = fx.token(both);
		UUID packageOnly = fx.seed("STUDENT");
		String withPackage = fx.token(packageOnly);
		grant(master, both, "MODULE", module, null);
		grant(master, both, "EXAM", null, exam);
		grant(master, packageOnly, "MODULE", module, null);

		clock.set(SITTING);
		for (String token : List.of(withExam, withPackage)) {
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isOk());
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		}

		clock.set(AFTER_WINDOW);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());

		// The student with the exam grant can still take the exam (a retake, as the first sitting is done).
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + withExam))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptNo").value(2));
		// The package grant alone does not open it.
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + withPackage))
			.andExpect(status().isNotFound());

		// Earlier results stay readable for both; the exam-grant student has the retake too.
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result?attempt=1").header("Authorization", "Bearer " + withExam))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptNo").value(1));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/attempts").header("Authorization", "Bearer " + withExam))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result?attempt=1").header("Authorization", "Bearer " + withPackage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attemptNo").value(1));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/attempts").header("Authorization", "Bearer " + withPackage))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));

		// The recipients of the exam's mail and notices: the exam grant counts, the archived package does not.
		List<UUID> recipients = exams.findActiveStudentsWithAccessToExam(exam);
		assertThat(recipients).contains(both).doesNotContain(packageOnly);
	}

	private void grant(String master, UUID student, String scope, UUID module, UUID exam) throws Exception {
		String target = module == null ? "\"examId\":\"" + exam + "\"" : "\"moduleId\":\"" + module + "\"";
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"%s\",\"scope\":\"%s\",%s,\"reason\":\"Paid before the window\"}".formatted(student, scope,
					target)))
			.andExpect(status().isCreated());
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(BEFORE_WINDOW);
		}

	}

}
