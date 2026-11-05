package com.coachingcenter.api.staffledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.common.audit.AuditRecorder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pay per written exam and the marker (staff pay plan, phase 8, decision P18): the pay-terms routes and who may read or set
 * them, the MCQ refusal, the notices, the create route's optional fields, the instructor's list, and the rate order the
 * earnings job uses (the exam's rate, else the instructor's, else the default).
 *
 * <p>
 * The test base switches the scheduled jobs off, so each test calls {@link StaffEarningsSyncJob#sync()} itself. Notices are
 * checked per recipient, so other data in the database cannot change a count.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class ExamPayTermsIT extends PostgresIntegrationTest {

	/** A live time in the future, so a created exam is not dated in the past. */
	private static final String LIVE_AT = "2027-01-10T12:00:00+06:00";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private StaffEarningsSyncJob job;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void theMasterAdminSetsAndClearsTheRateAndEachChangeIsLoggedNewestFirst() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Rate Master");
		UUID exam = exam("WRITTEN", title("Rates"));
		String token = token(master);

		JsonNode first = send(HttpMethod.PUT, pay(exam), token, body(60), 200);
		assertMoney(first.path("examRate"), "60");
		assertMoney(first.path("effectiveRate"), "60");
		// Null fields are left out of the JSON (non_null inclusion), so an exam with no marker has no instructor key.
		assertThat(first.path("instructor").isMissingNode() || first.path("instructor").isNull()).isTrue();
		assertThat(first.path("log").size()).isEqualTo(1);
		assertThat(first.path("log").get(0).path("kind").asText()).isEqualTo("RATE_SET");
		assertMoney(first.path("log").get(0).path("sheetRate"), "60");

		JsonNode second = send(HttpMethod.PUT, pay(exam), token, body(70), 200);
		assertThat(second.path("log").size()).isEqualTo(2);
		assertThat(second.path("log").get(0).path("kind").asText()).isEqualTo("RATE_CHANGED");
		assertMoney(second.path("log").get(0).path("previousSheetRate"), "60");
		assertMoney(second.path("log").get(0).path("sheetRate"), "70");
		assertThat(second.path("log").get(0).path("changedByName").asText()).isEqualTo("Rate Master");

		send(HttpMethod.PUT, pay(exam), token, body(70), 200);
		assertThat(logRows(exam)).isEqualTo(2);

		// Sent as raw JSON: the app's ObjectMapper leaves null fields out (non_null), which would turn {"sheetRate": null} into
		// {} and get the "key must be present" 400. The browser keeps the null.
		JsonNode cleared = sendRaw(HttpMethod.PUT, pay(exam), token, "{\"sheetRate\": null}", 200);
		assertThat(cleared.path("examRate").isMissingNode() || cleared.path("examRate").isNull()).isTrue();
		assertMoney(cleared.path("effectiveRate"), defaultRate().toPlainString());
		assertThat(cleared.path("log").get(0).path("kind").asText()).isEqualTo("RATE_CHANGED");
		JsonNode clearedRate = cleared.path("log").get(0).path("sheetRate");
		assertThat(clearedRate.isMissingNode() || clearedRate.isNull()).isTrue();
		assertThat(count("select count(*) from live_exam_pay_terms where exam_id = ?", exam)).isZero();

		assertThat(count("select count(*) from audit_log where action = ? and entity_id = ?",
				AuditRecorder.LIVE_EXAM_PAY_TERMS_SET, exam)).isEqualTo(3);
	}

	@Test
	void theAssignedInstructorIsToldWhenWhatTheyEarnChanges() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Told Master");
		UUID instructor = seed("INSTRUCTOR", "Told Instructor");
		UUID exam = exam("WRITTEN", title("Told"));
		assign(exam, instructor);
		String examTitle = examTitle(exam);
		String token = token(master);

		send(HttpMethod.PUT, pay(exam), token, body(60), 200);

		assertThat(rateNotices(instructor, examTitle)).isEqualTo(1);
		assertThat(payloadRate(instructor, "EXAM_RATE_CHANGED", examTitle, "fromRate")).isEqualByComparingTo(defaultRate());
		assertThat(payloadRate(instructor, "EXAM_RATE_CHANGED", examTitle, "toRate")).isEqualByComparingTo("60");

		send(HttpMethod.PUT, pay(exam), token, body(70), 200);
		assertThat(rateNotices(instructor, examTitle)).isEqualTo(2);
	}

	@Test
	void noRateNoticeGoesOutWhenTheInstructorsEarningsDoNotChange() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Same Master");
		UUID instructor = seed("INSTRUCTOR", "Same Instructor");
		jdbc.update("insert into staff_pay_settings (user_id, sheet_rate, updated_at) values (?, 60.00, now())", instructor);
		UUID exam = exam("WRITTEN", title("Same"));
		assign(exam, instructor);
		String examTitle = examTitle(exam);

		send(HttpMethod.PUT, pay(exam), token(master), body(60), 200);

		assertThat(rateNotices(instructor, examTitle)).isZero();
		assertThat(logRows(exam)).isEqualTo(1);
	}

	@Test
	void everyOtherMasterAdminIsToldButNotTheOneWhoMadeTheChange() throws Exception {
		UUID actor = seed("MASTER_ADMIN", "Changing Master");
		UUID other = seed("MASTER_ADMIN", "Other Master");
		UUID exam = exam("WRITTEN", title("Masters"));
		String examTitle = examTitle(exam);

		send(HttpMethod.PUT, pay(exam), token(actor), body(60), 200);

		assertThat(count("""
				select count(*) from staff_notifications
				where recipient_id = ? and kind = 'EXAM_PAY_TERMS_CHANGED' and payload->>'examTitleEn' = ?
				""", other, examTitle)).isEqualTo(1);
		assertThat(jdbc.queryForObject("""
				select payload->>'personName' from staff_notifications
				where recipient_id = ? and kind = 'EXAM_PAY_TERMS_CHANGED' and payload->>'examTitleEn' = ?
				""", String.class, other, examTitle)).isEqualTo("Changing Master");
		assertThat(count("""
				select count(*) from staff_notifications
				where recipient_id = ? and kind = 'EXAM_PAY_TERMS_CHANGED' and payload->>'examTitleEn' = ?
				""", actor, examTitle)).isZero();
	}

	@Test
	void anMcqExamRefusesARateAndOnlyTheMasterAdminSetsOne() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Refusal Master");
		UUID sysAdmin = seed("SYSTEM_ADMIN", "Refusal Admin");
		UUID instructor = seed("INSTRUCTOR", "Refusal Instructor");
		UUID mcq = exam("MCQ", title("Quiz"));
		UUID written = exam("WRITTEN", title("Paper"));
		assign(written, instructor);

		JsonNode refused = send(HttpMethod.PUT, pay(mcq), token(master), body(60), 400);
		assertThat(refused.path("fieldErrors").get(0).path("field").asText()).isEqualTo("sheetRate");
		assertThat(count("select count(*) from live_exam_pay_terms where exam_id = ?", mcq)).isZero();

		send(HttpMethod.PUT, pay(written), token(sysAdmin), body(60), 403);
		send(HttpMethod.PUT, pay(written), token(instructor), body(60), 403);
		send(HttpMethod.PUT, pay(written), token(master), Map.of("sheetRate", "sixty"), 400);
		send(HttpMethod.PUT, pay(written), token(master), body(0), 400);
		send(HttpMethod.PUT, pay(written), token(master), Map.of("sheetRate", 50.005), 400);
		send(HttpMethod.PUT, pay(written), token(master), Map.of(), 400);
		assertThat(count("select count(*) from live_exam_pay_terms where exam_id = ?", written)).isZero();
	}

	@Test
	void theTermsAreReadByTheMasterAdminAndTheAssignedInstructorOnly() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Reader Master");
		UUID sysAdmin = seed("SYSTEM_ADMIN", "Reader Admin");
		UUID instructor = seed("INSTRUCTOR", "Reader Instructor");
		UUID otherInstructor = seed("INSTRUCTOR", "Other Reader");
		UUID exam = exam("WRITTEN", title("Readable"));
		assign(exam, instructor);

		send(HttpMethod.GET, pay(exam), token(master), null, 200);
		JsonNode mine = send(HttpMethod.GET, pay(exam), token(instructor), null, 200);
		assertThat(mine.path("instructor").path("id").asText()).isEqualTo(instructor.toString());
		send(HttpMethod.GET, pay(exam), token(otherInstructor), null, 403);
		send(HttpMethod.GET, pay(exam), token(sysAdmin), null, 403);
		send(HttpMethod.GET, "/api/v1/live-exams/" + UUID.randomUUID() + "/pay-terms", token(master), null, 404);
	}

	@Test
	void aWrittenExamCanBeCreatedWithAPayRateAndAMarker() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Creator");
		UUID instructor = seed("INSTRUCTOR", "Created Marker");
		String token = token(master);
		UUID category = category(token, title("Create cat"));
		String examTitle = title("Created paper");

		JsonNode created = sendRaw(HttpMethod.POST, "/api/v1/live-exams", token,
				examBody("WRITTEN", examTitle, category, ",\"sheetRate\":60,\"instructorId\":\"" + instructor + "\""), 201);
		UUID exam = UUID.fromString(created.path("id").asText());

		assertThat(jdbc.queryForObject("select instructor_id from live_exams where id = ?", UUID.class, exam))
			.isEqualTo(instructor);
		assertThat(jdbc.queryForObject("select sheet_rate from live_exam_pay_terms where exam_id = ?", BigDecimal.class, exam))
			.isEqualByComparingTo("60");
		assertThat(jdbc.queryForObject("select kind from live_exam_assignment_log where exam_id = ?", String.class, exam))
			.isEqualTo("RATE_SET");
		assertThat(send(HttpMethod.GET, pay(exam), token, null, 200).path("instructor").path("id").asText())
			.isEqualTo(instructor.toString());
	}

	@Test
	void theCreateFieldsAreRefusedForAnMcqExamAndAMarkerThatIsNotAnInstructorLeavesNoExam() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Refused Creator");
		UUID student = student();
		String token = token(master);
		UUID category = category(token, title("Refused cat"));

		String mcqTitle = title("Refused quiz");
		JsonNode mcq = sendRaw(HttpMethod.POST, "/api/v1/live-exams", token,
				examBody("MCQ", mcqTitle, category, ",\"sheetRate\":60"), 400);
		assertThat(mcq.path("fieldErrors").get(0).path("field").asText()).isEqualTo("sheetRate");
		JsonNode mcqMarker = sendRaw(HttpMethod.POST, "/api/v1/live-exams", token,
				examBody("MCQ", mcqTitle, category, ",\"instructorId\":\"" + student + "\""), 400);
		assertThat(mcqMarker.path("fieldErrors").get(0).path("field").asText()).isEqualTo("instructorId");
		assertThat(count("select count(*) from live_exams where title_en = ?", mcqTitle)).isZero();

		String studentTitle = title("Student marker");
		sendRaw(HttpMethod.POST, "/api/v1/live-exams", token,
				examBody("WRITTEN", studentTitle, category, ",\"sheetRate\":60,\"instructorId\":\"" + student + "\""), 400);
		assertThat(count("select count(*) from live_exams where title_en = ?", studentTitle)).isZero();
	}

	@Test
	void aSystemAdminCannotSetARateOrAMarkerThroughTheCreateRouteButCanStillCreate() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Category Owner");
		UUID systemAdmin = seed("SYSTEM_ADMIN", "Exam Author");
		UUID instructor = seed("INSTRUCTOR", "Wanted Marker");
		UUID category = category(token(master), title("Author cat"));
		String author = token(systemAdmin);

		// Decision O1: only the Master Admin sets pay or assigns a marker, on create as everywhere else.
		String rated = title("Author rated");
		sendRaw(HttpMethod.POST, "/api/v1/live-exams", author, examBody("WRITTEN", rated, category, ",\"sheetRate\":60"), 403);
		String marked = title("Author marked");
		sendRaw(HttpMethod.POST, "/api/v1/live-exams", author,
				examBody("WRITTEN", marked, category, ",\"instructorId\":\"" + instructor + "\""), 403);
		assertThat(count("select count(*) from live_exams where title_en in (?, ?)", rated, marked)).isZero();
		assertThat(count("select count(*) from live_exam_pay_terms t join live_exams e on e.id = t.exam_id where e.title_en = ?",
				rated)).isZero();

		// Without the two fields, the System Admin creates a written exam exactly as before.
		sendRaw(HttpMethod.POST, "/api/v1/live-exams", author, examBody("WRITTEN", title("Author plain"), category, ""), 201);
	}

	@Test
	void aCreateWithoutTheNewFieldsLeavesNoPayTermsBehind() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Plain Creator");
		String token = token(master);
		UUID category = category(token, title("Plain cat"));

		JsonNode created = sendRaw(HttpMethod.POST, "/api/v1/live-exams", token,
				examBody("WRITTEN", title("Plain paper"), category, ""), 201);

		assertThat(count("select count(*) from live_exam_pay_terms where exam_id = ?",
				UUID.fromString(created.path("id").asText()))).isZero();
	}

	@Test
	void anInstructorListsTheirWrittenExamsWithTheRateEachSheetEarnsAt() throws Exception {
		UUID instructor = seed("INSTRUCTOR", "Lister");
		UUID other = seed("INSTRUCTOR", "Not Me");
		jdbc.update("insert into staff_pay_settings (user_id, sheet_rate, updated_at) values (?, 60.00, now())", instructor);
		UUID plain = exam("WRITTEN", title("Mine"));
		assign(plain, instructor);
		UUID rated = exam("WRITTEN", title("Mine rated"));
		assign(rated, instructor);
		jdbc.update("insert into live_exam_pay_terms (exam_id, sheet_rate, updated_at) values (?, 75.00, now())", rated);
		UUID theirs = exam("WRITTEN", title("Theirs"));
		assign(theirs, other);
		UUID markedSheet = sheet(rated, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(30), null);
		syncUntilPaid(markedSheet);

		JsonNode list = send(HttpMethod.GET, "/api/v1/me/pay/exams", token(instructor), null, 200);

		assertThat(list.size()).isEqualTo(2);
		JsonNode ratedRow = row(list, rated);
		assertMoney(ratedRow.path("effectiveRate"), "75");
		assertMoney(ratedRow.path("examRate"), "75");
		assertThat(ratedRow.path("marked").asInt()).isEqualTo(1);
		assertThat(ratedRow.path("published").asInt()).isEqualTo(1);
		JsonNode plainRow = row(list, plain);
		assertMoney(plainRow.path("effectiveRate"), "60");
		assertThat(plainRow.path("examRate").isMissingNode() || plainRow.path("examRate").isNull()).isTrue();
		assertThat(plainRow.path("marked").asInt()).isZero();

		UUID master = seed("MASTER_ADMIN", "Lister Master");
		send(HttpMethod.GET, "/api/v1/me/pay/exams", token(master), null, 403);
	}

	@Test
	void aSheetEarnsAtTheExamsRateThenTheInstructorsThenTheDefault() throws Exception {
		UUID master = seed("MASTER_ADMIN", "Order Master");
		UUID instructor = seed("INSTRUCTOR", "Order Instructor");
		UUID defaulted = seed("INSTRUCTOR", "Default Instructor");
		jdbc.update("insert into staff_pay_settings (user_id, sheet_rate, updated_at) values (?, 60.00, now())", instructor);

		UUID rated = exam("WRITTEN", title("Order rated"));
		assign(rated, instructor);
		UUID plain = exam("WRITTEN", title("Order plain"));
		assign(plain, instructor);
		UUID fallsBack = exam("WRITTEN", title("Order default"));
		assign(fallsBack, defaulted);
		send(HttpMethod.PUT, pay(rated), token(master), body(75), 200);

		UUID sheetRated = sheet(rated, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(30), null);
		UUID sheetPlain = sheet(plain, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(30), null);
		UUID sheetDefault = sheet(fallsBack, student(), 1, "LIVE", "SUBMITTED", defaulted, minutesAgo(90), minutesAgo(30),
				null);
		syncUntilPaid(sheetRated, sheetPlain, sheetDefault);

		assertLine(sheetRated, "75.00");
		assertLine(sheetPlain, "60.00");
		assertLine(sheetDefault, defaultRate().toPlainString());

		// A later change of the exam's rate reaches sheets published from now on and leaves the line already written alone.
		send(HttpMethod.PUT, pay(rated), token(master), body(80), 200);
		UUID later = sheet(rated, student(), 1, "LIVE", "SUBMITTED", instructor, minutesAgo(90), minutesAgo(20), null);
		syncUntilPaid(later);

		assertLine(later, "80.00");
		assertLine(sheetRated, "75.00");
	}

	/* ---------- helpers ---------- */

	/**
	 * Runs the earnings sync until each sheet has its line. The job takes the oldest unpaid sheets first, so a backlog left
	 * by other tests can need several runs before these sheets are reached.
	 */
	private void syncUntilPaid(UUID... sheets) {
		for (int run = 0; run < 50; run++) {
			job.sync();
			boolean all = true;
			for (UUID sheet : sheets) {
				all = all && count("select count(*) from staff_ledger where participant_id = ?", sheet) == 1;
			}
			if (all) {
				return;
			}
		}
		throw new AssertionError("The earnings sync did not reach the sheets.");
	}

	private void assertLine(UUID sheet, String amount) {
		assertThat(jdbc.queryForObject("select amount from staff_ledger where participant_id = ?", BigDecimal.class, sheet))
			.isEqualByComparingTo(amount);
		assertThat(jdbc.queryForObject("select sheet_rate from staff_ledger where participant_id = ?", BigDecimal.class, sheet))
			.isEqualByComparingTo(amount);
	}

	private static JsonNode row(JsonNode list, UUID exam) {
		for (JsonNode row : list) {
			if (exam.toString().equals(row.path("examId").asText())) {
				return row;
			}
		}
		throw new AssertionError("No row for " + exam + " in " + list);
	}

	private long logRows(UUID exam) {
		return count("select count(*) from live_exam_assignment_log where exam_id = ?", exam);
	}

	private long rateNotices(UUID instructor, String examTitle) {
		return count("""
				select count(*) from staff_notifications
				where recipient_id = ? and kind = 'EXAM_RATE_CHANGED' and payload->>'examTitleEn' = ?
				""", instructor, examTitle);
	}

	private BigDecimal payloadRate(UUID recipient, String kind, String examTitle, String key) {
		String text = jdbc.queryForObject("""
				select payload->>'%s' from staff_notifications
				where recipient_id = ? and kind = ? and payload->>'examTitleEn' = ?
				order by created_at desc limit 1
				""".formatted(key), String.class, recipient, kind, examTitle);
		return new BigDecimal(text);
	}

	private String examTitle(UUID exam) {
		return jdbc.queryForObject("select title_en from live_exams where id = ?", String.class, exam);
	}

	private void assign(UUID exam, UUID instructor) {
		jdbc.update("update live_exams set instructor_id = ? where id = ?", instructor, exam);
	}

	private BigDecimal defaultRate() {
		return jdbc.queryForObject("select sheet_rate from staff_pay_defaults where id = 1", BigDecimal.class);
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	private UUID exam(String type, String title) {
		UUID category = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)
				values (?, 'Pay', 'পে', 0, now(), now())
				""", category);
		UUID exam = UUID.randomUUID();
		jdbc.update("""
				insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en,
				  description_bn, rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status,
				  finally_saved, answers_changeable, created_at, updated_at)
				values (?, null, ?, ?, ?, 'খাতা', 'd', 'd', 'r', 'r', 'BN', now(), now() + make_interval(mins => 40),
				  40, 'LIVE', 'PUBLISHED', true, true, now(), now())
				""", exam, category, type, title);
		return exam;
	}

	private UUID category(String token, String title) throws Exception {
		JsonNode created = sendRaw(HttpMethod.POST, "/api/v1/live-exam-categories", token,
				"{\"titleEn\":\"" + title + "\",\"titleBn\":\"বিভাগ\"}", 201);
		return UUID.fromString(created.path("id").asText());
	}

	/** The create body. {@code extra} is spliced in as more members, such as the pay rate or the marker. */
	private static String examBody(String type, String title, UUID category, String extra) {
		String maximum = "WRITTEN".equals(type) ? ",\"maximum\":10" : "";
		return """
				{"categoryId":"%s","examType":"%s","paperLanguage":"BN","titleEn":"%s","titleBn":"পরীক্ষা",
				 "descriptionEn":"English description","descriptionBn":"বাংলা বিবরণ","rulesEn":"English rules",
				 "rulesBn":"বাংলা নিয়ম","durationMinutes":40,"passPercent":50,"liveAt":"%s"%s%s}
				""".formatted(category, type, title, LIVE_AT, maximum, extra);
	}

	private static String pay(UUID exam) {
		return "/api/v1/live-exams/" + exam + "/pay-terms";
	}

	private static Map<String, Object> body(Number sheetRate) {
		return Map.of("sheetRate", sheetRate);
	}

	/** A student who sat the paper. Students hold no pay, so they have no admin profile. */
	private UUID student() {
		return seed("STUDENT", null);
	}

	private UUID sheet(UUID exam, UUID student, int attempt, String kind, String status, UUID marker, Instant markedAt,
			Instant publishedAt, Instant readyAt) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at,
				  status, answer_stored_file_name, created_at, updated_at, attempt_no, evaluated_by, evaluated_at,
				  published_at, ready_for_release_at, score)
				values (?, ?, ?, ?, now(), now(), now(), ?, 'answer.pdf', now(), now(), ?, ?, ?, ?, ?, ?)
				""", id, exam, student, kind, status, attempt, marker, ts(markedAt), ts(publishedAt), ts(readyAt),
				marker == null ? null : 40);
		return id;
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static Instant minutesAgo(long minutes) {
		return Instant.now().minus(Duration.ofMinutes(minutes));
	}

	private static String title(String prefix) {
		return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
	}

	private static void assertMoney(JsonNode node, String expected) {
		assertThat(new BigDecimal(node.asText()).compareTo(new BigDecimal(expected))).as(node.toString()).isZero();
	}

	/** Sends one request, expects {@code status}, and returns the body read as JSON (a missing body reads as an empty node). */
	private JsonNode send(HttpMethod method, String path, String token, Object body, int status) throws Exception {
		MockHttpServletRequestBuilder builder = request(method, path).header("Authorization", "Bearer " + token);
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
		}
		MvcResult result = mockMvc.perform(builder).andExpect(status().is(status)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	/** As {@link #send}, with the body sent as the JSON text given. */
	private JsonNode sendRaw(HttpMethod method, String path, String token, String rawJson, int status) throws Exception {
		MvcResult result = mockMvc.perform(request(method, path).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(rawJson)).andExpect(status().is(status)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private UUID seed(String role, String fullName) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, uniquePhone(), "pay-terms-" + id + "@example.com", passwords.encode("password1"), role);
		if (fullName != null) {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, fullName);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc.queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/auth/login")
			.header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("identifier", phone, "password", "password1"))))
			.andExpect(status().isOk())
			.andReturn();
		return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

}
