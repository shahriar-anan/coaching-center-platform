package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
	private LiveExamDeadlineJob deadlineJob;

	@Autowired
	private StudentReminderJob reminders;

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
		mockMvc.perform(post("/api/v1/live-exam-participants/" + failedId + "/publish")
			.header("Authorization", "Bearer " + master))
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
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + missing.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.evaluatedPdf").doesNotExist());
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
			.andExpect(jsonPath("$.score").value(7))
			.andExpect(jsonPath("$.published").value(false));
		String firstFile = evaluatedFile(higherId);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.passed").doesNotExist())
			.andExpect(jsonPath("$.evaluatedPdf").doesNotExist())
			.andExpect(jsonPath("$.comment").doesNotExist())
			.andExpect(jsonPath("$.maximum").value(10))
			.andExpect(jsonPath("$.evaluatorName").doesNotExist());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/evaluated-pdf")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isNotFound());

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + lowerId + "/evaluation").file(sheet("peer.pdf"))
			.param("marks", "6")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:46:00Z"));
		String hidden = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(hidden).doesNotContain("Higher Paper", "Lower Paper");

		publishResult(master, higherId);
		publishResult(master, lowerId);
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
		JsonNode publishedBoard = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(rank(publishedBoard, "Higher Paper")).isEqualTo(1);
		assertThat(rank(publishedBoard, "Lower Paper")).isEqualTo(2);

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + higherId + "/evaluation").file(sheet("second.pdf"))
			.param("marks", "4")
			.header("Authorization", "Bearer " + instructor.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(4))
			.andExpect(jsonPath("$.published").value(false));
		assertThat(java.nio.file.Path.of(files.storageDir(), firstFile)).doesNotExist();
		assertThat(evaluatedFile(higherId)).isNotEqualTo(firstFile);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.passed").doesNotExist());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/evaluated-pdf")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isNotFound());
		String redrafted = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + higher.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(redrafted).doesNotContain("Higher Paper");
		assertThat(redrafted).contains("Lower Paper");

		publishResult(master, higherId);
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
			.andExpect(jsonPath("$.score").value(7))
			.andExpect(jsonPath("$.published").value(false));
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
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.comment").doesNotExist());
		publishResult(master, participant);
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
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comment").doesNotExist())
			.andExpect(jsonPath("$.score").doesNotExist());
		publishResult(master, participant);
		String blank = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comment").doesNotExist())
			.andExpect(jsonPath("$.score").value(7))
			.andExpect(jsonPath("$.evaluatorName").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(blank).doesNotContain("Comment Marker", marker.id().toString());
	}

	@Test
	void aWrittenExamRejectsALaterAttempt() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Retake mark");
		UUID exam = writtenExam(staff, module);
		Student missed = seedStudent("Missed Live");
		Student later = seedStudent("Later Retake");
		Student stranger = seedStudent("No Roster");
		mark(master, module, missed.id());
		mark(master, module, later.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(missed, exam);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + later.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + stranger.token()))
			.andExpect(status().isNotFound());

		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + later.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + missed.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + later.token()))
			.andExpect(status().isNotFound());

		clock.set(Instant.parse("2026-10-20T06:46:00Z"));
		String board = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + missed.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(board).contains("Missed Live");
		assertThat(board).doesNotContain("Later Retake");
		assertThat(json.readTree(board).get("rows")).hasSize(1);
		assertThat(json.readTree(board).get("rows").get(0).get("score").asInt()).isZero();
	}

	@Test
	void theBoardMarksOnlyTheReadersOwnRowAndStaffGetNone() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Mine");
		UUID exam = writtenExam(staff, module);
		Student first = seedStudent("First Missing");
		Student second = seedStudent("Second Missing");
		mark(master, module, first.id());
		mark(master, module, second.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(first, exam);
		take(second, exam);
		clock.set(Instant.parse("2026-10-20T06:46:00Z"));

		JsonNode asFirst = boardAs(first.token(), exam);
		assertThat(rowNamed(asFirst, "First Missing").get("mine").asBoolean()).isTrue();
		assertThat(rowNamed(asFirst, "Second Missing").get("mine").asBoolean()).isFalse();
		JsonNode asStaff = boardAs(master, exam);
		for (JsonNode row : asStaff.get("rows")) {
			assertThat(row.get("mine").asBoolean()).isFalse();
		}
		assertThat(asStaff.get("rows").get(0).has("studentId")).isFalse();
	}

	private JsonNode boardAs(String token, UUID exam) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private JsonNode rowNamed(JsonNode board, String name) {
		for (JsonNode row : board.get("rows")) {
			if (name.equals(row.get("name").asText())) {
				return row;
			}
		}
		throw new AssertionError("no board row named " + name);
	}

	@Test
	void anUndatedExamHasNoLeaderboard() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Undated board");
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Open quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "durationMinutes":40,"passPercent":50
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Ten","marks":10,"options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		Student student = seedStudent("Board Student");
		mark(master, module, student.id());

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows").isEmpty());
		clock.set(Instant.parse("2099-01-01T00:00:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/leaderboard").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows").isEmpty());
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

	@Test
	void aMasterAdminSaveStaysDraftUntilThatAdminPublishes() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Master draft");
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Master Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("master.pdf"))
			.param("marks", "6")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.published").value(false));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").doesNotExist());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.passed").doesNotExist())
			.andExpect(jsonPath("$.status").value("SUBMITTED"));
		publishResult(master, participant);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6))
			.andExpect(jsonPath("$.evaluatorName").doesNotExist());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(6));
	}

	@Test
	void participationCountsEveryStateWithoutWaitingForADeadlineRead() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID writtenModule = publishModule(system, "Count written");
		UUID mcqModule = publishModule(system, "Count mcq");
		Instructor marker = instructor(master, "Count Marker");
		assign(master, writtenModule, marker.id());
		UUID written = writtenExam(system, writtenModule);
		Paper paper = mcq(system, mcqModule);
		Student submitted = seedStudent("Submitted");
		Student missed = seedStudent("Missed");
		Student drafted = seedStudent("Drafted");
		Student published = seedStudent("Published");
		Student idle = seedStudent("Idle");
		Student mcqOne = seedStudent("Mcq one");
		Student mcqTwo = seedStudent("Mcq two");
		for (Student student : new Student[] { submitted, missed, drafted, published, idle }) {
			mark(master, writtenModule, student.id());
		}
		mark(master, mcqModule, mcqOne.id());
		mark(master, mcqModule, mcqTwo.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID draftedParticipant = take(drafted, written);
		UUID publishedParticipant = take(published, written);
		take(submitted, written);
		take(missed, written);
		take(idle, written);
		upload(submitted, written);
		upload(drafted, written);
		upload(published, written);
		take(mcqOne, paper.examId());
		take(mcqTwo, paper.examId());
		saveCorrect(mcqOne, paper);

		JsonNode live = participation(master, "moduleId", writtenModule.toString());
		assertThat(live.get("totalElements").asInt()).isEqualTo(1);
		JsonNode liveRow = live.get("content").get(0);
		assertThat(liveRow.get("examId").asText()).isEqualTo(written.toString());
		assertThat(liveRow.get("sitting").asInt()).isEqualTo(5);
		assertThat(liveRow.get("submitted").asInt()).isZero();
		assertThat(liveRow.get("failedToSubmit").asInt()).isZero();

		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		JsonNode after = participation(master, "moduleId", writtenModule.toString()).get("content").get(0);
		assertThat(after.get("sitting").asInt()).isZero();
		assertThat(after.get("submitted").asInt()).isEqualTo(3);
		assertThat(after.get("failedToSubmit").asInt()).isEqualTo(2);
		assertThat(after.get("awaitingMarking").asInt()).isEqualTo(3);
		assertThat(after.get("draftsAwaitingPublish").asInt()).isZero();
		assertThat(after.get("published").asInt()).isZero();
		assertThat(after.get("liveAt").asText()).isEqualTo("2026-10-20T12:00:00+06:00");
		assertThat(after.get("endAt").asText()).isEqualTo("2026-10-20T12:40:00+06:00");

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + draftedParticipant + "/evaluation").file(sheet("d.pdf"))
			.param("marks", "6")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + publishedParticipant + "/evaluation").file(sheet("p.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		publishResult(master, publishedParticipant);

		JsonNode marked = participation(master, "moduleId", writtenModule.toString()).get("content").get(0);
		assertThat(marked.get("awaitingMarking").asInt()).isEqualTo(1);
		assertThat(marked.get("draftsAwaitingPublish").asInt()).isEqualTo(1);
		assertThat(marked.get("published").asInt()).isEqualTo(1);
		assertThat(marked.get("submitted").asInt()).isEqualTo(3);
		assertThat(marked.get("failedToSubmit").asInt()).isEqualTo(2);

		JsonNode mcqRow = participation(master, "moduleId", mcqModule.toString()).get("content").get(0);
		assertThat(mcqRow.get("examId").asText()).isEqualTo(paper.examId().toString());
		assertThat(mcqRow.get("examType").asText()).isEqualTo("MCQ");
		assertThat(mcqRow.get("submitted").asInt()).isEqualTo(2);
		assertThat(mcqRow.get("failedToSubmit").asInt()).isZero();
		assertThat(mcqRow.has("awaitingMarking")).isFalse();
		assertThat(mcqRow.has("draftsAwaitingPublish")).isFalse();
		assertThat(mcqRow.has("published")).isFalse();
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_participants where exam_id = ? and submitted_at is not null", Integer.class,
				paper.examId())).isZero();
	}

	@Test
	void participationFiltersSortsPagesAndScopesAnInstructor() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID mine = publishModule(system, "Scope mine");
		UUID other = publishModule(system, "Scope other");
		Instructor marker = instructor(master, "Scope Marker");
		assign(master, mine, marker.id());
		UUID mineExam = writtenExam(system, mine);
		UUID otherExam = writtenExam(system, other);

		JsonNode asMarker = participation(marker.token());
		assertThat(examIds(asMarker)).contains(mineExam).doesNotContain(otherExam);
		mockMvc.perform(get("/api/v1/live-exam-participation").param("moduleId", other.toString())
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isNotFound());
		assertThat(examIds(participation(system, "moduleId", mine.toString()))).containsExactly(mineExam);
		assertThat(examIds(participation(system, "moduleId", other.toString()))).containsExactly(otherExam);
		assertThat(examIds(participation(master, "moduleId", other.toString()))).containsExactly(otherExam);

		assertThat(examIds(participation(master, "moduleId", mine.toString(), "type", "MCQ"))).isEmpty();
		assertThat(examIds(participation(master, "moduleId", mine.toString(), "type", "WRITTEN"))).containsExactly(mineExam);
		assertThat(examIds(participation(master, "moduleId", mine.toString(), "q", "noon"))).containsExactly(mineExam);
		assertThat(examIds(participation(master, "moduleId", mine.toString(), "q", "nothing like it"))).isEmpty();
		assertThat(examIds(participation(master, "moduleId", mine.toString(), "from", "2026-10-20T00:00:00Z",
				"to", "2026-10-21T00:00:00Z"))).containsExactly(mineExam);
		assertThat(examIds(participation(master, "moduleId", mine.toString(), "from", "2026-10-21T00:00:00Z"))).isEmpty();
		assertThat(examIds(participation(master, "moduleId", mine.toString(), "sort", "title"))).containsExactly(mineExam);
		assertThat(examIds(participation(master, "moduleId", mine.toString(), "sort", "sitting"))).containsExactly(mineExam);

		JsonNode firstPage = participation(system, "size", "1", "page", "0");
		assertThat(firstPage.get("content")).hasSize(1);
		assertThat(firstPage.get("size").asInt()).isEqualTo(1);
		assertThat(firstPage.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);

		mockMvc.perform(get("/api/v1/live-exam-participation").param("type", "ORAL")
			.header("Authorization", "Bearer " + master)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/live-exam-participation").param("sort", "random")
			.header("Authorization", "Bearer " + master)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/live-exam-participation").param("from", "yesterday")
			.header("Authorization", "Bearer " + master)).andExpect(status().isBadRequest());
	}

	@Test
	void participationHidesDraftAndDeletedExamsAndRefusesStudents() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(system, "Hidden rows");
		UUID kept = writtenExam(system, module);
		UUID removedModule = publishModule(system, "Hidden removed");
		UUID removed = mcq(system, removedModule).examId();
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.update("update live_exams set deleted_at = now() where id = ?", removed);
		UUID draft = UUID.randomUUID();
		jdbc.update("""
				insert into live_exams (id, module_id, exam_type, paper_language, title_en, title_bn, description_en,
				  description_bn, rules_en, rules_bn, live_at, duration_minutes, status, finally_saved, created_at, updated_at)
				values (?, ?, 'MCQ', 'BN', 'Draft only', 'খসড়া', 'd', 'd', 'r', 'r', now(), 10, 'DRAFT', false, now(), now())
				""", draft, module);

		assertThat(examIds(participation(master, "moduleId", module.toString()))).containsExactly(kept);

		Student student = seedStudent("Not staff");
		mockMvc.perform(get("/api/v1/live-exam-participation").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/live-exam-participation/summary").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-participation")).andExpect(status().isUnauthorized());
	}

	@Test
	void theSummaryCountsOnlyTheCallersScope() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID mine = publishModule(system, "Summary mine");
		UUID other = publishModule(system, "Summary other");
		Instructor marker = instructor(master, "Summary Marker");
		Instructor stranger = instructor(master, "Summary Stranger");
		assign(master, mine, marker.id());
		assign(master, other, stranger.id());
		UUID mineExam = writtenExam(system, mine);
		UUID otherExam = writtenExam(system, other);
		Student one = seedStudent("Summary one");
		Student two = seedStudent("Summary two");
		Student three = seedStudent("Summary three");
		mark(master, mine, one.id());
		mark(master, mine, two.id());
		mark(master, other, three.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(one, mineExam);
		take(two, mineExam);
		take(three, otherExam);
		upload(one, mineExam);
		upload(two, mineExam);
		upload(three, otherExam);
		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		UUID twoParticipant = new JdbcTemplate(dataSource).queryForObject(
				"select id from live_exam_participants where exam_id = ? and student_id = ?", UUID.class, mineExam, two.id());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + twoParticipant + "/evaluation").file(sheet("s.pdf"))
			.param("marks", "5")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exam-participation/summary").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.awaitingMarking").value(1))
			.andExpect(jsonPath("$.draftsAwaitingPublish").value(1));
		mockMvc.perform(get("/api/v1/live-exam-participation/summary").header("Authorization", "Bearer " + stranger.token()))
			.andExpect(jsonPath("$.awaitingMarking").value(1))
			.andExpect(jsonPath("$.draftsAwaitingPublish").value(0));
		JsonNode all = json.readTree(mockMvc.perform(get("/api/v1/live-exam-participation/summary")
			.header("Authorization", "Bearer " + master)).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		assertThat(all.get("awaitingMarking").asInt()).isGreaterThanOrEqualTo(2);
		assertThat(all.get("draftsAwaitingPublish").asInt()).isGreaterThanOrEqualTo(1);
	}

	private JsonNode participation(String token, String... params) throws Exception {
		var request = get("/api/v1/live-exam-participation").header("Authorization", "Bearer " + token);
		for (int index = 0; index < params.length; index += 2) {
			request = request.param(params[index], params[index + 1]);
		}
		return json.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private static java.util.List<UUID> examIds(JsonNode page) {
		java.util.List<UUID> ids = new java.util.ArrayList<>();
		for (JsonNode row : page.get("content")) {
			ids.add(UUID.fromString(row.get("examId").asText()));
		}
		return ids;
	}

	@Test
	void anMcqScoreWritesOneNoticeAndTheDeadlineJobAddsOneForTheStudentWhoNeverSubmitted() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Notice mcq");
		Paper paper = mcq(staff, module);
		Student submits = seedStudent("Submits");
		Student silent = seedStudent("Silent");
		mark(master, module, submits.id());
		mark(master, module, silent.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(submits, paper.examId());
		take(silent, paper.examId());
		saveCorrect(submits, paper);

		assertThat(noticeCount("MCQ_SCORE", submits.id())).isZero();
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit").header("Authorization", "Bearer " + submits.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit").header("Authorization", "Bearer " + submits.token()))
			.andExpect(status().isOk());
		assertThat(noticeCount("MCQ_SCORE", submits.id())).isEqualTo(1);
		assertThat(noticeCount("MCQ_SCORE", silent.id())).isZero();

		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		deadlineJob.settleDue();
		deadlineJob.settleDue();
		assertThat(noticeCount("MCQ_SCORE", submits.id())).isEqualTo(1);
		assertThat(noticeCount("MCQ_SCORE", silent.id())).isEqualTo(1);

		JsonNode mine = notices(submits.token());
		assertThat(mine.get("totalElements").asInt()).isEqualTo(1);
		JsonNode notice = mine.get("content").get(0);
		assertThat(notice.get("kind").asText()).isEqualTo("MCQ_SCORE");
		assertThat(notice.get("score").asInt()).isEqualTo(10);
		assertThat(notice.get("maximum").asInt()).isEqualTo(10);
		assertThat(notice.get("passed").asBoolean()).isTrue();
		assertThat(notice.get("exam").get("id").asText()).isEqualTo(paper.examId().toString());
		assertThat(notice.has("readAt")).isFalse();
		JsonNode silentNotice = notices(silent.token()).get("content").get(0);
		assertThat(silentNotice.get("score").asInt()).isZero();
		assertThat(silentNotice.get("passed").asBoolean()).isFalse();
	}

	@Test
	void aWrittenNoticeAppearsOnlyOnPublishAndAResaveHidesItsMarksUntilThePublishAgain() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Notice written");
		Instructor marker = instructor(master, "Notice Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Notice Writer");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		clock.set(Instant.parse("2026-10-20T06:45:00Z"));

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("one.pdf"))
			.param("marks", "6")
			.param("comment", "Private comment")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		assertThat(noticeCount("WRITTEN_RESULT", student.id())).isZero();
		assertThat(notices(student.token()).get("totalElements").asInt()).isZero();

		publishResult(master, participant);
		publishResult(master, participant);
		assertThat(noticeCount("WRITTEN_RESULT", student.id())).isEqualTo(1);
		JsonNode first = notices(student.token()).get("content").get(0);
		assertThat(first.get("kind").asText()).isEqualTo("WRITTEN_RESULT");
		assertThat(first.get("score").asInt()).isEqualTo(6);
		assertThat(first.get("maximum").asInt()).isEqualTo(10);
		String body = notices(student.token()).toString();
		assertThat(body).doesNotContain("Private comment", "Notice Marker", "evaluat", "phone");

		clock.set(Instant.parse("2026-10-20T07:00:00Z"));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("two.pdf"))
			.param("marks", "8")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		JsonNode hidden = notices(student.token()).get("content").get(0);
		assertThat(hidden.has("score")).isFalse();
		assertThat(hidden.has("maximum")).isFalse();
		assertThat(noticeCount("WRITTEN_RESULT", student.id())).isEqualTo(1);

		clock.set(Instant.parse("2026-10-20T07:05:00Z"));
		publishResult(master, participant);
		assertThat(noticeCount("WRITTEN_RESULT", student.id())).isEqualTo(2);
		JsonNode both = notices(student.token());
		assertThat(both.get("content").get(0).get("score").asInt()).isEqualTo(8);
		assertThat(both.get("content").get(1).get("score").asInt()).isEqualTo(8);
	}

	@Test
	void aFailedToSubmitWrittenPaperWritesNoNotice() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Notice failed");
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Notice Empty");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(student, exam);
		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		deadlineJob.settleDue();
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select status from live_exam_participants where exam_id = ? and student_id = ?", String.class, exam,
				student.id())).isEqualTo("FAILED_TO_SUBMIT");
		assertThat(notices(student.token()).get("totalElements").asInt()).isZero();
	}

	@Test
	void theReminderGoesOncePerStudentWithAccessInsideTheTwoDayLeadAndNeverWithoutAGrant() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Reminder module");
		UUID exam = writtenExam(staff, module);
		UUID otherExam = null;
		Student rostered = seedStudent("Rostered");
		Student bought = seedStudent("Bought");
		Student stranger = seedStudent("Stranger");
		mark(master, module, rostered.id());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID order = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id,
				  status, created_at, updated_at)
				values (?, ?, 'EXAM', ?, ?, 10.00, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now())
				""", order, bought.id(), module, exam, UUID.randomUUID().toString().replace("-", "").substring(0, 30));
		jdbc.update("""
				insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at)
				values (?, ?, 'EXAM', ?, ?, ?, now())
				""", UUID.randomUUID(), bought.id(), module, exam, order);

		clock.set(Instant.parse("2026-10-17T05:00:00Z"));
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", rostered.id())).isZero();

		clock.set(Instant.parse("2026-10-18T07:00:00Z"));
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", rostered.id())).isEqualTo(1);
		assertThat(noticeCount("EXAM_REMINDER", bought.id())).isEqualTo(1);
		assertThat(noticeCount("EXAM_REMINDER", stranger.id())).isZero();
		clock.set(Instant.parse("2026-10-19T07:00:00Z"));
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", rostered.id())).isEqualTo(1);
		assertThat(noticeCount("EXAM_REMINDER", bought.id())).isEqualTo(1);

		JsonNode reminder = notices(rostered.token()).get("content").get(0);
		assertThat(reminder.get("kind").asText()).isEqualTo("EXAM_REMINDER");
		assertThat(reminder.get("exam").get("liveAt").asText()).isEqualTo("2026-10-20T12:00:00+06:00");
		assertThat(reminder.has("score")).isFalse();
		assertThat(otherExam).isNull();

		Student late = seedStudent("Late access");
		mark(master, module, late.id());
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", late.id())).isEqualTo(1);

		clock.set(Instant.parse("2026-10-20T06:00:00Z"));
		Student after = seedStudent("After start");
		mark(master, module, after.id());
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", after.id())).isZero();
	}

	@Test
	void noReminderForADraftAnUnpublishedModuleOrAnUndatedExam() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Reminder gates");
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Gated");
		mark(master, module, student.id());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		clock.set(Instant.parse("2026-10-19T07:00:00Z"));

		jdbc.update("update live_exams set status = 'DRAFT' where id = ?", exam);
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isZero();
		jdbc.update("update live_exams set status = 'PUBLISHED' where id = ?", exam);
		jdbc.update("update live_exam_modules set status = 'DRAFT' where id = ?", module);
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isZero();
		jdbc.update("update live_exam_modules set status = 'PUBLISHED' where id = ?", module);
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isEqualTo(1);
	}

	@Test
	void theInboxPagesMarksReadAndKeepsOneStudentsNoticesFromAnother() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Inbox module");
		UUID exam = writtenExam(staff, module);
		Student one = seedStudent("Inbox one");
		Student two = seedStudent("Inbox two");
		mark(master, module, one.id());
		mark(master, module, two.id());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		UUID first = notice(jdbc, one.id(), exam, "2026-10-19T01:00:00Z");
		UUID second = notice(jdbc, one.id(), exam, "2026-10-19T02:00:00Z");
		UUID third = notice(jdbc, one.id(), exam, "2026-10-19T03:00:00Z");
		UUID foreign = notice(jdbc, two.id(), exam, "2026-10-19T04:00:00Z");

		JsonNode page = json.readTree(mockMvc.perform(get("/api/v1/me/notices").param("size", "2")
			.header("Authorization", "Bearer " + one.token())).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		assertThat(page.get("totalElements").asInt()).isEqualTo(3);
		assertThat(page.get("totalPages").asInt()).isEqualTo(2);
		assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(third.toString());
		assertThat(page.get("content").get(1).get("id").asText()).isEqualTo(second.toString());
		mockMvc.perform(get("/api/v1/me/notices/unread-count").header("Authorization", "Bearer " + one.token()))
			.andExpect(jsonPath("$.count").value(3));

		mockMvc.perform(post("/api/v1/me/notices/" + foreign + "/read").header("Authorization", "Bearer " + one.token()))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/me/notices/" + UUID.randomUUID() + "/read")
			.header("Authorization", "Bearer " + one.token()))
			.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select read_at is null from student_notices where id = ?", Boolean.class, foreign))
			.isTrue();

		mockMvc.perform(post("/api/v1/me/notices/" + first + "/read").header("Authorization", "Bearer " + one.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.readAt").exists());
		Instant readAt = readAt(first);
		clock.set(clock.instant().plusSeconds(600));
		mockMvc.perform(post("/api/v1/me/notices/" + first + "/read").header("Authorization", "Bearer " + one.token()))
			.andExpect(status().isOk());
		assertThat(readAt(first)).isEqualTo(readAt);
		mockMvc.perform(get("/api/v1/me/notices/unread-count").header("Authorization", "Bearer " + one.token()))
			.andExpect(jsonPath("$.count").value(2));

		mockMvc.perform(post("/api/v1/me/notices/read-all").header("Authorization", "Bearer " + one.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.updated").value(2));
		mockMvc.perform(post("/api/v1/me/notices/read-all").header("Authorization", "Bearer " + one.token()))
			.andExpect(jsonPath("$.updated").value(0));
		mockMvc.perform(get("/api/v1/me/notices/unread-count").header("Authorization", "Bearer " + two.token()))
			.andExpect(jsonPath("$.count").value(1));

		jdbc.update("update live_exams set deleted_at = now() where id = ?", exam);
		mockMvc.perform(get("/api/v1/me/notices/unread-count").header("Authorization", "Bearer " + two.token()))
			.andExpect(jsonPath("$.count").value(0));
		mockMvc.perform(get("/api/v1/me/notices").header("Authorization", "Bearer " + one.token()))
			.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void onlyAStudentReadsTheInbox() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		Instructor marker = instructor(master, "Inbox Marker");
		for (String token : new String[] { master, system, marker.token() }) {
			mockMvc.perform(get("/api/v1/me/notices").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/me/notices/unread-count").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
			mockMvc.perform(post("/api/v1/me/notices/read-all").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
			mockMvc.perform(post("/api/v1/me/notices/" + UUID.randomUUID() + "/read").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
		}
		mockMvc.perform(get("/api/v1/me/notices")).andExpect(status().isUnauthorized());
	}

	private UUID notice(JdbcTemplate jdbc, UUID studentId, UUID examId, String createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into student_notices (id, user_id, kind, message_key, exam_id, created_at)
				values (?, ?, 'EXAM_REMINDER', ?, ?, ?::timestamptz)
				""", id, studentId, id.toString(), examId, createdAt);
		return id;
	}

	private Instant readAt(UUID noticeId) {
		return new JdbcTemplate(dataSource).queryForObject("select read_at from student_notices where id = ?",
				java.sql.Timestamp.class, noticeId).toInstant();
	}

	private int noticeCount(String kind, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from student_notices where kind = ? and user_id = ?", Integer.class, kind, studentId);
	}

	private JsonNode notices(String token) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/me/notices").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	@Test
	void unassigningTheInstructorLetsAnotherTakeOverAndKeepsTheDraft() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Unassign");
		Instructor first = instructor(master, "First Marker");
		Instructor second = instructor(master, "Second Marker");
		assign(master, module, first.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Unassign Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "6")
			.header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"instructorId\":\"" + second.id() + "\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("INSTRUCTOR_ALREADY_ASSIGNED"));
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(new JdbcTemplate(dataSource).queryForObject("select count(*) from live_exam_instructors where module_id = ?", Integer.class, module))
			.isZero();
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from audit_log where action = 'LIVE_EXAM_INSTRUCTOR_UNASSIGN' and entity_id = ?",
				Integer.class, module)).isEqualTo(1);

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/submissions")
			.header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("again.pdf"))
			.param("marks", "7")
			.header("Authorization", "Bearer " + first.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/submissions").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].score").value(6))
			.andExpect(jsonPath("$[0].evaluatorName").value("First Marker"));

		assign(master, module, second.id());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/submissions")
			.header("Authorization", "Bearer " + second.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].score").value(6));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("again.pdf"))
			.param("marks", "8")
			.header("Authorization", "Bearer " + second.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(8))
			.andExpect(jsonPath("$.evaluatorName").value("Second Marker"));
		mockMvc.perform(post("/api/v1/live-exam-participants/" + participant + "/publish")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.published").value(true));
	}

	@Test
	void unassignNeedsAnAssignmentAnExistingModuleAndTheMasterAdmin() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		UUID module = publishModule(system, "Unassign guard");
		Instructor marker = instructor(master, "Guard Marker");

		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + UUID.randomUUID() + "/instructor")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());

		assign(master, module, marker.id());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/instructor").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + module + "/instructor"))
			.andExpect(status().isUnauthorized());
		assertThat(new JdbcTemplate(dataSource).queryForObject("select count(*) from live_exam_instructors where module_id = ?", Integer.class, module))
			.isEqualTo(1);
	}

	@Test
	void anInstructorAndASystemAdminCannotPublish() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Publish gate");
		Instructor marker = instructor(master, "Publish Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Publish Student");
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
		mockMvc.perform(post("/api/v1/live-exam-participants/" + participant + "/publish")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(post("/api/v1/live-exam-participants/" + participant + "/publish")
			.header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evaluatorName").value("Publish Marker"))
			.andExpect(jsonPath("$.score").value(7))
			.andExpect(jsonPath("$.published").value(false));
	}

	@Test
	void publishRejectsAnUnmarkedPaperAndAnMcq() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Reject publish");
		UUID written = writtenExam(staff, module);
		Student writer = seedStudent("Unmarked Writer");
		mark(master, module, writer.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(writer, written);
		upload(writer, written);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/submit").header("Authorization", "Bearer " + writer.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-participants/" + participant + "/publish")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EVALUATION_NOT_ALLOWED"));

		clock.set(TEN_AM);
		UUID quizModule = publishModule(staff, "Reject quiz");
		Paper paper = mcq(staff, quizModule);
		Student quiz = seedStudent("Quiz Taker");
		mark(master, quizModule, quiz.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID mcqParticipant = take(quiz, paper.examId());
		mockMvc.perform(post("/api/v1/live-exam-participants/" + mcqParticipant + "/publish")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_TYPE_MISMATCH"));
	}

	@Test
	void aSecondPublishLeavesThePublishedTime() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Second publish");
		Instructor marker = instructor(master, "Second Marker");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Second Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "9")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		publishResult(master, participant);
		Instant first = publishedAt(participant);
		clock.set(Instant.parse("2026-10-20T07:00:00Z"));
		publishResult(master, participant);
		assertThat(publishedAt(participant)).isEqualTo(first);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(9));
	}

	private String statusOf(JsonNode rows, String name) {
		for (JsonNode row : rows) {
			if (name.equals(row.get("studentName").asText())) {
				return row.get("status").asText();
			}
		}
		throw new AssertionError("Missing " + name);
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
						  "examType":"WRITTEN","paperLanguage":"BN","titleEn":"Noon paper","titleBn":"খাতা",
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
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Noon quiz","titleBn":"কুইজ",
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

	private void publishResult(String master, UUID participantId) throws Exception {
		mockMvc.perform(post("/api/v1/live-exam-participants/" + participantId + "/publish")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.published").value(true));
	}

	private Instant publishedAt(UUID participantId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select published_at from live_exam_participants where id = ?",
				(result, row) -> result.getTimestamp(1).toInstant(), participantId);
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

	@Test
	void aRetakeMcqScoreWritesItsOwnNotice() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Retake notice");
		Paper paper = mcq(staff, module);
		Student initial = seedStudent("Initial scorer");
		Student retaker = seedStudent("Retake scorer");
		mark(master, module, initial.id());
		mark(master, module, retaker.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(initial, paper.examId());
		saveCorrect(initial, paper);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit")
			.header("Authorization", "Bearer " + initial.token()))
			.andExpect(status().isOk());
		assertThat(noticeCount("MCQ_SCORE", initial.id())).isEqualTo(1);

		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/retake")
			.header("Authorization", "Bearer " + retaker.token()))
			.andExpect(status().isOk());
		saveCorrect(retaker, paper);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit")
			.header("Authorization", "Bearer " + retaker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(10));
		assertThat(noticeCount("MCQ_SCORE", retaker.id())).isEqualTo(1);
	}

	@Test
	void changedLiveAtAndRepublishWritesNewReminder() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Reminder change");
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Reminder moved");
		mark(master, module, student.id());

		clock.set(Instant.parse("2026-10-17T07:00:00Z"));
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isZero();

		new JdbcTemplate(dataSource).update(
			"update live_exams set live_at = ?::timestamptz where id = ?",
			"2026-10-19T12:00:00+06:00", exam);
		clock.set(Instant.parse("2026-10-18T07:00:00Z"));
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isEqualTo(1);

		new JdbcTemplate(dataSource).update(
			"update live_exams set live_at = ?::timestamptz where id = ?",
			"2026-10-20T12:00:00+06:00", exam);
		clock.set(Instant.parse("2026-10-19T07:00:00Z"));
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isEqualTo(2);
	}

	@Test
	void noReminderForArchivedCategoryAndNoticeHiddenWhenModuleDeleted() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Archived category");
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Archive watcher");
		mark(master, module, student.id());
		UUID categoryId = new JdbcTemplate(dataSource).queryForObject(
			"select category_id from live_exam_modules where id = ?", UUID.class, module);

		new JdbcTemplate(dataSource).update(
			"update live_exam_categories set archived_at = now() where id = ?", categoryId);
		clock.set(Instant.parse("2026-10-19T07:00:00Z"));
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isZero();

		new JdbcTemplate(dataSource).update(
			"update live_exam_categories set archived_at = null where id = ?", categoryId);
		reminders.scan();
		assertThat(noticeCount("EXAM_REMINDER", student.id())).isEqualTo(1);
		assertThat(notices(student.token()).get("totalElements").asInt()).isEqualTo(1);

		new JdbcTemplate(dataSource).update(
			"update live_exam_modules set deleted_at = now() where id = ?", module);
		JsonNode visible = json.readTree(mockMvc.perform(get("/api/v1/me/notices")
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(visible.get("totalElements").asInt()).isZero();
	}

	@Test
	void participationCountsRetakesAndArchivedModuleRows() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Count retakes");
		Paper paper = mcq(staff, module);
		Student initial = seedStudent("Initial taker");
		Student retaker = seedStudent("Retake counter");
		mark(master, module, initial.id());
		mark(master, module, retaker.id());

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		take(initial, paper.examId());
		take(retaker, paper.examId());
		saveCorrect(initial, paper);
		saveCorrect(retaker, paper);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit")
			.header("Authorization", "Bearer " + initial.token()))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit")
			.header("Authorization", "Bearer " + retaker.token()))
			.andExpect(status().isOk());

		clock.set(Instant.parse("2026-10-20T06:45:00Z"));
		Student lateStudent = seedStudent("Late taker");
		mark(master, module, lateStudent.id());
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/retake")
			.header("Authorization", "Bearer " + lateStudent.token()))
			.andExpect(status().isOk());
		saveCorrect(lateStudent, paper);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/submit")
			.header("Authorization", "Bearer " + lateStudent.token()))
			.andExpect(status().isOk());

		JsonNode part = participation(staff, "moduleId", module.toString()).get("content").get(0);
		assertThat(part.get("submitted").asInt()).isEqualTo(3);
		assertThat(part.get("retakes").asInt()).isEqualTo(1);

		UUID categoryId = new JdbcTemplate(dataSource).queryForObject(
			"select category_id from live_exam_modules where id = ?", UUID.class, module);
		new JdbcTemplate(dataSource).update(
			"update live_exam_categories set archived_at = now() where id = ?", categoryId);

		JsonNode archived = participation(staff, "moduleId", module.toString()).get("content").get(0);
		assertThat(archived.get("submitted").asInt()).isEqualTo(3);
		assertThat(archived.get("retakes").asInt()).isEqualTo(1);
	}

	@Test
	void deletedExamKeepsTheModuleGrantAndOrder() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Delete module");
		Paper paper = mcq(staff, module);
		Student student = seedStudent("Delete student");
		mark(master, module, student.id());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		UUID order = UUID.randomUUID();
		jdbc.update("""
			insert into live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id,
			  status, created_at, updated_at)
			values (?, ?, 'MODULE', ?, null, 10.00, 'BDT', 'SSLCOMMERZ', ?, 'SUCCEEDED', now(), now())
			""", order, student.id(), module, UUID.randomUUID().toString().replace("-", "").substring(0, 30));
		jdbc.update("""
			insert into live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at)
			values (?, ?, 'MODULE', ?, null, ?, now())
			""", UUID.randomUUID(), student.id(), module, order);

		mockMvc.perform(delete("/api/v1/live-exams/" + paper.examId()).header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject(
			"select count(*) from live_exam_grants where student_id = ? and scope = 'MODULE' and module_id = ?",
			Integer.class, student.id(), module)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from live_exam_orders where id = ?", String.class, order))
			.isEqualTo("SUCCEEDED");
		mockMvc.perform(get("/api/v1/live-exams/" + paper.examId()).header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk());
	}

	@Test
	void staffReadTheWrittenQuestionPaperOnlyWhereTheyMayMark() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String system = token(seed("SYSTEM_ADMIN", "Admin"));
		UUID module = publishModule(master, "Paper read");
		Instructor marker = instructor(master, "Marker");
		Instructor stranger = instructor(master, "Stranger");
		assign(master, module, marker.id());
		UUID exam = writtenExam(master, module);

		assertPaperReadable(master, exam);
		assertPaperReadable(system, exam);
		assertPaperReadable(marker.token(), exam);
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + stranger.token()))
			.andExpect(status().isNotFound());
		Student outsider = seedStudent("Outsider");
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + outsider.token()))
			.andExpect(status().is4xxClientError());
	}

	@Test
	void theMarkingScreenAndTheModuleReadCarryTheSavedContext() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Marking context");
		Instructor marker = instructor(master, "Context Instructor");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		Student student = seedStudent("Context Student");
		mark(master, module, student.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(student, exam);
		upload(student, exam);
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "7")
			.param("comment", "Good effort")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/live-exam-participants/" + participant).header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.comment").value("Good effort"))
			.andExpect(jsonPath("$.evaluatedFileName").isNotEmpty())
			.andExpect(jsonPath("$.examTitleEn").value("Noon paper"))
			.andExpect(jsonPath("$.examTitleBn").value("খাতা"))
			.andExpect(jsonPath("$.moduleId").value(module.toString()))
			.andExpect(jsonPath("$.moduleTitleEn").value("Marking context"))
			.andExpect(jsonPath("$.maximum").value(10))
			.andExpect(jsonPath("$.submittedAt").isNotEmpty())
			.andExpect(jsonPath("$.evaluatedAt").isNotEmpty())
			.andExpect(jsonPath("$.publishedAt").doesNotExist());

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.instructor.id").value(marker.id().toString()))
			.andExpect(jsonPath("$.instructor.name").value("Context Instructor"))
			.andExpect(jsonPath("$.instructor.phone").isNotEmpty())
			.andExpect(jsonPath("$.categoryPath.length()").value(1))
			.andExpect(jsonPath("$.categoryPath[0].titleEn").value("Marking context cat"))
			.andExpect(jsonPath("$.writtenCount").value(1))
			.andExpect(jsonPath("$.mcqCount").value(0));

		mockMvc.perform(get("/api/v1/live-exam-modules").param("q", "MARKING context").param("status", "PUBLISHED")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[?(@.id == '" + module + "')].writtenCount").value(org.hamcrest.Matchers.hasItem(1)));
		mockMvc.perform(get("/api/v1/live-exam-modules").param("status", "OPEN").header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		Instructor stranger = instructor(master, "Context Stranger");
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module).header("Authorization", "Bearer " + stranger.token()))
			.andExpect(status().isNotFound());

		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				marker.id());
		String studentView = mockMvc.perform(get("/api/v1/live-exam-modules/" + module)
			.header("Authorization", "Bearer " + student.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(studentView).doesNotContain(phone).doesNotContain("Context Instructor");
	}

	@Test
	void theSubmissionFeedAndTheResultsReportFollowTheInstructorsModules() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Feed");
		UUID other = publishModule(staff, "Feed other");
		Instructor marker = instructor(master, "Feed Instructor");
		assign(master, module, marker.id());
		UUID exam = writtenExam(staff, module);
		UUID otherExam = writtenExam(staff, other);
		Student alpha = seedStudent("Feed Alpha");
		Student beta = seedStudent("Feed Beta");
		mark(master, module, alpha.id());
		mark(master, other, beta.id());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		UUID participant = take(alpha, exam);
		upload(alpha, exam);
		take(beta, otherExam);
		upload(beta, otherExam);
		clock.set(Instant.parse("2026-10-20T06:46:00Z"));
		deadlineJob.settleDue();

		mockMvc.perform(get("/api/v1/live-exam-submissions").param("q", "feed").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].participantId").value(participant.toString()))
			.andExpect(jsonPath("$.content[0].studentName").value("Feed Alpha"))
			.andExpect(jsonPath("$.content[0].status").value("AWAITING"))
			.andExpect(jsonPath("$.content[0].answerPdf").value(true))
			.andExpect(jsonPath("$.content[0].moduleTitleEn").value("Feed"));
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("moduleId", module.toString())
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].studentName").value("Feed Alpha"));
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("moduleId", other.toString())
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].studentName").value("Feed Beta"));
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("moduleId", module.toString()).param("status", "PUBLISHED")
			.header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.totalElements").value(0));
		Instructor stranger = instructor(master, "Feed Stranger");
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("q", "feed").header("Authorization", "Bearer " + stranger.token()))
			.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/live-exam-submissions").header("Authorization", "Bearer " + alpha.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("status", "NOPE").header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation").file(sheet("mark.pdf"))
			.param("marks", "7")
			.param("comment", "Good")
			.header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exam-submissions").param("q", "feed alpha").header("Authorization", "Bearer " + marker.token()))
			.andExpect(jsonPath("$.content[0].status").value("DRAFT"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.summary.participants").value(1))
			.andExpect(jsonPath("$.summary.submitted").value(1))
			.andExpect(jsonPath("$.summary.passCount").value(0))
			.andExpect(jsonPath("$.rows.totalElements").value(1));

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish-results").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish-results").header("Authorization", "Bearer " + staff))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.published").value(1));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.published").value(0));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.summary.averageScore").value(7.0))
			.andExpect(jsonPath("$.summary.highestScore").value(7))
			.andExpect(jsonPath("$.summary.passCount").value(1))
			.andExpect(jsonPath("$.summary.passRate").value(100.0));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").param("status", "FAILED_TO_SUBMIT")
			.header("Authorization", "Bearer " + master))
			.andExpect(jsonPath("$.rows.totalElements").value(0));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/results").header("Authorization", "Bearer " + stranger.token()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + otherExam + "/results").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/students/" + alpha.id() + "/live-exam-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].examTitleEn").value("Noon paper"))
			.andExpect(jsonPath("$.content[0].score").value(7))
			.andExpect(jsonPath("$.content[0].published").value(true));
		mockMvc.perform(get("/api/v1/students/" + alpha.id() + "/live-exam-results").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/students/" + UUID.randomUUID() + "/live-exam-results").header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
	}

	@Test
	void aCopyKeepsTheContentAndLeavesTheDateAndPriceEmpty() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Copy source");
		Paper paper = mcq(staff, module);
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/copy").header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.titleEn").value("Noon quiz (copy)"))
			.andExpect(jsonPath("$.titleBn").value("কুইজ (কপি)"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.liveAt").doesNotExist())
			.andExpect(jsonPath("$.price").doesNotExist())
			.andExpect(jsonPath("$.questions.length()").value(1))
			.andExpect(jsonPath("$.questions[0].stem").value("Ten"));
		Instructor marker = instructor(master, "Copy Instructor");
		assign(master, module, marker.id());
		mockMvc.perform(post("/api/v1/live-exams/" + paper.examId() + "/copy").header("Authorization", "Bearer " + marker.token()))
			.andExpect(status().isForbidden());
	}

	@Test
	void theStudentSearchAndTheUpcomingListReadOnlyPublishedExams() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Upcoming search");
		Paper paper = mcq(staff, module);
		String matches = mockMvc.perform(get("/api/v1/public/live-exam-modules").param("q", "UPCOMING search").param("size", "500"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(matches).contains(module.toString());
		assertThat(upcomingIds("MCQ")).contains(paper.examId());
		assertThat(upcomingIds("WRITTEN")).doesNotContain(paper.examId());
		mockMvc.perform(get("/api/v1/public/live-exams"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aCopyFitsTheTitleColumnOrIsRefused() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Copy length");
		UUID exact = publishedMcq(staff, module, "e".repeat(492), "ক".repeat(494), 40,
				"2026-10-20T12:00:00+06:00");
		mockMvc.perform(post("/api/v1/live-exams/" + exact + "/copy").header("Authorization", "Bearer " + master))
			.andExpect(status().isCreated());
		UUID over = publishedMcq(staff, module, "f".repeat(494), "ক".repeat(494), 40, "2026-10-20T14:00:00+06:00");
		mockMvc.perform(post("/api/v1/live-exams/" + over + "/copy").header("Authorization", "Bearer " + master))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aRunningExamWithALongDurationStaysOnTheUpcomingList() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Long window");
		UUID exam = publishedMcq(staff, module, "Three day paper", "তিন দিন", 4320, "2026-10-20T12:00:00+06:00");
		clock.set(Instant.parse("2026-10-22T12:00:00Z"));
		assertThat(upcomingIds("MCQ")).contains(exam);
	}

	private UUID publishedMcq(String staff, UUID module, String titleEn, String titleBn, int minutes, String liveAt)
			throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams")
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"examType":"MCQ","paperLanguage":"BN","titleEn":"%s","titleBn":"%s","descriptionEn":"d",
					 "descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম","liveAt":"%s",
					 "durationMinutes":%d,"passPercent":50}
					""".formatted(titleEn, titleBn, liveAt, minutes)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"stem":"Ten","marks":10,"explanation":"Because","options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		return exam;
	}

	/** Every upcoming exam id of one type, across all pages (the server caps the page size). */
	private java.util.Set<UUID> upcomingIds(String examType) throws Exception {
		java.util.Set<UUID> ids = new java.util.HashSet<>();
		int pages = 1;
		for (int page = 0; page < pages; page++) {
			JsonNode body = json.readTree(mockMvc.perform(get("/api/v1/public/live-exams").param("upcoming", "true")
				.param("examType", examType).param("page", String.valueOf(page)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString());
			pages = body.get("totalPages").asInt();
			for (JsonNode row : body.get("content")) {
				ids.add(UUID.fromString(row.get("id").asText()));
			}
		}
		return ids;
	}

	@Test
	void aClashingPublishNamesTheExamItClashesWith() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID module = publishModule(staff, "Clash");
		UUID first = writtenExam(staff, module);
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams")
			.header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{
					  "examType":"WRITTEN","paperLanguage":"BN","titleEn":"Clash paper","titleBn":"সংঘাত",
					  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
					  "liveAt":"2026-10-20T12:10:00+06:00","durationMinutes":40,"passPercent":50,"maximum":10
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID second = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(multipart("/api/v1/live-exams/" + second + "/question-pdf").file(new MockMultipartFile("file",
			"paper.pdf", "application/pdf", tinyPdf())).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + second + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + second + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EXAM_WINDOW_CONFLICT"))
			.andExpect(jsonPath("$.details.id").value(first.toString()))
			.andExpect(jsonPath("$.details.titleEn").value("Noon paper"))
			.andExpect(jsonPath("$.details.liveAt").isNotEmpty());
	}

	@Test
	void aStudentRenamesOwnAccountAndStaffCannot() throws Exception {
		Student student = seedStudent("Old Name");
		mockMvc.perform(patch("/api/v1/me").header("Authorization", "Bearer " + student.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fullName\":\"  New Name  \"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("New Name"));
		mockMvc.perform(patch("/api/v1/me").header("Authorization", "Bearer " + student.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fullName\":\"   \"}"))
			.andExpect(status().isBadRequest());
		String master = token(seed("MASTER_ADMIN", "Master"));
		mockMvc.perform(patch("/api/v1/me").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"fullName\":\"Renamed\"}"))
			.andExpect(status().isForbidden());
	}

	private void assertPaperReadable(String token, UUID exam) throws Exception {
		MvcResult read = mockMvc
			.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(read.getResponse().getContentAsByteArray())
			.startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		assertThat(read.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
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
