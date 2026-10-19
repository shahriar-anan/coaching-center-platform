package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
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
@Import({ CapturingEmailConfig.class, LiveSittingIT.ClockConfig.class })
class LiveSittingIT extends PostgresIntegrationTest {

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

	@Autowired
	private LiveExamSittingService sittings;

	@BeforeEach
	void setClock() {
		clock.set(TEN_AM);
	}

	@Test
	void readingBeforeTakeHidesStemsAndALateTapKeepsTheSharedEnd() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());

		String before = mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rulesEn").value("English rules"))
			.andExpect(jsonPath("$.liveAt").value("2026-10-20T12:00:00+06:00"))
			.andExpect(jsonPath("$.endAt").value("2026-10-20T12:40:00+06:00"))
			.andExpect(jsonPath("$.questions").doesNotExist())
			.andExpect(jsonPath("$.questionPdf").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(before).doesNotContain("Two plus two");
		assertThat(participantCount(holder.examId(), holder.studentId())).isZero();

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		MvcResult taken = mockMvc
			.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startedAt\":\"2026-10-20T09:00:00+06:00\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deadlineAt").value("2026-10-20T12:40:00+06:00"))
			.andExpect(jsonPath("$.serverTime").value("2026-10-20T12:10:00+06:00"))
			.andExpect(jsonPath("$.kind").value("LIVE"))
			.andReturn();
		UUID participantId = UUID.fromString(json.readTree(taken.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(participantId.toString()));

		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").value("Two plus two"))
			.andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
			.andExpect(jsonPath("$.questions[0].explanation").doesNotExist());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/live-exam-modules/" + holder.moduleId() + "/roster/" + holder.studentId())
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(participantCount(holder.examId(), holder.studentId())).isEqualTo(1);
	}

	@Test
	void savingReplacesTheSelectionAndAnEarlySubmitHidesTheReviewUntilTheDeadline() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());

		save(student, holder.examId(), holder.questions());
		save(student, holder.examId(), holder.questions().subList(0, 2));
		assertThat(answerCount(holder.examId(), holder.studentId())).isEqualTo(2);

		String posted = mockMvc
			.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(5))
			.andExpect(jsonPath("$.maximum").value(10))
			.andExpect(jsonPath("$.passed").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(posted).doesNotContain("correct", "Because");

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].explanation").value("Because"))
			.andExpect(jsonPath("$.questions[0].options[0].correct").exists());
	}

	@Test
	void aBlankMcqPostsZeroAndIsNotFailedToSubmit() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.passed").value(false))
			.andExpect(jsonPath("$.status").value("SUBMITTED"));
	}

	@Test
	void aStudentWhoNeverTappedCanRetakeOnceAndALiveParticipantCannot() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		UUID absentId = seed("STUDENT");
		String absent = token(absentId);
		String live = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		mark(master, holder.moduleId(), absentId);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + live)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());

		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + live)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SITTING_NOT_OPEN"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + absent)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SITTING_NOT_OPEN"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/retake").header("Authorization", "Bearer " + live))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/retake").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"))
			.andExpect(jsonPath("$.deadlineAt").value("2026-10-20T13:21:00+06:00"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/retake").header("Authorization", "Bearer " + absent))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
	}

	@Test
	void aWrittenPdfReplacesThePreviousFileAndAMissingPdfIsFailedToSubmit() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Written");
		UUID exam = createWritten(staff, module);
		UUID uploaderId = seed("STUDENT");
		UUID missingId = seed("STUDENT");
		mark(master, module, uploaderId);
		mark(master, module, missingId);
		String uploader = token(uploaderId);
		String missing = token(missingId);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + uploader)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + missing)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());

		byte[] pdf = compressiblePdf();
		MockMultipartFile first = new MockMultipartFile("file", "one.pdf", "application/pdf", pdf);
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(first)
			.header("Authorization", "Bearer " + uploader))
			.andExpect(status().isNoContent());
		String firstName = storedAnswer(exam, uploaderId);
		byte[] stored = Files.readAllBytes(java.nio.file.Path.of(files.storageDir(), firstName));
		assertThat(stored).startsWith("%PDF".getBytes());
		assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("FlateDecode");
		assertThat(stored.length).isLessThan(pdf.length);

		MockMultipartFile second = new MockMultipartFile("file", "two.pdf", "application/pdf", pdf);
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(second)
			.header("Authorization", "Bearer " + uploader))
			.andExpect(status().isNoContent());
		String secondName = storedAnswer(exam, uploaderId);
		assertThat(secondName).isNotEqualTo(firstName);
		assertThat(java.nio.file.Path.of(files.storageDir(), firstName)).doesNotExist();

		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(hugePdf())
			.header("Authorization", "Bearer " + uploader))
			.andExpect(status().isPayloadTooLarge());
		assertThat(storedAnswer(exam, uploaderId)).isEqualTo(secondName);

		clock.set(Instant.parse("2026-10-20T07:25:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + missing))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED_TO_SUBMIT"))
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.passed").value(false));
	}

	@Test
	void anEarlySubmitStillAcceptsALaterSaveAndTheDeadlinePostsThatSelection() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		save(student, holder.examId(), holder.questions().subList(0, 1));
		String posted = mockMvc
			.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2))
			.andExpect(jsonPath("$.passed").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(posted).doesNotContain("Because");
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2))
			.andExpect(jsonPath("$.questions").doesNotExist());

		save(student, holder.examId(), holder.questions());
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(10))
			.andExpect(jsonPath("$.passed").value(true));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(10))
			.andExpect(jsonPath("$.passed").value(true))
			.andExpect(jsonPath("$.questions[0].explanation").value("Because"))
			.andExpect(jsonPath("$.questions[0].options[0].correct").exists());
	}

	@Test
	void aRetakeSubmitHidesTheReviewUntilThePersonalTimerEnds() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		UUID absentId = seed("STUDENT");
		String absent = token(absentId);
		mark(master, holder.moduleId(), absentId);
		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/retake").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deadlineAt").value("2026-10-20T13:21:00+06:00"));
		save(absent, holder.examId(), holder.questions().subList(0, 1));
		String posted = mockMvc
			.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(posted).doesNotContain("Because");
		save(absent, holder.examId(), holder.questions());
		clock.set(Instant.parse("2026-10-20T07:20:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(2))
			.andExpect(jsonPath("$.questions").doesNotExist());
		clock.set(Instant.parse("2026-10-20T07:21:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(10));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(10))
			.andExpect(jsonPath("$.questions[0].explanation").value("Because"));
	}

	@Test
	void aReadAfterTheDeadlinePostsAnMcqThatWasNeverSubmitted() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		UUID blankId = seed("STUDENT");
		String saved = token(holder.studentId());
		String blank = token(blankId);
		mark(master, holder.moduleId(), holder.studentId());
		mark(master, holder.moduleId(), blankId);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + saved)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + blank)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		save(saved, holder.examId(), holder.questions().subList(0, 2));
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));

		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + saved))
			.andExpect(status().isOk());
		assertThat(sittingStatus(holder.examId(), holder.studentId())).isEqualTo("SUBMITTED");
		assertThat(sittingScore(holder.examId(), holder.studentId())).isEqualTo(5);
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + blank))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.passed").value(false))
			.andExpect(jsonPath("$.status").value("SUBMITTED"));
	}

	@Test
	void aReadAfterTheWrittenDeadlinePostsAStoredPdfAndAMissingPdf() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Unread");
		UUID exam = createWritten(staff, module);
		UUID storedId = seed("STUDENT");
		UUID missingId = seed("STUDENT");
		mark(master, module, storedId);
		mark(master, module, missingId);
		String stored = token(storedId);
		String missing = token(missingId);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + stored)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + missing)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "answer.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + stored))
			.andExpect(status().isNoContent());
		clock.set(Instant.parse("2026-10-20T07:25:00Z"));

		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + missing))
			.andExpect(status().isOk());
		assertThat(sittingStatus(exam, missingId)).isEqualTo("FAILED_TO_SUBMIT");
		assertThat(sittingScore(exam, missingId)).isZero();
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + stored))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.passed").doesNotExist());
	}

	@Test
	void aWrittenSubmitBeforeTheDeadlineDoesNotScoreOrLockTheSheet() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Open sheet");
		UUID exam = createWritten(staff, module);
		UUID storedId = seed("STUDENT");
		UUID emptyId = seed("STUDENT");
		mark(master, module, storedId);
		mark(master, module, emptyId);
		String stored = token(storedId);
		String empty = token(emptyId);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + stored)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + empty)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "one.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + stored))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + stored))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.passed").doesNotExist());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "two.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + stored))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + empty))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.score").doesNotExist());
		assertThat(sittingStatus(exam, emptyId)).isEqualTo("IN_PROGRESS");
	}

	@Test
	void aRemovedRosterOrArchivedModuleRejectsAnotherSubmission() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Closed sheet");
		UUID exam = createWritten(staff, module);
		UUID studentId = seed("STUDENT");
		mark(master, module, studentId);
		String student = token(studentId);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "one.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", "inline"));
		String kept = storedAnswer(exam, studentId);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/live-exam-modules/" + module + "/roster/" + studentId)
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "two.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		assertThat(storedAnswer(exam, studentId)).isEqualTo(kept);

		mark(master, module, studentId);
		mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "three.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		assertThat(storedAnswer(exam, studentId)).isEqualTo(kept);
	}

	@Test
	void aBlankMcqFailsWhenThePassPercentIsZero() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Zero");
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","titleEn":"Open pass","titleBn":"খোলা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:00:00+06:00","durationMinutes":40,"passPercent":0
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		MvcResult question = mockMvc
			.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"stem":"Pick","marks":2,"options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode options = json.readTree(question.getResponse().getContentAsString());
		UUID questionId = UUID.fromString(options.get("id").asText());
		UUID wrong = null;
		for (JsonNode option : options.get("options")) {
			if (!option.get("correct").asBoolean()) {
				wrong = UUID.fromString(option.get("id").asText());
			}
		}
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		UUID blankId = seed("STUDENT");
		UUID wrongId = seed("STUDENT");
		mark(master, module, blankId);
		mark(master, module, wrongId);
		String blank = token(blankId);
		String answered = token(wrongId);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + blank)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + answered)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/live-exams/" + exam + "/answers").header("Authorization", "Bearer " + answered)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"questionId":"%s","optionId":"%s"}]}
					""".formatted(questionId, wrong)))
			.andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + blank))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.passed").value(false));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/submit").header("Authorization", "Bearer " + answered))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.passed").value(true));
	}

	@Test
	void aRepeatedQuestionInOneSaveIsRejected() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		save(student, holder.examId(), holder.questions().subList(0, 1));
		Question first = holder.questions().get(0);
		mockMvc.perform(put("/api/v1/live-exams/" + holder.examId() + "/answers").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"questionId":"%s","optionId":"%s"},{"questionId":"%s","optionId":"%s"}]}
					""".formatted(first.id(), first.correctOptionId(), first.id(), first.correctOptionId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(put("/api/v1/live-exams/" + holder.examId() + "/answers").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"answers":[{"questionId":"not-an-id","optionId":"%s"}]}
					""".formatted(first.correctOptionId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		assertThat(answerCount(holder.examId(), holder.studentId())).isEqualTo(1);
	}

	@Test
	void twoSimultaneousTakesReturnTheSameParticipant() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		mark(master, holder.moduleId(), holder.studentId());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
		try {
			java.util.concurrent.Future<UUID> first = pool.submit(() -> {
				start.await();
				return sittings.take(holder.examId(), holder.studentId(), null).id();
			});
			java.util.concurrent.Future<UUID> second = pool.submit(() -> {
				start.await();
				return sittings.take(holder.examId(), holder.studentId(), null).id();
			});
			start.countDown();
			assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS))
				.isEqualTo(second.get(20, java.util.concurrent.TimeUnit.SECONDS));
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(participantCount(holder.examId(), holder.studentId())).isEqualTo(1);
	}

	@Test
	void theQuestionPdfDownloadsOnlyDuringTheSitting() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Paper");
		UUID exam = createWritten(staff, module);
		UUID sitterId = seed("STUDENT");
		UUID otherId = seed("STUDENT");
		UUID retakeId = seed("STUDENT");
		mark(master, module, sitterId);
		mark(master, module, otherId);
		mark(master, module, retakeId);
		String sitter = token(sitterId);
		String other = token(otherId);
		String retake = token(retakeId);

		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + sitter))
			.andExpect(status().isNotFound());
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + sitter)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		byte[] viewed = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf")
			.header("Authorization", "Bearer " + sitter))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", "inline"))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(header().string("Content-Security-Policy",
					org.hamcrest.Matchers.containsString("frame-ancestors 'self' http://localhost:3000")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(viewed).startsWith("%PDF".getBytes());
		assertThat(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf")
			.header("Authorization", "Bearer " + sitter))
			.andReturn()
			.getResponse()
			.getHeader("Content-Disposition")).doesNotContain("filename", "attachment");
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + other))
			.andExpect(status().isNotFound());

		clock.set(Instant.parse("2026-10-20T07:25:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + sitter))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questionPdf").doesNotExist());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + sitter))
			.andExpect(status().isNotFound());
		clock.set(Instant.parse("2026-10-20T07:26:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + retake))
			.andExpect(status().isOk());
		byte[] retaken = mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf")
			.header("Authorization", "Bearer " + retake))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", "inline"))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();
		assertThat(retaken).startsWith("%PDF".getBytes());
		clock.set(Instant.parse("2026-10-20T08:06:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + retake))
			.andExpect(status().isNotFound());
	}

	@Test
	void removingTheRosterLeavesTheResultReadable() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		save(student, holder.examId(), holder.questions().subList(0, 2));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/live-exam-modules/" + holder.moduleId() + "/roster/" + holder.studentId())
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(participantCount(holder.examId(), holder.studentId())).isEqualTo(1);
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(5))
			.andExpect(jsonPath("$.passed").value(true));
		mockMvc.perform(put("/api/v1/live-exams/" + holder.examId() + "/answers").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answersJson(holder.questions())))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(5));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		assertThat(sittingScore(holder.examId(), holder.studentId())).isEqualTo(5);
	}

	@Test
	void anArchivedAncestorHidesTheExamAndLeavesThePostedResult() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID science = createCategory(staff, "Science");
		UUID physics = createCategory(staff, "Physics", science);
		UUID arts = createCategory(staff, "Arts");
		UUID hiddenModule = publishModuleIn(staff, physics, "Hidden");
		UUID openModule = publishModuleIn(staff, arts, "Open");
		UUID hiddenExam = publishMcqIn(staff, hiddenModule, "2026-10-20T12:00:00+06:00");
		UUID written = createWritten(staff, hiddenModule);
		UUID openExam = publishMcqIn(staff, openModule, "2026-10-20T12:00:00+06:00");
		UUID studentId = seed("STUDENT");
		UUID absentId = seed("STUDENT");
		String student = token(studentId);
		String absent = token(absentId);
		mark(master, hiddenModule, studentId);
		mark(master, hiddenModule, absentId);
		mark(master, openModule, absentId);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + hiddenExam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + hiddenExam + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0));
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exams/" + openExam).header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		mockMvc.perform(post("/api/v1/live-exam-categories/" + science + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/live-exams/" + hiddenExam).header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		mockMvc.perform(get("/api/v1/live-exams/" + hiddenExam).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + written).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + hiddenModule).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + hiddenModule + "/exams").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty());
		mockMvc.perform(post("/api/v1/live-exams/" + hiddenExam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exams/" + hiddenExam + "/retake").header("Authorization", "Bearer " + absent))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/live-exams/" + written + "/retake").header("Authorization", "Bearer " + absent))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + written + "/question-pdf").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(multipart("/api/v1/live-exams/" + written + "/answer-pdf").file(new MockMultipartFile("file", "late.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/live-exams/" + hiddenExam + "/answers").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"answers\":[]}"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + hiddenExam + "/leaderboard").header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + hiddenExam + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.passed").value(false));

		mockMvc.perform(post("/api/v1/live-exams/" + openExam + "/retake").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"));
	}

	private UUID createCategory(String token, String title) throws Exception {
		return createCategory(token, title, null);
	}

	private UUID createCategory(String token, String title, UUID parentId) throws Exception {
		String parent = parentId == null ? "" : "\"parentId\":\"%s\",".formatted(parentId);
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{%s"titleEn":"%s","titleBn":"বিভাগ"}
					""".formatted(parent, title)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID publishModuleIn(String token, UUID categoryId, String title) throws Exception {
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

	private UUID publishMcqIn(String staff, UUID module, String liveAt) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","titleEn":"Noon quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"%s","durationMinutes":40,"passPercent":50
						}
						""".formatted(liveAt)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		addQuestion(staff, exam, "Two plus two", 2);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		return exam;
	}

	private Holder publishMcq(String staff) throws Exception {
		UUID module = publishModule(staff, "Sitting");
		UUID student = seed("STUDENT");
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
		Question two = addQuestion(staff, exam, "Two plus two", 2);
		Question three = addQuestion(staff, exam, "Three plus three", 3);
		Question five = addQuestion(staff, exam, "Five plus five", 5);
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		return new Holder(module, exam, student, java.util.List.of(two, three, five));
	}

	private UUID createWritten(String staff, UUID module) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"WRITTEN","titleEn":"Noon paper","titleBn":"খাতা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"2026-10-20T12:45:00+06:00","durationMinutes":40,"passPercent":50,"maximum":10
						}
						"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		MockMultipartFile paper = new MockMultipartFile("file", "paper.pdf", "application/pdf", tinyPdf());
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/question-pdf").file(paper)
			.header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		return exam;
	}

	private Question addQuestion(String staff, UUID exam, String stem, int marks) throws Exception {
		MvcResult result = mockMvc
			.perform(post("/api/v1/live-exams/" + exam + "/questions").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"stem":"%s","marks":%d,"explanation":"Because","options":[{"text":"Yes","correct":true},{"text":"No","correct":false}]}
						""".formatted(stem, marks)))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = json.readTree(result.getResponse().getContentAsString());
		UUID correct = null;
		for (JsonNode option : body.get("options")) {
			if (option.get("correct").asBoolean()) {
				correct = UUID.fromString(option.get("id").asText());
			}
		}
		return new Question(UUID.fromString(body.get("id").asText()), correct);
	}

	private void save(String student, UUID examId, java.util.List<Question> questions) throws Exception {
		mockMvc.perform(put("/api/v1/live-exams/" + examId + "/answers").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content(answersJson(questions)))
			.andExpect(status().isNoContent());
	}

	private static String answersJson(java.util.List<Question> questions) {
		StringBuilder answers = new StringBuilder();
		for (Question question : questions) {
			if (!answers.isEmpty()) {
				answers.append(',');
			}
			answers.append("""
					{"questionId":"%s","optionId":"%s"}
					""".formatted(question.id(), question.correctOptionId()));
		}
		return "{\"answers\":[" + answers + "]}";
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

	private int participantCount(UUID examId, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from live_exam_participants where exam_id = ? and student_id = ? and deleted_at is null",
				Integer.class, examId, studentId);
	}

	private int answerCount(UUID examId, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject("""
				select count(*) from live_exam_answers a
				join live_exam_participants p on p.id = a.participant_id
				where p.exam_id = ? and p.student_id = ?
				""", Integer.class, examId, studentId);
	}

	private String sittingStatus(UUID examId, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select status from live_exam_participants where exam_id = ? and student_id = ?", String.class, examId,
				studentId);
	}

	private int sittingScore(UUID examId, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select score from live_exam_participants where exam_id = ? and student_id = ?", Integer.class, examId,
				studentId);
	}

	private String storedAnswer(UUID examId, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select answer_stored_file_name from live_exam_participants where exam_id = ? and student_id = ?",
				String.class, examId, studentId);
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), uniqueEmail(), passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			new JdbcTemplate(dataSource).update(
					"INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, 'Seed', ?)", id,
					"S" + id.toString().substring(0, 8));
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
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
				stream.showText("A".repeat(400));
				stream.endText();
			}
			document.save(output);
			return output.toByteArray();
		}
	}

	private static MockMultipartFile hugePdf() {
		return new MockMultipartFile("file", "huge.pdf", "application/pdf", tinyPdf()) {
			@Override
			public long getSize() {
				return LiveExamSittingService.MAX_PDF_BYTES + 1;
			}
		};
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "live-sitting-" + UUID.randomUUID() + "@example.com";
	}

	private record Question(UUID id, UUID correctOptionId) {
	}

	private record Holder(UUID moduleId, UUID examId, UUID studentId, java.util.List<Question> questions) {
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
