package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.files.FileStorageProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveEvaluationIT.ClockConfig.class })
class LiveEvaluationIT extends PostgresIntegrationTest {

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
	private FileStorageProperties files;

	@BeforeEach
	void setClock() {
		clock.set(TEN_AM);
	}

	@Test
	void creatingAnInstructorRejectsAPasswordAndActivationSignsThemIn() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String phone = uniquePhone();
		String email = uniqueEmail();
		mockMvc.perform(post("/api/v1/instructors").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Nadia Rahman","phone":"%s","email":"%s","password":"secret123"}
					""".formatted(phone, email)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PASSWORD_NOT_ACCEPTED"));

		MvcResult created = mockMvc.perform(post("/api/v1/instructors").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"Nadia Rahman","phone":"%s","email":"%s"}
					""".formatted(phone, email)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("INSTRUCTOR"))
			.andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
			.andExpect(jsonPath("$.activationCode").isNotEmpty())
			.andReturn();
		String code = json.readTree(created.getResponse().getContentAsString()).get("activationCode").asText();

		mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"%s","password":"password1"}
					""".formatted(phone, code)))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void aSecondAssignmentIsRejectedAndASystemAdminCannotAssignOrMark() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		UUID module = publishModule(token(seed("SYSTEM_ADMIN", "Staff")), "Assign");
		Instructor first = instructor(master, "First Instructor");
		Instructor second = instructor(master, "Second Instructor");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(first.id())))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(second.id())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTRUCTOR_ALREADY_ASSIGNED"));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(second.id())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

		UUID exam = writtenExam(token(seed("SYSTEM_ADMIN", "Paper")), module);
		Student student = seedStudent("Submitted Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void anInstructorOpensOnlyTheAssignedModule() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID assigned = publishModule(staff, "Assigned");
		UUID other = publishModule(staff, "Other");
		Instructor instructor = instructor(master, "Module Instructor");
		assign(master, assigned, instructor.id());
		UUID assignedExam = writtenExam(staff, assigned);
		UUID otherExam = writtenExam(staff, other);
		Student one = seedStudent("Assigned Student");
		Student two = seedStudent("Other Student");
		mark(master, assigned, one.id());
		mark(master, other, two.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(one, assignedExam);
		upload(one, assignedExam);
		UUID otherParticipant = take(two, otherExam);
		upload(two, otherExam);

		mockMvc.perform(get("/api/v1/live-exam-modules/" + assigned + "/submissions")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].studentName").value("Assigned Student"));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + other + "/submissions")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-participants/" + otherParticipant)
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + other + "/submissions").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].studentName").value("Other Student"));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + assigned + "/submissions").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
	}

	@Test
	void aFailedToSubmitRowIsListedAndCannotBeMarked() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Missing");
		Instructor instructor = instructor(master, "Marker");
		assign(master, module, instructor.id());
		UUID exam = writtenExam(staff, module);
		Student submitted = seedStudent("Has PDF");
		Student missing = seedStudent("No PDF");
		mark(master, module, submitted.id());
		mark(master, module, missing.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(submitted, exam);
		upload(submitted, exam);
		take(missing, exam);
		clock.set(Instant.parse("2026-10-20T06:46:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + missing.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED_TO_SUBMIT"));

		MvcResult listed = mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/submissions")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode rows = json.readTree(listed.getResponse().getContentAsString());
		UUID failedId = null;
		for (JsonNode row : rows) {
			if ("No PDF".equals(row.get("studentName").asText())) {
				assertThat(row.get("status").asText()).isEqualTo("FAILED_TO_SUBMIT");
				failedId = UUID.fromString(row.get("id").asText());
			}
		}
		assertThat(failedId).isNotNull();
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + failedId + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVALUATION_NOT_ALLOWED"));

		String board = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + missing.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(board).contains("No PDF");
		assertThat(board).doesNotContain("Has PDF");
		assertThat(json.readTree(board).get("rows").get(0).get("score").asInt()).isZero();
	}

	@Test
	void aLaterMarkReplacesThePdfAndChangesTheRank() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Marks");
		Instructor instructor = instructor(master, "Nadia Rahman");
		assign(master, module, instructor.id());
		UUID exam = writtenExam(staff, module);
		Student higher = seedStudent("Higher Paper");
		Student lower = seedStudent("Lower Paper");
		mark(master, module, higher.id());
		mark(master, module, lower.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID higherId = take(higher, exam);
		upload(higher, exam);
		UUID lowerId = take(lower, exam);
		upload(lower, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + lower.token()))
			.andExpect(status().isOk());

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + higherId + "/evaluation").file(sheet("first.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evaluatorName").value("Nadia Rahman"))
			.andExpect(jsonPath("$.score").value(7));
		String firstFile = evaluatedFile(higherId);
		String studentView = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(7))
			.andExpect(jsonPath("$.maximum").value(10))
			.andExpect(jsonPath("$.passed").value(true))
			.andExpect(jsonPath("$.evaluatedPdf").value(true))
			.andExpect(jsonPath("$.comment").doesNotExist())
			.andExpect(jsonPath("$.evaluatorName").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(studentView).doesNotContain("Nadia Rahman", instructor.id().toString());
		byte[] pdf = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/evaluated-pdf")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(pdf).startsWith("%PDF".getBytes());

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + lowerId + "/evaluation").file(sheet("peer.pdf"))
			.param("marks", "6")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:46:00Z"));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + higherId + "/evaluation").file(sheet("second.pdf"))
			.param("marks", "4")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(4));
		assertThat(java.nio.file.Path.of(files.storageDir(), firstFile)).doesNotExist();
		assertThat(evaluatedFile(higherId)).isNotEqualTo(firstFile);

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(4))
			.andExpect(jsonPath("$.passed").value(false));
		JsonNode board = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(rank(board, "Higher Paper")).isEqualTo(2);
		assertThat(rank(board, "Lower Paper")).isEqualTo(1);
	}

	@Test
	void theMcqBoardWaitsFiveMinutesAndSkipsTiedRanks() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Board");
		Paper paper = mcq(staff, module);
		Student first = seedStudent("Tie One");
		Student second = seedStudent("Tie Two");
		Student blank = seedStudent("Blank Paper");
		Student retake = seedStudent("Retake Paper");
		mark(master, module, first.id());
		mark(master, module, second.id());
		mark(master, module, blank.id());
		mark(master, module, retake.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(first, paper.examId());
		take(second, paper.examId());
		take(blank, paper.examId());
		saveCorrect(first, paper);
		saveCorrect(second, paper);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit").header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit").header("Authorization", "Bearer " + second.token()))
			.andExpect(status().isOk());

		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + paper.examId() + "/leaderboard")
			.header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows").isEmpty());
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/retake").header("Authorization", "Bearer " + retake.token()))
			.andExpect(status().isOk());
		saveCorrect(retake, paper);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit").header("Authorization", "Bearer " + retake.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(10));

		clock.set(Instant.parse("2026-10-20T06:46:00Z"));
		String board = mockMvc.perform(get("/api/v1/live-exams/" + paper.examId() + "/leaderboard")
			.header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(board).doesNotContain("Retake Paper", first.phone(), "evaluator");
		JsonNode rows = json.readTree(board);
		assertThat(rank(rows, "Tie One")).isEqualTo(1);
		assertThat(rank(rows, "Tie Two")).isEqualTo(1);
		assertThat(rank(rows, "Blank Paper")).isEqualTo(3);
		assertThat(rows.get("rows")).hasSize(3);
	}

	@Test
	void anInstructorListsOnlyAssignedModules() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID assigned = publishModule(staff, "Kept Module");
		UUID other = publishModule(staff, "Other Module");
		Instructor instructor = instructor(master, "List Instructor");
		assign(master, assigned, instructor.id());

		JsonNode page = json.readTree(mockMvc.perform(get("/api/v1/live-exam-modules")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(page.get("content")).hasSize(1);
		assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(assigned.toString());
		assertThat(page.get("content").get(0).get("titleEn").asText()).isEqualTo("Kept Module");
		assertThat(page.toString()).doesNotContain(other.toString());
	}

	@Test
	void theAnswerPdfDownloadsForTheMarkerAndRejectsEveryoneElse() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Sheets");
		UUID elsewhere = publishModule(staff, "Elsewhere");
		Instructor marker = instructor(master, "Sheet Marker");
		Instructor other = instructor(master, "Other Marker");
		assign(master, module, marker.id());
		assign(master, elsewhere, other.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Sheet Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);

		byte[] instructorBytes = mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/answer-pdf")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(instructorBytes).startsWith("%PDF".getBytes());
		byte[] masterBytes = mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/answer-pdf")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(masterBytes).startsWith("%PDF".getBytes());
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/answer-pdf")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/answer-pdf")
			.header("Authorization", "Bearer " + other.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant + "/answer-pdf")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isForbidden());
	}

	@Test
	void aSystemAdminReadsTheEvaluatorAndCannotMark() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Visible");
		Instructor marker = instructor(master, "Visible Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Visible Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evaluatorName").value("Visible Marker"))
			.andExpect(jsonPath("$.score").value(7));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("again.pdf"))
			.param("marks", "4")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void aCommentReachesTheStudentAndABlankCommentStaysAbsent() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Comments");
		Instructor marker = instructor(master, "Comment Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Comment Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "7")
			.param("comment", "Noted")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		String withComment = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(7))
			.andExpect(jsonPath("$.comment").value("Noted"))
			.andExpect(jsonPath("$.evaluatorName").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(withComment).doesNotContain("Comment Marker", marker.id().toString());

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("next.pdf"))
			.param("marks", "7")
			.param("comment", "   ")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		String blank = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comment").doesNotExist())
			.andExpect(jsonPath("$.evaluatorName").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(blank).doesNotContain("Comment Marker", marker.id().toString());
	}

	@Test
	void aWrittenRetakeCanBeMarkedAndStaysOffTheLiveBoard() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Retake mark");
		Instructor marker = instructor(master, "Retake Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student missed = seedStudent("Missed Live");
		Student later = seedStudent("Later Retake");
		mark(master, module, missed.id());
		mark(master, module, later.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(missed, exam);
		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		UUID retakeId = takeRetake(later, exam);
		upload(later, exam);
		clock.set(Instant.parse("2026-10-20T06:46:00Z"));

		String before = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + missed.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(before).contains("Missed Live");
		assertThat(before).doesNotContain("Later Retake");
		assertThat(json.readTree(before).get("rows").get(0).get("score").asInt()).isZero();

		clock.set(Instant.parse("2026-10-20T07:21:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + later.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.score").doesNotExist());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + retakeId + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "8")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(8));
		String after = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + missed.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(after).contains("Missed Live");
		assertThat(after).doesNotContain("Later Retake");
		assertThat(json.readTree(after).get("rows")).hasSize(1);
	}

	@Test
	void theSubmissionListPostsTheDeadlineWithoutASubmitCall() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Unsent");
		Instructor marker = instructor(master, "Deadline Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student stored = seedStudent("Stored Sheet");
		Student missing = seedStudent("Missing Sheet");
		mark(master, module, stored.id());
		mark(master, module, missing.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID storedId = take(stored, exam);
		upload(stored, exam);
		UUID missingId = take(missing, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));

		JsonNode rows = json.readTree(mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/submissions")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(statusOf(rows, "Stored Sheet")).isEqualTo("SUBMITTED");
		assertThat(statusOf(rows, "Missing Sheet")).isEqualTo("FAILED_TO_SUBMIT");
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + missingId + "/evaluation").file(sheet("no.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVALUATION_NOT_ALLOWED"));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + storedId + "/evaluation").file(sheet("yes.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(7));
	}

	@Test
	void openingOrMarkingADuePaperPostsItWithoutTheSubmissionList() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Direct");
		Instructor marker = instructor(master, "Direct Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student stored = seedStudent("Direct Stored");
		Student missing = seedStudent("Direct Missing");
		mark(master, module, stored.id());
		mark(master, module, missing.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID storedId = take(stored, exam);
		upload(stored, exam);
		UUID missingId = take(missing, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + storedId + "/evaluation").file(sheet("yes.pdf"))
			.param("marks", "8")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.score").value(8));
		mockMvc.perform(get("/api/v1/live-exam-participants/" + missingId).header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED_TO_SUBMIT"))
			.andExpect(jsonPath("$.score").value(0));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + missingId + "/evaluation").file(sheet("no.pdf"))
			.param("marks", "8")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVALUATION_NOT_ALLOWED"));
	}

	private String statusOf(JsonNode rows, String name) {
		for (JsonNode row : rows) {
			if (name.equals(row.get("studentName").asText())) {
				return row.get("status").asText();
			}
		}
		throw new AssertionError("Missing " + name);
	}

	private UUID takeRetake(Student student, UUID examId) throws Exception {
		MvcResult taken = mockMvc.perform(post("/api/v1/live-exams/" + examId + "/retake")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andReturn();
		return UUID.fromString(json.readTree(taken.getResponse().getContentAsString()).get("id").asText());
	}

	private int rank(JsonNode board, String name) {
		for (JsonNode row : board.get("rows")) {
			if (name.equals(row.get("name").asText())) {
				return row.get("rank").asInt();
			}
		}
		throw new AssertionError("Missing " + name);
	}

	private Instructor instructor(String master, String name) throws Exception {
		String phone = uniquePhone();
		String email = uniqueEmail();
		MvcResult created = mockMvc.perform(post("/api/v1/instructors").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fullName":"%s","phone":"%s","email":"%s"}
					""".formatted(name, phone, email)))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = json.readTree(created.getResponse().getContentAsString());
		String code = body.get("activationCode").asText();
		mockMvc.perform(post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"phone":"%s","code":"%s","password":"password1"}
					""".formatted(phone, code)))
			.andExpect(status().isNoContent());
		String access = tokenFor(phone);
		return new Instructor(UUID.fromString(body.get("id").asText()), access);
	}

	private void assign(String master, UUID moduleId, UUID instructorId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + moduleId + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"instructorId":"%s"}
					""".formatted(instructorId)))
			.andExpect(status().isCreated());
	}

	private UUID writtenExam(String staff, UUID module) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"WRITTEN","titleEn":"Noon paper","titleBn":"খাতা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50,"maximum":10
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/question-pdf").file(new MockMultipartFile("file",
			"paper.pdf", "application/pdf", tinyPdf()))
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		return exam;
	}

	private Paper mcq(String staff, UUID module) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","titleEn":"Noon quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":50
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		MvcResult question = mockMvc
			.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"stem":"Ten","marks":10,"explanation":"Because","options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = json.readTree(question.getResponse().getContentAsString());
		UUID correct = null;
		for (JsonNode option : body.get("options")) {
			if (option.get("correct").asBoolean()) {
				correct = UUID.fromString(option.get("id").asText());
			}
		}
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		return new Paper(exam, UUID.fromString(body.get("id").asText()), correct);
	}

	private void saveCorrect(Student student, Paper paper) throws Exception {
		mockMvc.perform(put("/api/v1/live-exams/" + paper.examId() + "/answers").header("Authorization", "Bearer " + student.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"questionId":"%s","optionId":"%s"}]}
					""".formatted(paper.questionId(), paper.correctOptionId())))
			.andExpect(status().isNoContent());
	}

	private UUID take(Student student, UUID examId) throws Exception {
		MvcResult taken = mockMvc.perform(post("/api/v1/live-exams/" + examId + "/take")
			.header("Authorization", "Bearer " + student.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andReturn();
		return UUID.fromString(json.readTree(taken.getResponse().getContentAsString()).get("id").asText());
	}

	private void upload(Student student, UUID examId) throws Exception {
		mockMvc.perform(multipart("/api/v1/live-exams/" + examId + "/answer-pdf").file(sheet("answer.pdf"))
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isNoContent());
	}

	private UUID publishModule(String token, String title) throws Exception {
		MvcResult category = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s cat","titleBn":"বিভাগ"}
					""".formatted(title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID categoryId = UUID.fromString(json.readTree(category.getResponse().getContentAsString()).get("id").asText());
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID module = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/final-save").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/publish").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());
		return module;
	}

	private void mark(String master, UUID moduleId, UUID studentId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-modules/" + moduleId + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"studentId":"%s"}
					""".formatted(studentId)))
			.andExpect(status().isCreated());
	}

	private String evaluatedFile(UUID participantId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select evaluated_stored_file_name from live_exam_participants where id = ?", String.class, participantId);
	}

	private Student seedStudent(String name) throws Exception {
		UUID id = seed("STUDENT", name);
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class, id);
		return new Student(id, phone, token(id));
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"S" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		return tokenFor(phone);
	}

	private String tokenFor(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

	private static MockMultipartFile sheet(String name) throws Exception {
		return new MockMultipartFile("file", name, "application/pdf", compressiblePdf());
	}

	private static byte[] tinyPdf() {
		return """
				%PDF-1.1
				1 0 obj<</Type/Catalog>>endobj
				trailer<</Root 1 0 R>>
				%%EOF
				""".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
	}

	private static byte[] compressiblePdf() throws Exception {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page, AppendMode.OVERWRITE, false)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				stream.newLineAtOffset(40, 700);
				stream.showText("B".repeat(400));
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "live-eval-" + UUID.randomUUID() + "@example.com";
	}

	private record Instructor(UUID id, String token) {
	}

	private record Student(UUID id, String phone, String token) {
	}

	private record Paper(UUID examId, UUID questionId, UUID correctOptionId) {
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
