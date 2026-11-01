package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.common.audit.AuditRecorder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Who gets an announcement, and who sees it on a page (plan sections 6, 7 and 9). The holder rule is the same for the send and
 * for the page: an active roster row, a grant, or a started attempt on a free exam, with expired and revoked access left out.
 * The sitting window is 12:00 to 12:40 Dhaka (06:00 to 06:40 UTC), as in the other sitting tests. Nulls are left out of JSON,
 * so a missing field means null.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, AnnouncementAudiencesIT.ClockConfig.class })
class AnnouncementAudiencesIT extends PostgresIntegrationTest {

	private static final Instant BEFORE = Instant.parse("2026-10-20T04:00:00Z");

	private static final Instant SIT_AT = Instant.parse("2026-10-20T06:10:00Z");

	private static final Instant SUBMIT_AT = Instant.parse("2026-10-20T06:12:00Z");

	/**
	 * Two days after the window. A paid exam with one access day, sat in the window of 20 October, ran out on 21 October at
	 * 06:40 UTC, so a grant made on 20 October has run out by now, and a grant made now has not.
	 */
	private static final Instant LATER = Instant.parse("2026-10-22T04:00:00Z");

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

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

	private PackageFixtures pf;

	@BeforeEach
	void setUp() {
		clock.set(BEFORE);
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
		pf = new PackageFixtures(mockMvc, json, dataSource, passwords);
	}

	@Test
	void allReachesActiveStudentsOnly() throws Exception {
		String master = fx.master();
		UUID active = fx.seed("STUDENT");
		UUID inactive = fx.seed("STUDENT");
		UUID deleted = fx.seed("STUDENT");
		fx.jdbc().update("update users set status = 'INACTIVE' where id = ?", inactive);
		fx.jdbc().update("update users set deleted_at = now() where id = ?", deleted);

		int expected = activeStudents();
		JsonNode sent = announce(master, announcementJson("Holiday", "ALL", null, null, null));

		assertThat(sent.get("recipients").asInt()).isEqualTo(expected);
		assertThat(recipients(idOf(sent))).hasSize(expected).contains(active).doesNotContain(inactive, deleted);
	}

	@Test
	void moduleReachesRosterGrantsAndStartedFreeAttemptsButNotRevokedOrExpired() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Pkg pkg = pkg(staff, "Module audience package", true);

		UUID rostered = fx.seed("STUDENT");
		pf.markRoster(pkg.id(), rostered, BEFORE);
		UUID moduleGranted = fx.seed("STUDENT");
		grantModule(master, moduleGranted, pkg.id());
		UUID examGranted = fx.seed("STUDENT");
		grantExam(master, examGranted, pkg.paid());
		UUID attempted = fx.seed("STUDENT");
		sit(fx.token(attempted), pkg.free());
		UUID revoked = fx.seed("STUDENT");
		grantModule(master, revoked, pkg.id());
		revokeGrants(revoked);
		UUID revokedAttempt = fx.seed("STUDENT");
		grantExam(master, revokedAttempt, pkg.paid());
		sit(fx.token(revokedAttempt), pkg.paid());
		revokeGrants(revokedAttempt);
		UUID stranger = fx.seed("STUDENT");

		JsonNode preview = read(send(get("/api/v1/announcements/preview").param("audience", "MODULE")
			.param("moduleId", pkg.id().toString()), master).andExpect(status().isOk()));
		UUID announcement = idOf(announce(master, announcementJson("Module news", "MODULE", pkg.id(), null, null)));

		assertThat(recipients(announcement)).containsExactlyInAnyOrder(rostered, moduleGranted, examGranted, attempted)
			.doesNotContain(revoked, revokedAttempt, stranger);
		assertThat(preview.get("recipients").asInt()).isEqualTo(4);
	}

	@Test
	void examReachesGrantsPackageHoldersAndFreeStartersOnly() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Pkg pkg = pkg(staff, "Exam audience package", false);
		UUID category = fx.category(staff, "Exam audience standalone");
		UUID free = standalone(staff, master, category, "Free exam for the audience", true);

		UUID examGranted = fx.seed("STUDENT");
		grantExam(master, examGranted, pkg.paid());
		UUID rosteredInPackage = fx.seed("STUDENT");
		pf.markRoster(pkg.id(), rosteredInPackage, BEFORE);
		UUID packageGranted = fx.seed("STUDENT");
		grantModule(master, packageGranted, pkg.id());
		UUID stranger = fx.seed("STUDENT");
		UUID freeStarter = fx.seed("STUDENT");
		sit(fx.token(freeStarter), free);
		UUID neverStarted = fx.seed("STUDENT");

		UUID paidAnnouncement = idOf(announce(master, announcementJson("Paper postponed", "EXAM", null, pkg.paid(), null)));
		assertThat(recipients(paidAnnouncement)).containsExactlyInAnyOrder(examGranted, rosteredInPackage, packageGranted)
			.doesNotContain(stranger);

		UUID freeAnnouncement = idOf(announce(master, announcementJson("Free paper timing", "EXAM", null, free, null)));
		assertThat(recipients(freeAnnouncement)).containsExactly(freeStarter).doesNotContain(neverStarted, stranger);

		// The notice sits in the announcements tab. Its exam part stays empty, as for every announcement; the exam id is in
		// the announcement part.
		JsonNode notice = noticeFor(freeStarter, freeAnnouncement);
		assertThat(notice.get("kind").asText()).isEqualTo("ANNOUNCEMENT");
		assertThat(notice.get("group").asText()).isEqualTo("ANNOUNCEMENTS");
		assertThat(absent(notice, "exam")).isTrue();
		assertThat(notice.get("announcement").get("examId").asText()).isEqualTo(free.toString());
		assertThat(absent(notice.get("announcement"), "moduleId")).isTrue();
		assertThat(notice.get("announcement").get("audience").asText()).isEqualTo("EXAM");

		// Once the exam is deleted its notices leave the inbox, as exam notices already do.
		fx.jdbc().update("update live_exams set deleted_at = now() where id = ?", free);
		assertThat(hasAnnouncement(notices(fx.token(freeStarter)), freeAnnouncement)).isFalse();
	}

	@Test
	void expiredAndRevokedAccessIsNotAHolder() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Pkg pkg = pkg(staff, "Expiry package", false);
		fx.jdbc().update("update live_exams set access_days = 1 where id = ?", pkg.paid());

		UUID expiredModuleGrant = fx.seed("STUDENT");
		grantModule(master, expiredModuleGrant, pkg.id());
		UUID expiredRoster = fx.seed("STUDENT");
		pf.markRoster(pkg.id(), expiredRoster, BEFORE);
		UUID expiredExamGrant = fx.seed("STUDENT");
		grantExam(master, expiredExamGrant, pkg.paid());
		clock.set(LATER);
		UUID liveModuleGrant = fx.seed("STUDENT");
		grantModule(master, liveModuleGrant, pkg.id());

		UUID packageAnnouncement = idOf(announce(master, announcementJson("Expiry check", "MODULE", pkg.id(), null, null)));
		assertThat(recipients(packageAnnouncement)).containsExactly(liveModuleGrant)
			.doesNotContain(expiredModuleGrant, expiredRoster, expiredExamGrant);

		UUID examAnnouncement = idOf(announce(master, announcementJson("Exam expiry check", "EXAM", null, pkg.paid(), null)));
		assertThat(recipients(examAnnouncement)).containsExactly(liveModuleGrant)
			.doesNotContain(expiredExamGrant, expiredRoster, expiredModuleGrant);
	}

	/**
	 * R4: an exam taken out of a package stays open to the students who held the package before the removal, so they keep
	 * getting its announcements. A student who got the package after the removal never had the exam through it.
	 */
	@Test
	void examAnnouncementsStillReachStudentsWhoHeldThePackageBeforeTheExamLeftIt() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Pkg pkg = pkg(staff, "Removed exam package", true);

		UUID grantedBefore = fx.seed("STUDENT");
		grantModule(master, grantedBefore, pkg.id());
		UUID rosteredBefore = fx.seed("STUDENT");
		pf.markRoster(pkg.id(), rosteredBefore, BEFORE);

		Instant removedAt = Instant.parse("2026-10-20T12:00:00Z");
		fx.jdbc().update("update live_exam_package_exams set removed_at = ? where module_id = ? and exam_id = ?",
				java.sql.Timestamp.from(removedAt), pkg.id(), pkg.paid());
		clock.set(removedAt.plusSeconds(3600));
		UUID grantedAfter = fx.seed("STUDENT");
		grantModule(master, grantedAfter, pkg.id());
		UUID stranger = fx.seed("STUDENT");

		JsonNode preview = read(send(get("/api/v1/announcements/preview").param("audience", "EXAM")
			.param("examId", pkg.paid().toString()), master).andExpect(status().isOk()));
		UUID announcement = idOf(announce(master, announcementJson("Paper corrected", "EXAM", null, pkg.paid(), null)));

		assertThat(recipients(announcement)).containsExactlyInAnyOrder(grantedBefore, rosteredBefore)
			.doesNotContain(grantedAfter, stranger);
		assertThat(preview.get("recipients").asInt()).isEqualTo(2);
		// The page shows it to the same students, and not to the one who joined after the removal.
		assertThat(pageAnnouncements(fx.token(grantedBefore), "exam", pkg.paid()).size()).isEqualTo(1);
		assertThat(pageAnnouncements(fx.token(grantedAfter), "exam", pkg.paid()).size()).isZero();
	}

	@Test
	void selectedReachesExactlyThePickedStudentsAndRefusesOthers() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID one = fx.seed("STUDENT");
		UUID two = fx.seed("STUDENT");
		UUID inactive = fx.seed("STUDENT");
		fx.jdbc().update("update users set status = 'INACTIVE' where id = ?", inactive);
		UUID unknown = UUID.randomUUID();
		UUID staffMember = fx.seed("SYSTEM_ADMIN");

		UUID announcement = idOf(announce(master, announcementJson("Picked students", "SELECTED", null, null,
				List.of(one, two, one))));
		assertThat(recipients(announcement)).containsExactlyInAnyOrder(one, two);
		assertThat(fx.jdbc().queryForObject("select count(*) from student_notices where announcement_id = ?"
				+ " and exam_id is null and module_id is null", Integer.class, announcement)).isEqualTo(2);

		// An inactive student, an unknown id and a staff member are each refused, and each is named in the field errors.
		JsonNode refused = read(send(post("/api/v1/announcements").contentType(JSON).content(announcementJson("Refused",
				"SELECTED", null, null, List.of(one, inactive, unknown, staffMember))), master)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED")));
		assertThat(refused.get("fieldErrors").size()).isEqualTo(3);
		assertThat(fieldErrorIds(refused)).containsExactlyInAnyOrder(inactive.toString(), unknown.toString(),
				staffMember.toString());
		send(post("/api/v1/announcements").contentType(JSON).content(announcementJson("Empty", "SELECTED", null, null, List.of())),
				master).andExpect(status().isBadRequest());
		send(post("/api/v1/announcements").contentType(JSON).content(announcementJson("Too many", "SELECTED", null, null,
				manyIds(501))), master).andExpect(status().isBadRequest());
		// ALL takes no picks, and SELECTED takes no package.
		send(post("/api/v1/announcements").contentType(JSON).content(announcementJson("All with picks", "ALL", null, null,
				List.of(one))), master).andExpect(status().isBadRequest());
		UUID otherPackage = pkg(staff, "Selected package", false).id();
		send(post("/api/v1/announcements").contentType(JSON).content(announcementJson("Selected with package", "SELECTED",
				otherPackage, null, List.of(one))), master).andExpect(status().isBadRequest());
		// Selected students have no preview: they are picked by hand.
		send(get("/api/v1/announcements/preview").param("audience", "SELECTED"), master).andExpect(status().isBadRequest());
	}

	@Test
	void previewEqualsTheSendAndWritesNothing() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Pkg pkg = pkg(staff, "Preview package", false);
		UUID rostered = fx.seed("STUDENT");
		pf.markRoster(pkg.id(), rostered, BEFORE);
		UUID granted = fx.seed("STUDENT");
		grantExam(master, granted, pkg.paid());

		int notices = countAllNotices();
		JsonNode preview = read(send(get("/api/v1/announcements/preview").param("audience", "MODULE")
			.param("moduleId", pkg.id().toString()), master).andExpect(status().isOk()));
		assertThat(countAllNotices()).as("a preview writes nothing").isEqualTo(notices);
		assertThat(preview.get("recipients").asInt()).isEqualTo(2);

		JsonNode sent = announce(master, announcementJson("Preview check", "MODULE", pkg.id(), null, null));
		assertThat(sent.get("recipients").asInt()).isEqualTo(preview.get("recipients").asInt());

		JsonNode examPreview = read(send(get("/api/v1/announcements/preview").param("audience", "EXAM")
			.param("examId", pkg.paid().toString()), master).andExpect(status().isOk()));
		assertThat(examPreview.get("recipients").asInt()).isEqualTo(2);
		send(get("/api/v1/announcements/preview").param("audience", "MODULE"), master).andExpect(status().isBadRequest());
		send(get("/api/v1/announcements/preview").param("audience", "SOMETHING"), master).andExpect(status().isBadRequest());
	}

	@Test
	void aPackageWithNoHoldersSendsToZeroAndStillSaves() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID empty = fx.module(staff, "Nobody yet package");

		JsonNode sent = announce(master, announcementJson("Nobody yet", "MODULE", empty, null, null));
		assertThat(sent.get("recipients").asInt()).isZero();
		assertThat(sent.get("audience").asText()).isEqualTo("MODULE");
		assertThat(sent.get("moduleId").asText()).isEqualTo(empty.toString());
		assertThat(absent(sent, "examId")).isTrue();
	}

	@Test
	void withdrawHidesTheAnnouncementFromTheNoticesThePageAndTheCount() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Pkg pkg = pkg(staff, "Withdraw package", false);
		UUID holder = fx.seed("STUDENT");
		pf.markRoster(pkg.id(), holder, BEFORE);
		String token = fx.token(holder);
		UUID announcement = idOf(announce(master, announcementJson("Withdraw me", "MODULE", pkg.id(), null, null)));

		assertThat(hasAnnouncement(notices(token), announcement)).isTrue();
		long unreadBefore = unreadCount(token);
		assertThat(unreadBefore).isPositive();
		assertThat(pageAnnouncements(token, "package", pkg.id()).size()).isEqualTo(1);

		JsonNode withdrawn = read(send(put("/api/v1/announcements/" + announcement + "/withdraw"), staff)
			.andExpect(status().isOk()).andExpect(jsonPath("$.withdrawn").value(true)));
		assertThat(absent(withdrawn, "withdrawnAt")).isFalse();
		String firstWithdrawnAt = withdrawn.get("withdrawnAt").asText();

		assertThat(hasAnnouncement(notices(token), announcement)).isFalse();
		assertThat(pageAnnouncements(token, "package", pkg.id()).size()).isZero();
		assertThat(unreadCount(token)).as("the unread count drops by the withdrawn notice").isEqualTo(unreadBefore - 1);
		UUID notice = noticeId(holder, announcement);
		send(post("/api/v1/me/notices/" + notice + "/read"), token).andExpect(status().isNotFound());
		send(post("/api/v1/me/notices/read-all"), token).andExpect(status().isOk());
		assertThat(fx.jdbc().queryForObject("select read_at is null from student_notices where id = ?", Boolean.class, notice))
			.as("a withdrawn notice is not marked read, even by read-all").isTrue();

		// Withdrawing again changes nothing: the same instant, and one audit row only.
		JsonNode again = read(send(put("/api/v1/announcements/" + announcement + "/withdraw"), staff).andExpect(status().isOk()));
		assertThat(again.get("withdrawnAt").asText()).isEqualTo(firstWithdrawnAt);
		assertThat(fx.jdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class,
				AuditRecorder.ANNOUNCEMENT_WITHDRAWN, announcement)).isEqualTo(1);
		assertThat(fx.jdbc().queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class,
				AuditRecorder.ANNOUNCEMENT_SENT, announcement)).isEqualTo(1);

		send(put("/api/v1/announcements/" + UUID.randomUUID() + "/withdraw"), staff).andExpect(status().isNotFound());
	}

	@Test
	void myAnnouncementsShowsHoldersAndFreeExamsAndNothingToOthers() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		Pkg pkg = pkg(staff, "Page package", false);
		UUID category = fx.category(staff, "Page standalone");
		UUID free = standalone(staff, master, category, "Free page exam", true);
		UUID paid = pkg.paid();

		UUID holder = fx.seed("STUDENT");
		pf.markRoster(pkg.id(), holder, BEFORE);
		UUID stranger = fx.seed("STUDENT");
		UUID anyStudent = fx.seed("STUDENT");
		String holderToken = fx.token(holder);
		String strangerToken = fx.token(stranger);
		String anyToken = fx.token(anyStudent);

		announce(master, announcementJson("First package news", "MODULE", pkg.id(), null, null));
		clock.set(BEFORE.plusSeconds(3600));
		announce(master, announcementJson("Second package news", "MODULE", pkg.id(), null, null));
		clock.set(BEFORE);
		announce(master, announcementJson("Free exam postponed", "EXAM", null, free, null));
		announce(master, announcementJson("Paid exam postponed", "EXAM", null, paid, null));

		JsonNode page = pageAnnouncements(holderToken, "package", pkg.id());
		assertThat(page.size()).isEqualTo(2);
		assertThat(page.get(0).get("titleEn").asText()).isEqualTo("Second package news");
		assertThat(page.get(1).get("titleEn").asText()).isEqualTo("First package news");
		assertThat(page.get(0).get("bodyEn").asText()).isEqualTo("Closed on Friday");
		assertThat(absent(page.get(0), "audience")).as("the page shows titles and bodies only").isTrue();

		// The holder of the package sees the paid exam's announcement on its page. A stranger sees neither.
		assertThat(pageAnnouncements(holderToken, "exam", paid).get(0).get("titleEn").asText())
			.isEqualTo("Paid exam postponed");
		assertThat(pageAnnouncements(strangerToken, "package", pkg.id()).size()).isZero();
		assertThat(pageAnnouncements(strangerToken, "exam", paid).size()).isZero();
		// Any signed-in student sees a free exam's announcement, though nobody has started it.
		assertThat(pageAnnouncements(anyToken, "exam", free).get(0).get("titleEn").asText()).isEqualTo("Free exam postponed");

		// An unknown or deleted target is an empty list, not an error. An unknown kind is 404.
		assertThat(pageAnnouncements(holderToken, "package", UUID.randomUUID()).size()).isZero();
		send(get("/api/v1/me/announcements").param("kind", "course").param("id", pkg.id().toString()), holderToken)
			.andExpect(status().isNotFound());

		// Anonymous is 401. Staff, who are not students, are 403.
		send(get("/api/v1/me/announcements").param("kind", "package").param("id", pkg.id().toString()), null)
			.andExpect(status().isUnauthorized());
		send(get("/api/v1/me/announcements").param("kind", "package").param("id", pkg.id().toString()), staff)
			.andExpect(status().isForbidden());
	}

	@Test
	void systemAdminSendsPreviewsListsAndWithdraws() throws Exception {
		String system = fx.staff();

		JsonNode sent = announce(system, announcementJson("System admin news", "ALL", null, null, null));
		send(get("/api/v1/announcements/preview").param("audience", "ALL"), system).andExpect(status().isOk())
			.andExpect(jsonPath("$.recipients").value(activeStudents()));
		send(get("/api/v1/announcements"), system).andExpect(status().isOk());
		send(put("/api/v1/announcements/" + idOf(sent) + "/withdraw"), system).andExpect(status().isOk());
	}

	@Test
	void instructorAndStudentGetForbiddenOnEveryAnnouncementRoute() throws Exception {
		String master = fx.master();
		UUID announcement = idOf(announce(master, announcementJson("Staff only", "ALL", null, null, null)));
		String instructor = fx.token(fx.seed("INSTRUCTOR"));
		String student = fx.token(fx.seed("STUDENT"));

		for (String token : List.of(instructor, student)) {
			send(post("/api/v1/announcements").contentType(JSON).content(announcementJson("Nope", "ALL", null, null, null)), token)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
			send(get("/api/v1/announcements/preview").param("audience", "ALL"), token).andExpect(status().isForbidden());
			send(get("/api/v1/announcements"), token).andExpect(status().isForbidden());
			send(put("/api/v1/announcements/" + announcement + "/withdraw"), token).andExpect(status().isForbidden());
		}
		assertThat(fx.jdbc().queryForObject("select withdrawn_at is null from announcements where id = ?", Boolean.class,
				announcement)).isTrue();
	}

	@Test
	void historyShowsTheTargetAndWithdrawalAndFiltersByAudience() throws Exception {
		String staff = fx.staff();
		String master = fx.master();
		UUID category = fx.category(staff, "History category");
		UUID exam = standalone(staff, master, category, "History exam", false);
		UUID everyone = idOf(announce(master, announcementJson("History everyone", "ALL", null, null, null)));
		UUID onExam = idOf(announce(master, announcementJson("History exam news", "EXAM", null, exam, null)));
		send(put("/api/v1/announcements/" + onExam + "/withdraw"), master).andExpect(status().isOk());

		JsonNode rows = read(send(get("/api/v1/announcements").param("size", "100"), master).andExpect(status().isOk()))
			.get("content");
		JsonNode examRow = rowFor(rows, onExam);
		assertThat(examRow.get("audience").asText()).isEqualTo("EXAM");
		assertThat(examRow.get("examId").asText()).isEqualTo(exam.toString());
		assertThat(examRow.get("examTitleEn").asText()).isEqualTo("History exam");
		assertThat(examRow.get("withdrawn").asBoolean()).isTrue();
		assertThat(absent(examRow, "withdrawnAt")).isFalse();
		assertThat(absent(examRow, "bodyEn")).as("the history leaves the body out").isTrue();
		JsonNode everyoneRow = rowFor(rows, everyone);
		assertThat(absent(everyoneRow, "examId")).isTrue();
		assertThat(everyoneRow.get("withdrawn").asBoolean()).isFalse();
		assertThat(absent(everyoneRow, "withdrawnAt")).isTrue();

		JsonNode examOnly = read(send(get("/api/v1/announcements").param("audience", "EXAM").param("size", "100"), master)
			.andExpect(status().isOk())).get("content");
		assertThat(examOnly.size()).isPositive();
		for (JsonNode row : examOnly) {
			assertThat(row.get("audience").asText()).isEqualTo("EXAM");
		}
		send(get("/api/v1/announcements").param("audience", "NOPE"), master).andExpect(status().isBadRequest());
	}

	// ---- helpers -------------------------------------------------------------------------------------------------

	/** A package with one paid member exam, and a free member exam when asked. Published. */
	private Pkg pkg(String staff, String title, boolean withFree) throws Exception {
		UUID category = pf.createCategory(staff, title + " category");
		UUID pkg = pf.createPackage(staff, category, title);
		UUID paid = pf.createExam(staff, pkg, "MCQ", title + " paper", AuthoringFixture.LIVE_AT);
		pf.addQuestion(staff, paid);
		pf.publishExam(staff, paid);
		UUID free = null;
		if (withFree) {
			free = pf.createExam(staff, pkg, "MCQ", title + " free paper", AuthoringFixture.LIVE_AT);
			pf.addQuestion(staff, free);
			pf.publishExam(staff, free);
			fx.jdbc().update("update live_exams set is_free = true where id = ?", free);
		}
		pf.finalSaveAndPublish(staff, pkg);
		return new Pkg(pkg, paid, free);
	}

	/** A published standalone MCQ exam with one question. Free when asked: open to every student, with no price. */
	private UUID standalone(String staff, String master, UUID category, String title, boolean free) throws Exception {
		UUID exam = fx.createStandalone(staff, category, "MCQ", title, AuthoringFixture.LIVE_AT, 40, null);
		fx.question(staff, exam, "Two plus two", 2);
		fx.publishNew(master, exam);
		if (free) {
			fx.jdbc().update("update live_exams set is_free = true where id = ?", exam);
		}
		return exam;
	}

	private void grantModule(String master, UUID student, UUID module) throws Exception {
		send(post("/api/v1/live-exam-grants").contentType(JSON)
			.content("""
					{"studentId":"%s","scope":"MODULE","moduleId":"%s","examId":null,"reason":"Audience test grant"}
					""".formatted(student, module)), master).andExpect(status().isCreated());
	}

	private void grantExam(String master, UUID student, UUID exam) throws Exception {
		send(post("/api/v1/live-exam-grants").contentType(JSON)
			.content("""
					{"studentId":"%s","scope":"EXAM","moduleId":null,"examId":"%s","reason":"Audience test grant"}
					""".formatted(student, exam)), master).andExpect(status().isCreated());
	}

	private void revokeGrants(UUID student) {
		fx.jdbc().update("update live_exam_grants set revoked_at = now() where student_id = ?", student);
	}

	/** Sits the exam inside the window and submits it, then puts the clock back where it was. */
	private void sit(String token, UUID exam) throws Exception {
		Instant saved = clock.instant();
		clock.set(SIT_AT);
		send(post("/api/v1/live-exams/" + exam + "/take").contentType(JSON).content("{}"), token).andExpect(status().isOk());
		clock.set(SUBMIT_AT);
		send(post("/api/v1/live-exams/" + exam + "/submit"), token).andExpect(status().isOk());
		clock.set(saved);
	}

	/** Sends an announcement as the token's staff member and expects it to be created. */
	private JsonNode announce(String token, String body) throws Exception {
		return read(send(post("/api/v1/announcements").contentType(JSON).content(body), token).andExpect(status().isCreated()));
	}

	private static UUID idOf(JsonNode node) {
		return UUID.fromString(node.get("id").asText());
	}

	/** The students who got an announcement's notice. */
	private Set<UUID> recipients(UUID announcement) {
		return Set.copyOf(fx.jdbc().queryForList("select user_id from student_notices where announcement_id = ?", UUID.class,
				announcement));
	}

	private int countAllNotices() {
		return fx.jdbc().queryForObject("select count(*) from student_notices", Integer.class);
	}

	private int activeStudents() {
		return fx.jdbc().queryForObject("select count(*) from users where role = 'STUDENT' and status = 'ACTIVE'"
				+ " and deleted_at is null", Integer.class);
	}

	private UUID noticeId(UUID student, UUID announcement) {
		return fx.jdbc().queryForObject("select id from student_notices where user_id = ? and announcement_id = ?", UUID.class,
				student, announcement);
	}

	private JsonNode noticeFor(UUID student, UUID announcement) throws Exception {
		for (JsonNode node : notices(fx.token(student))) {
			if (node.get("announcement") != null && announcement.toString().equals(node.get("announcement").get("id").asText())) {
				return node;
			}
		}
		throw new AssertionError("The student has no notice for the announcement " + announcement);
	}

	/** The student's announcement tab, one page of 50. */
	private JsonNode notices(String token) throws Exception {
		return read(send(get("/api/v1/me/notices").param("group", "ANNOUNCEMENTS").param("size", "50"), token)
			.andExpect(status().isOk())).get("content");
	}

	private static boolean hasAnnouncement(JsonNode content, UUID announcement) {
		for (JsonNode node : content) {
			if (node.get("announcement") != null && announcement.toString().equals(node.get("announcement").get("id").asText())) {
				return true;
			}
		}
		return false;
	}

	private long unreadCount(String token) throws Exception {
		return read(send(get("/api/v1/me/notices/unread-count"), token).andExpect(status().isOk())).get("count").asLong();
	}

	private JsonNode pageAnnouncements(String token, String kind, UUID target) throws Exception {
		return read(send(get("/api/v1/me/announcements").param("kind", kind).param("id", target.toString()), token)
			.andExpect(status().isOk()));
	}

	private static JsonNode rowFor(JsonNode rows, UUID id) {
		for (JsonNode row : rows) {
			if (id.toString().equals(row.get("id").asText())) {
				return row;
			}
		}
		throw new AssertionError("No history row for " + id);
	}

	/** The ids named in the field errors. Each message starts with the bad id. */
	private static List<String> fieldErrorIds(JsonNode error) {
		List<String> ids = new ArrayList<>();
		for (JsonNode field : error.get("fieldErrors")) {
			ids.add(field.get("message").asText().split(" ")[0]);
		}
		return ids;
	}

	private static List<UUID> manyIds(int count) {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ids.add(UUID.randomUUID());
		}
		return ids;
	}

	/** A field is absent when it is missing or null: nulls are left out of JSON. */
	private static boolean absent(JsonNode node, String field) {
		return node.get(field) == null || node.get(field).isNull();
	}

	private static String announcementJson(String titleEn, String audience, UUID moduleId, UUID examId, List<UUID> studentIds) {
		return """
				{"titleEn":"%s","titleBn":"ঘোষণা","bodyEn":"Closed on Friday","bodyBn":"","audience":"%s",\
				"moduleId":%s,"examId":%s,"studentIds":%s}
				""".formatted(titleEn, audience, quoted(moduleId), quoted(examId), idList(studentIds));
	}

	private static String quoted(UUID id) {
		return id == null ? "null" : "\"" + id + "\"";
	}

	private static String idList(List<UUID> ids) {
		if (ids == null) {
			return "null";
		}
		StringBuilder list = new StringBuilder("[");
		for (int i = 0; i < ids.size(); i++) {
			list.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append('"');
		}
		return list.append(']').toString();
	}

	private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return mockMvc.perform(request);
	}

	private JsonNode read(ResultActions actions) throws Exception {
		return json.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	/** A package with its paid member exam, and its free member exam when it has one. */
	private record Pkg(UUID id, UUID paid, UUID free) {
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-20T04:00:00Z"));
		}

	}

}
