package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import tools.jackson.databind.ObjectMapper;

/** A6: the student preview of an exam, for promotion (C10', P-b): after the window or in practice, with no key and no purchase. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExamPreviewIT.ClockConfig.class })
class ExamPreviewIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	/** Inside the window of the exams below (12:00 to 12:40 in Dhaka). */
	private static final Instant IN_WINDOW = Instant.parse("2026-10-20T06:10:00Z");

	/** After the window of the exams below. */
	private static final Instant AFTER_WINDOW = Instant.parse("2026-10-20T07:00:00Z");

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
	void thePreviewIsClosedBeforeAndDuringTheWindowAndOpenAfterIt() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Preview window");
		UUID exam = fx.createExam(staff, module, "MCQ", "Preview me", NOON, 40, "\"previewEnabled\":true");
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);
		String student = fx.token(fx.seed("STUDENT"));

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/preview").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		clock.set(IN_WINDOW);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/preview").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		clock.set(AFTER_WINDOW);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/preview").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phase").value("ENDED"))
			.andExpect(jsonPath("$.questions[0].stem").value("Stem"));
	}

	@Test
	void thePreviewNeedsNoPurchaseAndNeverCarriesTheKeyOrTheExplanation() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Preview leak");
		// Only the Master Admin may set a price (EXAM_PRICE), so the priced exam is created by the Master Admin.
		UUID exam = fx.createExam(master, module, "MCQ", "No leak", NOON, 40, "\"previewEnabled\":true,\"price\":200");
		fx.question(staff, exam, "Pick the key", 1);
		fx.publishNew(master, exam);
		String signedIn = fx.token(fx.seed("STUDENT"));
		clock.set(AFTER_WINDOW);

		String body = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/preview").header("Authorization", "Bearer " + signedIn))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options", org.hamcrest.Matchers.hasSize(2)))
			.andExpect(jsonPath("$.questions[0].multipleAnswer").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(body).doesNotContain("\"correct\"").doesNotContain("\"explanation\"");
	}

	@Test
	void aPracticeExamIsPreviewableAtAnyTime() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Preview practice");
		UUID practice = fx.createExam(staff, module, "MCQ", "Practice preview", null, 40,
				"\"mode\":\"PRACTICE\",\"previewEnabled\":true");
		fx.question(staff, practice, "Stem", 1);
		fx.publishNew(master, practice);
		String student = fx.token(fx.seed("STUDENT"));

		mockMvc.perform(get("/api/v1/live-exams/" + practice + "/preview").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phase").value("PRACTICE"));
	}

	@Test
	void aDisabledPreviewAndADraftAreNotFound() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Preview off");
		UUID off = fx.createExam(staff, module, "MCQ", "Off", NOON, 40, null);
		fx.question(staff, off, "Stem", 1);
		fx.publishNew(master, off);
		UUID draft = fx.createExam(staff, module, "MCQ", "Draft", NOON, 40, "\"previewEnabled\":true");
		fx.question(staff, draft, "Stem", 1);
		String student = fx.token(fx.seed("STUDENT"));
		clock.set(AFTER_WINDOW);

		mockMvc.perform(get("/api/v1/live-exams/" + off + "/preview").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + draft + "/preview").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
	}

	@Test
	void aSignedOutRequestIsRejectedAndAWrittenPreviewReturnsThePaper() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Preview written");
		UUID written = fx.createExam(staff, module, "WRITTEN", "Paper preview", NOON, 40, "\"previewEnabled\":true");
		fx.uploadPdf(staff, written, "preview");
		fx.publishNew(master, written);
		String student = fx.token(fx.seed("STUDENT"));
		clock.set(AFTER_WINDOW);

		mockMvc.perform(get("/api/v1/live-exams/" + written + "/preview"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/preview").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasPaper").value(true));
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/preview/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
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
