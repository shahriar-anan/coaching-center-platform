package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PATCH {@code categoryId} on any exam, in a package or standalone (NR-01 UI Step 1 fix). An unknown or archived category is
 * a 400 with a field error. On a published exam the change is staged, shows in the change list, and applies at publish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class ExamCategoryChangeIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private PackageFixtures fx;

	@BeforeEach
	void setUp() {
		fx = new PackageFixtures(mockMvc, json, dataSource, passwords);
	}

	@Test
	void aDraftExamMovesToAnotherCategoryWhetherItIsInAPackageOrStandalone() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID first = fx.createCategory(system, "First shelf");
		UUID second = fx.createCategory(system, "Second shelf");
		UUID standalone = createStandalone(system, first, "Standalone paper");
		UUID pkg = fx.createPackage(system, first, "Shelf package");
		UUID inPackage = fx.createExam(system, pkg, "Packaged paper", null);

		mockMvc.perform(patch("/api/v1/live-exams/" + standalone).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"categoryId\":\"" + second + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryId").value(second.toString()));
		mockMvc.perform(patch("/api/v1/live-exams/" + inPackage).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"categoryId\":\"" + second + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryId").value(second.toString()));

		assertThat(fx.body(system, "/api/v1/live-exams/" + standalone).get("categoryId").asText()).isEqualTo(second.toString());
		assertThat(fx.body(system, "/api/v1/live-exams/" + inPackage).get("categoryId").asText()).isEqualTo(second.toString());
	}

	@Test
	void anUnknownOrArchivedCategoryIsRefusedWithAFieldErrorAndTheExamKeepsItsCategory() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID home = fx.createCategory(system, "Home shelf");
		UUID archived = fx.createCategory(system, "Closed shelf");
		UUID exam = createStandalone(system, home, "Refused paper");
		mockMvc.perform(post("/api/v1/live-exam-categories/" + archived + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());

		refuse(system, exam, UUID.randomUUID().toString());
		refuse(system, exam, archived.toString());

		assertThat(fx.body(system, "/api/v1/live-exams/" + exam).get("categoryId").asText()).isEqualTo(home.toString());
	}

	@Test
	void aPublishedExamStagesTheCategoryAndTheChangeListNamesItUntilPublish() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID from = fx.createCategory(system, "Old shelf");
		UUID to = fx.createCategory(system, "New shelf");
		UUID archived = fx.createCategory(system, "Retired shelf");
		UUID exam = createStandalone(system, from, "Staged paper");
		fx.addQuestion(system, exam);
		fx.publishExam(system, exam);
		mockMvc.perform(post("/api/v1/live-exam-categories/" + archived + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());

		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"categoryId\":\"" + to + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingChanges").value(true))
			.andExpect(jsonPath("$.categoryId").value(to.toString()));
		refuse(system, exam, archived.toString());

		JsonNode changes = fx.body(system, "/api/v1/live-exams/" + exam + "/pending-changes");
		JsonNode categoryChange = null;
		for (JsonNode field : changes.get("fields")) {
			if ("categoryId".equals(field.get("field").asText())) {
				categoryChange = field;
			}
		}
		assertThat(categoryChange).isNotNull();
		assertThat(categoryChange.get("before").asText()).isEqualTo(from.toString());
		assertThat(categoryChange.get("after").asText()).isEqualTo(to.toString());

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"" + changes.get("revisionId").asText() + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.categoryId").value(to.toString()))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		assertThat(fx.body(system, "/api/v1/live-exams/" + exam).get("categoryId").asText()).isEqualTo(to.toString());
	}

	private UUID createStandalone(String token, UUID category, String titleEn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "examType":"MCQ","paperLanguage":"BN","titleEn":"%s","titleBn":"খাতা",
					  "descriptionEn":"English description","descriptionBn":"বাংলা বিবরণ",
					  "rulesEn":"English rules","rulesBn":"বাংলা নিয়ম",
					  "durationMinutes":40,"passPercent":50,"categoryId":"%s"
					}
					""".formatted(titleEn, category)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	/** A PATCH with a category that cannot be chosen: a 400 that names the category field. */
	private void refuse(String token, UUID exam, String categoryId) throws Exception {
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"categoryId\":\"" + categoryId + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
	}

}
