package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.ObjectMapper;

/** Packages hold existing exams in any category, in an order, and a removal keeps access for holders (NR-01, R2, R4, B1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class PackageMembershipIT extends PostgresIntegrationTest {

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
	void theSameExamInTwoPackagesIsReadFromEachOne() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Shared shelf");
		UUID first = fx.createPackage(system, category, "First package");
		UUID second = fx.createPackage(system, category, "Second package");
		UUID exam = fx.createExam(system, first, "Shared exam", null);
		fx.addQuestion(system, exam);
		fx.publishExam(system, exam);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + second + "/exams/" + exam)
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.examIds[0]").value(exam.toString()));
		fx.finalSaveAndPublish(system, first);
		fx.finalSaveAndPublish(system, second);

		assertThat(PackageFixtures.ids(fx.content(system, "/api/v1/public/live-exam-modules/" + first + "/exams")))
			.containsExactly(exam);
		assertThat(PackageFixtures.ids(fx.content(system, "/api/v1/public/live-exam-modules/" + second + "/exams")))
			.containsExactly(exam);
		assertThat(PackageFixtures.ids(fx.content(system, "/api/v1/live-exams?packageId=" + first))).containsExactly(exam);
		assertThat(PackageFixtures.ids(fx.content(system, "/api/v1/live-exams?packageId=" + second))).containsExactly(exam);
	}

	@Test
	void aDuplicateAddIsRejectedAndTheExamStaysOnce() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Duplicate shelf");
		UUID pkg = fx.createPackage(system, category, "Only package");
		UUID exam = fx.createExam(system, pkg, "Once only", null);

		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/exams/" + exam)
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_ALREADY_IN_PACKAGE"));
		assertThat(PackageFixtures.ids(fx.content(system, "/api/v1/live-exams?packageId=" + pkg))).containsExactly(exam);
	}

	@Test
	void aRemovedExamCanBeAddedBackAndTheOrderFollowsThePositions() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Order shelf");
		UUID pkg = fx.createPackage(system, category, "Ordered package");
		UUID one = fx.createExam(system, pkg, "One", null);
		UUID two = fx.createExam(system, pkg, "Two", null);
		UUID three = fx.createExam(system, pkg, "Three", null);
		for (UUID exam : List.of(one, two, three)) {
			fx.addQuestion(system, exam);
			fx.publishExam(system, exam);
		}
		fx.finalSaveAndPublish(system, pkg);
		String public1 = "/api/v1/public/live-exam-modules/" + pkg + "/exams";

		String master = fx.token(fx.seed("MASTER_ADMIN"));
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg + "/exams/order").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"examIds":["%s","%s","%s"]}
					""".formatted(three, one, two)))
			.andExpect(status().isOk());
		// The package is published, so the order is staged until the Master Admin publishes it (NR-01, B1 and B4).
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, public1)), one, two, three);
		publishPackage(master, pkg);
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, public1)), three, one, two);

		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg + "/exams/order").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"examIds":["%s","%s"]}
					""".formatted(one, two)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + pkg + "/exams/" + one).header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		publishPackage(master, pkg);
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, public1)), three, two);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/exams/" + one).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		publishPackage(master, pkg);
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, public1)), three, two, one);
	}

	private void publishPackage(String master, UUID pkg) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
	}

	@Test
	void aStudentWhoHeldThePackageBeforeARemovalKeepsTheExamAndALaterHolderDoesNot() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Access shelf");
		UUID pkg = fx.createPackage(system, category, "Access package");
		// Starts at 10:10 (TEN_AM plus ten minutes), so the questions are still editable when the test adds them.
		UUID exam = fx.createExam(system, pkg, "Kept paper", "2026-10-20T10:10:00+06:00");
		fx.addQuestion(system, exam);
		fx.publishExam(system, exam);
		fx.finalSaveAndPublish(system, pkg);

		UUID earlyHolder = fx.seed("STUDENT");
		UUID lateHolder = fx.seed("STUDENT");
		fx.markRoster(pkg, earlyHolder, TEN_AM.minusSeconds(86_400));
		// Removal is staged on the published package, then the Master Admin publishes it, as the removal takes effect.
		clock.set(TEN_AM.minusSeconds(1800));
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + pkg + "/exams/" + exam).header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		fx.markRoster(pkg, lateHolder, TEN_AM.minusSeconds(600));

		assertThat(access.canSit(earlyHolder, exams.findById(exam).orElseThrow(), TEN_AM.plusSeconds(900))).isTrue();
		assertThat(access.canSit(lateHolder, exams.findById(exam).orElseThrow(), TEN_AM.plusSeconds(900))).isFalse();

		clock.set(TEN_AM.plusSeconds(900));
		String early = fx.token(earlyHolder);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + early)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		String late = fx.token(lateHolder);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + late)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isNotFound());
	}

}
