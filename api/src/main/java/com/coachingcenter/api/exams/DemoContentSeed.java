package com.coachingcenter.api.exams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.files.LocalFileStorage;

/**
 * Staging-only demo catalog. Every demo row uses a fixed UUID whose first group starts with a letter or digit that
 * names its table, and the demo namespace is {@code <prefix>0000000-0000-4000-8000-<12 digits>}. The cleanup script
 * deletes by those prefixes, so it removes demo rows and nothing else. Inserts skip rows that exist, so a second run
 * adds nothing. Dates are fixed when the seed runs; re-run after cleanup to refresh them.
 */
@Component
public class DemoContentSeed {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	static final String NO_LOGIN_HASH = "DEMO-NO-LOGIN";

	static final int STUDENTS = 8;

	static final UUID INSTRUCTOR = demo('6', 1);

	static final UUID CATEGORY_BJS = demo('a', 1);

	static final UUID CATEGORY_BAR = demo('a', 2);

	static final UUID CATEGORY_ACADEMIC = demo('a', 3);

	static final UUID MODULE_19 = demo('b', 1);

	static final UUID MODULE_20 = demo('b', 2);

	static final UUID MODULE_ACADEMIC = demo('b', 3);

	private static final Logger log = LoggerFactory.getLogger(DemoContentSeed.class);

	private static final Duration SITTING = Duration.ofMinutes(40);

	private static final String INSERT_USER = """
			INSERT INTO users (id, phone, email, password_hash, role, status, email_verified_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
			""";

	private static final String INSERT_EXAM = """
			INSERT INTO live_exams (id, module_id, exam_type, title_en, title_bn, description_en, description_bn, rules_en,
			  rules_bn, paper_language, live_at, duration_minutes, pass_percent, written_maximum, question_stored_file_name,
			  question_original_file_name, question_byte_size, question_content_type, status, finally_saved, price,
			  created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', true, ?, ?, ?)
			""";

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	private final LocalFileStorage files;

	public DemoContentSeed(JdbcTemplate jdbc, PasswordEncoder passwords, LocalFileStorage files) {
		this.jdbc = jdbc;
		this.passwords = passwords;
		this.files = files;
	}

	/**
	 * Loads the demo content. {@code env} reads the {@code SEED_INSTRUCTOR_*} and {@code SEED_STUDENT_EMAIL} values; no
	 * password or account is written into code.
	 */
	@Transactional
	public void seed(String[] profiles, Function<String, String> env, Instant now) {
		StagingExamSeed.refuseProduction(profiles);
		if (Arrays.stream(profiles).noneMatch(profile -> "staging".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The demo seed runs on the staging or dev profile only.");
		}
		Instructor instructor = instructor(env);
		ensureInstructor(instructor, now);
		ensureStudents(now);
		ensureCatalog(Schedule.at(now), now);
		ensureRoster(env.apply("SEED_STUDENT_EMAIL"), now);
		jdbc.update("""
				INSERT INTO live_exam_instructors (module_id, instructor_id, assigned_at) VALUES (?, ?, ?)
				ON CONFLICT DO NOTHING
				""", MODULE_19, INSTRUCTOR, ts(now));
	}

	private Instructor instructor(Function<String, String> env) {
		String password = StagingExamSeed.required("SEED_INSTRUCTOR_PASSWORD", env.apply("SEED_INSTRUCTOR_PASSWORD"));
		StagingExamSeed.requirePassword("SEED_INSTRUCTOR_PASSWORD", password);
		String phone;
		try {
			phone = PhoneNumbers.normalize(StagingExamSeed.required("SEED_INSTRUCTOR_PHONE", env.apply("SEED_INSTRUCTOR_PHONE")));
		}
		catch (ApiException exception) {
			throw new IllegalStateException("SEED_INSTRUCTOR_PHONE is not a valid phone number.");
		}
		return new Instructor(phone,
				StagingExamSeed.required("SEED_INSTRUCTOR_EMAIL", env.apply("SEED_INSTRUCTOR_EMAIL")).toLowerCase(Locale.ROOT),
				StagingExamSeed.required("SEED_INSTRUCTOR_NAME", env.apply("SEED_INSTRUCTOR_NAME")), password);
	}

	private void ensureInstructor(Instructor instructor, Instant now) {
		Integer taken = jdbc.queryForObject(
				"SELECT count(*) FROM users WHERE phone = ? AND id <> ? AND deleted_at IS NULL", Integer.class,
				instructor.phone(), INSTRUCTOR);
		if (taken != null && taken > 0) {
			throw new IllegalStateException("SEED_INSTRUCTOR_PHONE is already in use.");
		}
		jdbc.update(INSERT_USER + " ON CONFLICT DO NOTHING", INSTRUCTOR, instructor.phone(), instructor.email(),
				passwords.encode(instructor.password()), "INSTRUCTOR", ts(now), ts(now), ts(now));
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?) ON CONFLICT DO NOTHING", INSTRUCTOR,
				instructor.name());
	}

	private void ensureStudents(Instant now) {
		for (int number = 1; number <= STUDENTS; number++) {
			UUID id = demo('5', number);
			String phone = "+880170000010" + number;
			jdbc.update(INSERT_USER + " ON CONFLICT DO NOTHING", id, phone, "demo-student-" + number + "@example.com",
					NO_LOGIN_HASH, "STUDENT", null, ts(now), ts(now));
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
					id, STUDENT_NAMES[number - 1], String.format(Locale.ROOT, "DEMO-STU-%03d", number));
		}
	}

	private void ensureCatalog(Schedule when, Instant now) {
		category(CATEGORY_BJS, null, "BJS Exam", "বিজেএস এক্সাম", 0, now);
		category(CATEGORY_BAR, null, "Bar Council Exam", "বার কাউন্সিল এক্সাম", 1, now);
		category(CATEGORY_ACADEMIC, null, "Academic", "একাডেমিক", 2, now);
		module(MODULE_19, CATEGORY_BJS, "19th BJS", "১৯তম বিজেএস", 2500, 0, now);
		module(MODULE_20, CATEGORY_BJS, "20th BJS", "২০তম বিজেএস", 2000, 1, now);
		module(MODULE_ACADEMIC, CATEGORY_ACADEMIC, "General law practice", "সাধারণ আইন অনুশীলন", null, 0, now);

		exam(new ExamSpec(demo('e', 1), MODULE_19, LiveExam.MCQ, "CrPC", "সিআরপিসি", when.tomorrowNoon(), 40, 40, null,
				null, false, CRPC), 1, now);
		exam(new ExamSpec(demo('e', 2), MODULE_19, LiveExam.MCQ, "Penal Code", "পেনাল কোড", when.yesterdayNoon(), 40, 40,
				null, null, false, PENAL), 2, now);
		exam(new ExamSpec(demo('e', 3), MODULE_19, LiveExam.MCQ, "Evidence Act", "সাক্ষ্য আইন", when.soon(), 40, 50, null,
				null, false, EVIDENCE), 3, now);
		exam(new ExamSpec(demo('e', 4), MODULE_19, LiveExam.WRITTEN, "CPC (written)", "সিপিসি", null, 40, 50, 50, null,
				true, List.of()), 4, now);
		exam(new ExamSpec(demo('e', 5), MODULE_19, LiveExam.WRITTEN, "Evidence (written)", "ইভিডেন্স", when.yesterdayAfternoon(),
				40, 50, 50, null, true, List.of()), 5, now);
		exam(new ExamSpec(demo('e', 6), MODULE_20, LiveExam.MCQ, "20th BJS paper A", "২০তম বিজেএস ক-পত্র", null, 40, 50,
				null, new BigDecimal("300.00"), false, EVIDENCE), 6, now);
		exam(new ExamSpec(demo('e', 7), MODULE_20, LiveExam.MCQ, "20th BJS paper B", "২০তম বিজেএস খ-পত্র", null, 40, 50,
				null, null, false, EVIDENCE), 7, now);
		exam(new ExamSpec(demo('e', 8), MODULE_ACADEMIC, LiveExam.MCQ, "General law quiz", "সাধারণ আইন কুইজ", null, 20, 50,
				null, null, false, GENERAL), 8, now);

		participants(when, now);
		writtenMarks(when, now);
	}

	private void category(UUID id, UUID parent, String titleEn, String titleBn, int position, Instant now) {
		jdbc.update("""
				INSERT INTO live_exam_categories (id, parent_id, title_en, title_bn, position, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", id, parent, titleEn, titleBn, position, ts(now), ts(now));
	}

	private void module(UUID id, UUID category, String titleEn, String titleBn, Integer price, int position, Instant now) {
		jdbc.update("""
				INSERT INTO live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn, access_mode,
				  status, finally_saved, position, price, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', 'PUBLISHED', true, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", id, category, titleEn, titleBn, "Demo module for the client review", "ডেমো মডিউল রিভিউয়ের জন্য",
				position, price == null ? null : new BigDecimal(price).setScale(2), ts(now), ts(now));
	}

	private void exam(ExamSpec spec, int examNo, Instant now) {
		Integer existing = jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id = ?", Integer.class, spec.id());
		if (existing != null && existing > 0) {
			return;
		}
		String storedName = null;
		String originalName = null;
		Long byteSize = null;
		String contentType = null;
		if (spec.questionPdf()) {
			LocalFileStorage.StoredFile stored = storePdf(spec.titleEn() + ".pdf", "Demo question paper: " + spec.titleEn());
			storedName = stored.storedFileName();
			originalName = stored.originalFileName();
			byteSize = stored.byteSize();
			contentType = stored.contentType();
		}
		jdbc.update(INSERT_EXAM, spec.id(), spec.moduleId(), spec.examType(), spec.titleEn(), spec.titleBn(),
				"Demo exam for the client review", "ডেমো পরীক্ষা রিভিউয়ের জন্য", "Demo rules: read every question before you answer.",
				"ডেমো নিয়ম: উত্তর দেওয়ার আগে প্রতিটি প্রশ্ন পড়ুন।", spec.paperLanguage(), spec.liveAt() == null ? null : ts(spec.liveAt()),
				spec.durationMinutes(), spec.passPercent(), spec.writtenMaximum(), storedName, originalName, byteSize,
				contentType, spec.price(), ts(now), ts(now));
		List<Question> questions = spec.questions();
		for (int index = 0; index < questions.size(); index++) {
			question(spec.id(), examNo, index + 1, questions.get(index), now);
		}
	}

	private void question(UUID examId, int examNo, int number, Question question, Instant now) {
		UUID questionId = demo('f', examNo * 100L + number);
		jdbc.update("""
				INSERT INTO live_exam_questions (id, exam_id, stem, marks, explanation, position, created_at, updated_at)
				VALUES (?, ?, ?, 1, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", questionId, examId, question.stem(), question.explanation(), number - 1, ts(now), ts(now));
		for (int option = 0; option < question.options().size(); option++) {
			jdbc.update("""
					INSERT INTO live_exam_options (id, question_id, option_text, correct, position, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
					""", demo('9', (examNo * 100L + number) * 10 + option + 1), questionId, question.options().get(option),
					option == question.correctIndex(), option, ts(now), ts(now));
		}
	}

	private void participants(Schedule when, Instant now) {
		List<Integer> scores = List.of(9, 8, 8, 7, 6, 6, 5, 3);
		Instant start = when.yesterdayNoon();
		for (int number = 1; number <= STUDENTS; number++) {
			int score = scores.get(number - 1);
			insertParticipant(demo('7', 200L + number), demo('e', 2), demo('5', number), start, score,
					score * 100 >= 40 * PENAL.size(), now, start.plus(Duration.ofMinutes(20)));
		}
	}

	/** Two written sittings on the Evidence paper: one marked and published, one marked and still a draft. */
	private void writtenMarks(Schedule when, Instant now) {
		Instant start = when.yesterdayAfternoon();
		String answerFile = jdbc.queryForObject("SELECT question_stored_file_name FROM live_exams WHERE id = ?", String.class,
				demo('e', 5));
		String answerOriginal = jdbc.queryForObject("SELECT question_original_file_name FROM live_exams WHERE id = ?",
				String.class, demo('e', 5));
		Long answerSize = jdbc.queryForObject("SELECT question_byte_size FROM live_exams WHERE id = ?", Long.class, demo('e', 5));
		UUID published = demo('7', 501);
		UUID draft = demo('7', 502);
		insertWritten(published, demo('5', 1), 38, start, now, now, "ভালো উত্তর, তবে রেফারেন্স আরও লিখুন।", answerFile,
				answerOriginal, answerSize);
		insertWritten(draft, demo('5', 2), 30, start, now, null, "আংশিক উত্তর, বিস্তারিত লিখুন।", answerFile, answerOriginal,
				answerSize);
	}

	private void insertWritten(UUID id, UUID student, int score, Instant start, Instant now, Instant publishedAt,
			String comment, String answerFile, String answerOriginal, Long answerSize) {
		jdbc.update("""
				INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, score,
				  passed, status, answer_stored_file_name, answer_original_file_name, answer_byte_size, answer_content_type,
				  comment, evaluated_by, evaluated_at, published_at, created_at, updated_at)
				VALUES (?, ?, ?, 'LIVE', ?, ?, ?, ?, true, 'SUBMITTED', ?, ?, ?, 'application/pdf', ?, ?, ?, ?, ?, ?)
				ON CONFLICT DO NOTHING
				""", id, demo('e', 5), student, ts(start), ts(start.plus(SITTING)), ts(start.plus(Duration.ofMinutes(30))),
				score, answerFile, answerOriginal, answerSize, comment, INSTRUCTOR, ts(now),
				publishedAt == null ? null : ts(publishedAt), ts(now), ts(now));
	}

	private void insertParticipant(UUID id, UUID exam, UUID student, Instant start, int score, boolean passed, Instant now,
			Instant submitted) {
		jdbc.update("""
				INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, score,
				  passed, status, created_at, updated_at)
				VALUES (?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?) ON CONFLICT DO NOTHING
				""", id, exam, student, ts(start), ts(start.plus(SITTING)), ts(submitted), score, passed, ts(now), ts(now));
	}

	private void ensureRoster(String studentEmail, Instant now) {
		if (studentEmail == null || studentEmail.isBlank()) {
			log.warn("SEED_STUDENT_EMAIL is not set; the demo roster has no existing student.");
			return;
		}
		List<UUID> found = jdbc.queryForList(
				"SELECT id FROM users WHERE lower(email) = ? AND role = 'STUDENT' AND deleted_at IS NULL", UUID.class,
				studentEmail.trim().toLowerCase(Locale.ROOT));
		if (found.isEmpty()) {
			log.warn("SEED_STUDENT_EMAIL does not match a student; the demo roster has no existing student.");
			return;
		}
		jdbc.update("INSERT INTO live_exam_roster (module_id, student_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
				MODULE_19, found.get(0), ts(now));
	}

	private LocalFileStorage.StoredFile storePdf(String originalName, String line) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
				content.newLineAtOffset(72, 720);
				content.showText(line);
				content.endText();
			}
			document.save(out);
			byte[] bytes = out.toByteArray();
			return files.save(originalName, "application/pdf", new ByteArrayInputStream(bytes), bytes.length);
		}
		catch (IOException exception) {
			throw new IllegalStateException("The demo PDF could not be written.", exception);
		}
	}

	private static java.sql.Timestamp ts(Instant instant) {
		return java.sql.Timestamp.from(instant);
	}

	/** A deterministic demo id. {@code prefix} names the table; {@code n} is the row number. */
	static UUID demo(char prefix, long n) {
		return UUID.fromString(String.format(Locale.ROOT, "%c0000000-0000-4000-8000-%012d", prefix, n));
	}

	record Instructor(String phone, String email, String name, String password) {
	}

	record Schedule(Instant tomorrowNoon, Instant yesterdayNoon, Instant yesterdayAfternoon, Instant soon) {

		static Schedule at(Instant now) {
			LocalDate today = now.atZone(DHAKA).toLocalDate();
			return new Schedule(today.plusDays(1).atTime(12, 0).atZone(DHAKA).toInstant(),
					today.minusDays(1).atTime(12, 0).atZone(DHAKA).toInstant(),
					today.minusDays(1).atTime(14, 0).atZone(DHAKA).toInstant(), now.plus(Duration.ofMinutes(30)));
		}

	}

	record Question(String stem, String explanation, int correctIndex, List<String> options) {
	}

	record ExamSpec(UUID id, UUID moduleId, String examType, String titleEn, String titleBn, Instant liveAt,
			int durationMinutes, int passPercent, Integer writtenMaximum, BigDecimal price, boolean questionPdf,
			List<Question> questions) {

		String paperLanguage() {
			return "BN";
		}

	}

	private static Question q(String stem, String explanation, int correctIndex, String... options) {
		return new Question(stem, explanation, correctIndex, List.of(options));
	}

	private static final String[] STUDENT_NAMES = { "রফিকুল ইসলাম", "নাজমা আক্তার", "তানভীর হাসান", "সাবরিনা খাতুন",
			"মোস্তাফিজুর রহমান", "ফারজানা ইয়াসমিন", "আরিফ হোসেন", "রুমানা পারভীন" };

	static final List<Question> CRPC = List.of(
			q("গ্রেফতারি পরোয়ানা সাধারণত কে জারি করেন?", "বিচারিক আদেশ হিসেবে পরোয়ানা সাধারণত ম্যাজিস্ট্রেট বা আদালত জারি করেন।", 1,
					"পুলিশের সাধারণ ডায়েরি লেখক", "ম্যাজিস্ট্রেট বা আদালত", "জেলা প্রশাসক", "আইনজীবী"),
			q("পুলিশ সংজ্ঞাযোগ্য অপরাধের তথ্য পেলে কী লিপিবদ্ধ করেন?", "সংজ্ঞাযোগ্য অপরাধের প্রাথমিক তথ্য এজাহার হিসেবে লিপিবদ্ধ হয়।", 1,
					"সমন", "এজাহার (এফআইআর)", "জব্দ তালিকা", "আপিল আবেদন"),
			q("কোন অপরাধে জামিন সাধারণত আইনগত অধিকার হিসেবে দাবি করা যায়?", "জামিনযোগ্য অপরাধে জামিন সাধারণত আইনগত অধিকার।", 1,
					"হত্যা", "জামিনযোগ্য অপরাধ", "রাষ্ট্রদ্রোহ", "মাদক পাচার"),
			q("তদন্তকালে পুলিশের নেওয়া জবানবন্দি আদালতে সাধারণত কীভাবে ব্যবহৃত হয়?",
					"এই জবানবন্দি সাধারণত সাক্ষীর বক্তব্য যাচাই বা বিরোধ দেখাতে ব্যবহৃত হয়।", 1, "মূল সাক্ষ্য",
					"শুধু সাক্ষীর বক্তব্য যাচাইয়ে", "স্বীকারোক্তি", "দলিল"),
			q("সমন ও ওয়ারেন্টের মূল পার্থক্য কী?", "সমন আদালতে হাজির হওয়ার নির্দেশ; ওয়ারেন্ট গ্রেফতার বা হাজির করার বাধ্যতামূলক আদেশ।",
					0, "সমন হাজিরার আহ্বান, ওয়ারেন্ট গ্রেফতারের আদেশ", "দুটোই একই", "সমন শুধু পুলিশ জারি করে",
					"ওয়ারেন্ট শুধু সাক্ষীর জন্য"),
			q("নালিশী মামলা কাকে বলে?", "নালিশী মামলায় বাদী নিজে আদালতে অভিযোগ দাখিল করেন।", 1,
					"রাষ্ট্রের পক্ষে পুলিশ দায়ের করা মামলা", "যে মামলায় বাদী নিজে আদালতে নালিশ করেন",
					"শুধু দেওয়ানি বিষয়ক মামলা", "সরকারি কর্মচারীর বিরুদ্ধে মামলা"),
			q("দায়রা আদালত সাধারণত কোন ধরনের মামলার বিচার করে?", "গুরুতর অপরাধের বিচার সাধারণত দায়রা আদালতে হয়।", 1,
					"সামান্য জরিমানার মামলা", "মৃত্যু বা যাবজ্জীবন দণ্ডযোগ্য অপরাধ", "শুধু চেক প্রতারণা", "পারিবারিক বিরোধ"),
			q("গ্রেফতারকৃত ব্যক্তিকে সাধারণত কত ঘণ্টার মধ্যে ম্যাজিস্ট্রেটের সামনে হাজির করতে হয়?",
					"গ্রেফতারের ২৪ ঘণ্টার মধ্যে ম্যাজিস্ট্রেটের সামনে হাজির করতে হয়।", 1, "১২ ঘণ্টা", "২৪ ঘণ্টা", "৪৮ ঘণ্টা",
					"৭২ ঘণ্টা"),
			q("সাক্ষী হাজির না হলে আদালত সাধারণত কী আদেশ দিতে পারে?", "সাক্ষী উপস্থিত না হলে আদালত বাধ্য করার জন্য ওয়ারেন্ট জারি করতে পারে।",
					0, "সাক্ষীর বিরুদ্ধে ওয়ারেন্ট জারি", "মামলা খারিজ বাধ্যতামূলক", "রাষ্ট্রপতির কাছে আবেদন",
					"সাক্ষী ছাড়া রায় বাধ্যতামূলক"),
			q("ফৌজদারি মামলায় 'নির্দোষ অনুমান' বলতে কী বোঝায়?",
					"ফৌজদারি মামলায় অভিযুক্ত দোষী প্রমাণিত না হওয়া পর্যন্ত নির্দোষ গণ্য হয়।", 1, "প্রমাণের ভার অভিযোগকারীর",
					"নির্দোষ অনুমান", "দ্বৈত বিপদ", "স্বাভাবিক ন্যায়বিচার"));

	static final List<Question> PENAL = List.of(
			q("কোনো ব্যক্তি ইচ্ছাকৃতভাবে অন্যের মৃত্যু ঘটালে সাধারণত কী অপরাধ হয়?", "ইচ্ছাকৃত মৃত্যু ঘটানো দণ্ডবিধির আওতায় খুনের অপরাধ।",
					1, "চুরি", "খুন", "প্রতারণা", "মানহানি"),
			q("অন্যের সম্পত্তি তার অনুমতি ছাড়া অসাধু উদ্দেশ্যে সরানোকে কী বলে?", "অসাধু উদ্দেশ্যে অন্যের সম্পত্তি স্থানান্তরে চুরি হয়।", 0,
					"চুরি", "ছিনতাই", "আত্মসাৎ", "জালিয়াতি"),
			q("প্রতারণার মূল উপাদান কোনটি?", "প্রতারণায় ভুল ধারণা সৃষ্টি করে সম্পত্তি বা সুবিধা আদায় করা হয়।", 1,
					"সম্মতি ছাড়া শারীরিক আঘাত", "মিথ্যা উপস্থাপনের মাধ্যমে কাউকে বিভ্রান্ত করে সুবিধা নেওয়া", "কাজ না করা",
					"লিখিত চুক্তি না থাকা"),
			q("আমানতের সম্পত্তি অসাধুভাবে নিজের কাজে লাগালে কোন অপরাধ?", "বিশ্বাস রেখে পাওয়া সম্পত্তির অসাধু ব্যবহার বিশ্বাসভঙ্গ।", 0,
					"বিশ্বাসভঙ্গ", "ষড়যন্ত্র", "ভয়প্রদর্শন", "দাঙ্গা"),
			q("কথা বা লেখায় কাউকে সম্মানহানি করার উদ্দেশ্যে অভিযোগ করাকে কী বলে?", "সম্মানহানিকর বক্তব্য বা প্রকাশনাকে মানহানি বলা হয়।", 0,
					"মানহানি", "জবরদখল", "গণ-উৎপাত", "অপহরণ"),
			q("অবৈধ কাজের জন্য দুই বা ততোধিক ব্যক্তির চুক্তিকে কী বলে?", "অবৈধ কাজের চুক্তিতে ষড়যন্ত্র গড়ে ওঠে, কাজ না ঘটলেও অপরাধ।", 0,
					"অপরাধমূলক ষড়যন্ত্র", "সাধারণ চুক্তি", "অংশীদারিত্ব", "সহায়তা"),
			q("কাউকে আত্মহত্যায় প্ররোচিত করা কী?", "আত্মহত্যায় প্ররোচনা দণ্ডনীয় অপরাধ।", 1, "নিষিদ্ধ নয়", "অপরাধ",
					"শুধু নৈতিক দোষ", "দেওয়ানি বিষয়"),
			q("কাউকে ক্ষতির ভয় দেখিয়ে কিছু করতে বাধ্য করা কোন অপরাধ?", "ক্ষতির ভয় দেখিয়ে বাধ্য করা ভয়প্রদর্শন।", 0,
					"ভয়প্রদর্শন", "সাধারণ উপহাস", "দাবি জানানো", "আবেদন"),
			q("অপরাধে সহায়তা বা উৎসাহ দেওয়াকে কী বলে?", "অপরাধে প্ররোচনা বা সহায়তা দেওয়াও দণ্ডনীয়।", 0, "প্ররোচনা (আবেটমেন্ট)",
					"সাক্ষ্য গোপন", "জামিন", "আপস"),
			q("অপরাধ করার চেষ্টা সম্পর্কে সাধারণ নিয়ম কী?", "অপরাধের চেষ্টাও সাধারণত আলাদাভাবে দণ্ডনীয়।", 0,
					"অপরাধ করার চেষ্টাও দণ্ডনীয় হতে পারে", "চেষ্টা করলে কোনো শাস্তি নেই", "শুধু সফল অপরাধ দণ্ডনীয়",
					"চেষ্টা শুধু দেওয়ানি বিষয়"));

	static final List<Question> EVIDENCE = List.of(
			q("পুলিশ হেফাজতে দেওয়া স্বীকারোক্তি সাধারণত কীভাবে গণ্য হয়?",
					"সাক্ষ্য আইনের বিধান অনুযায়ী পুলিশের কাছে স্বীকারোক্তি সাধারণত দোষ প্রমাণে ব্যবহার করা যায় না।", 1,
					"দোষ স্বীকার হিসেবে সরাসরি গ্রাহ্য", "সাধারণত আদালতে দোষ স্বীকার হিসেবে গ্রাহ্য নয়", "সর্বদা প্রমাণ",
					"শুধু জামিনে"),
			q("দলিলের বিষয়বস্তু প্রমাণে প্রাথমিক সাক্ষ্য কী?", "মূল দলিলই সর্বোত্তম সাক্ষ্য হিসেবে গণ্য হয়।", 0, "মূল দলিল",
					"প্রতিবেশীর মৌখিক বর্ণনা", "অনুমান", "শুধু ফটোকপি"),
			q("শোনা কথার সাক্ষ্য (হিয়ারসে) সাধারণত কেমন?", "শোনা কথা সাধারণত সীমিত গ্রহণযোগ্যতার, আইনে কিছু ব্যতিক্রম রয়েছে।", 1,
					"সবসময় গ্রাহ্য", "সাধারণত গ্রাহ্য নয়, কিছু ব্যতিক্রম আছে", "শুধু দেওয়ানি মামলায় গ্রাহ্য",
					"স্বীকারোক্তির চেয়ে বেশি শক্তিশালী"),
			q("সাক্ষ্যের জন্য আইনত কত সংখ্যক সাক্ষী বাধ্যতামূলক?", "সাধারণত এক সাক্ষীর সাক্ষ্যও প্রমাণ হিসেবে গ্রহণযোগ্য হতে পারে।", 1,
					"কমপক্ষে দুজন", "কোনো নির্দিষ্ট সংখ্যা বাধ্যতামূলক নয়", "পাঁচজন", "সবাই আদালতের কর্মচারী"),
			q("নির্দিষ্ট বিষয়ে বিশেষজ্ঞের মতামত কী হিসেবে বিবেচিত?", "নির্দিষ্ট বিষয়ে বিশেষজ্ঞের মতামত সাক্ষ্য হিসেবে গ্রাহ্য।", 1,
					"সাধারণ ধারণা", "বিশেষজ্ঞ সাক্ষ্য", "আদালতের রায়", "স্বীকারোক্তি"));

	static final List<Question> GENERAL = List.of(
			q("বাংলাদেশের সংবিধান কত সালে গৃহীত হয়?", "বাংলাদেশের সংবিধান ১৯৭২ সালে গৃহীত হয়।", 1, "১৯৭১", "১৯৭২", "১৯৭৫", "১৯৭৮"),
			q("বাংলাদেশের সুপ্রিম কোর্ট কয়টি বিভাগে বিভক্ত?", "আপিল বিভাগ ও হাইকোর্ট বিভাগ।", 1, "এক", "দুই", "তিন", "চার"),
			q("রাষ্ট্রপতি কত বছরের মেয়াদে নির্বাচিত হন?", "রাষ্ট্রপতির মেয়াদ সাধারণত পাঁচ বছর।", 1, "চার", "পাঁচ", "ছয়", "সাত"),
			q("মৌলিক অধিকার সংবিধানের কোন ভাগে বর্ণিত?", "মৌলিক অধিকার সংবিধানের তৃতীয় ভাগে বর্ণিত।", 2, "প্রথম ভাগ", "দ্বিতীয় ভাগ",
					"তৃতীয় ভাগ", "চতুর্থ ভাগ"),
			q("জাতীয় সংসদের সাধারণ মেয়াদ কত বছর?", "সংসদের সাধারণ মেয়াদ পাঁচ বছর, যদি আগে ভেঙে না যায়।", 2, "তিন", "চার", "পাঁচ",
					"ছয়"));

}
