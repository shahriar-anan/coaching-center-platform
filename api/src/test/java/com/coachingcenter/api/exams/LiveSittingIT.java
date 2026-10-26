package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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

	@Autowired
	private LiveExamDeadlineJob deadlineJob;

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
		String withheld = mockMvc
			.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].stem").exists())
			.andExpect(jsonPath("$.questions[0].options[0].selected").value(true))
			.andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
			.andExpect(jsonPath("$.questions[0].explanation").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(withheld).doesNotContain("\"correct\"", "Because");
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options[0].selected").value(true))
			.andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist())
			.andExpect(jsonPath("$.questions[0].explanation").doesNotExist());

		releaseAnswers(holder.examId());
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].explanation").value("Because"))
			.andExpect(jsonPath("$.questions[0].options[0].correct").value(true))
			.andExpect(jsonPath("$.questions[0].options[1].correct").value(false));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options[0].correct").value(true))
			.andExpect(jsonPath("$.questions[0].explanation").value("Because"));
	}

	@Test
	void releasedAnswersStayHiddenWhileTheStudentsOwnSittingIsOpen() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		releaseAnswers(holder.examId());
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		save(student, holder.examId(), holder.questions());

		String during = mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options[0].selected").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(during).doesNotContain("\"correct\"", "Because");
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		String early = mockMvc
			.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(early).doesNotContain("\"correct\"", "Because");
		assertThat(mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).doesNotContain("\"correct\"", "Because");

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options[0].correct").value(true))
			.andExpect(jsonPath("$.questions[0].explanation").value("Because"));
	}

	/** AG-02: the staff exam view carries the release time, and the student view never does. */
	@Test
	void answersReleasedAtFollowsTheReleaseAndIsNeverOnTheStudentView() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		String exam = "/api/v1/live-exams/" + holder.examId();

		mockMvc.perform(get(exam).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersReleasedAt").doesNotExist());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersReleasedAt").doesNotExist());

		mockMvc.perform(put(exam + "/answers-released").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":true}"))
			.andExpect(status().isOk());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersReleasedAt").isNotEmpty());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersReleasedAt").doesNotExist());

		mockMvc.perform(put(exam + "/answers-released").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON).content("{\"released\":false}"))
			.andExpect(status().isOk());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersReleasedAt").doesNotExist());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.answersReleasedAt").doesNotExist());
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
		releaseAnswers(holder.examId());
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
		releaseAnswers(holder.examId());
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
	void theDeadlineJobPostsMcqSittingsWithoutAnyRead() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		UUID blankId = seed("STUDENT");
		String saved = token(holder.studentId());
		String blank = token(blankId);
		mark(master, holder.moduleId(), holder.studentId());
		mark(master, holder.moduleId(), blankId);
		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		for (String student : new String[] { saved, blank }) {
			mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isOk());
		}
		save(saved, holder.examId(), holder.questions().subList(0, 2));

		deadlineJob.settleDue();
		assertThat(submittedAt(holder.examId(), holder.studentId())).isNull();
		assertThat(submittedAt(holder.examId(), blankId)).isNull();

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		deadlineJob.settleDue();

		assertThat(sittingStatus(holder.examId(), holder.studentId())).isEqualTo("SUBMITTED");
		assertThat(sittingScore(holder.examId(), holder.studentId())).isEqualTo(5);
		assertThat(sittingStatus(holder.examId(), blankId)).isEqualTo("SUBMITTED");
		assertThat(sittingScore(holder.examId(), blankId)).isZero();
		assertThat(passed(holder.examId(), blankId)).isFalse();
		Object firstPost = submittedAt(holder.examId(), holder.studentId());
		assertThat(firstPost).isNotNull();

		clock.set(Instant.parse("2026-10-20T06:55:00Z"));
		deadlineJob.settleDue();
		assertThat(submittedAt(holder.examId(), holder.studentId())).isEqualTo(firstPost);
		assertThat(sittingScore(holder.examId(), holder.studentId())).isEqualTo(5);
	}

	@Test
	void theDeadlineJobMarksAWrittenSittingWithoutAPdfAsFailedToSubmit() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Job written");
		UUID exam = createWritten(staff, module);
		UUID storedId = seed("STUDENT");
		UUID missingId = seed("STUDENT");
		mark(master, module, storedId);
		mark(master, module, missingId);
		String stored = token(storedId);
		String missing = token(missingId);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		for (String student : new String[] { stored, missing }) {
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isOk());
		}
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "answer.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + stored))
			.andExpect(status().isNoContent());

		clock.set(Instant.parse("2026-10-20T07:25:00Z"));
		deadlineJob.settleDue();

		assertThat(sittingStatus(exam, missingId)).isEqualTo("FAILED_TO_SUBMIT");
		assertThat(sittingScore(exam, missingId)).isZero();
		assertThat(sittingStatus(exam, storedId)).isEqualTo("SUBMITTED");
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select score from live_exam_participants where exam_id = ? and student_id = ?", Integer.class, exam, storedId))
			.isNull();
	}

	/** R-7: uploads replace each other until the deadline, the last one counts without a submit, and none means zero. */
	@Test
	void aWrittenUploadAtTheDeadlineIsRefusedAndTheLastEarlierUploadCountsWithoutASubmit() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Deadline written");
		UUID exam = createWritten(staff, module);
		UUID uploaderId = seed("STUDENT");
		UUID missingId = seed("STUDENT");
		mark(master, module, uploaderId);
		mark(master, module, missingId);
		String uploader = token(uploaderId);
		String missing = token(missingId);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		for (String student : new String[] { uploader, missing }) {
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deadlineAt").value("2026-10-20T13:25:00+06:00"));
		}
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "first.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + uploader))
			.andExpect(status().isNoContent());
		clock.set(Instant.parse("2026-10-20T07:24:59Z"));
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "last.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + uploader))
			.andExpect(status().isNoContent());
		String last = storedAnswer(exam, uploaderId);

		clock.set(Instant.parse("2026-10-20T07:25:00Z"));
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "late.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + uploader))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));
		assertThat(storedAnswer(exam, uploaderId)).isEqualTo(last);
		assertThat(sittingStatus(exam, uploaderId)).isEqualTo("IN_PROGRESS");

		deadlineJob.settleDue();
		assertThat(sittingStatus(exam, uploaderId)).isEqualTo("SUBMITTED");
		assertThat(storedAnswer(exam, uploaderId)).isEqualTo(last);
		assertThat(new JdbcTemplate(dataSource).queryForObject(
				"select answer_original_file_name from live_exam_participants where exam_id = ? and student_id = ?",
				String.class, exam, uploaderId)).isEqualTo("last.pdf");
		assertThat(sittingStatus(exam, missingId)).isEqualTo("FAILED_TO_SUBMIT");
		assertThat(sittingScore(exam, missingId)).isZero();
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + missing))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED_TO_SUBMIT"))
			.andExpect(jsonPath("$.score").value(0))
			.andExpect(jsonPath("$.passed").value(false));
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + uploader))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andExpect(jsonPath("$.score").doesNotExist());

		clock.set(Instant.parse("2026-10-20T07:30:00Z"));
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "later.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + missing))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));
		assertThat(storedAnswer(exam, missingId)).isNull();
	}

	@Test
	void theDeadlineJobSkipsADeletedSitting() throws Exception {
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
		new JdbcTemplate(dataSource).update(
				"update live_exam_participants set deleted_at = now() where exam_id = ? and student_id = ?", holder.examId(),
				holder.studentId());

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		deadlineJob.settleDue();

		assertThat(submittedAt(holder.examId(), holder.studentId())).isNull();
	}

	@Test
	void theDeadlineJobSettlesAtMostOneBatchPerRun() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		Holder holder = publishMcq(staff);
		int count = LiveExamDeadlineJob.BATCH_SIZE + 1;
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		// The oldest deadlines in the table, so these rows are the ones the job picks first.
		jdbc.update("""
				WITH students AS (
				    INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				    SELECT gen_random_uuid(), '+8801' || (? + n)::text, 'batch-' || gen_random_uuid() || '@example.com',
				           'unused', 'STUDENT', 'ACTIVE', now(), now()
				    FROM generate_series(1, ?) AS n
				    RETURNING id
				), profiles AS (
				    INSERT INTO student_profiles (user_id, full_name, student_code)
				    SELECT id, 'Batch', 'B' || substr(md5(id::text), 1, 8) FROM students
				)
				INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, status,
				                                    created_at, updated_at)
				SELECT gen_random_uuid(), ?, id, 'LIVE', ?, ?, 'IN_PROGRESS', now(), now() FROM students
				""", ThreadLocalRandom.current().nextLong(100_000_000L, 899_000_000L), count, holder.examId(),
				Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")), Timestamp.from(Instant.parse("2000-01-01T00:30:00Z")));
		String settledRows = """
				select count(*) from live_exam_participants
				where exam_id = ? and deadline_at = '2000-01-01T00:30:00Z' and submitted_at is not null
				""";

		assertThat(deadlineJob.settleDue()).isEqualTo(LiveExamDeadlineJob.BATCH_SIZE);
		assertThat(jdbc.queryForObject(settledRows, Integer.class, holder.examId())).isEqualTo(LiveExamDeadlineJob.BATCH_SIZE);

		assertThat(deadlineJob.settleDue()).isGreaterThanOrEqualTo(1);
		assertThat(jdbc.queryForObject(settledRows, Integer.class, holder.examId())).isEqualTo(count);
		assertThat(jdbc.queryForObject("""
				select count(*) from live_exam_participants
				where exam_id = ? and deadline_at = '2000-01-01T00:30:00Z' and status = 'SUBMITTED' and score = 0
				""", Integer.class, holder.examId())).isEqualTo(count);
	}

	@Test
	void anMcqStatusFollowsTheSittingAndTheListTheDetailAndTheRoutineAgree() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		UUID otherId = seed("STUDENT");
		String student = token(holder.studentId());
		String other = token(otherId);
		mark(master, holder.moduleId(), holder.studentId());
		mark(master, holder.moduleId(), otherId);

		assertThat(myStatus(student, holder.moduleId(), holder.examId())).isEqualTo("UPCOMING");
		assertThat(detailStatus(student, holder.examId())).isEqualTo("UPCOMING");
		assertThat(routineStatus(student, holder.examId())).isEqualTo("UPCOMING");

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		assertThat(myStatus(student, holder.moduleId(), holder.examId())).isEqualTo("OPEN");
		assertThat(detailStatus(student, holder.examId())).isEqualTo("OPEN");
		assertThat(routineStatus(student, holder.examId())).isEqualTo("OPEN");

		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		assertThat(myStatus(student, holder.moduleId(), holder.examId())).isEqualTo("IN_PROGRESS");
		assertThat(routineStatus(student, holder.examId())).isEqualTo("IN_PROGRESS");
		assertThat(myStatus(other, holder.moduleId(), holder.examId())).isEqualTo("OPEN");

		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		assertThat(myStatus(student, holder.moduleId(), holder.examId())).isEqualTo("SUBMITTED");
		assertThat(detailStatus(student, holder.examId())).isEqualTo("SUBMITTED");
		assertThat(myStatus(other, holder.moduleId(), holder.examId())).isEqualTo("OPEN");

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		assertThat(myStatus(student, holder.moduleId(), holder.examId())).isEqualTo("RESULT_READY");
		assertThat(detailStatus(student, holder.examId())).isEqualTo("RESULT_READY");
		assertThat(myStatus(other, holder.moduleId(), holder.examId())).isEqualTo("MISSED");
		mockMvc.perform(get("/api/v1/live-exam-modules/" + holder.moduleId() + "/exams")
			.header("Authorization", "Bearer " + other))
			.andExpect(jsonPath("$.content[0].retakeAvailable").value(true));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + holder.moduleId() + "/exams")
			.header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.content[0].retakeAvailable").value(false));
	}

	@Test
	void aWrittenStatusWaitsForThePublishAndAMissedWrittenOffersNoLaterAttempt() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Status written");
		UUID exam = createWritten(staff, module);
		UUID writerId = seed("STUDENT");
		UUID missedId = seed("STUDENT");
		String writer = token(writerId);
		String missed = token(missedId);
		mark(master, module, writerId);
		mark(master, module, missedId);

		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		MvcResult taken = mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take")
			.header("Authorization", "Bearer " + writer)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andReturn();
		UUID participant = UUID.fromString(json.readTree(taken.getResponse().getContentAsString()).get("id").asText());
		assertThat(myStatus(writer, module, exam)).isEqualTo("IN_PROGRESS");
		mockMvc.perform(multipart("/api/v1/live-exams/" + exam + "/answer-pdf").file(new MockMultipartFile("file", "answer.pdf",
			"application/pdf", compressiblePdf()))
			.header("Authorization", "Bearer " + writer))
			.andExpect(status().isNoContent());
		assertThat(myStatus(writer, module, exam)).isEqualTo("SUBMITTED");

		clock.set(Instant.parse("2026-10-20T07:30:00Z"));
		assertThat(myStatus(writer, module, exam)).isEqualTo("AWAITING_RESULT");
		assertThat(myStatus(missed, module, exam)).isEqualTo("MISSED");
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + missed))
			.andExpect(jsonPath("$.content[0].retakeAvailable").value(false));

		mockMvc.perform(multipart("/api/v1/live-exam-participants/" + participant + "/evaluation")
			.file(new MockMultipartFile("file", "mark.pdf", "application/pdf", compressiblePdf()))
			.param("marks", "5")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(myStatus(writer, module, exam)).isEqualTo("AWAITING_RESULT");
		mockMvc.perform(post("/api/v1/live-exam-participants/" + participant + "/publish")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(myStatus(writer, module, exam)).isEqualTo("RESULT_READY");
		assertThat(detailStatus(writer, exam)).isEqualTo("RESULT_READY");
	}

	@Test
	void aWrittenPaperWithNoPdfShowsFailedToSubmitAndStaffAndAnUndatedExamGetNoStatus() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Status failed");
		UUID exam = createWritten(staff, module);
		UUID studentId = seed("STUDENT");
		String student = token(studentId);
		mark(master, module, studentId);
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());

		clock.set(Instant.parse("2026-10-20T07:30:00Z"));
		assertThat(myStatus(student, module, exam)).isEqualTo("FAILED_TO_SUBMIT");
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].myStatus").doesNotExist())
			.andExpect(jsonPath("$.content[0].retakeAvailable").doesNotExist());
		mockMvc.perform(get("/api/v1/live-exams/" + exam).header("Authorization", "Bearer " + staff))
			.andExpect(jsonPath("$.myStatus").doesNotExist());
		mockMvc.perform(get("/api/v1/public/live-exams/" + exam))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myStatus").doesNotExist());

		clock.set(TEN_AM);
		UUID undatedModule = publishModule(staff, "Status undated");
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + undatedModule + "/exams").header("Authorization", "Bearer " + staff)
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
		UUID undated = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		addQuestion(staff, undated, "Two plus two", 2);
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mark(master, undatedModule, studentId);
		assertThat(myStatus(student, undatedModule, undated)).isEqualTo("NOT_ANNOUNCED");
	}

	private String myStatus(String token, UUID moduleId, UUID examId) throws Exception {
		JsonNode content = json.readTree(mockMvc.perform(get("/api/v1/live-exam-modules/" + moduleId + "/exams")
			.header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString()).get("content");
		for (JsonNode row : content) {
			if (row.get("id").asText().equals(examId.toString())) {
				return row.get("myStatus").asText();
			}
		}
		throw new AssertionError("Exam not listed " + examId);
	}

	private String detailStatus(String token, UUID examId) throws Exception {
		return json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + examId).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("myStatus").asText();
	}

	private String routineStatus(String token, UUID examId) throws Exception {
		JsonNode content = json.readTree(mockMvc.perform(get("/api/v1/me/exam-routine").param("size", "100")
			.header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString()).get("content");
		for (JsonNode row : content) {
			if (row.get("id").asText().equals(examId.toString())) {
				return row.get("myStatus").asText();
			}
		}
		throw new AssertionError("Exam not on the routine " + examId);
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
	void anOpenSittingReturnsTheSavedSelectionWithoutTheCorrectOption() throws Exception {
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

		String during = mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options[0].selected").value(true))
			.andExpect(jsonPath("$.questions[0].options[1].selected").value(false))
			.andExpect(jsonPath("$.questions[1].options[0].selected").value(true))
			.andExpect(jsonPath("$.questions[2].options[0].selected").value(false))
			.andExpect(jsonPath("$.questions[2].options[1].selected").value(false))
			.andExpect(jsonPath("$.questions[0].explanation").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(during).doesNotContain("\"correct\"", "Because");

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		releaseAnswers(holder.examId());
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options[0].selected").value(true))
			.andExpect(jsonPath("$.questions[1].options[0].selected").value(true))
			.andExpect(jsonPath("$.questions[0].options[0].correct").value(true))
			.andExpect(jsonPath("$.questions[0].explanation").value("Because"));

		UUID laterId = seed("STUDENT");
		String later = token(laterId);
		mark(master, holder.moduleId(), laterId);
		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + holder.examId() + "/retake").header("Authorization", "Bearer " + later))
			.andExpect(status().isOk());
		save(later, holder.examId(), holder.questions().subList(2, 3));
		String retake = mockMvc.perform(get("/api/v1/live-exams/" + holder.examId()).header("Authorization", "Bearer " + later))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.questions[0].options[0].selected").value(false))
			.andExpect(jsonPath("$.questions[2].options[0].selected").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(retake).doesNotContain("\"correct\"", "Because");
	}

	@Test
	void theSittingBlockAndTheResultKindAreTheStudentsOwn() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		Holder holder = publishMcq(staff);
		String student = token(holder.studentId());
		mark(master, holder.moduleId(), holder.studentId());
		UUID otherId = seed("STUDENT");
		String other = token(otherId);
		mark(master, holder.moduleId(), otherId);
		String exam = "/api/v1/live-exams/" + holder.examId();

		clock.set(Instant.parse("2026-10-20T06:00:00Z"));
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sitting").doesNotExist());

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post(exam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sitting.kind").value("LIVE"))
			.andExpect(jsonPath("$.sitting.startedAt").isString())
			.andExpect(jsonPath("$.sitting.deadlineAt").isString())
			.andExpect(jsonPath("$.sitting.submittedAt").doesNotExist());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + other))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sitting").doesNotExist());

		clock.set(Instant.parse("2026-10-20T06:20:00Z"));
		mockMvc.perform(post(exam + "/submit").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.sitting.submittedAt").isString());

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("LIVE"));

		clock.set(Instant.parse("2026-10-20T06:41:00Z"));
		mockMvc.perform(post(exam + "/retake").header("Authorization", "Bearer " + other))
			.andExpect(status().isOk());
		mockMvc.perform(get(exam).header("Authorization", "Bearer " + other))
			.andExpect(jsonPath("$.sitting.kind").value("RETAKE"));
		clock.set(Instant.parse("2026-10-20T07:22:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + holder.examId() + "/result").header("Authorization", "Bearer " + other))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"));
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
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Open pass","titleBn":"খোলা",
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
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + retake))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		clock.set(Instant.parse("2026-10-20T07:26:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake").header("Authorization", "Bearer " + retake))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("RETAKE_NOT_ALLOWED"));
		assertThat(participantCount(exam, retakeId)).isZero();
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/question-pdf").header("Authorization", "Bearer " + retake))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exams/" + exam + "/result").header("Authorization", "Bearer " + retake))
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

	@Test
	void theRoutineListsOpenExamsAcrossMarkedModules() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID science = createCategory(staff, "Science");
		UUID physics = createCategory(staff, "Physics", science);
		UUID arts = createCategory(staff, "Arts");
		UUID thermo = publishModuleIn(staff, physics, "Thermo");
		UUID literature = publishModuleIn(staff, arts, "Literature");
		UUID quiz = publishMcqIn(staff, thermo, "2026-10-20T12:00:00+06:00");
		UUID paper = publishWrittenAt(staff, literature, "2026-10-20T12:10:00+06:00");
		UUID studentId = seed("STUDENT");
		mark(master, thermo, studentId);
		mark(master, literature, studentId);
		String student = token(studentId);

		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].id").value(quiz.toString()))
			.andExpect(jsonPath("$.content[0].examType").value("MCQ"))
			.andExpect(jsonPath("$.content[0].titleEn").value("Noon quiz"))
			.andExpect(jsonPath("$.content[0].titleBn").value("কুইজ"))
			.andExpect(jsonPath("$.content[0].moduleId").value(thermo.toString()))
			.andExpect(jsonPath("$.content[0].moduleTitleEn").value("Thermo"))
			.andExpect(jsonPath("$.content[0].moduleTitleBn").value("মডিউল"))
			.andExpect(jsonPath("$.content[0].categories.length()").value(2))
			.andExpect(jsonPath("$.content[0].categories[0].id").value(science.toString()))
			.andExpect(jsonPath("$.content[0].categories[0].titleEn").value("Science"))
			.andExpect(jsonPath("$.content[0].categories[0].titleBn").value("বিভাগ"))
			.andExpect(jsonPath("$.content[0].categories[1].id").value(physics.toString()))
			.andExpect(jsonPath("$.content[0].categories[1].titleEn").value("Physics"))
			.andExpect(jsonPath("$.content[0].liveAt").value("2026-10-20T12:00:00+06:00"))
			.andExpect(jsonPath("$.content[0].endAt").value("2026-10-20T12:40:00+06:00"))
			.andExpect(jsonPath("$.content[0].durationMinutes").value(40))
			.andExpect(jsonPath("$.content[0].descriptionEn").doesNotExist())
			.andExpect(jsonPath("$.content[0].rulesEn").doesNotExist())
			.andExpect(jsonPath("$.content[0].questions").doesNotExist())
			.andExpect(jsonPath("$.content[0].questionPdf").doesNotExist())
			.andExpect(jsonPath("$.content[1].id").value(paper.toString()))
			.andExpect(jsonPath("$.content[1].examType").value("WRITTEN"))
			.andExpect(jsonPath("$.content[1].moduleTitleEn").value("Literature"))
			.andExpect(jsonPath("$.content[1].categories.length()").value(1))
			.andExpect(jsonPath("$.content[1].categories[0].titleEn").value("Arts"))
			.andExpect(jsonPath("$.content[1].liveAt").value("2026-10-20T12:10:00+06:00"))
			.andExpect(jsonPath("$.content[1].endAt").value("2026-10-20T12:50:00+06:00"))
			.andExpect(jsonPath("$.content[1].questionPdf").doesNotExist());
		assertThat(participantCount(quiz, studentId)).isZero();
		UUID oneModule = seed("STUDENT");
		mark(master, thermo, oneModule);
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + token(oneModule)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(quiz.toString()));

		UUID sameTimeModule = publishModuleIn(staff, arts, "Same time");
		UUID first = publishMcqIn(staff, literature, "2026-10-20T13:00:00+06:00");
		UUID second = publishMcqIn(staff, sameTimeModule, "2026-10-20T13:00:00+06:00");
		mark(master, sameTimeModule, studentId);
		java.util.List<UUID> expected = java.util.List.of(first, second).stream().sorted().toList();
		MvcResult tied = mockMvc.perform(get("/api/v1/me/exam-routine").param("page", "0").param("size", "20")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode rows = json.readTree(tied.getResponse().getContentAsString()).get("content");
		java.util.List<UUID> tiedIds = new java.util.ArrayList<>();
		for (JsonNode row : rows) {
			if ("2026-10-20T13:00:00+06:00".equals(row.get("liveAt").asText())) {
				tiedIds.add(UUID.fromString(row.get("id").asText()));
			}
		}
		assertThat(tiedIds).containsExactlyElementsOf(expected);
	}

	@Test
	void theRoutineHidesDraftsAnUnmarkedStudentAndStaff() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		String instructor = token(seed("INSTRUCTOR"));
		UUID category = createCategory(staff, "Open");
		UUID published = publishModuleIn(staff, category, "Published");
		UUID draftModule = draftModule(staff, category, "Draft module");
		publishMcqIn(staff, draftModule, "2026-10-20T12:00:00+06:00");
		UUID draftExam = createMcq(staff, published, "2026-10-20T12:00:00+06:00", false);
		createMcq(staff, published, "2026-10-20T13:00:00+06:00", true);
		UUID live = publishMcqIn(staff, published, "2026-10-20T14:00:00+06:00");
		UUID marked = seed("STUDENT");
		UUID unmarked = seed("STUDENT");
		mark(master, published, marked);
		mark(master, draftModule, marked);
		String student = token(marked);

		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(live.toString()));
		assertThat(draftExam).isNotNull();
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + token(unmarked)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").isEmpty());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + master))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + staff))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + instructor))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/me/exam-routine")).andExpect(status().isUnauthorized());
	}

	@Test
	void theRoutineDropsAnEndedExamAndLeavesTheRetake() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Window");
		UUID early = publishMcqIn(staff, module, "2026-10-20T12:00:00+06:00");
		UUID later = publishWrittenAt(staff, module, "2026-10-20T12:45:00+06:00");
		UUID studentId = seed("STUDENT");
		UUID absentId = seed("STUDENT");
		mark(master, module, studentId);
		mark(master, module, absentId);
		String student = token(studentId);
		String absent = token(absentId);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].id").value(early.toString()))
			.andExpect(jsonPath("$.content[1].id").value(later.toString()));

		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(get("/api/v1/me/exam-routine").param("now", "2099-01-01T00:00:00Z").param("size", "1")
			.header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(later.toString()));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2));
		assertThat(participantCount(early, absentId)).isZero();
		mockMvc.perform(post("/api/v1/live-exams/" + early + "/retake").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("RETAKE"));
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + absent))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(later.toString()));

		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + later + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.content[0].id").value(later.toString()));
		clock.set(Instant.parse("2026-10-20T07:25:00Z"));
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.content").isEmpty());
		mockMvc.perform(get("/api/v1/live-exams/" + later + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
	}

	@Test
	void rosterRemovalAndArchiveDropTheRoutineRowAndLeaveTheResult() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID science = createCategory(staff, "Science");
		UUID physics = createCategory(staff, "Physics", science);
		UUID arts = createCategory(staff, "Arts");
		UUID hidden = publishModuleIn(staff, physics, "Hidden");
		UUID open = publishModuleIn(staff, arts, "Open");
		UUID hiddenExam = publishMcqIn(staff, hidden, "2026-10-20T12:00:00+06:00");
		UUID openExam = publishMcqIn(staff, open, "2026-10-20T12:45:00+06:00");
		UUID studentId = seed("STUDENT");
		mark(master, hidden, studentId);
		mark(master, open, studentId);
		String student = token(studentId);

		clock.set(Instant.parse("2026-10-20T06:10:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + hiddenExam + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:40:00Z"));
		mockMvc.perform(get("/api/v1/live-exams/" + hiddenExam + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0));
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + hidden + "/roster/" + studentId)
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(openExam.toString()));
		mockMvc.perform(get("/api/v1/live-exams/" + hiddenExam + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.score").value(0));

		mark(master, hidden, studentId);
		clock.set(TEN_AM);
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.content.length()").value(2));
		mockMvc.perform(post("/api/v1/live-exam-categories/" + science + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(openExam.toString()));
		mockMvc.perform(post("/api/v1/live-exam-modules/" + open + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(jsonPath("$.content").isEmpty());
		mockMvc.perform(get("/api/v1/live-exams/" + hiddenExam + "/result").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk());
	}

	@Test
	void anUndatedExamStaysOffTheRoutineUntilItHasAWindow() throws Exception {
		String staff = token(seed("SYSTEM_ADMIN"));
		String master = token(seed("MASTER_ADMIN"));
		UUID module = publishModule(staff, "Undated");
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
		UUID undated = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		addQuestion(staff, undated, "Two plus two", 2);
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		UUID dated = publishMcqIn(staff, module, "2026-10-20T12:00:00+06:00");
		UUID studentId = seed("STUDENT");
		mark(master, module, studentId);
		String student = token(studentId);

		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(dated.toString()));
		mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + student)
			.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(2));
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SITTING_NOT_OPEN"));
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/retake").header("Authorization", "Bearer " + student))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SITTING_NOT_OPEN"));

		mockMvc.perform(patch("/api/v1/live-exams/" + undated).header("Authorization", "Bearer " + staff)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"liveAt\":\"2026-10-20T12:45:00+06:00\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));
		mockMvc.perform(get("/api/v1/me/exam-routine").header("Authorization", "Bearer " + student))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].id").value(dated.toString()));
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/final-save").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/publish").header("Authorization", "Bearer " + staff))
			.andExpect(status().isOk());
		clock.set(Instant.parse("2026-10-20T06:50:00Z"));
		mockMvc.perform(post("/api/v1/live-exams/" + undated + "/take").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.kind").value("LIVE"));
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

	private UUID draftModule(String token, UUID categoryId, String title) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","titleEn":"%s","titleBn":"মডিউল","descriptionEn":"d","descriptionBn":"বি"}
					""".formatted(categoryId, title)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID createMcq(String staff, UUID module, String liveAt, boolean finalSave) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Noon quiz","titleBn":"কুইজ",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"%s","durationMinutes":40,"passPercent":50
						}
						""".formatted(liveAt)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID exam = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		addQuestion(staff, exam, "Two plus two", 2);
		if (finalSave) {
			mockMvc.perform(post("/api/v1/live-exams/" + exam + "/final-save").header("Authorization", "Bearer " + staff))
				.andExpect(status().isOk());
		}
		return exam;
	}

	private UUID publishWrittenAt(String staff, UUID module, String liveAt) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"WRITTEN","paperLanguage":"BN","titleEn":"Noon paper","titleBn":"খাতা",
						  "descriptionEn":"d","descriptionBn":"বি","rulesEn":"English rules","rulesBn":"নিয়ম",
						  "liveAt":"%s","durationMinutes":40,"passPercent":50,"maximum":10
						}
						""".formatted(liveAt)))
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

	private UUID publishMcqIn(String staff, UUID module, String liveAt) throws Exception {
		MvcResult created = mockMvc
			.perform(post("/api/v1/live-exam-modules/" + module + "/exams").header("Authorization", "Bearer " + staff)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Noon quiz","titleBn":"কুইজ",
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
						  "examType":"MCQ","paperLanguage":"BN","titleEn":"Noon quiz","titleBn":"কুইজ",
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
						  "examType":"WRITTEN","paperLanguage":"BN","titleEn":"Noon paper","titleBn":"খাতা",
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

	/** What the staff release call sets (R-4). That call belongs to API-ADMIN, so the test writes the column. */
	private void releaseAnswers(UUID examId) {
		new JdbcTemplate(dataSource).update("update live_exams set answers_released_at = now() where id = ?", examId);
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

	private Object submittedAt(UUID examId, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select submitted_at from live_exam_participants where exam_id = ? and student_id = ?", Object.class, examId,
				studentId);
	}

	private Boolean passed(UUID examId, UUID studentId) {
		return new JdbcTemplate(dataSource).queryForObject(
				"select passed from live_exam_participants where exam_id = ? and student_id = ?", Boolean.class, examId,
				studentId);
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
