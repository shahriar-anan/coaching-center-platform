package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;

/**
 * The package enrolment rules that have no other test (plan 14.3 D22, 14.4, 14.11 and 14.8): a roster row and a direct grant
 * cannot coexist on a package, the overlap fields on a quote, the package link on the enrolment lists, and the exam-time email on
 * a direct enrolment. Nothing else is covered here. The clock is 10:00 in Dhaka on 20 October 2026.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, InstallmentTestSupport.ClockConfig.class })
class PackageEnrolmentRulesIT extends InstallmentTestSupport {

	private static final String FUTURE = "2026-11-25T12:00:00+06:00";

	private static final String PAST = "2026-10-01T12:00:00+06:00";

	@Test
	void aRosterRowBlocksADirectGrantOnThePackage() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Roster Blocks Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Rostered Student");
		UUID module = module(system, "Roster blocks");
		rosterAdd(master, module, student).andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(grantBody(student, module, "Trying a grant")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STUDENT_ON_ROSTER"));

		// The same refusal for a payment, which is the path the enrol form takes.
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(paidGrantBody(student, module)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STUDENT_ON_ROSTER"));

		// Nothing was written for the refused enrolments: no grant and no payment plan.
		assertThat(count("select count(*) from live_exam_grants where student_id = ?", student)).isZero();
		assertThat(count("select count(*) from live_exam_payment_plans where student_id = ?", student)).isZero();
	}

	@Test
	void aDirectGrantBlocksAddingTheStudentToTheRoster() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Grant Blocks Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Granted Student");
		UUID module = module(system, "Grant blocks");
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(grantBody(student, module, "Direct first")))
			.andExpect(status().isCreated());

		rosterAdd(master, module, student)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("STUDENT_HAS_DIRECT_ENROLMENT"));

		// A paste does not fail for the whole list. The student is listed under alreadyEnrolled and not marked.
		String email = jdbc().queryForObject("select email from users where id = ?", String.class, student);
		MvcResult paste = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster/paste")
			.header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"values\":[\"" + email + "\"]}"))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode body = json.readTree(paste.getResponse().getContentAsString());
		assertThat(body.get("marked").size()).isZero();
		assertThat(body.get("alreadyEnrolled").get(0).get("id").asText()).isEqualTo(student.toString());
		assertThat(count("select count(*) from live_exam_roster where student_id = ?", student)).isZero();
	}

	@Test
	void aDirectPackageEnrolmentSendsTheExamTimeEmailOnlyForExamsThatHaveNotEnded() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Email Package Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Emailed Package Student");
		UUID module = module(system, "Email package");
		UUID upcoming = datedExam(master, module, FUTURE, "Upcoming in package");
		UUID ended = datedExam(master, module, PAST, "Ended in package");

		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(grantBody(student, module, "Package with dates")))
			.andExpect(status().isCreated());

		assertThat(examMails(student, upcoming)).isEqualTo(1);
		assertThat(examMails(student, ended)).isZero();
	}

	@Test
	void aDirectExamEnrolmentSendsTheExamTimeEmail() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Email Exam Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Emailed Exam Student");
		UUID module = module(system, "Email exam");
		UUID exam = datedExam(master, module, FUTURE, "Enrolled exam");

		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examGrantBody(student, exam, "Exam enrolment")))
			.andExpect(status().isCreated());

		assertThat(examMails(student, exam)).isEqualTo(1);
	}

	@Test
	void theQuoteNamesAnExamTheStudentHoldsThroughAnotherPackage() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Overlap Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Overlap Student");
		UUID first = module(system, "First package");
		UUID second = module(system, "Second package");
		UUID exam = datedExam(master, first, FUTURE, "Shared exam");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + second + "/exams/" + exam).header("Authorization", "Bearer " + master))
			.andExpect(status().is2xxSuccessful());
		// Attaching an exam to a published package only stages the change. The membership exists once the package is published again.
		mockMvc.perform(post("/api/v1/live-exam-modules/" + second + "/final-save").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + second + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(grantBody(student, first, "Holds the first package")))
			.andExpect(status().isCreated());

		// Enrolling in the second package: the shared exam is an overlap, named by the package that gives it.
		mockMvc.perform(get("/api/v1/live-exam-grants/quote").param("scope", "MODULE").param("moduleId", second.toString())
			.param("studentId", student.toString()).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overlaps.length()").value(1))
			.andExpect(jsonPath("$.overlaps[0].kind").value("EXAM"))
			.andExpect(jsonPath("$.overlaps[0].id").value(exam.toString()))
			.andExpect(jsonPath("$.overlaps[0].via").value("PACKAGE"))
			.andExpect(jsonPath("$.overlaps[0].packageTitleEn").value("First package"));

		// Enrolling on the exam: the first package is the overlap, and it names itself, so it has no package title.
		mockMvc.perform(get("/api/v1/live-exam-grants/quote").param("scope", "EXAM").param("examId", exam.toString())
			.param("studentId", student.toString()).header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.overlaps.length()").value(1))
			.andExpect(jsonPath("$.overlaps[0].kind").value("PACKAGE"))
			.andExpect(jsonPath("$.overlaps[0].id").value(first.toString()))
			.andExpect(jsonPath("$.overlaps[0].via").value("DIRECT"));
	}

	@Test
	void theEnrolmentListsGiveAPackageRowItsPackageAndAnExamGrantNone() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Link Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID packageStudent = seed("STUDENT", "Package Row Student");
		UUID examStudent = seed("STUDENT", "Exam Row Student");
		UUID module = module(system, "Linked package");
		UUID exam = datedExam(master, module, FUTURE, "Linked exam");
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(grantBody(packageStudent, module, "Package route")))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exam-grants").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content(examGrantBody(examStudent, exam, "Exam route")))
			.andExpect(status().isCreated());

		// The exam page: the package student's row names the package; the exam student's row names none.
		JsonNode examRows = json.readTree(mockMvc.perform(get("/api/v1/live-exams/" + exam + "/enrolments")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		assertThat(rowFor(examRows, packageStudent).get("moduleId").asText()).isEqualTo(module.toString());
		assertThat(rowFor(examRows, examStudent).has("moduleId")).isFalse();

		// The package page: the row names its own package.
		JsonNode packageRows = json.readTree(mockMvc.perform(get("/api/v1/live-exam-modules/" + module + "/enrolments")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		assertThat(rowFor(packageRows, packageStudent).get("moduleId").asText()).isEqualTo(module.toString());
	}

	/** The row of one student in an enrolment list, or a missing node when the student is not there. */
	private JsonNode rowFor(JsonNode page, UUID student) {
		for (JsonNode row : page.get("content")) {
			if (student.toString().equals(row.get("studentId").asText())) {
				return row;
			}
		}
		throw new AssertionError("no row for " + student);
	}

	/** The roster add for one student, as the package page makes it. */
	private org.springframework.test.web.servlet.ResultActions rosterAdd(String master, UUID module, UUID student) throws Exception {
		return mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/roster").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + student + "\"}"));
	}

	/** A package grant with a reason and no payment (No charge). */
	private static String grantBody(UUID student, UUID module, String reason) {
		return "{\"studentId\":\"" + student + "\",\"scope\":\"MODULE\",\"moduleId\":\"" + module + "\",\"examId\":null,\"reason\":\""
				+ reason + "\"}";
	}

	/** An exam grant with a reason and no payment. */
	private static String examGrantBody(UUID student, UUID exam, String reason) {
		return "{\"studentId\":\"" + student + "\",\"scope\":\"EXAM\",\"moduleId\":null,\"examId\":\"" + exam + "\",\"reason\":\""
				+ reason + "\"}";
	}

	/** A package grant taken with a payment, the way the enrol form sends it. */
	private static String paidGrantBody(UUID student, UUID module) {
		return "{\"studentId\":\"" + student + "\",\"scope\":\"MODULE\",\"moduleId\":\"" + module + "\",\"examId\":null,\"reason\":\"\","
				+ "\"payment\":{\"agreedTotal\":300,\"amount\":100,\"method\":\"BKASH\",\"reference\":\"REF-ROSTER\","
				+ "\"paidOn\":\"" + TODAY + "\",\"note\":null,\"schedule\":[" + row("100", TODAY) + "," + row("100", "2026-11-20") + ","
				+ row("100", "2026-12-20") + "]}}";
	}

	/** A published exam on a package, with one question, dated at {@code liveAt}. */
	private UUID datedExam(String master, UUID module, String liveAt, String title) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/live-exam-modules/" + module + "/exams")
			.header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"examType":"MCQ","paperLanguage":"BN","titleEn":"%s","titleBn":"কুইজ","descriptionEn":"d","descriptionBn":"বি",
					 "rulesEn":"English rules","rulesBn":"নিয়ম","liveAt":"%s","durationMinutes":40,"passPercent":50}
					""".formatted(title, liveAt)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
		mockMvc.perform(post("/api/v1/live-exams/" + id + "/questions").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"stem\":\"Two plus two\",\"marks\":2,\"explanation\":\"Because\",\"options\":[{\"text\":\"Four\",\"correct\":true},"
					+ "{\"text\":\"Five\",\"correct\":false}]}"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/live-exams/" + id + "/final-save").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/live-exams/" + id + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		return id;
	}

	/** The exam-time mail rows for one student and one exam (the message key holds both ids). */
	private long examMails(UUID student, UUID exam) {
		return count("select count(*) from exam_outbound_mail where kind = 'EXAM_TIME' and user_id = ? and message_key like ?",
				student, "%" + exam + "%");
	}

	private long count(String sql, Object... args) {
		Long value = jdbc().queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

}
