package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A1: the exam fields, the window apart from the duration, the standalone create, and the removed locks. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, ExamWindowAuthoringIT.ClockConfig.class })
class ExamWindowAuthoringIT extends PostgresIntegrationTest {

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
	void aWindowShorterThanTheDurationIsRejected() throws Exception {
		String staff = fx.staff();
		UUID module = fx.module(staff, "Short window");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "Too short", NOON, 40, "\"endAt\":\"2026-10-20T12:30:00+06:00\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_INVALID"));

		UUID exam = fx.createExam(staff, module, "MCQ", "Window ok", NOON, 40, "\"endAt\":\"2026-10-20T18:00:00+06:00\"");
		JsonNode created = fx.exam(staff, exam);
		assertThat(created.get("liveAt").asText()).isEqualTo(NOON);
		assertThat(created.get("endAt").asText()).isEqualTo("2026-10-20T18:00:00+06:00");
		assertThat(created.get("durationMinutes").asInt()).isEqualTo(40);

		// Shortening the window below the duration on an edit is rejected too, and nothing changes.
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"endAt\":\"2026-10-20T12:20:00+06:00\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_INVALID"));
		assertThat(fx.exam(staff, exam).get("endAt").asText()).isEqualTo("2026-10-20T18:00:00+06:00");
		// A window of exactly one duration is allowed.
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"endAt\":\"2026-10-20T12:40:00+06:00\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void aStartWithoutAnEndFillsTheEndFromTheDurationAndTheDurationMovesALegacyEnd() throws Exception {
		String staff = fx.staff();
		UUID module = fx.module(staff, "Compat");
		UUID exam = fx.createExam(staff, module, "MCQ", "Start only", NOON, 40, null);
		assertThat(fx.exam(staff, exam).get("endAt").asText()).isEqualTo("2026-10-20T12:40:00+06:00");

		// The current web sends only liveAt: the end follows it.
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T13:00:00+06:00\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.endAt").value("2026-10-20T13:40:00+06:00"));
		// A longer duration on a window that was one duration long moves the end with it.
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"durationMinutes\":60}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.endAt").value("2026-10-20T14:00:00+06:00"));

		UUID wide = fx.createExam(staff, module, "MCQ", "Wide", NOON, 40, "\"endAt\":\"2026-10-20T20:00:00+06:00\"");
		// A wide window stays wide when only the duration changes.
		mockMvc.perform(patch("/api/v1/live-exams/" + wide).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"durationMinutes\":30}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.endAt").value("2026-10-20T20:00:00+06:00"));
		// A window with an end and no start is not a window.
		mockMvc.perform(patch("/api/v1/live-exams/" + wide).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":null,\"endAt\":\"2026-10-20T20:00:00+06:00\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_INVALID"));
	}

	@Test
	void aPracticeExamHasNoWindowAndIsPublishedOpen() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Practice");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "Dated practice", NOON, 40, "\"mode\":\"PRACTICE\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_INVALID"));

		UUID practice = fx.createExam(staff, module, "MCQ", "Practice", null, 40, "\"mode\":\"PRACTICE\"");
		JsonNode created = fx.exam(staff, practice);
		assertThat(created.get("mode").asText()).isEqualTo("PRACTICE");
		assertThat(created.get("phase").asText()).isEqualTo("PRACTICE");
		assertThat(created.has("liveAt")).isFalse();
		fx.question(staff, practice, "Stem", 1);
		fx.publishNew(master, practice);
		assertThat(fx.jdbc().queryForObject("select published_at is not null from live_exams where id = ?", Boolean.class,
				practice)).isTrue();

		// A practice exam cannot be given a window.
		mockMvc.perform(patch("/api/v1/live-exams/" + practice).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"" + NOON + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_INVALID"));
	}

	@Test
	void theModeChangesOnlyOnADraftNobodyHasStarted() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Mode");
		UUID draft = fx.createExam(staff, module, "MCQ", "Draft", NOON, 40, null);
		mockMvc.perform(patch("/api/v1/live-exams/" + draft).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"mode\":\"PRACTICE\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mode").value("PRACTICE"))
			.andExpect(jsonPath("$.liveAt").doesNotExist());
		mockMvc.perform(patch("/api/v1/live-exams/" + draft).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"mode\":\"SOMETIMES\"}"))
			.andExpect(status().isBadRequest());

		fx.question(staff, draft, "Stem", 1);
		fx.publishNew(master, draft);
		mockMvc.perform(patch("/api/v1/live-exams/" + draft).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"mode\":\"LIVE\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("mode"));
		// Saying the mode it already has is no change.
		mockMvc.perform(patch("/api/v1/live-exams/" + draft).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"mode\":\"PRACTICE\",\"titleEn\":\"Renamed\"}"))
			.andExpect(status().isOk());
	}

	@Test
	void aPublishedExamTakesEditsToTheScheduleDurationPassPercentAndPaperLanguageBeforeDuringAndAfterTheWindow()
			throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Edits");
		UUID exam = fx.createExam(staff, module, "MCQ", "Editable", NOON, 40, "\"endAt\":\"2026-10-20T18:00:00+06:00\"");
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(master, exam);

		for (String now : List.of("2026-10-20T04:00:00Z", "2026-10-20T07:00:00Z", "2026-10-21T04:00:00Z")) {
			clock.set(Instant.parse(now));
			mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"durationMinutes\":45,\"passPercent\":60,\"paperLanguage\":\"EN\","
						+ "\"liveAt\":\"2026-10-25T12:00:00+06:00\",\"endAt\":\"2026-10-25T20:00:00+06:00\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"))
				.andExpect(jsonPath("$.durationMinutes").value(45))
				.andExpect(jsonPath("$.passPercent").value(60))
				.andExpect(jsonPath("$.paperLanguage").value("EN"))
				.andExpect(jsonPath("$.liveAt").value("2026-10-25T12:00:00+06:00"));
			mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"durationMinutes\":40,\"passPercent\":50,\"paperLanguage\":\"BN\"}"))
				.andExpect(status().isOk());
		}
	}

	@Test
	void anEndedWindowPublishesAndTwoOverlappingWindowsInOnePackageBothPublish() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Overlap");
		UUID first = fx.createExam(staff, module, "MCQ", "First", NOON, 40, "\"endAt\":\"2026-10-20T18:00:00+06:00\"");
		fx.question(staff, first, "Stem", 1);
		UUID second = fx.createExam(staff, module, "MCQ", "Second", "2026-10-20T12:10:00+06:00", 40,
				"\"endAt\":\"2026-10-20T18:00:00+06:00\"");
		fx.question(staff, second, "Stem", 1);
		UUID ended = fx.createExam(staff, module, "MCQ", "Ended", "2026-10-19T12:00:00+06:00", 40, null);
		fx.question(staff, ended, "Stem", 1);
		fx.publishNew(master, first);
		fx.publishNew(master, second);
		clock.set(Instant.parse("2026-10-21T04:00:00Z"));
		fx.publishNew(master, ended);
		assertThat(fx.exam(staff, ended).get("phase").asText()).isEqualTo("ENDED");
	}

	@Test
	void aStandaloneExamIsCreatedInAnOpenCategoryAndBelongsToNoPackage() throws Exception {
		String staff = fx.staff();
		UUID category = fx.category(staff, "Standalone");
		UUID exam = fx.createStandalone(staff, category, "MCQ", "Alone", NOON, 40, null);
		JsonNode created = fx.exam(staff, exam);
		assertThat(created.get("categoryId").asText()).isEqualTo(category.toString());
		assertThat(created.has("moduleId")).isFalse();
		assertThat(created.get("packages")).isEmpty();
		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_package_exams where exam_id = ?", Integer.class,
				exam)).isZero();

		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "No category", NOON, 40, null)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "Unknown category", NOON, 40,
					"\"categoryId\":\"" + UUID.randomUUID() + "\"")))
			.andExpect(status().isNotFound());

		UUID archived = fx.category(staff, "Archived");
		mockMvc.perform(post("/api/v1/live-exam-categories/" + archived + "/archive").header("Authorization", "Bearer " + fx.master()))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "In archive", NOON, 40, "\"categoryId\":\"" + archived + "\"")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));

		String student = token("STUDENT");
		mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "By student", NOON, 40, "\"categoryId\":\"" + category + "\"")))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/live-exams").contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "Anonymous", NOON, 40, "\"categoryId\":\"" + category + "\"")))
			.andExpect(status().isUnauthorized());

		// A standalone exam publishes: it needs no package (R2).
		fx.question(staff, exam, "Stem", 1);
		fx.publishNew(fx.master(), exam);
	}

	@Test
	void anExamCreatedInAPackageTakesItsCategoryItsMembershipAndItsDefaultInstructor() throws Exception {
		String staff = fx.staff();
		UUID instructor = fx.seed("INSTRUCTOR");
		UUID category = fx.category(staff, "Inherit");
		UUID module = fx.moduleIn(staff, category, "Inherit pack");
		fx.jdbc().update("insert into live_exam_instructors (module_id, instructor_id, assigned_at) values (?, ?, now())",
				module, instructor);
		UUID exam = fx.createExam(staff, module, "MCQ", "Inherits", NOON, 40, null);
		JsonNode created = fx.exam(staff, exam);
		assertThat(created.get("categoryId").asText()).isEqualTo(category.toString());
		assertThat(created.get("moduleId").asText()).isEqualTo(module.toString());
		assertThat(created.get("packages")).hasSize(1);
		assertThat(created.get("packages").get(0).get("id").asText()).isEqualTo(module.toString());
		assertThat(created.get("instructor").get("id").asText()).isEqualTo(instructor.toString());
		assertThat(fx.jdbc().queryForObject(
				"select count(*) from live_exam_package_exams where exam_id = ? and module_id = ? and removed_at is null",
				Integer.class, exam, module)).isEqualTo(1);
	}

	@Test
	void theNewFieldsRoundTripAndPopularIsIgnored() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID module = fx.module(staff, "Fields");
		String extra = "\"negativeMarks\":0.25,\"answersChangeable\":false,\"previewEnabled\":true,\"price\":200,"
				+ "\"discountPercent\":30,\"discountEndsAt\":\"2026-11-01T00:00:00Z\",\"accessDays\":90,\"popular\":true";
		// The System Admin cannot set commercial fields; the Master Admin can.
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content(AuthoringFixture.examBody("MCQ", "Priced by staff", NOON, 40, extra)))
			.andExpect(status().isForbidden());
		UUID exam = fx.createExam(master, module, "MCQ", "Fields", NOON, 40, extra);
		JsonNode view = fx.exam(staff, exam);
		assertThat(view.get("negativeMarks").decimalValue()).isEqualByComparingTo("0.25");
		assertThat(view.get("answersChangeable").asBoolean()).isFalse();
		assertThat(view.get("previewEnabled").asBoolean()).isTrue();
		assertThat(view.get("accessDays").asInt()).isEqualTo(90);
		assertThat(view.get("popular").asBoolean()).isFalse();
		assertThat(view.get("price").decimalValue()).isEqualByComparingTo("200");
		JsonNode pricing = view.get("pricing");
		assertThat(pricing.get("discountPercent").asInt()).isEqualTo(30);
		assertThat(pricing.get("effectivePrice").decimalValue()).isEqualByComparingTo("140");
		assertThat(pricing.get("free").asBoolean()).isFalse();
		assertThat(fx.jdbc().queryForObject("select popular from live_exams where id = ?", Boolean.class, exam)).isFalse();

		// A new exam defaults to "answers can be changed" on and no negative marking (P-c).
		UUID plain = fx.createExam(staff, module, "MCQ", "Plain", NOON, 40, null);
		JsonNode defaults = fx.exam(staff, plain);
		assertThat(defaults.get("answersChangeable").asBoolean()).isTrue();
		assertThat(defaults.has("negativeMarks")).isFalse();
		assertThat(defaults.get("previewEnabled").asBoolean()).isFalse();
		assertThat(defaults.get("mode").asText()).isEqualTo("LIVE");

		mockMvc.perform(patch("/api/v1/live-exams/" + plain).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"negativeMarks\":0.5,\"answersChangeable\":false,\"previewEnabled\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.negativeMarks").value(0.5))
			.andExpect(jsonPath("$.answersChangeable").value(false))
			.andExpect(jsonPath("$.previewEnabled").value(true));
		mockMvc.perform(patch("/api/v1/live-exams/" + plain).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"negativeMarks\":-1}"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/v1/live-exams/" + plain).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void pricingRulesAreCheckedAsAWholeAndReportedPerField() throws Exception {
		String master = fx.master();
		UUID module = fx.module(master, "Pricing");
		UUID exam = fx.createExam(master, module, "MCQ", "Pricing", NOON, 40, "\"price\":100");
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("price"));
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"discountPercent\":100}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("discountPercent"));
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"discountEndsAt\":\"2026-11-01T00:00:00Z\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("discountEndsAt"));
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"accessDays\":0}"))
			.andExpect(status().isBadRequest());
		// Free is fine once the price is cleared in the same request.
		mockMvc.perform(patch("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"free\":true,\"price\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pricing.free").value(true))
			.andExpect(jsonPath("$.price").doesNotExist());
	}

	@Test
	void deletingAnExamRemovesItFromEveryPackageAndCopyingKeepsEveryPackage() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "Copy cat");
		UUID first = fx.moduleIn(staff, category, "Copy one");
		UUID second = fx.moduleIn(staff, category, "Copy two");
		UUID exam = fx.createExam(master, first, "MCQ", "Source", NOON, 40,
				"\"price\":150,\"negativeMarks\":0.25,\"previewEnabled\":true,\"accessDays\":30");
		fx.jdbc().update("""
				insert into live_exam_package_exams (id, module_id, exam_id, position, added_at)
				values (gen_random_uuid(), ?, ?, 0, now())
				""", second, exam);
		fx.question(staff, exam, "Single", 2);

		var result = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/copy").header("Authorization", "Bearer " + staff))
			.andExpect(status().isCreated())
			.andReturn();
		UUID copy = fx.id(result);
		JsonNode copied = fx.exam(staff, copy);
		assertThat(copied.get("status").asText()).isEqualTo("DRAFT");
		assertThat(copied.get("categoryId").asText()).isEqualTo(category.toString());
		assertThat(copied.get("packages")).hasSize(2);
		assertThat(copied.has("liveAt")).isFalse();
		assertThat(copied.has("endAt")).isFalse();
		assertThat(copied.has("price")).isFalse();
		assertThat(copied.get("popular").asBoolean()).isFalse();
		assertThat(copied.get("negativeMarks").decimalValue()).isEqualByComparingTo("0.25");
		assertThat(copied.get("previewEnabled").asBoolean()).isTrue();
		assertThat(copied.get("accessDays").asInt()).isEqualTo(30);
		assertThat(copied.get("questions")).hasSize(1);

		mockMvc.perform(delete("/api/v1/live-exams/" + copy).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(fx.jdbc().queryForObject(
				"select count(*) from live_exam_package_exams where exam_id = ? and removed_at is null", Integer.class, copy))
			.isZero();
		assertThat(fx.jdbc().queryForObject(
				"select count(*) from live_exam_package_exams where exam_id = ? and removed_at is not null", Integer.class,
				copy)).isEqualTo(2);
	}

	@Test
	void aPaperVersionRecordsWhoUploadedIt() throws Exception {
		UUID uploader = fx.seed("SYSTEM_ADMIN");
		String staff = fx.token(uploader);
		UUID module = fx.module(staff, "Paper author");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Paper", NOON, 40, null);
		fx.uploadPdf(staff, exam);
		fx.uploadPdf(staff, exam, "two");
		assertThat(fx.jdbc().queryForList(
				"select created_by from live_exam_question_papers where exam_id = ? order by version", UUID.class, exam))
			.containsExactly(uploader, uploader);
		var copy = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/copy").header("Authorization", "Bearer " + staff))
			.andExpect(status().isCreated())
			.andReturn();
		assertThat(fx.jdbc().queryForObject(
				"select created_by from live_exam_question_papers where exam_id = ?", UUID.class, fx.id(copy)))
			.isEqualTo(uploader);
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(jsonPath("$.questionPdf").value(true));
	}

	private String token(String role) throws Exception {
		return fx.token(fx.seed(role));
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
