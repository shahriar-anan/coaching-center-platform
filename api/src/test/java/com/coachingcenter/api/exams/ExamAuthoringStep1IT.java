package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 UI Step 1, API slice: paper language "Both" and no language at all, blank rich text on create, publish refusing
 * blank text with the field named, and the HTML sanitizer on every save of an exam or package text field.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExamAuthoringStep1IT.ClockConfig.class })
class ExamAuthoringStep1IT extends PostgresIntegrationTest {

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
	void paperLanguageIsOptionalAndAcceptsBoth() throws Exception {
		String staff = fx.staff();
		UUID category = fx.category(staff, "Paper language");

		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examJson(category, null, "Unset", "", "")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.paperLanguage").doesNotExist());
		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examJson(category, "BOTH", "Both", "", "")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.paperLanguage").value("BOTH"));
		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examJson(category, "EN", "English", "", "")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.paperLanguage").value("EN"));
		for (String invalid : new String[] { "XX", "bn", "" }) {
			mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content(examJson(category, invalid, "Invalid " + invalid, "", "")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
	}

	@Test
	void aPatchWithNullClearsThePaperLanguage() throws Exception {
		String staff = fx.staff();
		UUID category = fx.category(staff, "Clear language");
		UUID exam = created(staff, examJson(category, "BN", "Cleared", "", ""));

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"paperLanguage\":null}"))
			.andExpect(status().isOk());

		assertThat(fx.exam(staff, exam).hasNonNull("paperLanguage")).isFalse();
	}

	@Test
	void createAcceptsBlankDescriptionAndRulesButNotBlankTitles() throws Exception {
		String staff = fx.staff();
		UUID category = fx.category(staff, "Blank text");
		UUID exam = created(staff, examJson(category, "BN", "", "", ""));

		JsonNode saved = fx.exam(staff, exam);
		assertThat(saved.get("descriptionEn").asText()).isEmpty();
		assertThat(saved.get("rulesBn").asText()).isEmpty();

		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examJson(category, "BN", "   ", "", "")))
			.andExpect(status().isCreated());
		Map<String, Object> blankTitle = examFields(category, "BN", "", "", "");
		blankTitle.put("titleEn", " ");
		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(blankTitle)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void publishRefusesBlankTextAndNamesEachField() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Publish text");
		UUID exam = created(staff, examJson(category, "BN", "", "", ""));
		fx.finalSave(staff, exam);

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_TEXT_REQUIRED"))
			.andExpect(jsonPath("$.fieldErrors.length()").value(3))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("descriptionEn"))
			.andExpect(jsonPath("$.fieldErrors[2].field").value("rulesBn"));

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"descriptionEn\":\"Now filled\"}"))
			.andExpect(status().isOk());
		fx.finalSave(staff, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.fieldErrors.length()").value(2))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("rulesEn"));

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"rulesEn\":\"English rules\",\"rulesBn\":\"বাংলা নিয়ম\"}"))
			.andExpect(status().isOk());
		fx.finalSave(staff, exam);
		// The text gate passes now; the exam has no question yet, which is the next refusal.
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("QUESTIONS_REQUIRED"));
	}

	@Test
	void htmlIsSanitizedOnCreateAndPlainTextIsKept() throws Exception {
		String staff = fx.staff();
		UUID category = fx.category(staff, "Sanitize");
		String dirty = "<p onclick=\"x()\">Hi <strong>there</strong></p><script>evil()</script>"
				+ "<a href=\"javascript:evil()\">bad</a><a href=\"https://example.com/rules\">ok</a>"
				+ "<img src=\"x\" onerror=\"evil()\">";
		UUID exam = created(staff, examJson(category, "BN", dirty, "", ""));

		String saved = fx.exam(staff, exam).get("descriptionEn").asText();
		assertThat(saved).contains("<p>Hi <strong>there</strong></p>");
		assertThat(saved).contains("href=\"https://example.com/rules\"").contains("rel=\"nofollow\"");
		assertThat(saved).doesNotContain("onclick").doesNotContain("<script").doesNotContain("javascript:")
			.doesNotContain("onerror").doesNotContain("<img");

		UUID plain = created(staff, examJson(category, "BN", "Line one\nLine two & more", "", ""));
		assertThat(fx.exam(staff, plain).get("descriptionEn").asText()).isEqualTo("Line one\nLine two & more");
	}

	@Test
	void aStagedEditOnAPublishedExamIsSanitizedToo() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Staged sanitize");
		UUID exam = created(staff, examJson(category, "BN", "Published", "English rules", "Bangla rules"));
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"rulesEn\":\"<p onmouseover=\\\"x()\\\">Staged</p><img src=\\\"x\\\" onerror=\\\"y()\\\">\"}"))
			.andExpect(status().isOk());

		String staged = fx.exam(staff, exam).get("rulesEn").asText();
		assertThat(staged).isEqualTo("<p>Staged</p>");
	}

	@Test
	void aPackageDescriptionIsSanitizedOnCreate() throws Exception {
		String staff = fx.staff();
		UUID category = fx.category(staff, "Package text");
		String body = json.writeValueAsString(Map.of("categoryId", category, "titleEn", "Package", "titleBn", "মডিউল",
				"descriptionEn", "<p onclick=\"x()\">Package <em>text</em></p><script>evil()</script>", "descriptionBn",
				"বি"));
		mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.descriptionEn").value("<p>Package <em>text</em></p>"));
	}

	/** An exam body in the 12:00 window; {@code paperLanguage} null means the member is left out. */
	private String examJson(UUID category, String paperLanguage, String descriptionEn, String rulesEn, String rulesBn)
			throws Exception {
		return json.writeValueAsString(examFields(category, paperLanguage, descriptionEn, rulesEn, rulesBn));
	}

	private static Map<String, Object> examFields(UUID category, String paperLanguage, String descriptionEn,
			String rulesEn, String rulesBn) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("examType", "MCQ");
		body.put("categoryId", category);
		if (paperLanguage != null) {
			body.put("paperLanguage", paperLanguage);
		}
		body.put("titleEn", "Exam " + UUID.randomUUID().toString().substring(0, 8));
		body.put("titleBn", "পরীক্ষা");
		body.put("descriptionEn", descriptionEn);
		body.put("descriptionBn", "বাংলা বিবরণ");
		body.put("rulesEn", rulesEn);
		body.put("rulesBn", rulesBn);
		body.put("liveAt", NOON);
		body.put("durationMinutes", 40);
		body.put("passPercent", 50);
		return body;
	}

	private UUID created(String token, String body) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return fx.id(result);
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
