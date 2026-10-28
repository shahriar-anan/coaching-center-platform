package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 16: two retake requests at the same moment create one attempt (D2). The student has a finished
 * MCQ attempt. Each repetition is a fresh student and exam, so the race runs five times in one invocation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, RetakeRaceIT.ClockConfig.class })
class RetakeRaceIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

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

	@RepeatedTest(5)
	void twoRetakeRequestsAtTheSameMomentCreateExactlyOneAttempt() throws Exception {
		clock.set(BEFORE);
		AuthoringFixture fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Retake race");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Race quiz", AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, AuthoringFixture.questionJson("Stem", 1, "Because", false, "*A", "B"));
		fx.publishNew(master, exam);
		UUID student = fx.seed("STUDENT");
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Race grant"}
					""".formatted(student, exam)))
			.andExpect(status().isCreated());
		String token = fx.token(student);

		// A finished first attempt, so both retake requests are allowed by state.
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + token)
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:20:00Z"));

		List<Outcome> outcomes = race(exam, token);

		for (Outcome outcome : outcomes) {
			assertThat(outcome.status()).as("never a 500: %s", outcome.body()).isNotEqualTo(500);
			if (outcome.status() == 409) {
				assertThat(outcome.body()).contains("ATTEMPT_IN_PROGRESS");
			}
			else {
				assertThat(outcome.status()).isEqualTo(200);
				assertThat(outcome.body()).contains("\"attemptNo\":2");
			}
		}
		assertThat(outcomes.stream().filter(outcome -> outcome.status() == 200).count()).isGreaterThanOrEqualTo(1);
		Integer created = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_participants where exam_id = ? and student_id = ? and attempt_no = 2 and deleted_at is null",
				Integer.class, exam, student);
		assertThat(created).isEqualTo(1);
	}

	/** Two threads call POST /retake; a latch releases them together. */
	private List<Outcome> race(UUID exam, String token) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Outcome>> futures = new ArrayList<>();
			for (int thread = 0; thread < 2; thread++) {
				Callable<Outcome> call = () -> {
					go.await();
					MvcResult result = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake")
						.header("Authorization", "Bearer " + token)).andReturn();
					return new Outcome(result.getResponse().getStatus(), result.getResponse().getContentAsString());
				};
				futures.add(pool.submit(call));
			}
			go.countDown();
			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> future : futures) {
				outcomes.add(future.get());
			}
			return outcomes;
		}
		finally {
			pool.shutdownNow();
		}
	}

	private record Outcome(int status, String body) {
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
