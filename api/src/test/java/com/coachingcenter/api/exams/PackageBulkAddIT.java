package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.springframework.test.web.servlet.ResultActions;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Bulk add of existing exams to a package (B1): each id is added or skipped with a reason, and a published package stages the
 * additions as one revision.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class PackageBulkAddIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

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

	private PackageFixtures fx;

	@BeforeEach
	void setUp() {
		clock.set(TEN_AM);
		fx = new PackageFixtures(mockMvc, json, dataSource, passwords);
	}

	@Test
	void aDraftPackageTakesSeveralExamsInOneRequestInRequestOrder() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk draft shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk draft package");
		UUID one = standalone(system, category, "Bulk one");
		UUID two = standalone(system, category, "Bulk two");
		UUID three = standalone(system, category, "Bulk three");

		bulk(system, pkg, bulkBody(one, two, three))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added[0]").value(one.toString()))
			.andExpect(jsonPath("$.added[1]").value(two.toString()))
			.andExpect(jsonPath("$.added[2]").value(three.toString()))
			.andExpect(jsonPath("$.skipped").isEmpty())
			.andExpect(jsonPath("$.pendingChanges").value(false));

		JsonNode members = members(system, pkg);
		assertThat(memberIds(members)).containsExactly(one, two, three);
		assertThat(members.get("pendingChanges").asBoolean()).isFalse();
	}

	@Test
	void duplicatesInOneRequestAreAddedOnceAndAnExamAlreadyInThePackageIsSkipped() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk duplicate shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk duplicate package");
		UUID existing = fx.createExam(system, pkg, "Already here", null);
		UUID fresh = standalone(system, category, "Fresh");

		bulk(system, pkg, bulkBody(existing, fresh, fresh))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added.length()").value(1))
			.andExpect(jsonPath("$.added[0]").value(fresh.toString()))
			.andExpect(jsonPath("$.skipped.length()").value(1))
			.andExpect(jsonPath("$.skipped[0].examId").value(existing.toString()))
			.andExpect(jsonPath("$.skipped[0].reason").value("ALREADY_IN_PACKAGE"));
		assertThat(memberIds(members(system, pkg))).containsExactly(existing, fresh);
	}

	@Test
	void anArchivedExamAndAnUnknownIdAreSkippedWithTheirReasons() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk skip shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk skip package");
		UUID good = standalone(system, category, "Good");
		UUID withdrawn = standalone(system, category, "Withdrawn");
		mockMvc.perform(post("/api/v1/live-exams/" + withdrawn + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		UUID unknown = UUID.randomUUID();

		bulk(system, pkg, bulkBody(good, withdrawn, unknown))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added.length()").value(1))
			.andExpect(jsonPath("$.added[0]").value(good.toString()))
			.andExpect(jsonPath("$.skipped.length()").value(2))
			.andExpect(jsonPath("$.skipped[0].examId").value(withdrawn.toString()))
			.andExpect(jsonPath("$.skipped[0].reason").value("ARCHIVED"))
			.andExpect(jsonPath("$.skipped[1].examId").value(unknown.toString()))
			.andExpect(jsonPath("$.skipped[1].reason").value("NOT_FOUND"));
		assertThat(memberIds(members(system, pkg))).containsExactly(good);
	}

	@Test
	void aDraftExamIsAllowedInThePackage() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk draft status shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk draft status package");
		UUID draft = standalone(system, category, "Still a draft");
		assertThat(fx.body(system, "/api/v1/live-exams/" + draft).get("status").asText()).isEqualTo("DRAFT");

		bulk(system, pkg, bulkBody(draft))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added[0]").value(draft.toString()))
			.andExpect(jsonPath("$.skipped").isEmpty());
		assertThat(memberIds(members(system, pkg))).containsExactly(draft);
	}

	@Test
	void onAPublishedPackageTheAdditionsAreStagedUntilThePackageIsPublished() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk staged shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk staged package");
		UUID original = fx.createExam(system, pkg, "Original", null);
		fx.addQuestion(system, original);
		fx.publishExam(system, original);
		fx.finalSaveAndPublish(system, pkg);
		UUID two = standalone(system, category, "Staged two");
		UUID one = standalone(system, category, "Staged one");

		bulk(system, pkg, bulkBody(two, one))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.added[0]").value(two.toString()))
			.andExpect(jsonPath("$.added[1]").value(one.toString()))
			.andExpect(jsonPath("$.pendingChanges").value(true));

		JsonNode members = members(system, pkg);
		assertThat(memberIds(members)).containsExactly(original, two, one);
		assertThat(members.get("pendingChanges").asBoolean()).isTrue();
		// The live list still shows only the published package; the staged exams wait for the Master Admin's publish.
		assertThat(PackageFixtures.ids(fx.content(system, "/api/v1/live-exam-modules/" + pkg + "/exams"))).containsExactly(original);
	}

	@Test
	void anEmptyOrMissingIdListIsRefusedWithValidationFailed() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk empty shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk empty package");

		bulk(system, pkg, "{\"examIds\":[]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		bulk(system, pkg, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void moreThanFiveHundredIdsOrANonUuidIsRefusedWithValidationFailed() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk limit shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk limit package");
		UUID[] tooMany = Stream.generate(UUID::randomUUID).limit(501).toArray(UUID[]::new);

		bulk(system, pkg, bulkBody(tooMany))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		bulk(system, pkg, "{\"examIds\":[\"not-a-uuid\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aStudentAndAnInstructorCannotBulkAddOrReadTheMembers() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk forbidden shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk forbidden package");
		UUID exam = standalone(system, category, "Not for them");
		String student = fx.token(fx.seed("STUDENT"));
		String instructor = fx.token(fx.seed("INSTRUCTOR"));

		for (String token : List.of(student, instructor)) {
			bulk(token, pkg, bulkBody(exam)).andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/live-exam-modules/" + pkg + "/exams/members").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		}
		assertThat(memberIds(members(system, pkg))).isEmpty();
	}

	@Test
	void anArchivedPackageRefusesABulkAddWithModuleArchived() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID category = fx.createCategory(system, "Bulk archived shelf");
		UUID pkg = fx.createPackage(system, category, "Bulk archived package");
		UUID exam = standalone(system, category, "Too late");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());

		bulk(system, pkg, bulkBody(exam))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("MODULE_ARCHIVED"));
	}

	/** An MCQ exam in {@code category} with no package: it joins a package only through the bulk add. */
	private UUID standalone(String token, UUID category, String titleEn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","examType":"MCQ","paperLanguage":"BN","titleEn":"%s","titleBn":"খাতা",
					 "descriptionEn":"English description","descriptionBn":"বাংলা বিবরণ",
					 "rulesEn":"English rules","rulesBn":"বাংলা নিয়ম","durationMinutes":40,"passPercent":50}
					""".formatted(category, titleEn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private ResultActions bulk(String token, UUID pkg, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/exams/bulk").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String bulkBody(UUID... ids) {
		return "{\"examIds\":[" + Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
	}

	private JsonNode members(String token, UUID pkg) throws Exception {
		return fx.body(token, "/api/v1/live-exam-modules/" + pkg + "/exams/members");
	}

	private static List<UUID> memberIds(JsonNode members) {
		List<UUID> ids = new ArrayList<>();
		members.get("examIds").forEach(node -> ids.add(UUID.fromString(node.asText())));
		return ids;
	}

}
