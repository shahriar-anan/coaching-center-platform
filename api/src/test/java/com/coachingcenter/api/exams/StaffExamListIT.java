package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The staff exam list across packages: each filter, the packages each exam is in, and an instructor's own exams (NR-01, B3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class StaffExamListIT extends PostgresIntegrationTest {

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
	void eachFilterNarrowsTheListAndAnExamInTwoPackagesCarriesBoth() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID master = fx.seed("MASTER_ADMIN");
		String masterToken = fx.token(master);
		UUID shelf = fx.createCategory(system, "Filter shelf");
		UUID otherShelf = fx.createCategory(system, "Filter other shelf");
		UUID first = fx.createPackage(system, shelf, "Filter first");
		UUID second = fx.createPackage(system, otherShelf, "Filter second");

		// Questions can be added only before the window opens, so the setup runs at 03:30 UTC, then the clock returns to TEN_AM.
		clock.set(TEN_AM.minusSeconds(1800));
		UUID live = fx.createExam(system, first, "Live paper", "2026-10-20T10:00:00+06:00");
		UUID shared = fx.createExam(system, first, "Shared paper", null);
		UUID written = fx.createExam(system, first, "WRITTEN", "Written paper", null);
		UUID practice = fx.createExam(system, first, "Practice paper", null);
		fx.sql("update live_exams set mode = 'PRACTICE', live_at = null, ends_at = null where id = ?", practice);
		UUID moving = fx.createExam(system, second, "Moving paper", "2026-10-21T10:00:00+06:00");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + second + "/exams/" + shared).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + second + "/exams/" + moving).header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		fx.addQuestion(system, live);
		fx.publishExam(system, live);
		// The moving exam stays a draft: publishing a standalone exam is A's publish path, and no filter below needs it.
		clock.set(TEN_AM);
		mockMvc.perform(put("/api/v1/live-exams/" + live + "/popular").header("Authorization", "Bearer " + masterToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isOk());
		fx.sql("insert into live_exam_revisions (id, exam_id, payload, created_by, created_at, updated_by, updated_at) "
				+ "values (gen_random_uuid(), ?, '{}'::jsonb, ?, now(), ?, now())", shared, master, master);

		String base = "/api/v1/live-exams?categoryId=" + shelf;
		assertThat(ids(system, base)).containsExactlyInAnyOrder(live, shared, written, practice);
		assertThat(ids(system, base + "&packageId=" + second)).containsExactly(shared);
		assertThat(ids(system, base + "&type=WRITTEN")).containsExactly(written);
		assertThat(ids(system, base + "&mode=PRACTICE")).containsExactly(practice);
		assertThat(ids(system, base + "&status=PUBLISHED")).containsExactly(live);
		assertThat(ids(system, base + "&status=DRAFT")).containsExactlyInAnyOrder(shared, written, practice);
		assertThat(ids(system, base + "&phase=LIVE")).containsExactly(live);
		assertThat(ids(system, base + "&phase=UNANNOUNCED")).containsExactlyInAnyOrder(shared, written);
		assertThat(ids(system, base + "&phase=PRACTICE")).containsExactly(practice);
		assertThat(ids(system, base + "&phase=ENDED")).isEmpty();
		assertThat(ids(system, base + "&q=practice")).containsExactly(practice);
		assertThat(ids(system, base + "&pending=true")).containsExactly(shared);
		assertThat(ids(system, base + "&popular=true")).containsExactly(live);
		assertThat(ids(system, "/api/v1/live-exams?categoryId=" + otherShelf + "&standalone=true")).containsExactly(moving);
		assertThat(ids(system, "/api/v1/live-exams?categoryId=" + otherShelf + "&phase=UPCOMING")).containsExactly(moving);

		JsonNode shownShared = rowOf(system, base + "&packageId=" + second, shared);
		assertThat(shownShared.get("packages")).hasSize(2);
		assertThat(shownShared.get("pendingChanges").asBoolean()).isTrue();
		assertThat(shownShared.get("questionCount").asInt()).isZero();
		JsonNode shownLive = rowOf(system, base, live);
		assertThat(shownLive.get("questionCount").asInt()).isEqualTo(1);
		assertThat(shownLive.get("popular").asBoolean()).isTrue();
		assertThat(shownLive.get("phase").asText()).isEqualTo("LIVE");
		assertThat(shownLive.get("pricing").hasNonNull("effectivePrice")).isFalse();

		mockMvc.perform(get(base + "&status=ARCHIVED").header("Authorization", "Bearer " + system)).andExpect(status().isOk());
		mockMvc.perform(get(base + "&phase=SOMETIMES").header("Authorization", "Bearer " + system))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void anInstructorSeesOnlyTheExamsAssignedToThemAndAStudentIsRefused() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID instructor = fx.seed("INSTRUCTOR");
		UUID student = fx.seed("STUDENT");
		UUID shelf = fx.createCategory(system, "Instructor shelf");
		UUID pkg = fx.createPackage(system, shelf, "Instructor package");
		UUID mine = fx.createExam(system, pkg, "Mine", null);
		UUID theirs = fx.createExam(system, pkg, "Theirs", null);
		fx.sql("update live_exams set instructor_id = ? where id = ?", instructor, mine);

		String teacher = fx.token(instructor);
		String base = "/api/v1/live-exams?categoryId=" + shelf;
		assertThat(ids(teacher, base)).containsExactly(mine);
		assertThat(ids(system, base)).containsExactlyInAnyOrder(mine, theirs);
		assertThat(rowOf(teacher, base, mine).get("instructor").get("id").asText()).isEqualTo(instructor.toString());
		mockMvc.perform(get(base).header("Authorization", "Bearer " + fx.token(student)))
			.andExpect(status().isForbidden());
	}

	/** An instructor's row carries resultsReleased: false on a written exam until publish-results releases it, then true. */
	@Test
	void anInstructorRowShowsResultsReleasedOnlyAfterPublishResults() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID instructor = fx.seed("INSTRUCTOR");
		UUID studentId = fx.seed("STUDENT");
		UUID shelf = fx.createCategory(system, "Release shelf");
		UUID pkg = fx.createPackage(system, shelf, "Release package");
		UUID paper = fx.createExam(system, pkg, "WRITTEN", "Release paper", "2026-10-20T12:00:00+06:00");
		fx.sql("update live_exams set instructor_id = ? where id = ?", instructor, paper);
		mockMvc.perform(multipart("/api/v1/live-exams/" + paper + "/question-pdf")
			.file(new MockMultipartFile("file", "paper.pdf", "application/pdf", AuthoringFixture.tinyPdf("paper")))
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + paper + "/final-save").header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + paper + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		fx.finalSaveAndPublish(system, pkg);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":\"" + studentId + "\"}"))
			.andExpect(status().isCreated());

		String student = fx.token(studentId);
		String teacher = fx.token(instructor);
		String base = "/api/v1/live-exams?categoryId=" + shelf;
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + paper + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exams/" + paper + "/answer-pdf")
			.file(new MockMultipartFile("file", "answer.pdf", "application/pdf", realPdf()))
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isNoContent());
		clock.set(Instant.parse("2026-10-20T06:55:00Z"));
		UUID participant = new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class, paper, studentId);
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation")
			.file(new MockMultipartFile("file", "mark.pdf", "application/pdf", realPdf()))
			.param("marks", "6")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());

		assertThat(rowOf(teacher, base, paper).get("resultsReleased").asBoolean()).as("held until publish-results").isFalse();
		mockMvc.perform(post("/api/v1/live-exams/" + paper + "/publish-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(rowOf(teacher, base, paper).get("resultsReleased").asBoolean()).as("released by publish-results").isTrue();
	}

	/** A PDF that the server can parse and compress, as a student's answer or a marked sheet is. */
	private static byte[] realPdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.OVERWRITE, false)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(40, 700);
				stream.showText("Answer script");
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private List<UUID> ids(String token, String path) throws Exception {
		return PackageFixtures.ids(fx.content(token, path));
	}

	private JsonNode rowOf(String token, String path, UUID id) throws Exception {
		for (JsonNode row : fx.content(token, path)) {
			if (id.toString().equals(row.get("id").asText())) {
				return row;
			}
		}
		throw new AssertionError("No row " + id + " in " + path);
	}

}
