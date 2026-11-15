package com.coachingcenter.api.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.auth.CapturingEmailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Onboarding an application and joining the batch (public-admission plan, sections 2 C, 5 and 7; Q3, P8, P13, P14). An applicant with no
 * account gets one at onboarding and an activation code by mail; an applicant whose email or phone matches an account is joined to that
 * account with a join code (never a second account); the join code is checked, locked after five wrong tries, and replaced by a resend;
 * the desk refuses a phone that already has an open admission; a student who already has a batch can be onboarded to another one, and is
 * told so. Every test makes its own people and batches, so the class runs again on the same database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, OfflineOnboardingIT.ClockConfig.class })
@TestPropertySource(properties = "app.offline-admission.enabled=true")
class OfflineOnboardingIT extends PostgresIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");

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
	private CapturingEmailSender emails;

	@BeforeEach
	void fixTheClock() {
		clock.set(NOW);
	}

	@Test
	void anApplicantWithNoAccountGetsOneAccountAndTheActivationCodeMakesItActive() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Activate " + unique());
		String email = uniqueEmail();
		String mobile = validMobile();
		UUID admission = submittedApplication(batch, email, mobile);
		readyAdmission(admission, batch, master);

		JsonNode onboarded = respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		assertThat(onboarded.get("state").asText()).isEqualTo("ONBOARDED");
		assertThat(onboarded.get("alreadyAccessTo")).isEmpty();
		assertThat(jdbc().queryForObject("select count(*) from users where email = ?", Long.class, email)).isEqualTo(1L);
		assertThat(jdbc().queryForObject("select status from users where email = ?", String.class, email))
			.isEqualTo("PENDING_ACTIVATION");
		String code = codeFrom(email);
		assertThat(emails.latestRendered(email)).contains("You are admitted");
		// V101: staff see the activation code that was sent, so they can read it out, until the account is activated.
		assertThat(detail(admission, master).get("sentCode").asText()).isEqualTo(code);

		respond(post("/api/v1/auth/activate", null, "{\"email\":\"" + email + "\",\"code\":\"WRONGCODE\",\"password\":\"password1\"}"),
				400);
		respond(post("/api/v1/auth/activate", null, "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"password\":\"password1\"}"),
				204);
		assertThat(jdbc().queryForObject("select status from users where email = ?", String.class, email)).isEqualTo("ACTIVE");
		assertThat(detail(admission, master).hasNonNull("sentCode")).isFalse();

		UUID student = jdbc().queryForObject("select id from users where email = ?", UUID.class, email);
		JsonNode mine = json.readTree(mockMvc.perform(get("/api/v1/me/admissions").header("Authorization", "Bearer " + token(student))
			.header("X-Client-Type", "mobile")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(mine.get(0).get("state").asText()).isEqualTo("ONBOARDED");
	}

	@Test
	void onboardingCopiesNothingFromTheBatchAndGivesOnlyTheItemsChosenForTheStudent() throws Exception {
		// P17: a batch carries no default exams or packages. Onboarding gives the student exactly the items the admission setup holds.
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "No defaults " + unique());
		UUID admission = submittedApplication(batch, uniqueEmail(), validMobile());
		readyAdmission(admission, batch, master);
		assertThat(jdbc().queryForObject("select count(*) from offline_admission_items where admission_id = ?", Long.class, admission))
			.isEqualTo(1L);

		JsonNode onboarded = respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		assertThat(onboarded.get("state").asText()).isEqualTo("ONBOARDED");
		assertThat(jdbc().queryForObject("select count(*) from offline_admission_items where admission_id = ?", Long.class, admission))
			.isEqualTo(1L);
	}

	@Test
	void anApplicantWhoseEmailMatchesAnAccountJoinsThatAccountWithItsCodeAndNoSecondAccountIsMade() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Match " + unique());
		String email = uniqueEmail();
		UUID existing = seedStudent("Existing", email, plusForm(validMobile()));
		UUID admission = submittedApplication(batch, email, validMobile());
		readyAdmission(admission, batch, master);

		JsonNode waiting = respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		assertThat(waiting.get("state").asText()).isEqualTo("READY");
		assertThat(jdbc().queryForObject("select count(*) from users where email = ?", Long.class, email)).isEqualTo(1L);
		assertThat(jdbc().queryForObject("select student_id from offline_admissions where id = ?", UUID.class, admission))
			.isEqualTo(existing);
		assertThat(jdbc().queryForObject("select matched_user_id from offline_admissions where id = ?", UUID.class, admission))
			.isEqualTo(existing);
		String code = codeFrom(email);

		String studentToken = token(existing);
		respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"NOTTHECODE\"}"), 400);
		JsonNode joined = respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + code + "\"}"), 200);
		assertThat(joined.get("state").asText()).isEqualTo("ONBOARDED");
		// The code works once.
		respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + code + "\"}"), 400);
	}

	@Test
	void anApplicantWhosePhoneBelongsToAnotherAccountIsNotJoinedToIt() throws Exception {
		// Decision D3 (13 Nov): applicants are matched by email only. A phone that belongs to another account is never joined, so the
		// onboarding is refused until the form is fixed, and no account is made with that phone.
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Phone " + unique());
		String mobile = validMobile();
		seedStudent("Phone Owner", uniqueEmail(), plusForm(mobile));
		String applicantEmail = uniqueEmail();
		UUID admission = submittedApplication(batch, applicantEmail, mobile);
		readyAdmission(admission, batch, master);

		JsonNode refused = respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 400);
		assertThat(refused.toString()).contains("mobile");
		assertThat(jdbc().queryForObject("select count(*) from users where email = ?", Long.class, applicantEmail)).isZero();
		assertThat(jdbc().queryForObject("select student_id from offline_admissions where id = ?", UUID.class, admission)).isNull();
	}

	@Test
	void anApplicantWhoseEmailMatchesAnAccountIsJoinedToIt() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Email " + unique());
		String mobile = validMobile();
		String accountEmail = uniqueEmail();
		UUID existing = seedStudent("Email Match", accountEmail, plusForm(mobile));
		UUID admission = submittedApplication(batch, accountEmail, mobile);
		readyAdmission(admission, batch, master);

		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		// The join code goes to the account's address.
		assertThat(emails.latestRendered(accountEmail)).contains("Join the batch");
		assertThat(jdbc().queryForObject("select student_id from offline_admissions where id = ?", UUID.class, admission))
			.isEqualTo(existing);
	}

	@Test
	void fiveWrongJoinCodesLockTheCodeUntilANewOneIsSent() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Lock " + unique());
		String email = uniqueEmail();
		UUID existing = seedStudent("Locked", email, plusForm(validMobile()));
		UUID admission = submittedApplication(batch, email, validMobile());
		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		String code = codeFrom(email);
		String studentToken = token(existing);
		for (int attempt = 0; attempt < 5; attempt++) {
			respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"WRONG" + attempt + "\"}"), 400);
		}
		JsonNode locked = respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + code + "\"}"), 400);
		assertThat(locked.get("code").asText()).isEqualTo("CODE_ATTEMPTS_EXCEEDED");

		respond(post("/api/v1/offline-admissions/" + admission + "/resend-code", master, null), 200);
		String fresh = codeFrom(email);
		respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + fresh + "\"}"), 200);
	}

	@Test
	void resendingTheJoinCodeStopsTheOldOneWorking() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Resend " + unique());
		String email = uniqueEmail();
		UUID existing = seedStudent("Resent", email, plusForm(validMobile()));
		UUID admission = submittedApplication(batch, email, validMobile());
		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		String first = codeFrom(email);
		// V101: the join code is in the student's notice and on the staff screen, as in the email.
		assertThat(noticeCodes(admission)).containsExactly(first);
		assertThat(detail(admission, master).get("sentCode").asText()).isEqualTo(first);

		JsonNode resent = respond(post("/api/v1/offline-admissions/" + admission + "/resend-code", master, null), 200);
		assertThat(resent.get("state").asText()).isEqualTo("READY");
		String second = codeFrom(email);
		assertThat(second).isNotEqualTo(first);
		assertThat(emails.latestRendered(email)).contains("A new code");
		// The old notice loses its code, which no longer works; the new notice and the staff screen show the new one.
		assertThat(noticeCodes(admission)).containsExactly(second);
		assertThat(resent.get("sentCode").asText()).isEqualTo(second);

		String studentToken = token(existing);
		JsonNode notices = json.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/me/notices?page=0&size=20")
			.header("Authorization", "Bearer " + studentToken).header("X-Client-Type", "web")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		java.util.List<String> shown = new java.util.ArrayList<>();
		for (JsonNode notice : notices.get("content")) {
			if ("ADMISSION_JOIN_CODE".equals(notice.get("kind").asText()) && notice.hasNonNull("code")) {
				shown.add(notice.get("code").asText());
			}
		}
		assertThat(shown).containsExactly(second);

		respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + first + "\"}"), 400);
		respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + second + "\"}"), 200);
		// Joined: the code is used, so neither the notice nor the staff screen shows it any more.
		assertThat(noticeCodes(admission)).isEmpty();
		assertThat(detail(admission, master).hasNonNull("sentCode")).isFalse();
	}

	/** The codes the join-code notices of one admission still show (V101), newest first. */
	private java.util.List<String> noticeCodes(UUID admission) {
		return jdbc().queryForList("select code from student_notices where kind = 'ADMISSION_JOIN_CODE' and message_key like ?"
				+ " and code is not null order by created_at desc", String.class, admission + ":%");
	}

	@Test
	void resendingTheActivationCodeStopsTheOldOneWorking() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Activation resend " + unique());
		String email = uniqueEmail();
		UUID admission = submittedApplication(batch, email, validMobile());
		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		String first = codeFrom(email);

		respond(post("/api/v1/offline-admissions/" + admission + "/resend-code", master, null), 200);
		String second = codeFrom(email);
		respond(post("/api/v1/auth/activate", null, "{\"email\":\"" + email + "\",\"code\":\"" + first + "\",\"password\":\"password1\"}"),
				400);
		respond(post("/api/v1/auth/activate", null, "{\"email\":\"" + email + "\",\"code\":\"" + second + "\",\"password\":\"password1\"}"),
				204);
	}

	@Test
	void theDeskRefusesAPhoneThatAlreadyHasAnOpenAdmissionAndNamesTheBatch() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Desk " + unique());
		UUID student = seedStudent("Walk-in", uniqueEmail(), plusForm(validMobile()));
		String localPhone = jdbc().queryForObject("select phone from users where id = ?", String.class, student);
		respond(post("/api/v1/me/admission/apply", token(student), "{\"batchId\":\"" + batch + "\"}"), 201);
		String batchName = jdbc().queryForObject("select name_en from offline_batches where id = ?", String.class, batch);

		JsonNode refused = respond(post("/api/v1/offline-admissions/desk", master, "{\"phone\":\"" + localPhone + "\"}"), 409);
		assertThat(refused.get("message").asText()).contains(batchName);

		// An account with no open admission is refused as before (a duplicate phone), and a new phone is admitted as before.
		UUID plain = seedStudent("Plain", uniqueEmail(), plusForm(validMobile()));
		String plainPhone = jdbc().queryForObject("select phone from users where id = ?", String.class, plain);
		respond(post("/api/v1/offline-admissions/desk", master, "{\"phone\":\"" + plainPhone + "\"}"), 409);
		respond(post("/api/v1/offline-admissions/desk", master, "{\"phone\":\"" + validMobile() + "\"}"), 201);
	}

	@Test
	void aStudentWithAnAdmissionInOneBatchIsOnboardedToAnotherAndIsToldOfBothBatches() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID first = batch(master, "Running " + unique());
		UUID second = batch(master, "Second " + unique());
		String email = uniqueEmail();
		UUID student = seedStudent("Runner", email, validMobile());
		String studentToken = token(student);

		// The first batch: the student applies, sends the form, is onboarded and joins.
		UUID firstAdmission = studentApplies(studentToken, first, email);
		readyAdmission(firstAdmission, first, master);
		respond(post("/api/v1/offline-admissions/" + firstAdmission + "/onboard", master, rollBody()), 200);
		respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + codeFrom(email) + "\"}"), 200);
		String firstName = jdbc().queryForObject("select name_en from offline_batches where id = ?", String.class, first);

		// The second batch, while the first one is running.
		UUID secondAdmission = studentApplies(studentToken, second, email);
		readyAdmission(secondAdmission, second, master);
		JsonNode onboarded = respond(post("/api/v1/offline-admissions/" + secondAdmission + "/onboard", master, rollBody()), 200);
		assertThat(onboarded.get("alreadyAccessTo").get(0).get("nameEn").asText()).isEqualTo(firstName);
		// R3: the batch is not added until the student joins, so the "batch added" notice waits for the join.
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'ADMISSION_BATCH_ADDED'",
				Long.class, student)).isZero();
		// The student email for the second batch lists both batches.
		String mail = emails.latestRendered(email);
		assertThat(mail).contains(firstName).contains("Batches you have access to");

		JsonNode joined = respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + codeFrom(email) + "\"}"), 200);
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'ADMISSION_BATCH_ADDED'",
				Long.class, student)).isEqualTo(1L);
		assertThat(joined.get("state").asText()).isEqualTo("ONBOARDED");
		assertThat(jdbc().queryForObject("select count(*) from offline_admissions where student_id = ? and status = 'ONBOARDED'",
				Long.class, student)).isEqualTo(2L);
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------------

	@Test
	void aBatchWhatsAppLinkReachesOnlyItsOnboardedStudents() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "WhatsApp " + unique());
		String link = "https://chat.whatsapp.com/GroupInvite1234";
		respond(patch("/api/v1/offline-batches/" + batch, master, "{\"whatsappUrl\":\"https://example.com/group\"}"), 400);
		respond(patch("/api/v1/offline-batches/" + batch, master, "{\"whatsappUrl\":\"" + link + "\"}"), 200);

		// The public batch list never carries the link.
		String publicList = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/public/offline-batches")).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(publicList).doesNotContain(link);

		String email = uniqueEmail();
		UUID student = seedStudent("Group Student", email, plusForm(validMobile()));
		String studentToken = token(student);
		UUID admission = studentApplies(studentToken, batch, email);
		assertThat(ownAdmission(studentToken, admission).hasNonNull("whatsappUrl")).isFalse();

		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		respond(post("/api/v1/me/admission/join", studentToken, "{\"code\":\"" + codeFrom(email) + "\"}"), 200);
		assertThat(ownAdmission(studentToken, admission).get("whatsappUrl").asText()).isEqualTo(link);
	}

	/** One of the student's own admissions, from their admissions list. */
	private JsonNode ownAdmission(String studentToken, UUID admission) throws Exception {
		JsonNode list = json.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/me/admissions")
			.header("Authorization", "Bearer " + studentToken).header("X-Client-Type", "web")).andExpect(status().isOk()).andReturn().getResponse()
			.getContentAsString());
		for (JsonNode row : list) {
			if (admission.toString().equals(row.get("id").asText())) {
				return row;
			}
		}
		throw new AssertionError("Admission not in the student's list");
	}

	/** The suggested roll of an admission's batch, or null. */
	private String suggestion(UUID admission, String master) throws Exception {
		JsonNode body = json.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/offline-admissions/" + admission + "/next-roll")
			.header("Authorization", "Bearer " + master)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		return body.hasNonNull("suggestion") ? body.get("suggestion").asText() : null;
	}

	@Test
	void onboardingNeedsARollNumberThatNoOtherStudentHas() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Roll " + unique());
		UUID first = submittedApplication(batch, uniqueEmail(), validMobile());
		readyAdmission(first, batch, master);
		String roll = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L));

		respond(post("/api/v1/offline-admissions/" + first + "/onboard", master, null), 400);
		respond(post("/api/v1/offline-admissions/" + first + "/onboard", master, "{\"rollNo\":\"12\"}"), 400);
		respond(post("/api/v1/offline-admissions/" + first + "/onboard", master, "{\"rollNo\":\"" + roll + "\"}"), 200);
		assertThat(detail(first, master).get("rollNo").asText()).isEqualTo(roll);

		UUID second = submittedApplication(batch, uniqueEmail(), validMobile());
		readyAdmission(second, batch, master);
		respond(post("/api/v1/offline-admissions/" + second + "/onboard", master, "{\"rollNo\":\"" + roll + "\"}"), 400);
	}

	@Test
	void theSuggestedRollFollowsTheBatchAndSkipsRollsTakenElsewhere() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Suggest " + unique());
		UUID other = batch(master, "Other " + unique());
		respond(patch("/api/v1/offline-batches/" + batch, master, "{\"rollStart\":\"192001\"}"), 200);

		UUID first = submittedApplication(batch, uniqueEmail(), validMobile());
		readyAdmission(first, batch, master);
		assertThat(suggestion(first, master)).isEqualTo("192001");
		respond(post("/api/v1/offline-admissions/" + first + "/onboard", master, "{\"rollNo\":\"192001\"}"), 200);

		// 192002 is taken by a student of another batch, so the next free one for this batch is 192003.
		UUID elsewhere = submittedApplication(other, uniqueEmail(), validMobile());
		readyAdmission(elsewhere, other, master);
		respond(post("/api/v1/offline-admissions/" + elsewhere + "/onboard", master, "{\"rollNo\":\"192002\"}"), 200);
		UUID second = submittedApplication(batch, uniqueEmail(), validMobile());
		readyAdmission(second, batch, master);
		assertThat(suggestion(second, master)).isEqualTo("192003");

		// The starting roll cannot move once the batch has a roll in use.
		respond(patch("/api/v1/offline-batches/" + batch, master, "{\"rollStart\":\"500001\"}"), 400);
	}

	@Test
	void aRollGivenWithAJoinCodeIsReleasedWhenTheAdmissionGoesBackToReview() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Release " + unique());
		String accountEmail = uniqueEmail();
		seedStudent("Release Student", accountEmail, plusForm(validMobile()));
		UUID admission = submittedApplication(batch, accountEmail, validMobile());
		readyAdmission(admission, batch, master);
		String roll = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L));
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, "{\"rollNo\":\"" + roll + "\"}"), 200);
		assertThat(detail(admission, master).get("rollNo").asText()).isEqualTo(roll);

		// A6: the join code and the roll go with the onboarding that was not finished.
		respond(post("/api/v1/offline-admissions/" + admission + "/back-to-review", master, null), 200);
		assertThat(detail(admission, master).hasNonNull("rollNo")).isFalse();
	}

	@Test
	void theMasterAdminChangesARollOnlyAfterOnboarding() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Change " + unique());
		UUID admission = submittedApplication(batch, uniqueEmail(), validMobile());
		respond(patch("/api/v1/offline-admissions/" + admission + "/roll", master, "{\"rollNo\":\"700001\"}"), 409);

		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, "{\"rollNo\":\"700002\"}"), 200);
		respond(patch("/api/v1/offline-admissions/" + admission + "/roll", master, "{\"rollNo\":\"700003\"}"), 200);
		assertThat(detail(admission, master).get("rollNo").asText()).isEqualTo("700003");
	}

	/** The staff detail of one admission. */
	private JsonNode detail(UUID admission, String master) throws Exception {
		return json.readTree(mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/offline-admissions/" + admission)
			.header("Authorization", "Bearer " + master)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	/** An applicant whose email is an existing account's is readied and onboarded, so the join code goes out to that account. */
	private void joinWaiting(UUID admission, UUID batch, String master) throws Exception {
		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
	}

	@Test
	void aJoinPendingAdmissionIsNotOnboardedAgainAndSendingItBackStopsItsOldCode() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Waiting " + unique());
		String accountEmail = uniqueEmail();
		UUID existing = seedStudent("Waiting Student", accountEmail, plusForm(validMobile()));
		UUID admission = submittedApplication(batch, accountEmail, validMobile());
		joinWaiting(admission, batch, master);
		String oldCode = codeFrom(accountEmail);

		JsonNode waiting = detail(admission, master);
		assertThat(waiting.get("joinPending").asBoolean()).isTrue();
		assertThat(waiting.get("canResendCode").asBoolean()).isTrue();
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 409);

		// Back to review clears the code (R2): the old code no longer joins, and the admission is no longer waiting.
		respond(post("/api/v1/offline-admissions/" + admission + "/back-to-review", master, null), 200);
		JsonNode review = detail(admission, master);
		assertThat(review.get("joinPending").asBoolean()).isFalse();
		assertThat(review.hasNonNull("joinCodeSentAt")).isFalse();
		assertThat(review.hasNonNull("sentCode")).isFalse();
		assertThat(noticeCodes(admission)).isEmpty();
		respond(post("/api/v1/offline-admissions/" + admission + "/ready", master, null), 200);
		respond(post("/api/v1/me/admission/join", token(existing), "{\"code\":\"" + oldCode + "\"}"), 400);
	}

	@Test
	void aWaitingApplicationCanBeClosedAndItsCodeStopsWorking() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Closing " + unique());
		String accountEmail = uniqueEmail();
		UUID existing = seedStudent("Closing Student", accountEmail, plusForm(validMobile()));
		UUID admission = submittedApplication(batch, accountEmail, validMobile());
		joinWaiting(admission, batch, master);
		String code = codeFrom(accountEmail);

		// Decision D1: any open admission can be closed, with a reason. Nothing was granted or scheduled before onboarding.
		respond(post("/api/v1/offline-admissions/" + admission + "/close", master, "{\"reason\":\"Applied twice\"}"), 200);
		assertThat(detail(admission, master).get("state").asText()).isEqualTo("CLOSED");
		respond(post("/api/v1/me/admission/join", token(existing), "{\"code\":\"" + code + "\"}"), 400);
	}

	@Test
	void aNewAccountGetsItsApprovalNoticeOnlyWhenItActivates() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Master"));
		UUID batch = batch(master, "Approval " + unique());
		String email = uniqueEmail();
		UUID admission = submittedApplication(batch, email, validMobile());
		readyAdmission(admission, batch, master);
		respond(post("/api/v1/offline-admissions/" + admission + "/onboard", master, rollBody()), 200);
		UUID student = UUID.fromString(detail(admission, master).get("studentId").asText());

		// N2: a pending account is told at activation, not at onboarding (its notice could not be written before).
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'ADMISSION_APPROVED'",
				Long.class, student)).isZero();
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/activate").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"code\":\"" + codeFrom(email) + "\",\"password\":\"password1\"}"))
			.andExpect(status().isNoContent());
		assertThat(jdbc().queryForObject("select count(*) from student_notices where user_id = ? and kind = 'ADMISSION_APPROVED'",
				Long.class, student)).isEqualTo(1L);
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(dataSource);
	}

	private ResultActions post(String url, String token, String body) throws Exception {
		var request = MockMvcRequestBuilders.post(url);
		if (token != null) {
			request = request.header("Authorization", "Bearer " + token);
		}
		if (body != null) {
			request = request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request);
	}

	private JsonNode respond(ResultActions actions, int expected) throws Exception {
		MvcResult result = actions.andExpect(status().is(expected)).andReturn();
		String text = result.getResponse().getContentAsString();
		return text.isBlank() ? json.createObjectNode() : json.readTree(text);
	}

	/** The newest code mailed to the address: the part of the captured mail before its first separator. */
	private String codeFrom(String email) {
		String captured = emails.latestRendered(email);
		assertThat(captured).isNotNull();
		return captured.substring(0, captured.indexOf('|'));
	}

	/** An applicant with no account has sent the form, with its photo, and the office has it under review. */
	private UUID submittedApplication(UUID batch, String email, String mobile) throws Exception {
		JsonNode started = respond(post("/api/v1/public/offline-applications", null,
				"{\"batchId\":\"" + batch + "\",\"email\":\"" + email + "\",\"mobile\":\"" + mobile + "\"}"), 201);
		String token = started.get("token").asText();
		respond(put("/api/v1/public/offline-applications/" + token + "/form", fullForm(email, mobile)), 200);
		respond(multipart(HttpMethod.PUT, "/api/v1/public/offline-applications/" + token + "/photo")
			.file(new MockMultipartFile("file", "me.png", "image/png", png())), 200);
		JsonNode sent = respond(post("/api/v1/public/offline-applications/" + token + "/submit", null, null), 200);
		assertThat(sent.get("state").asText()).isEqualTo("SUBMITTED");
		return UUID.fromString(started.get("admission").get("id").asText());
	}

	private ResultActions put(String url, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** A signed-in student applies for a batch, fills in the form, sends it with a photo, and it is under review. */
	private UUID studentApplies(String studentToken, UUID batch, String email) throws Exception {
		JsonNode applied = respond(post("/api/v1/me/admission/apply", studentToken, "{\"batchId\":\"" + batch + "\"}"), 201);
		UUID admission = UUID.fromString(applied.get("id").asText());
		String mobile = "01" + ThreadLocalRandom.current().nextInt(300000000, 999999999);
		respond(put("/api/v1/me/admission/form", fullForm(email, mobile), studentToken), 200);
		respond(multipart(HttpMethod.PUT, "/api/v1/me/admission/photo").file(new MockMultipartFile("file", "me.png", "image/png", png()))
			.header("Authorization", "Bearer " + studentToken), 200);
		respond(post("/api/v1/me/admission/submit", studentToken, null), 200);
		return admission;
	}

	private ResultActions put(String url, String body, String token) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.put(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private JsonNode respond(org.springframework.test.web.servlet.RequestBuilder request, int expected) throws Exception {
		return respond(mockMvc.perform(request), expected);
	}

	/** Setup, one exam, ready: the staff steps before the Master Admin onboards. */
	private void readyAdmission(UUID admission, UUID batch, String master) throws Exception {
		respond(patch("/api/v1/offline-admissions/" + admission + "/setup", master, "{\"batchId\":\"" + batch + "\"}"), 200);
		respond(post("/api/v1/offline-admissions/" + admission + "/items", master, "{\"scope\":\"EXAM\",\"examId\":\"" + publishedExam()
				+ "\"}"), 200);
		JsonNode ready = respond(post("/api/v1/offline-admissions/" + admission + "/ready", master, null), 200);
		assertThat(ready.get("state").asText()).isEqualTo("READY");
	}

	private ResultActions patch(String url, String token, String body) throws Exception {
		return mockMvc.perform(MockMvcRequestBuilders.patch(url).header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private UUID batch(String token, String name) throws Exception {
		MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/offline-batches").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nameEn":"%s","nameBn":"ব্যাচ","endsOn":"2027-04-30","courseFee":3000}
					""".formatted(name)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private UUID publishedExam() {
		UUID id = UUID.randomUUID();
		UUID category = UUID.randomUUID();
		jdbc().update("insert into live_exam_categories (id, title_en, title_bn, position, created_at, updated_at)"
				+ " values (?, 'Category', 'বিভাগ', 0, now(), now())", category);
		jdbc().update("""
				insert into live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en, description_bn,
				  rules_en, rules_bn, paper_language, live_at, ends_at, duration_minutes, mode, status, finally_saved, pass_percent,
				  answers_changeable, is_free, access_days, published_at, created_at, updated_at)
				values (?, null, ?, 'MCQ', ?, 'পরীক্ষা', '', '', '', '', 'BN', ?, ?, 30, 'LIVE', 'PUBLISHED', true, 50, true, false, 30, ?,
				  now(), now())
				""", id, category, "Exam " + id, Timestamp.from(Instant.parse("2026-10-01T04:00:00Z")),
				Timestamp.from(Instant.parse("2027-12-31T04:00:00Z")), Timestamp.from(Instant.parse("2026-09-01T04:00:00Z")));
		return id;
	}

	private UUID seed(String role, String name) {
		return seedStudent(role, name, uniqueEmail(), validMobile(), role);
	}

	private UUID seedStudent(String name, String email, String phone) {
		return seedStudent("STUDENT", name, email, phone, "STUDENT");
	}

	private UUID seedStudent(String role, String name, String email, String phone, String kind) {
		String stored = phone.startsWith("01") ? "+880" + phone.substring(1) : phone;
		UUID id = UUID.randomUUID();
		jdbc().update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, stored, email, passwords.encode("password1"), kind);
		if ("STUDENT".equals(kind)) {
			jdbc().update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", id, name,
					"A" + id.toString().substring(0, 8));
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

	/** The form every required field of which is filled in, with the given email and mobile. */
	private static String fullForm(String email, String mobile) {
		return "{\"fullName\":\"Rahim Uddin\",\"fatherName\":\"Karim Uddin\",\"motherName\":\"Amena Begum\","
				+ "\"dateOfBirth\":\"2004-05-10\",\"email\":\"" + email + "\",\"gender\":\"MALE\",\"university\":\"Dhaka University\","
				+ "\"semesterBatch\":\"2024-25\",\"nid\":\"1234567890123\",\"mobile\":\"" + mobile + "\","
				+ "\"emergencyMobile\":\"01812345678\",\"hallMess\":\"Shahid Minar Hall\",\"presentAddress\":\"House 1, Dhaka\","
				+ "\"permanentAddress\":\"Village, District\"}";
	}

	/** The phone as the account stores it: the +880 form of an 11-digit mobile. */
	private static String plusForm(String mobile) {
		return "+880" + mobile.substring(1);
	}

	private static String uniqueEmail() {
		return "offline-onb-" + UUID.randomUUID() + "@example.com";
	}

	private static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** An 11-digit Bangladesh mobile starting 017. */
	private static String validMobile() {
		return "017" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
	}

	private static byte[] png() throws IOException {
		BufferedImage image = new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(NOW);
		}

	}


	/** A request body with a roll number nobody has yet: onboarding requires one. */
	private static String rollBody() {
		return "{\"rollNo\":\"" + java.util.concurrent.ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L) + "\"}";
	}
}
