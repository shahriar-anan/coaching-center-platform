package com.coachingcenter.api.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.coachingcenter.api.auth.Role;

class EmailTemplatesTest {

	private static final MailContext CTX = new MailContext("https://exam.example", "https://admin.exam.example", 7, 10, 10);

	private static final MailRecipient STUDENT = new MailRecipient("sumon@example.com", "সুমন আহমেদ", Role.STUDENT);

	private static final MailRecipient INSTRUCTOR = new MailRecipient("teacher@example.com", "Rafiq Hasan", Role.INSTRUCTOR);

	private static Map<String, RenderedEmail> all(MailContext ctx) {
		Map<String, RenderedEmail> mails = new LinkedHashMap<>();
		mails.put("activation-student", EmailTemplates.activation(ctx, STUDENT, "K7M2Q9RX"));
		mails.put("activation-instructor", EmailTemplates.activation(ctx, INSTRUCTOR, "K7M2Q9RX"));
		mails.put("email-verification", EmailTemplates.emailVerification(ctx, STUDENT, "482913"));
		mails.put("password-reset", EmailTemplates.passwordReset(ctx, STUDENT, "P4R8W2YN"));
		mails.put("password-changed", EmailTemplates.passwordChanged(ctx, STUDENT));
		mails.put("phone-changed", EmailTemplates.phoneChanged(ctx, STUDENT));
		mails.put("payment-receipt", EmailTemplates.paymentReceipt(ctx, "বিজেএস ফুল মক প্যাকেজ", "BJS Full Mock Package", "2500.00",
				"DEMO-PKG-B3-A", "2026-10-27 15:30"));
		mails.put("purchase-notice", EmailTemplates.purchaseNotice(ctx, "সুমন আহমেদ", "+8801711000011", "sumon@example.com",
				"বিজেএস ফুল মক প্যাকেজ", "BJS Full Mock Package", "2500.00", "bKash", "DEMO-PKG-B3-A", "2026-10-27 15:30"));
		mails.put("exam-hour", EmailTemplates.examNotice(ctx, true, "সাক্ষ্য আইন এমসিকিউ (রাত ১১টা)", "Evidence MCQ (11 PM)",
				"2026-10-28 23:00", "/exam/abc"));
		mails.put("exam-time", EmailTemplates.examNotice(ctx, false, "সাক্ষ্য আইন এমসিকিউ (রাত ১১টা)", "Evidence MCQ (11 PM)",
				"2026-10-28 23:00", "/exam/abc"));
		return mails;
	}

	@Test
	void everyMailCarriesTheLogoAsAHeaderImageLinkedFromTheSiteAndNothingIsAttached() {
		all(CTX).forEach((name, mail) -> {
			assertThat(mail.html()).as(name).contains("<img src=\"https://exam.example/brand/logo-light.png\"").contains("alt=\"SYMAM LAW\"");
			assertThat(mail.html()).as(name).doesNotContain("cid:").doesNotContain("data:image");
			// The mark comes before the first heading: it is the header of the mail.
			assertThat(mail.html().indexOf("logo-light.png")).as(name).isLessThan(mail.html().indexOf("<h1"));
		});
	}

	@Test
	void everyMailIsBilingualHasAPlainTextTwinAndRunsNoScript() {
		all(CTX).forEach((name, mail) -> {
			assertThat(mail.subject()).as(name).contains(" · ");
			assertThat(mail.html()).as(name).doesNotContain("<script").doesNotContain("javascript:");
			assertThat(mail.text()).as(name).isNotBlank().doesNotContain("<").contains("SYMAM LAW");
			assertThat(mail.html()).as(name).contains("<meta name=\"viewport\"").contains("max-width:600px");
		});
	}

	@Test
	void aCodeMailShowsTheCodeSelectableWithACopyButtonAndIcon() {
		Map<String, String> codes = Map.of("activation-student", "K7M2Q9RX", "activation-instructor", "K7M2Q9RX",
				"email-verification", "482913", "password-reset", "P4R8W2YN");
		Map<String, RenderedEmail> mails = all(CTX);
		codes.forEach((name, code) -> {
			RenderedEmail mail = mails.get(name);
			assertThat(mail.html()).as(name).contains(">" + code + "</div>").contains("user-select:all");
			assertThat(mail.html()).as(name).contains("কোড কপি করুন · Copy code").contains("https://exam.example/brand/copy-icon.png");
			assertThat(mail.html()).as(name).contains("https://exam.example/code#c=" + code + "&amp;for=");
			assertThat(mail.text()).as(name).contains(code);
		});
	}

	@Test
	void theCodeRidesInTheAddressFragmentAndNeverInAQueryString() {
		Map<String, RenderedEmail> mails = all(CTX);
		assertThat(mails.get("activation-student").html()).contains("https://exam.example/activate#code=K7M2Q9RX");
		assertThat(mails.get("password-reset").html()).contains("https://exam.example/reset-password#code=P4R8W2YN");
		mails.forEach((name, mail) -> assertThat(mail.html() + mail.text()).as(name).doesNotContain("?code=").doesNotContain("&code=")
				.doesNotContain("?c="));
	}

	@Test
	void staffAreSentToTheAdminSiteAndStudentsToTheStudentSite() {
		Map<String, RenderedEmail> mails = all(CTX);
		assertThat(mails.get("activation-instructor").html()).contains("https://admin.exam.example/activate#code=K7M2Q9RX");
		assertThat(mails.get("activation-student").html()).contains("href=\"https://exam.example/activate#code=K7M2Q9RX\"")
			.doesNotContain("https://admin.exam.example/activate");
		// The logo and the copy page are always on the student site.
		assertThat(mails.get("activation-instructor").html()).contains("https://exam.example/brand/logo-light.png");
	}

	@Test
	void theWelcomeMailsGreetByFirstNameAndSayWelcomeOnBoard() {
		RenderedEmail activation = EmailTemplates.activation(CTX, STUDENT, "K7M2Q9RX");
		assertThat(activation.subject()).isEqualTo("স্বাগতম! আপনার অ্যাকাউন্ট চালু করুন · Welcome on board");
		assertThat(activation.html()).contains("স্বাগতম, সুমন!").contains("Welcome on board").contains("শিক্ষার্থী").contains("৭ দিন");
		assertThat(EmailTemplates.activation(CTX, new MailRecipient("a@b.c", null, null), "K7M2Q9RX").html()).contains(">স্বাগতম!<");
		assertThat(EmailTemplates.emailVerification(CTX, STUDENT, "482913").html()).contains("স্বাগতম, সুমন!").contains("Welcome on board");
	}

	@Test
	void textFromAPersonOrARecordIsEscaped() {
		MailRecipient hostile = new MailRecipient("x@example.com", "Tom <script>alert(1)</script>", Role.STUDENT);
		RenderedEmail mail = EmailTemplates.activation(CTX, hostile, "K7M2Q9RX");
		assertThat(mail.html()).doesNotContain("<script>alert").contains("Tom");
		RenderedEmail receipt = EmailTemplates.paymentReceipt(CTX, "A & B <i>", "C \"D\"", "10.00", "T-1", "now");
		assertThat(receipt.html()).contains("A &amp; B &lt;i&gt;").contains("C &quot;D&quot;").doesNotContain("<i>");
	}

	@Test
	void theResetMailSaysHowLongTheCodeLivesAndWhatToDoIfItWasNotAsked() {
		RenderedEmail mail = EmailTemplates.passwordReset(CTX, STUDENT, "P4R8W2YN");
		assertThat(mail.html()).contains("১০ মিনিট").contains("10 minutes").contains("ignore this email");
		assertThat(mail.subject()).isEqualTo("পাসওয়ার্ড রিসেটের কোড · Reset your password");
	}

	@Test
	void theReceiptAndTheMasterAdminNoteShowTheOrderDetails() {
		RenderedEmail receipt = EmailTemplates.paymentReceipt(CTX, "বিজেএস ফুল মক প্যাকেজ", "BJS Full Mock Package", "2500.00", "DEMO-PKG-B3-A",
				"2026-10-27 15:30");
		assertThat(receipt.html()).contains("৳2500.00").contains("DEMO-PKG-B3-A").contains("BJS Full Mock Package")
			.contains("https://exam.example/my-modules");
		RenderedEmail notice = EmailTemplates.purchaseNotice(CTX, "সুমন আহমেদ", "+8801711000011", "sumon@example.com", "বিজেএস ফুল মক প্যাকেজ",
				"BJS Full Mock Package", "2500.00", "bKash", "DEMO-PKG-B3-A", "2026-10-27 15:30");
		assertThat(notice.html()).contains("bKash").contains("+8801711000011").contains("https://admin.exam.example/staff/payments");
		assertThat(notice.subject()).isEqualTo("নতুন পেমেন্ট · New purchase: BJS Full Mock Package");
	}

	/** Writes each mail as an HTML file under target/email-previews, for looking at in a browser. The logo and icon load from the web folder. */
	@Test
	void writesPreviewFilesForReview() throws Exception {
		String webPublic = Path.of("..", "web", "public").toAbsolutePath().normalize().toUri().toString().replaceAll("/$", "");
		MailContext preview = new MailContext(webPublic, webPublic, 7, 10, 10);
		Path dir = Path.of("target", "email-previews");
		Files.createDirectories(dir);
		for (Map.Entry<String, RenderedEmail> entry : all(preview).entrySet()) {
			Files.writeString(dir.resolve(entry.getKey() + ".html"), entry.getValue().html(), StandardCharsets.UTF_8);
		}
		assertThat(Files.list(dir).count()).isGreaterThanOrEqualTo(10);
	}

}
