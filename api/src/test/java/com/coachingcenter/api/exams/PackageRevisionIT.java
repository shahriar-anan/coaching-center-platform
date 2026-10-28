package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A published package takes its edits as a staged revision that the Master Admin publishes (NR-01, E34 and B4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class PackageRevisionIT extends PostgresIntegrationTest {

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

	@Autowired
	private LiveExamAccess access;

	@Autowired
	private LiveExamRepository exams;

	private PackageFixtures fx;

	@BeforeEach
	void setUp() {
		clock.set(TEN_AM);
		fx = new PackageFixtures(mockMvc, json, dataSource, passwords);
	}

	@Test
	void studentReadsStayUnchangedUntilTheMasterAdminPublishes() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		String student = fx.token(fx.seed("STUDENT"));
		UUID pkg = publishedPackage(system, "Old title");

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"New title\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.titleEn").value("New title"))
			.andExpect(jsonPath("$.pendingChanges").value(true));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + pkg))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Old title"));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + pkg).header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.titleEn").value("Old title"));

		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("New title"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + pkg))
			.andExpect(jsonPath("$.titleEn").value("New title"));
	}

	@Test
	void theChangeListNamesEveryChangedFieldAndMembership() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID pkg = publishedPackage(system, "Listed title");
		UUID exam = fx.createExam(system, fx.createPackage(system, fx.createCategory(system, "Spare shelf"), "Spare"),
				"Moving paper", null);

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Revised title\"}"))
			.andExpect(status().isOk());
		// Only the Master Admin holds EXAM_PRICE, so the price is staged by the Master Admin.
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"price\":400,\"discountPercent\":20}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/exams/" + exam).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingChanges").value(true));

		JsonNode changes = fx.body(system, "/api/v1/live-exam-modules/" + pkg + "/pending-changes");
		assertThat(changes.get("revisionId").asText()).isNotBlank();
		assertThat(field(changes, "titleEn").get("before").asText()).isEqualTo("Listed title");
		assertThat(field(changes, "titleEn").get("after").asText()).isEqualTo("Revised title");
		assertThat(field(changes, "price").get("after").decimalValue()).isEqualByComparingTo("400");
		assertThat(field(changes, "discountPercent").get("after").asInt()).isEqualTo(20);
		assertThat(changes.hasNonNull("paper")).isFalse();
		assertThat(changes.get("impact").get("notifiesStudents").asBoolean()).isFalse();
		JsonNode members = changes.get("packageExams");
		assertThat(members).hasSize(1);
		assertThat(members.get(0).get("examId").asText()).isEqualTo(exam.toString());
		assertThat(members.get(0).get("change").asText()).isEqualTo("ADDED");
	}

	@Test
	void discardRemovesTheStagedRevisionAndAnEmptyPackageHasNothingToPublish() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID pkg = publishedPackage(system, "Clean title");

		mockMvc.perform(get("/api/v1/live-exam-modules/" + pkg + "/pending-changes").header("Authorization", "Bearer " + system))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + pkg + "/pending-changes").header("Authorization", "Bearer " + system))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NO_PENDING_CHANGES"));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NO_PENDING_CHANGES"));

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"descriptionEn\":\"Edited then dropped\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + pkg + "/pending-changes").header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system))
			.andExpect(jsonPath("$.descriptionEn").value("d"))
			.andExpect(jsonPath("$.pendingChanges").value(false));
	}

	@Test
	void aStaleRevisionIdIsRejectedAndTheCurrentOneIsPublished() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID pkg = publishedPackage(system, "Versioned title");
		patchTitle(system, pkg, "First edit");
		String first = fx.body(system, "/api/v1/live-exam-modules/" + pkg + "/pending-changes").get("revisionId").asText();
		patchTitle(system, pkg, "Second edit");

		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"%s\"}".formatted(UUID.randomUUID())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PENDING_CHANGES_STALE"));
		String current = fx.body(system, "/api/v1/live-exam-modules/" + pkg + "/pending-changes").get("revisionId").asText();
		assertThat(current).isNotEqualTo(first);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"%s\"}".formatted(current)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Second edit"));
	}

	@Test
	void aSystemAdminCanStageButOnlyTheMasterAdminPublishes() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID pkg = publishedPackage(system, "Staged by admin");
		patchTitle(system, pkg, "Awaiting master");

		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + pkg + "/pending-changes").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		patchTitle(system, pkg, "Awaiting master again");
		String revision = fx.body(system, "/api/v1/live-exam-modules/" + pkg + "/pending-changes").get("revisionId").asText();
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"revisionId\":\"%s\"}".formatted(revision)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.titleEn").value("Awaiting master again"));
	}

	@Test
	void aStagedMembershipGrantsAccessToHoldersOnlyOnceItIsPublished() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID pkg = publishedPackage(system, "Holders package");
		UUID elsewhere = fx.createPackage(system, fx.createCategory(system, "Other shelf"), "Elsewhere");
		// Starts at 10:10 so the question is still editable at TEN_AM, the clock of this test.
		UUID exam = fx.createExam(system, elsewhere, "Added later", "2026-10-20T10:10:00+06:00");
		fx.addQuestion(system, exam);
		fx.publishExam(system, exam);
		UUID holder = fx.seed("STUDENT");
		fx.markRoster(pkg, holder, TEN_AM.minusSeconds(86_400));

		assertThat(access.canSit(holder, exams.findById(exam).orElseThrow(), TEN_AM)).isFalse();
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/exams/" + exam).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		assertThat(access.canSit(holder, exams.findById(exam).orElseThrow(), TEN_AM)).isFalse();
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(access.canSit(holder, exams.findById(exam).orElseThrow(), TEN_AM)).isTrue();
	}

	/** The discount is the Master Admin's, as the price is (EXAM_PRICE): a System Admin cannot stage one (NR-01, B4). */
	@Test
	void aSystemAdminCannotStageADiscountButTheMasterAdminCan() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID pkg = publishedPackage(system, "Discounted package");

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"discountPercent\":15}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + pkg + "/pending-changes").header("Authorization", "Bearer " + system))
			.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"discountPercent\":15}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pendingChanges").value(true));
		assertThat(field(fx.body(system, "/api/v1/live-exam-modules/" + pkg + "/pending-changes"), "discountPercent")
			.get("after").asInt()).isEqualTo(15);
	}

	/** A package with a title and no edits waiting, published by the Master Admin's first publish path. */
	private UUID publishedPackage(String system, String titleEn) throws Exception {
		UUID category = fx.createCategory(system, "Revision shelf");
		UUID pkg = fx.createPackage(system, category, titleEn);
		fx.finalSaveAndPublish(system, pkg);
		return pkg;
	}

	private void patchTitle(String system, UUID pkg, String titleEn) throws Exception {
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"%s\"}".formatted(titleEn)))
			.andExpect(status().isOk());
	}

	private static JsonNode field(JsonNode changes, String name) {
		for (JsonNode change : changes.get("fields")) {
			if (name.equals(change.get("field").asText())) {
				return change;
			}
		}
		throw new AssertionError("No change to " + name);
	}

}
