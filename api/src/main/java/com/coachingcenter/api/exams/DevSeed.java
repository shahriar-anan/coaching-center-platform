package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.files.LocalFileStorage;

/**
 * The minimal dev seed, switched on by {@code SEED_DEV=true} on the dev profile only. It is separate from {@link DemoContentSeed},
 * which feeds staging.
 *
 * <p>Accounts (the Master Admin comes from {@code MASTER_ADMIN_*}; everyone here shares {@code SEED_DEV_PASSWORD}): two System
 * Admins, two instructors, two students. Catalog: two root categories, each with one sub-category, and one MCQ and one written exam
 * in each sub-category; and one paid package, in the second sub-category, with its own MCQ and written exam. Each exam is there for
 * one case:
 * <ul>
 * <li>1 BJS MCQ: live and open now, free.</li>
 * <li>2 BJS written (instructor 1): live and finished, free. Student 1's paper awaits marking; student 2's is marked, not released.</li>
 * <li>3 Bar MCQ: live and not open yet (opens tomorrow), free.</li>
 * <li>4 Bar written (instructor 1): live and open now, free.</li>
 * <li>5 Package MCQ: practice mode (no window), reached through the package.</li>
 * <li>6 Package written (instructor 2): live and open now, reached through the package.</li>
 * </ul>
 * Student 1 holds a manual grant on the package; student 2 does not. Open windows run from yesterday to 30 days ahead, so a run
 * stays usable for a month; a reset is a fresh database and a new run. The seed only inserts, and only once.
 */
@Component
public class DevSeed {

	/** Every exam is this long. */
	static final int EXAM_MINUTES = 20;

	static final int MCQ_MARKS = 5;

	static final int WRITTEN_FULL_MARKS = 50;

	static final int PASS_PERCENT = 40;

	static final UUID STAFF_1 = DemoContentSeed.demo('4', 1);

	static final UUID STAFF_2 = DemoContentSeed.demo('4', 2);

	static final UUID INSTRUCTOR_1 = DemoContentSeed.demo('6', 1);

	static final UUID INSTRUCTOR_2 = DemoContentSeed.demo('6', 2);

	static final UUID STUDENT_1 = DemoContentSeed.demo('5', 1);

	static final UUID STUDENT_2 = DemoContentSeed.demo('5', 2);

	static final UUID CATEGORY_A = DemoContentSeed.demo('a', 1);

	static final UUID CATEGORY_A1 = DemoContentSeed.demo('a', 2);

	static final UUID CATEGORY_B = DemoContentSeed.demo('a', 3);

	static final UUID CATEGORY_B1 = DemoContentSeed.demo('a', 4);

	static final UUID PACKAGE = DemoContentSeed.demo('b', 1);

	/** Phones and emails are fixed so either can be used to sign in. */
	static final List<DemoContentSeed.Account> ACCOUNTS = List.of(
			new DemoContentSeed.Account(STAFF_1, "01711000001", "staff1@example.com", "Staff One", "SYSTEM_ADMIN", null),
			new DemoContentSeed.Account(STAFF_2, "01711000002", "staff2@example.com", "Staff Two", "SYSTEM_ADMIN", null),
			new DemoContentSeed.Account(INSTRUCTOR_1, "01711000003", "instructor1@example.com", "Instructor One", "INSTRUCTOR", null),
			new DemoContentSeed.Account(INSTRUCTOR_2, "01711000004", "instructor2@example.com", "Instructor Two", "INSTRUCTOR", null),
			new DemoContentSeed.Account(STUDENT_1, "01711000011", "student1@example.com", "Student One", "STUDENT", "DEV-STU-001"),
			new DemoContentSeed.Account(STUDENT_2, "01711000012", "student2@example.com", "Student Two", "STUDENT", "DEV-STU-002"));

	private static final String INSERT_USER = """
			INSERT INTO users (id, phone, email, password_hash, role, status, email_verified_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?) ON CONFLICT DO NOTHING
			""";

	private static final String INSERT_EXAM = """
			INSERT INTO live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en, description_bn,
			  rules_en, rules_bn, paper_language, live_at, ends_at, mode, duration_minutes, pass_percent, written_maximum,
			  question_stored_file_name, question_original_file_name, question_byte_size, question_content_type, status,
			  finally_saved, is_free, instructor_id, published_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', true, ?, ?, ?, ?, ?)
			""";

	private static final String INSERT_SITTING = """
			INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, score,
			  passed, status, comment, evaluated_by, evaluated_at, answer_stored_file_name, answer_original_file_name,
			  answer_byte_size, answer_content_type, evaluated_stored_file_name, evaluated_original_file_name,
			  evaluated_byte_size, evaluated_content_type, created_at, updated_at)
			VALUES (?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	private final DemoContentSeed demo;

	public DevSeed(JdbcTemplate jdbc, PasswordEncoder passwords, DemoContentSeed demo) {
		this.jdbc = jdbc;
		this.passwords = passwords;
		this.demo = demo;
	}

	/** Loads the dev content once. {@code env} reads {@code SEED_DEV_PASSWORD}; no password is written into code. */
	@Transactional
	public void seed(String[] profiles, Function<String, String> env, Instant now) {
		DemoContentSeed.refuseProduction(profiles);
		if (Arrays.stream(profiles).noneMatch(profile -> "dev".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The dev seed runs on the dev profile only.");
		}
		String password = DemoContentSeed.required("SEED_DEV_PASSWORD", env.apply("SEED_DEV_PASSWORD"));
		DemoContentSeed.requirePassword("SEED_DEV_PASSWORD", password);
		Integer already = jdbc.queryForObject("SELECT count(*) FROM live_exam_categories WHERE id = ?", Integer.class, CATEGORY_A);
		if (already != null && already > 0) {
			return;
		}
		String hash = passwords.encode(password);
		for (DemoContentSeed.Account account : ACCOUNTS) {
			account(account, hash, now);
		}
		category(CATEGORY_A, null, "Bangladesh Judicial Service (BJS)", "বাংলাদেশ জুডিশিয়াল সার্ভিস (বিজেএস)", 0, now);
		category(CATEGORY_A1, CATEGORY_A, "Assistant Judge Recruitment Exam", "সহকারী জজ নিয়োগ পরীক্ষা", 0, now);
		category(CATEGORY_B, null, "Bar Council Enrolment", "বার কাউন্সিল এনরোলমেন্ট", 1, now);
		category(CATEGORY_B1, CATEGORY_B, "Advocate Enrolment Exam", "অ্যাডভোকেট এনরোলমেন্ট পরীক্ষা", 0, now);
		jdbc.update("""
				INSERT INTO live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn, access_mode,
				  status, finally_saved, position, price, created_at, updated_at)
				VALUES (?, ?, 'Bar Council Complete Preparation Package', 'বার কাউন্সিল সম্পূর্ণ প্রস্তুতি প্যাকেজ',
				  'A paid bundle for the Advocate Enrolment Exam: a Penal Code MCQ practice set and a written criminal law mock exam.',
				  'অ্যাডভোকেট এনরোলমেন্ট পরীক্ষার পেইড প্যাকেজ: দণ্ডবিধির এমসিকিউ অনুশীলন সেট এবং ফৌজদারি আইনের রিটেন মক পরীক্ষা।', 'MANUAL', 'PUBLISHED', true, 0, ?, ?, ?)
				""", PACKAGE, CATEGORY_B1, new BigDecimal("500.00"), ts(now), ts(now));

		Instant openFrom = now.minus(Duration.ofDays(1));
		Instant openTo = now.plus(Duration.ofDays(30));
		Instant finishedAt = now.minus(Duration.ofDays(2));
		Instant tomorrow = now.plus(Duration.ofDays(1));
		List<Exam> exams = List.of(
				new Exam(1, "MCQ", null, CATEGORY_A1, "BJS Preliminary Model Test: Civil Procedure Code", "বিজেএস প্রিলিমিনারি মডেল টেস্ট: দেওয়ানি কার্যবিধি",
						"A free MCQ model test in the style of the BJS preliminary: questions on the Code of Civil Procedure, 1908.",
						"বিজেএস প্রিলিমিনারির ধাঁচে বিনামূল্যের এমসিকিউ মডেল টেস্ট: দেওয়ানি কার্যবিধি, ১৯০৮ থেকে প্রশ্ন।",
						"LIVE", openFrom, openTo, true, null, DemoContentSeedQuestions.CPC),
				new Exam(2, "WRITTEN", null, CATEGORY_A1, "BJS Written Mock: Criminal Law (Penal Code and CrPC)", "বিজেএস লিখিত মক: ফৌজদারি আইন (দণ্ডবিধি ও ফৌজদারি কার্যবিধি)",
						"A written mock for the BJS written stage on the Penal Code, 1860 and the Code of Criminal Procedure, 1898. Upload your handwritten answers as one PDF.",
						"বিজেএস লিখিত পর্বের জন্য দণ্ডবিধি, ১৮৬০ ও ফৌজদারি কার্যবিধি, ১৮৯৮-এর উপর মক পরীক্ষা। হাতে লেখা উত্তর একটি পিডিএফে আপলোড করুন।",
						"LIVE", finishedAt, finishedAt.plus(Duration.ofMinutes(EXAM_MINUTES)), true, INSTRUCTOR_1, List.of()),
				new Exam(3, "MCQ", null, CATEGORY_B1, "Bar Council Preliminary Model Test: General Law", "বার কাউন্সিল প্রিলিমিনারি মডেল টেস্ট: সাধারণ আইন",
						"A free MCQ model test for the Bar Council preliminary round: constitutional, civil and criminal law basics.",
						"বার কাউন্সিল প্রিলিমিনারি পর্বের জন্য বিনামূল্যের এমসিকিউ মডেল টেস্ট: সংবিধান, দেওয়ানি ও ফৌজদারি আইনের মৌলিক বিষয়।",
						"LIVE", tomorrow, tomorrow.plus(Duration.ofDays(30)), true, null, DemoContentSeedQuestions.GENERAL_LAW),
				new Exam(4, "WRITTEN", null, CATEGORY_B1, "Bar Council Written Mock: Civil Law and Procedure", "বার কাউন্সিল লিখিত মক: দেওয়ানি আইন ও কার্যবিধি",
						"A written mock for the Bar Council written round on the Contract Act, the Transfer of Property Act and civil procedure. Upload your handwritten answers as one PDF.",
						"বার কাউন্সিল লিখিত পর্বের জন্য চুক্তি আইন, সম্পত্তি হস্তান্তর আইন ও দেওয়ানি কার্যবিধির উপর মক পরীক্ষা। হাতে লেখা উত্তর একটি পিডিএফে আপলোড করুন।",
						"LIVE", openFrom, openTo, true, INSTRUCTOR_1, List.of()),
				new Exam(5, "MCQ", PACKAGE, CATEGORY_B1, "Penal Code Practice Set (MCQ)", "দণ্ডবিধি অনুশীলন সেট (এমসিকিউ)",
						"An always-open practice set on the Penal Code, 1860, for the Bar Council preliminary round. Part of the preparation package.",
						"বার কাউন্সিল প্রিলিমিনারির জন্য দণ্ডবিধি, ১৮৬০-এর সবসময় খোলা অনুশীলন সেট। প্রস্তুতি প্যাকেজের অংশ।",
						"PRACTICE", null, null, false, null, DemoContentSeedQuestions.PENAL_FULL),
				new Exam(6, "WRITTEN", PACKAGE, CATEGORY_B1, "Bar Council Written Mock: Criminal Law", "বার কাউন্সিল লিখিত মক: ফৌজদারি আইন",
						"A written mock on criminal law and the Evidence Act for the Bar Council written round. Part of the preparation package.",
						"বার কাউন্সিল লিখিত পর্বের জন্য ফৌজদারি আইন ও সাক্ষ্য আইনের উপর মক পরীক্ষা। প্রস্তুতি প্যাকেজের অংশ।",
						"LIVE", openFrom, openTo, false, INSTRUCTOR_2, List.of()));
		for (Exam exam : exams) {
			exam(exam, now);
		}

		// The finished written exam: student 1's paper is waiting for a mark, student 2's is marked and waiting for release.
		Exam finished = exams.get(1);
		sitting(DemoContentSeed.demo('7', 1), finished, STUDENT_1, null, now);
		sitting(DemoContentSeed.demo('7', 2), finished, STUDENT_2, 32, now);

		jdbc.update("""
				INSERT INTO live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source)
				VALUES (?, ?, 'MODULE', ?, NULL, NULL, ?, 'MANUAL')
				""", DemoContentSeed.demo('d', 1), STUDENT_1, PACKAGE, ts(now));
	}

	private void account(DemoContentSeed.Account account, String hash, Instant now) {
		String phone;
		try {
			phone = PhoneNumbers.normalize(account.phone());
		}
		catch (ApiException exception) {
			throw new IllegalStateException("A dev phone number is not valid.");
		}
		jdbc.update(INSERT_USER, account.id(), phone, account.email(), hash, account.role(), ts(now), ts(now), ts(now));
		if ("STUDENT".equals(account.role())) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?)", account.id(),
					account.name(), account.code());
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?)", account.id(), account.name());
		}
	}

	private void category(UUID id, UUID parent, String titleEn, String titleBn, int position, Instant now) {
		jdbc.update("""
				INSERT INTO live_exam_categories (id, parent_id, title_en, title_bn, position, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, parent, titleEn, titleBn, position, ts(now), ts(now));
	}

	private void exam(Exam exam, Instant now) {
		boolean written = "WRITTEN".equals(exam.type());
		String storedName = null;
		String originalName = null;
		Long byteSize = null;
		String contentType = null;
		if (written) {
			LocalFileStorage.StoredFile stored = demo.storePdf(exam.titleEn() + ".pdf",
					List.of("Question paper: " + exam.titleEn(), "Duration: " + EXAM_MINUTES + " minutes; full marks: "
							+ WRITTEN_FULL_MARKS, "1. State the governing provisions and explain them with the leading principles.",
							"2. Apply the law to a short problem of your choice, giving reasons."));
			storedName = stored.storedFileName();
			originalName = stored.originalFileName();
			byteSize = stored.byteSize();
			contentType = stored.contentType();
		}
		String rulesBn = written ? "কাগজে লিখে " + EXAM_MINUTES + " মিনিটের মধ্যে একটি পিডিএফ আপলোড করুন।"
				: EXAM_MINUTES + " মিনিটের মধ্যে সব প্রশ্নের উত্তর দিন।";
		String rules = written ? "Write on paper and upload one PDF within " + EXAM_MINUTES + " minutes."
				: "Answer every question within " + EXAM_MINUTES + " minutes.";
		jdbc.update(INSERT_EXAM, exam.id(), exam.module(), exam.category(), exam.type(), exam.titleEn(), exam.titleBn(), exam.descriptionEn(),
				exam.descriptionBn(), rules, rulesBn, ts(exam.liveAt()), ts(exam.endsAt()), exam.mode(), EXAM_MINUTES, PASS_PERCENT,
				written ? WRITTEN_FULL_MARKS : null, storedName, originalName, byteSize, contentType, exam.free(), exam.instructor(),
				ts(now), ts(now), ts(now));
		if (exam.module() != null) {
			jdbc.update("INSERT INTO live_exam_package_exams (id, module_id, exam_id, position, added_at) VALUES (?, ?, ?, ?, ?)",
					DemoContentSeed.demo('1', exam.number()), exam.module(), exam.id(), exam.number(), ts(now));
		}
		if (storedName != null) {
			jdbc.update("INSERT INTO live_exam_question_papers (id, exam_id, version, stored_file_name, original_file_name,"
					+ " byte_size, content_type, created_at) VALUES (?, ?, 1, ?, ?, ?, ?, ?)", DemoContentSeed.demo('2', exam.number()),
					exam.id(), storedName, originalName, byteSize, contentType, ts(now));
			jdbc.update("UPDATE live_exams SET question_paper_id = ? WHERE id = ?", DemoContentSeed.demo('2', exam.number()), exam.id());
		}
		for (int index = 0; index < exam.questions().size(); index++) {
			DemoContentSeed.Question question = exam.questions().get(index);
			UUID questionId = DemoContentSeed.demo('f', exam.number() * 100L + index + 1);
			jdbc.update("""
					INSERT INTO live_exam_questions (id, exam_id, stem, marks, explanation, position, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", questionId, exam.id(), question.stem(), MCQ_MARKS, question.explanation(), index, ts(now), ts(now));
			for (int option = 0; option < question.options().size(); option++) {
				jdbc.update("""
						INSERT INTO live_exam_options (id, question_id, option_text, correct, position, created_at, updated_at)
						VALUES (?, ?, ?, ?, ?, ?, ?)
						""", DemoContentSeed.demo('9', (exam.number() * 100L + index + 1) * 10 + option + 1), questionId,
						question.options().get(option), option == question.correctIndex(), option, ts(now), ts(now));
			}
		}
	}

	/** A submitted paper of a finished written exam. With a {@code score} it is also marked (and not released). */
	private void sitting(UUID id, Exam exam, UUID student, Integer score, Instant now) {
		Instant started = exam.liveAt();
		Instant deadline = started.plus(Duration.ofMinutes(EXAM_MINUTES));
		Instant submitted = started.plus(Duration.ofMinutes(15));
		String who = student.equals(STUDENT_1) ? "student1" : "student2";
		LocalFileStorage.StoredFile answer = demo.storePdf(exam.titleEn() + " answer " + who + ".pdf",
				List.of("Dev answer script", "Exam: " + exam.titleEn(), "Student: " + who));
		LocalFileStorage.StoredFile marked = score == null ? null
				: demo.storePdf(exam.titleEn() + " marked " + who + ".pdf",
						List.of("Dev marked script", "Exam: " + exam.titleEn(), "Marks: " + score + " / " + WRITTEN_FULL_MARKS));
		jdbc.update(INSERT_SITTING, id, exam.id(), student, ts(started), ts(deadline), ts(submitted), score,
				score == null ? null : score * 100 >= PASS_PERCENT * WRITTEN_FULL_MARKS, score == null ? null : "Good answer; cite more references.",
				score == null ? null : INSTRUCTOR_1, score == null ? null : ts(started.plus(Duration.ofHours(2))), answer.storedFileName(),
				answer.originalFileName(), answer.byteSize(), "application/pdf", marked == null ? null : marked.storedFileName(),
				marked == null ? null : marked.originalFileName(), marked == null ? null : marked.byteSize(),
				marked == null ? null : "application/pdf", ts(now), ts(now));
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private record Exam(int number, String type, UUID module, UUID category, String titleEn, String titleBn, String descriptionEn, String descriptionBn, String mode, Instant liveAt,
			Instant endsAt, boolean free, UUID instructor, List<DemoContentSeed.Question> questions) {

		UUID id() {
			return DemoContentSeed.demo('e', number);
		}
	}

}
