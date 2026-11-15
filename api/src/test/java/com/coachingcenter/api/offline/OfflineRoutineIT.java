package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Monthly routines of an offline batch (plan sections 4.2, 4.4, 5 and 6, stage 2): the reader on the sample routine, the preview and its
 * flags, the unknown instructor "Milon", the confirm and its refusals, the difference on a second upload, the settled classes that stay
 * as they are, the student copy without names, and the permissions. Every test makes its own batch, so the class can run again on the
 * same database. The instructors the sample names are created when they are missing, and a "Milon" left by an earlier run is renamed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineRoutineIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineRoutineIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-10-20T04:00:00Z");

	private static final String SAMPLE = "fixtures/routine/20th BJS Foundation Batch- NOVEMBER.pdf";

	/**
	 * The table rows of the sample, counted on its pages: page 1 has 14 (25 October to 6 November), page 2 has 21 (7 to 24 November) and
	 * page 3 has 8 (26 November to 7 December). The header and the blank row under the last row are not classes.
	 */
	private static final int SAMPLE_ROWS = 43;

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

	@BeforeEach
	void fixTheClock() {
		clock.set(NOW);
	}

	@Test
	void sampleRoutineYieldsTheRowsCountedOnItsPagesAndTheirFlags() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		ensureKnown(master, "Tuhin Hossain");
		ensureKnown(master, "Anarul Islam");
		makeUnknown(master, "Milon");
		UUID batch = batch(master, "2027-06-30");

		JsonNode routine = upload(master, batch, "2026-11", sample(), 201);

		assertThat(routine.get("status").asText()).isEqualTo("PREVIEW");
		JsonNode rows = routine.get("rows");
		assertThat(rows.size()).isEqualTo(SAMPLE_ROWS);

		// 27-07-2026 sits among October's dates: before the upload day (past), out of order, and its day (Tuesday) is not the real day (Monday).
		JsonNode july = findRow(routine, "2026-07-27", "Contract Act");
		assertThat(codes(july)).contains("DATE_PAST", "DATE_OUT_OF_ORDER", "WEEKDAY_MISMATCH");

		// The Evidence Act rows have no instructor in the PDF: they import empty and are flagged, never refused.
		JsonNode evidence = findRow(routine, "2026-10-30", "Evidence Act");
		assertThat(evidence.get("instructorText").asText()).isEmpty();
		assertThat(codes(evidence)).contains("NO_INSTRUCTOR");
		assertThat(count(rows, "instructorText", "")).isEqualTo(7);

		// Three exam rows: MCQ Exam (5 November), Exam (21 November) and Monthly Exam (7 December). Each is flagged for who is paid.
		List<JsonNode> exams = byKind(rows, "EXAM");
		assertThat(exams).hasSize(3);
		for (JsonNode exam : exams) {
			assertThat(codes(exam)).contains("EXAM_ROW");
		}
		JsonNode mcq = findRow(routine, "2026-11-05", "MCQ Exam");
		assertThat(mcq.get("instructorText").asText()).isEmpty();
		assertThat(mcq.get("topic").asText()).isEqualTo("3rd to 19th BJS Previous Question");
		assertThat(mcq.get("startsAt").asText()).isEqualTo("17:00");
		assertThat(mcq.get("endsAt").asText()).isEqualTo("18:20");
		JsonNode bracketed = findRow(routine, "2026-11-21", "Exam");
		assertThat(bracketed.get("topic").asText()).isEqualTo("(26 Oct- 17 Nov)");

		// A date cell that spans two rows is carried down to the second row, with its day.
		JsonNode second = findRow(routine, "2026-10-30", "Succession");
		assertThat(second.get("date").asText()).isEqualTo("2026-10-30");
		assertThat(second.get("day").asText()).isEqualTo("Friday");
		assertThat(second.get("startsAt").asText()).isEqualTo("10:35");
		assertThat(second.get("endsAt").asText()).isEqualTo("12:15");
		assertThat(count(rows, "date", "2026-11-06")).isEqualTo(2);

		// "Milon" is not known yet: every row with that name is listed as a new instructor, and the confirm is not blocked by it.
		assertThat(count(rows, "instructorText", "Milon")).isEqualTo(12);
		assertThat(routine.get("canConfirm").asBoolean()).isTrue();
		assertThat(unmatchedNames(routine)).containsExactly("Milon:12");
	}

	@Test
	void aRoutineCountsFromItsUploadDayAndANewUploadReplacesOnlyFromItsOwnDay() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		ensureKnown(master, "Tuhin Hossain");
		ensureKnown(master, "Anarul Islam");
		ensureKnown(master, "Milon");
		UUID batch = batch(master, "2027-06-30");

		// Uploaded on 10 November (Dhaka): the routine counts from that day, and its earlier dates are flagged as past.
		clock.set(Instant.parse("2026-11-10T04:00:00Z"));
		JsonNode first = upload(master, batch, null, sample(), 201);
		assertThat(first.get("effectiveFrom").asText()).isEqualTo("2026-11-10");
		assertThat(codes(findRow(first, "2026-11-07", "Constitution"))).contains("DATE_PAST");
		assertThat(codes(findRow(first, "2026-11-24", "Registration Act"))).doesNotContain("DATE_PAST");

		// The past rows still become classes.
		respond(post("/api/v1/offline-routines/" + id(first) + "/confirm", master, null), 200);
		assertThat(classOn(batch, "2026-11-07", "Constitution")).isNotNull();
		assertThat(classOn(batch, "2026-11-24", "Registration Act")).isNotNull();

		// Both classes are changed by hand after the confirm.
		jdbc().update("update offline_classes set topic = 'Changed by hand' where batch_id = ? and held_on in ('2026-11-07', '2026-11-24')",
				batch);

		// The same routine uploaded again on 20 November replaces the classes from 20 November onward only: the class of 24 November takes
		// the routine's values again, and the class of 7 November, before that day, keeps the change even though it is unsettled.
		clock.set(Instant.parse("2026-11-20T04:00:00Z"));
		JsonNode second = upload(master, batch, null, sample(), 201);
		assertThat(second.get("effectiveFrom").asText()).isEqualTo("2026-11-20");
		respond(post("/api/v1/offline-routines/" + id(second) + "/confirm", master, null), 200);

		assertThat(classOn(batch, "2026-11-07", "Constitution").getTopic()).isEqualTo("Changed by hand");
		assertThat(classOn(batch, "2026-11-24", "Registration Act").getTopic()).isNotEqualTo("Changed by hand");
		assertThat(jdbc().queryForObject("select status from offline_batch_routines where id = ?", String.class, id(first)))
			.isEqualTo("SUPERSEDED");
		assertThat(jdbc().queryForObject("select count(*) from offline_batch_routines where batch_id = ? and status = 'CONFIRMED'",
				Integer.class, batch)).isEqualTo(1);
	}

	@Test
	void milonIsUnknownBeforeMatchingAndResolvedAfter() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		ensureKnown(master, "Tuhin Hossain");
		ensureKnown(master, "Anarul Islam");
		makeUnknown(master, "Milon");
		UUID batch = batch(master, "2027-06-30");
		JsonNode routine = upload(master, batch, "2026-11", sample(), 201);
		UUID id = id(routine);
		JsonNode before = findRow(routine, "2026-11-07", "Constitution");
		assertThat(before.get("instructorText").asText()).isEqualTo("Milon");
		assertThat(before.hasNonNull("instructorId")).isFalse();
		assertThat(codes(before)).contains("INSTRUCTOR_UNKNOWN");

		JsonNode matched = respond(post("/api/v1/offline-routines/" + id + "/instructor-matches", master,
				"{\"name\":\"Milon\",\"newName\":\"Milon\"}"), 200);

		assertThat(unmatchedNames(matched)).isEmpty();
		assertThat(matched.get("canConfirm").asBoolean()).isTrue();
		JsonNode after = findRow(matched, "2026-11-07", "Constitution");
		assertThat(after.hasNonNull("instructorId")).isTrue();
		assertThat(codes(after)).doesNotContain("INSTRUCTOR_UNKNOWN");
		assertThat(count(matched.get("rows"), "instructorText", "Milon")).isEqualTo(12);
		// The name is now a live instructor, so the next routine matches it without a choice.
		assertThat(jdbc().queryForObject("select count(*) from offline_instructors where lower(name) = 'milon' and archived_at is null",
				Long.class)).isEqualTo(1L);
	}

	@Test
	void aPdfWithoutTheRoutineHeaderIsRefusedAndNothingIsSaved() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "2027-06-30");
		MockMultipartFile letter = new MockMultipartFile("file", "letter.pdf", "application/pdf", textPdf("Dear student, see you soon."));

		JsonNode refusal = respond(upload(master, batch, "2026-11", letter), 400);

		assertThat(refusal.get("message").asText()).isEqualTo("This PDF does not match the routine format.");
		assertThat(routineCount(batch)).isZero();
	}

	@Test
	void aNonPdfUploadIsRefusedAndNothingIsSaved() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "2027-06-30");
		MockMultipartFile text = new MockMultipartFile("file", "routine.pdf", "application/pdf", "not a pdf".getBytes());

		respond(upload(master, batch, "2026-11", text), 400);

		assertThat(routineCount(batch)).isZero();
	}

	@Test
	void thePreviewIsNotAConfirmedRoutineAndConfirmAddsAnUnknownInstructorFromThePdf() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		ensureKnown(master, "Tuhin Hossain");
		ensureKnown(master, "Anarul Islam");
		makeUnknown(master, "Milon");
		UUID batch = batch(master, "2027-06-30");
		UUID id = id(upload(master, batch, "2026-11", sample(), 201));

		assertThat(classCount(batch)).isZero();
		JsonNode listed = respondTo(get("/api/v1/offline-batches/" + batch + "/routines").header("Authorization", "Bearer " + master), 200);
		assertThat(listed.get(0).get("status").asText()).isEqualTo("PREVIEW");
		assertThat(listed.get(0).get("studentCopy").asBoolean()).isFalse();

		// Nobody matched "Milon": the confirm adds him as an instructor under the name the PDF uses and assigns his classes to him.
		JsonNode confirmed = respond(post("/api/v1/offline-routines/" + id + "/confirm", master, null), 200);
		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(unmatchedNames(confirmed)).isEmpty();
		Integer milons = jdbc().queryForObject("select count(*) from offline_instructors where name = ? and archived_at is null", Integer.class,
				"Milon");
		assertThat(milons).isEqualTo(1);
		Integer taught = jdbc().queryForObject("select count(*) from offline_classes c join offline_instructors i on i.id = c.instructor_id"
				+ " where c.batch_id = ? and i.name = ?", Integer.class, batch, "Milon");
		assertThat(taught).isPositive();
	}

	@Test
	void confirmCreatesTheClassesTheStudentCopyHasNoNamesAndTheStudentIsToldOnce() throws Exception {
		UUID masterId = seed("MASTER_ADMIN", "Master");
		String master = token(masterId);
		ensureKnown(master, "Tuhin Hossain");
		ensureKnown(master, "Anarul Islam");
		makeUnknown(master, "Milon");
		UUID batch = batch(master, "2027-06-30");
		UUID student = seed("STUDENT", "Rahim");
		admitted(student, masterId, batch);
		UUID id = id(upload(master, batch, "2026-11", sample(), 201));
		respond(post("/api/v1/offline-routines/" + id + "/instructor-matches", master, "{\"name\":\"Milon\",\"newName\":\"Milon\"}"), 200);

		JsonNode confirmed = respond(post("/api/v1/offline-routines/" + id + "/confirm", master, null), 200);

		assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
		assertThat(confirmed.get("studentCopy").asBoolean()).isTrue();
		assertThat(confirmed.get("version").asInt()).isEqualTo(1);
		assertThat(classCount(batch)).isEqualTo(SAMPLE_ROWS);
		assertThat(jdbc().queryForObject("select count(*) from offline_classes where batch_id = ? and status = 'SCHEDULED'", Long.class,
				batch)).isEqualTo((long) SAMPLE_ROWS);
		assertThat(notices(student, "BATCH_ROUTINE_UPDATED")).isEqualTo(1L);
		// The student's own admission now says the batch has a routine, so the page shows it (it said "coming soon" before).
		JsonNode mine = respondTo(get("/api/v1/me/admission").header("Authorization", "Bearer " + token(student)), 200);
		assertThat(mine.get("hasRoutine").asBoolean()).isTrue();

		// The original keeps the names; the student copy has the same pages and no names in its text.
		byte[] original = bytes(get("/api/v1/offline-routines/" + id + "/original.pdf").header("Authorization", "Bearer " + master));
		assertThat(text(original)).contains("Tuhin");
		byte[] copy = bytes(get("/api/v1/offline-routines/" + id + "/student.pdf").header("Authorization", "Bearer " + master));
		try (PDDocument document = Loader.loadPDF(copy)) {
			assertThat(document.getNumberOfPages()).isEqualTo(3);
		}
		String copyText = text(copy);
		assertThat(copyText).doesNotContain("Tuhin").doesNotContain("Anarul").doesNotContain("Milon").doesNotContain("Hossain");
	}

	@Test
	void aSecondUploadShowsTheDifferenceAndConfirmKeepsTheSettledClasses() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		ensureKnown(master, "Tuhin Hossain");
		ensureKnown(master, "Anarul Islam");
		makeUnknown(master, "Milon");
		UUID batch = batch(master, "2027-06-30");
		UUID first = id(upload(master, batch, "2026-11", sample(), 201));
		respond(post("/api/v1/offline-routines/" + first + "/instructor-matches", master, "{\"name\":\"Milon\",\"newName\":\"Milon\"}"), 200);
		respond(post("/api/v1/offline-routines/" + first + "/confirm", master, null), 200);

		JsonNode second = upload(master, batch, "2026-11", sample(), 201);
		UUID secondId = id(second);
		assertThat(second.get("difference").isNull()).isFalse();
		assertThat(second.get("difference").get("added").size()).isZero();
		assertThat(second.get("difference").get("cancelled").size()).isZero();
		assertThat(second.get("difference").get("changed").size()).isZero();
		assertThat(second.get("difference").get("unchanged").asInt()).isEqualTo(SAMPLE_ROWS);

		// A settled class: the 26 November registration class is paid. The new routine moves its time: it is not changed.
		jdbc().update("update offline_classes set status = 'PAID' where batch_id = ? and held_on = '2026-11-26'"
				+ " and subject = 'Registration Act'", batch);
		// The 25 October class moves from 5.10 PM to 6.00 PM: the same date and subject, so it is a change.
		JsonNode moved = findRow(second, "2026-10-25", "Succession");
		respond(patch("/api/v1/offline-routines/" + secondId + "/rows/" + index(second, moved), master, "{\"time\":\"6.00 PM\"}"), 200);
		// The 24 November registration class is renamed in the new routine: the old class has no match and is cancelled, the row is added.
		JsonNode renamed = findRow(second, "2026-11-24", "Registration Act");
		respond(patch("/api/v1/offline-routines/" + secondId + "/rows/" + index(second, renamed), master,
				"{\"subject\":\"Registration Law\"}"), 200);
		JsonNode settledRow = findRow(second, "2026-11-26", "Registration Act");
		JsonNode withChanges = respond(patch("/api/v1/offline-routines/" + secondId + "/rows/" + index(second, settledRow), master,
				"{\"time\":\"7.00 PM\"}"), 200);

		JsonNode difference = withChanges.get("difference");
		assertThat(difference.get("added")).hasSize(1);
		assertThat(difference.get("added").get(0).get("subject").asText()).isEqualTo("Registration Law");
		assertThat(difference.get("changed")).hasSize(1);
		assertThat(difference.get("changed").get(0).get("after").get("startsAt").asText()).isEqualTo("18:00");
		assertThat(difference.get("cancelled")).hasSize(1);
		assertThat(difference.get("cancelled").get(0).get("subject").asText()).isEqualTo("Registration Act");
		assertThat(difference.get("settled")).hasSize(1);

		JsonNode confirmed = respond(post("/api/v1/offline-routines/" + secondId + "/confirm", master, null), 200);
		assertThat(confirmed.get("version").asInt()).isEqualTo(2);
		assertThat(jdbc().queryForObject("select status from offline_batch_routines where id = ?", String.class, first)).isEqualTo("SUPERSEDED");
		assertThat(jdbc().queryForObject("select count(*) from offline_classes where batch_id = ? and status = 'CANCELLED'", Long.class,
				batch)).isEqualTo(1L);

		// The settled class is exactly as it was paid: its time is still 5.10 PM, and its status is still PAID.
		OfflineClass paid = classOn(batch, "2026-11-26", "Registration Act");
		assertThat(paid.getStartsAt()).isEqualTo(LocalTime.of(17, 10));
		assertThat(paid.getStatus()).isEqualTo("PAID");
		// The changed class took the new time.
		assertThat(classOn(batch, "2026-10-25", "Succession").getStartsAt()).isEqualTo(LocalTime.of(18, 0));
	}

	@Test
	void staffCanUploadButAStudentIsRefusedOnEveryRoutineRoute() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		String staff = token(seed("SYSTEM_ADMIN", "Staff"));
		String student = token(seed("STUDENT", "Rahim"));
		UUID batch = batch(master, "2027-06-30");

		respond(upload(staff, batch, "2026-11", sample()), 201);
		respond(upload(student, batch, "2026-11", sample()), 403);
		respondTo(get("/api/v1/offline-batches/" + batch + "/routines").header("Authorization", "Bearer " + student), 403);
		respondTo(get("/api/v1/offline-batches/" + batch + "/routines").header("Authorization", "Bearer " + staff), 200);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	@Autowired
	private OfflineClassRepository classes;

	private OfflineClass classOn(UUID batch, String date, String subject) {
		return classes.findByBatchIdOrderByHeldOnAscStartsAtAsc(batch).stream()
			.filter(item -> LocalDate.parse(date).equals(item.getHeldOn()) && subject.equals(item.getSubject()))
			.findFirst()
			.orElseThrow();
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	/** An upload answered with the expected status; returns the routine. */
	private JsonNode upload(String token, UUID batch, String month, MockMultipartFile file, int expected) throws Exception {
		return respond(upload(token, batch, month, file), expected);
	}

	private ResultActions upload(String token, UUID batch, String month, MockMultipartFile file) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/offline-batches/" + batch + "/routines").file(file);
		if (month != null) {
			request.param("month", month);
		}
		request.header("Authorization", "Bearer " + token);
		return mockMvc.perform(request);
	}

	private MockMultipartFile sample() throws IOException {
		return new MockMultipartFile("file", "20th BJS Foundation Batch- NOVEMBER.pdf", "application/pdf",
				new ClassPathResource(SAMPLE).getInputStream().readAllBytes());
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(url).header("Authorization", "Bearer " + token);
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** A request answered with the expected status; returns its JSON body (an empty object for no body). */
	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	private JsonNode respondTo(RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	private byte[] bytes(RequestBuilder request) throws Exception {
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
	}

	private static String text(byte[] pdf) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private static byte[] textPdf(String line) throws IOException {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.newLineAtOffset(72, 700);
				content.showText(line);
				content.endText();
			}
			document.save(out);
			return out.toByteArray();
		}
	}

	private static UUID id(JsonNode routine) {
		return UUID.fromString(routine.get("id").asText());
	}

	/** The first row of a routine with the date and the subject given. */
	private static JsonNode findRow(JsonNode routine, String date, String subject) {
		for (JsonNode row : routine.get("rows")) {
			if (date.equals(text(row, "date")) && subject.equals(text(row, "subject"))) {
				return row;
			}
		}
		throw new AssertionError("No row " + date + " " + subject);
	}

	private static int index(JsonNode routine, JsonNode row) {
		JsonNode rows = routine.get("rows");
		for (int index = 0; index < rows.size(); index++) {
			if (rows.get(index).equals(row)) {
				return index;
			}
		}
		throw new AssertionError("Row not in routine");
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private static List<JsonNode> byKind(JsonNode rows, String kind) {
		List<JsonNode> found = new ArrayList<>();
		for (JsonNode row : rows) {
			if (kind.equals(text(row, "kind"))) {
				found.add(row);
			}
		}
		return found;
	}

	private static int count(JsonNode rows, String field, String value) {
		int count = 0;
		for (JsonNode row : rows) {
			String actual = text(row, field);
			if (value.equals(actual == null ? "" : actual)) {
				count++;
			}
		}
		return count;
	}

	private static Set<String> codes(JsonNode row) {
		Set<String> codes = new HashSet<>();
		row.get("flags").forEach(flag -> codes.add(flag.get("code").asText()));
		return codes;
	}

	private static List<String> unmatchedNames(JsonNode routine) {
		List<String> names = new ArrayList<>();
		for (JsonNode name : routine.get("unmatchedNames")) {
			names.add(name.get("name").asText() + ":" + name.get("rows").asInt());
		}
		return names;
	}

	private long classCount(UUID batch) {
		Long count = jdbc().queryForObject("select count(*) from offline_classes where batch_id = ?", Long.class, batch);
		return count == null ? 0L : count;
	}

	private long routineCount(UUID batch) {
		Long count = jdbc().queryForObject("select count(*) from offline_batch_routines where batch_id = ?", Long.class, batch);
		return count == null ? 0L : count;
	}

	private long notices(UUID student, String kind) {
		Long count = jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = ?", Long.class, student, kind);
		return count == null ? 0L : count;
	}

	/** An onboarded admission in the batch, so the routine confirm has a student to tell. */
	private void admitted(UUID student, UUID createdBy, UUID batch) {
		UUID admission = UUID.randomUUID();
		jdbc().update("""
				insert into offline_admissions (id, student_id, status, created_by, created_at, submit_count, submitted_at,
				  full_name, father_name, mother_name, date_of_birth, email, gender, university, semester_batch, nid, mobile,
				  emergency_mobile, hall_mess, thana, district, present_address, permanent_address, batch_id)
				values (?, ?, 'ONBOARDED', ?, ?, 1, ?, 'Rahim Uddin', 'Karim Uddin', 'Amena Begum', '2004-05-10', ?, 'MALE',
				  'Dhaka University', '2024-25', '1234567890123', ?, '01912345678', 'Shahid Minar Hall', 'Dhanmondi', 'Dhaka',
				  'House 1, Road 2, Dhaka', 'Village, Thana, District', ?)
				""", admission, student, createdBy, Timestamp.from(NOW), Timestamp.from(NOW), uniqueEmail(), validMobile(), batch);
		jdbc().update("insert into student_photos (user_id, stored_name, content_type, byte_size, updated_at) values (?, ?, ?, ?, now())",
				student, "photo-" + UUID.randomUUID() + ".jpg", "image/jpeg", 100L);
	}

	/** A batch made through the API, as the Master Admin makes it, with the default class length. */
	private UUID batch(String token, String endsOn) throws Exception {
		JsonNode created = respond(post("/api/v1/offline-batches", token, "{\"nameEn\":\"BJS Foundation\",\"nameBn\":\"বিজেএস ফাউন্ডেশন\","
				+ "\"endsOn\":\"" + endsOn + "\",\"defaultMonthlyFee\":3000}"), 201);
		assertThat(created.get("defaultClassMinutes").asInt()).isEqualTo(80);
		return UUID.fromString(created.get("id").asText());
	}

	/** Makes sure a live instructor answers to this name, so the routine can match it. */
	private void ensureKnown(String token, String name) throws Exception {
		if (knownKeys(token).contains(key(name))) {
			return;
		}
		respond(post("/api/v1/offline-instructors", token, "{\"name\":\"" + name + "\"}"), 201);
	}

	/**
	 * Makes sure no live instructor answers to this name. An instructor left by an earlier run of this class is renamed (its classes keep
	 * the link to them), so the routine's name is unknown again.
	 */
	private void makeUnknown(String token, String name) throws Exception {
		for (JsonNode instructor : liveInstructors(token)) {
			boolean named = key(instructor.get("name").asText()).equals(key(name));
			boolean aliased = false;
			for (JsonNode alias : instructor.get("aliases")) {
				aliased |= key(alias.asText()).equals(key(name));
			}
			if (named || aliased) {
				respond(patch("/api/v1/offline-instructors/" + instructor.get("id").asText(), token,
						"{\"name\":\"" + name + " (earlier " + UUID.randomUUID() + ")\",\"aliases\":[]}"), 200);
			}
		}
	}

	private Set<String> knownKeys(String token) throws Exception {
		Set<String> keys = new HashSet<>();
		for (JsonNode instructor : liveInstructors(token)) {
			keys.add(key(instructor.get("name").asText()));
			for (JsonNode alias : instructor.get("aliases")) {
				keys.add(key(alias.asText()));
			}
		}
		return keys;
	}

	private List<JsonNode> liveInstructors(String token) throws Exception {
		JsonNode all = respondTo(get("/api/v1/offline-instructors").header("Authorization", "Bearer " + token), 200);
		List<JsonNode> found = new ArrayList<>();
		all.forEach(found::add);
		return found;
	}

	private static String key(String name) {
		return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private UUID seed(String role, String name) {
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, "+8801" + ThreadLocalRandom.current().nextInt(300000000, 999999999), "offline-routine-" + id + "@example.com",
				passwords.encode("password1"), role);
		if ("STUDENT".equals(role)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"R" + id.toString().substring(0, 8));
		}
		else {
			jdbc().update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", id, name);
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = jdbc().queryForObject("select phone from users where id = ?", String.class, userId);
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
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

	private static String uniqueEmail() {
		return "offline-routine-form-" + UUID.randomUUID() + "@example.com";
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}

}
