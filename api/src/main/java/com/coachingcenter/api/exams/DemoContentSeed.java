package com.coachingcenter.api.exams;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.files.LocalFileStorage;

/**
 * The demo catalog for the client demo and QA: three packages (MCQ only, written only, both), fourteen exams, login students
 * 5101 to 5105, orders and grants, sittings, notices, one announcement, and the staff accounts. Staging and dev only.
 *
 * <p>Every demo row has a fixed UUID whose first group names its table: {@code a} categories, {@code b} modules, {@code e}
 * exams, {@code f} questions, {@code 9} options, {@code 7} sittings, {@code 5} students, {@code 6} instructors, {@code 4} the
 * System Admin, {@code c} orders, {@code d} grants, {@code 8} the announcement. The cleanup script deletes by those prefixes.
 *
 * <p>A run refreshes the schedule: exam dates, sittings, orders and the notices are set relative to the run time, so the
 * live exam {@code e2} is live at every run. The catalog rows are inserted once; a run over an older catalog is refused, so
 * the cleanup runs first. Copy in this file is a draft for the client's content review (decision D-d10).
 */
@Component
public class DemoContentSeed {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	static final String NO_LOGIN_HASH = "DEMO-NO-LOGIN";

	static final int STUDENTS = 8;

	static final int MCQ_MARKS = 5;

	static final int MCQ_FULL_MARKS = 50;

	static final int WRITTEN_FULL_MARKS = 50;

	static final int PASS_PERCENT = 40;

	/** The demo papers are a few kilobytes; the app-wide material limit is set separately and can be smaller. */
	private static final long DEMO_PDF_MAX_BYTES = 1024L * 1024L;

	static final UUID SYSTEM_ADMIN = demo('4', 1);

	static final UUID INSTRUCTOR = demo('6', 1);

	static final UUID INSTRUCTOR_B = demo('6', 2);

	static final UUID CATEGORY_ROOT = demo('a', 1);

	static final UUID CATEGORY_MCQ = demo('a', 2);

	static final UUID CATEGORY_WRITTEN = demo('a', 3);

	static final UUID CATEGORY_FULL = demo('a', 4);

	static final UUID CATEGORY_BAR = demo('a', 5);

	/** b1: five MCQ exams. */
	static final UUID MODULE_MCQ = demo('b', 1);

	/** b2: four written exams. */
	static final UUID MODULE_WRITTEN = demo('b', 2);

	/** b3: three MCQ and two written exams. */
	static final UUID MODULE_FULL = demo('b', 3);

	static final UUID ANNOUNCEMENT = demo('8', 1);

	static final UUID OWNER_PACKAGE = demo('5', 101);

	static final UUID OWNER_EXAM = demo('5', 102);

	static final UUID LIVE_STUDENT = demo('5', 103);

	static final UUID NO_PHONE = demo('5', 104);

	static final UUID REMINDER_STUDENT = demo('5', 105);

	private static final Duration SITTING = Duration.ofMinutes(180);

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

	private static final String UPSERT_SITTING = """
			INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, score,
			  passed, status, comment, evaluated_by, evaluated_at, published_at, answer_stored_file_name,
			  answer_original_file_name, answer_byte_size, answer_content_type, evaluated_stored_file_name,
			  evaluated_original_file_name, evaluated_byte_size, evaluated_content_type, created_at, updated_at)
			VALUES (?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET exam_id = EXCLUDED.exam_id, student_id = EXCLUDED.student_id,
			  started_at = EXCLUDED.started_at, deadline_at = EXCLUDED.deadline_at, submitted_at = EXCLUDED.submitted_at,
			  score = EXCLUDED.score, passed = EXCLUDED.passed, status = EXCLUDED.status, comment = EXCLUDED.comment,
			  evaluated_by = EXCLUDED.evaluated_by, evaluated_at = EXCLUDED.evaluated_at, published_at = EXCLUDED.published_at,
			  updated_at = EXCLUDED.updated_at, deleted_at = NULL
			""";

	private static final String UPSERT_ORDER = """
			INSERT INTO live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id, status,
			  val_id, payment_channel, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, 'BDT', 'SSLCOMMERZ', ?, ?, ?, ?, ?, ?)
			ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, amount = EXCLUDED.amount, val_id = EXCLUDED.val_id,
			  payment_channel = EXCLUDED.payment_channel, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at
			""";

	private static final String ACCESS_STUDENTS = """
			SELECT u.id FROM users u
			WHERE u.deleted_at IS NULL AND u.role = 'STUDENT' AND u.status = 'ACTIVE' AND (
			  EXISTS (SELECT 1 FROM live_exam_roster r WHERE r.module_id = ? AND r.student_id = u.id)
			  OR EXISTS (SELECT 1 FROM live_exam_grants g
			    WHERE g.scope = 'MODULE' AND g.module_id = ? AND g.student_id = u.id AND g.revoked_at IS NULL)
			  OR EXISTS (SELECT 1 FROM live_exam_grants g
			    WHERE g.scope = 'EXAM' AND g.exam_id = ? AND g.student_id = u.id AND g.revoked_at IS NULL))
			""";

	private static final String INSERT_ANNOUNCEMENT_NOTICE = """
			INSERT INTO student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at,
			  announcement_id)
			SELECT ?, u.id, 'ANNOUNCEMENT', CAST(u.id AS VARCHAR) || ':' || CAST(? AS VARCHAR), NULL, NULL, NULL, ?, ?
			FROM users u
			WHERE u.id = ? AND u.role = 'STUDENT' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
			ON CONFLICT (kind, message_key) DO NOTHING
			""";

	private static final String INSERT_QUESTION = """
			INSERT INTO live_exam_questions (id, exam_id, stem, marks, explanation, position, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
			""";

	private static final String INSERT_OPTION = """
			INSERT INTO live_exam_options (id, question_id, option_text, correct, position, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
			""";

	private static final List<Question> CRPC = List.of(
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
					"রাষ্ট্রের পক্ষে পুলিশ দায়ের করা মামলা", "যে মামলায় বাদী নিজে আদালতে নালিশ করেন", "শুধু দেওয়ানি বিষয়ক মামলা",
					"সরকারি কর্মচারীর বিরুদ্ধে মামলা"),
			q("দায়রা আদালত সাধারণত কোন ধরনের মামলার বিচার করে?", "গুরুতর অপরাধের বিচার সাধারণত দায়রা আদালতে হয়।", 1,
					"সামান্য জরিমানার মামলা", "মৃত্যু বা যাবজ্জীবন দণ্ডযোগ্য অপরাধ", "শুধু চেক প্রতারণা", "পারিবারিক বিরোধ"),
			q("গ্রেফতারকৃত ব্যক্তিকে সাধারণত কত ঘণ্টার মধ্যে ম্যাজিস্ট্রেটের সামনে হাজির করতে হয়?",
					"গ্রেফতারের ২৪ ঘণ্টার মধ্যে ম্যাজিস্ট্রেটের সামনে হাজির করতে হয়।", 1, "১২ ঘণ্টা", "২৪ ঘণ্টা", "৪৮ ঘণ্টা", "৭২ ঘণ্টা"),
			q("সাক্ষী হাজির না হলে আদালত সাধারণত কী আদেশ দিতে পারে?", "সাক্ষী উপস্থিত না হলে আদালত বাধ্য করার জন্য ওয়ারেন্ট জারি করতে পারে।",
					0, "সাক্ষীর বিরুদ্ধে ওয়ারেন্ট জারি", "মামলা খারিজ বাধ্যতামূলক", "রাষ্ট্রপতির কাছে আবেদন",
					"সাক্ষী ছাড়া রায় বাধ্যতামূলক"),
			q("ফৌজদারি মামলায় 'নির্দোষ অনুমান' বলতে কী বোঝায়?",
					"ফৌজদারি মামলায় অভিযুক্ত দোষী প্রমাণিত না হওয়া পর্যন্ত নির্দোষ গণ্য হয়।", 1, "প্রমাণের ভার অভিযোগকারীর",
					"নির্দোষ অনুমান", "দ্বৈত বিপদ", "স্বাভাবিক ন্যায়বিচার"));

	private static final List<Question> PENAL = List.of(
			q("কোনো ব্যক্তি ইচ্ছাকৃতভাবে অন্যের মৃত্যু ঘটালে সাধারণত কী অপরাধ হয়?", "ইচ্ছাকৃত মৃত্যু ঘটানো দণ্ডবিধির আওতায় খুনের অপরাধ।", 1,
					"চুরি", "খুন", "প্রতারণা", "মানহানি"),
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
			q("কাউকে আত্মহত্যায় প্ররোচিত করা কী?", "আত্মহত্যায় প্ররোচনা দণ্ডনীয় অপরাধ।", 1, "নিষিদ্ধ নয়", "অপরাধ", "শুধু নৈতিক দোষ",
					"দেওয়ানি বিষয়"),
			q("কাউকে ক্ষতির ভয় দেখিয়ে কিছু করতে বাধ্য করা কোন অপরাধ?", "ক্ষতির ভয় দেখিয়ে বাধ্য করা ভয়প্রদর্শন।", 0, "ভয়প্রদর্শন",
					"সাধারণ উপহাস", "দাবি জানানো", "আবেদন"),
			q("অপরাধে সহায়তা বা উৎসাহ দেওয়াকে কী বলে?", "অপরাধে প্ররোচনা বা সহায়তা দেওয়াও দণ্ডনীয়।", 0, "প্ররোচনা (আবেটমেন্ট)",
					"সাক্ষ্য গোপন", "জামিন", "আপস"),
			q("অপরাধ করার চেষ্টা সম্পর্কে সাধারণ নিয়ম কী?", "অপরাধের চেষ্টাও সাধারণত আলাদাভাবে দণ্ডনীয়।", 0,
					"অপরাধ করার চেষ্টাও দণ্ডনীয় হতে পারে", "চেষ্টা করলে কোনো শাস্তি নেই", "শুধু সফল অপরাধ দণ্ডনীয়",
					"চেষ্টা শুধু দেওয়ানি বিষয়"));

	private static final List<Question> EVIDENCE = List.of(
			q("পুলিশ হেফাজতে দেওয়া স্বীকারোক্তি সাধারণত কীভাবে গণ্য হয়?",
					"সাক্ষ্য আইনের বিধান অনুযায়ী পুলিশের কাছে স্বীকারোক্তি সাধারণত দোষ প্রমাণে ব্যবহার করা যায় না।", 1,
					"দোষ স্বীকার হিসেবে সরাসরি গ্রাহ্য", "সাধারণত আদালতে দোষ স্বীকার হিসেবে গ্রাহ্য নয়", "সর্বদা প্রমাণ", "শুধু জামিনে"),
			q("দলিলের বিষয়বস্তু প্রমাণে প্রাথমিক সাক্ষ্য কী?", "মূল দলিলই সর্বোত্তম সাক্ষ্য হিসেবে গণ্য হয়।", 0, "মূল দলিল",
					"প্রতিবেশীর মৌখিক বর্ণনা", "অনুমান", "শুধু ফটোকপি"),
			q("শোনা কথার সাক্ষ্য (হিয়ারসে) সাধারণত কেমন?", "শোনা কথা সাধারণত সীমিত গ্রহণযোগ্যতার, আইনে কিছু ব্যতিক্রম রয়েছে।", 1,
					"সবসময় গ্রাহ্য", "সাধারণত গ্রাহ্য নয়, কিছু ব্যতিক্রম আছে", "শুধু দেওয়ানি মামলায় গ্রাহ্য",
					"স্বীকারোক্তির চেয়ে বেশি শক্তিশালী"),
			q("সাক্ষ্যের জন্য আইনত কত সংখ্যক সাক্ষী বাধ্যতামূলক?", "সাধারণত এক সাক্ষীর সাক্ষ্যও প্রমাণ হিসেবে গ্রহণযোগ্য হতে পারে।", 1,
					"কমপক্ষে দুজন", "কোনো নির্দিষ্ট সংখ্যা বাধ্যতামূলক নয়", "পাঁচজন", "সবাই আদালতের কর্মচারী"),
			q("নির্দিষ্ট বিষয়ে বিশেষজ্ঞের মতামত কী হিসেবে বিবেচিত?", "নির্দিষ্ট বিষয়ে বিশেষজ্ঞের মতামত সাক্ষ্য হিসেবে গ্রাহ্য।", 1,
					"সাধারণ ধারণা", "বিশেষজ্ঞ সাক্ষ্য", "আদালতের রায়", "স্বীকারোক্তি"));

	private static final List<Question> GENERAL = List.of(
			q("বাংলাদেশের সংবিধান কত সালে গৃহীত হয়?", "বাংলাদেশের সংবিধান ১৯৭২ সালে গৃহীত হয়।", 1, "১৯৭১", "১৯৭২", "১৯৭৫", "১৯৭৮"),
			q("বাংলাদেশের সুপ্রিম কোর্ট কয়টি বিভাগে বিভক্ত?", "আপিল বিভাগ ও হাইকোর্ট বিভাগ।", 1, "এক", "দুই", "তিন", "চার"),
			q("রাষ্ট্রপতি কত বছরের মেয়াদে নির্বাচিত হন?", "রাষ্ট্রপতির মেয়াদ সাধারণত পাঁচ বছর।", 1, "চার", "পাঁচ", "ছয়", "সাত"),
			q("মৌলিক অধিকার সংবিধানের কোন ভাগে বর্ণিত?", "মৌলিক অধিকার সংবিধানের তৃতীয় ভাগে বর্ণিত।", 2, "প্রথম ভাগ", "দ্বিতীয় ভাগ",
					"তৃতীয় ভাগ", "চতুর্থ ভাগ"),
			q("জাতীয় সংসদের সাধারণ মেয়াদ কত বছর?", "সংসদের সাধারণ মেয়াদ পাঁচ বছর, যদি আগে ভেঙে না যায়।", 2, "তিন", "চার", "পাঁচ", "ছয়"));

	private static final String[] STUDENT_NAMES = { "রফিকুল ইসলাম", "নাজমা আক্তার", "তানভীর হাসান", "সাবরিনা খাতুন",
			"মোস্তাফিজুর রহমান", "ফারজানা ইয়াসমিন", "আরিফ হোসেন", "রুমানা পারভীন" };

	/** Login students. 5104 has no phone, so the phone gate shows on every buy button. */
	static final List<LoginStudent> LOGIN_STUDENTS = List.of(
			new LoginStudent(OWNER_PACKAGE, "+8801700005101", "demo-owner-package@example.com", "সুমন আহমেদ", "DEMO-STU-101"),
			new LoginStudent(OWNER_EXAM, "+8801700005102", "demo-owner-exam@example.com", "রিনা বেগম", "DEMO-STU-102"),
			new LoginStudent(LIVE_STUDENT, "+8801700005103", "demo-live@example.com", "করিম উদ্দিন", "DEMO-STU-103"),
			new LoginStudent(NO_PHONE, null, "demo-no-phone@example.com", "সাদিয়া রহমান", "DEMO-STU-104"),
			new LoginStudent(REMINDER_STUDENT, "+8801700005105", "demo-reminder@example.com", "ফয়সাল মাহমুদ", "DEMO-STU-105"));

	private static final String BN_MCQ_RULES = "১. উত্তর দেওয়ার আগে প্রতিটি প্রশ্ন পড়ুন।\n২. সময়সীমা %s মিনিট; পরীক্ষা একই সময় পর্যন্ত খোলা থাকে।\n"
			+ "৩. সময় শেষ হলে উত্তর নিজে থেকে জমা হয় এবং জমার পর বদলানো যায় না।";

	private static final String EN_MCQ_RULES = "1. Read every question before you answer.\n2. The time limit is %d minutes, and the paper stays open for the same time.\n"
			+ "3. Answers are submitted automatically when the time ends, and cannot be changed after you submit.";

	private static final String BN_WRITTEN_RULES = "১. উত্তর কাগজে লিখে একটি PDF ফাইলে স্ক্যান করে আপলোড করুন।\n"
			+ "২. সময়সীমার আগে যতবার খুশি আপলোড করা যায়; শেষেরটি গণ্য হবে।\n"
			+ "৩. সময়সীমার পরে আপলোড নেওয়া হবে না। আপলোড না থাকলে পরীক্ষা 'শেষ করা যায়নি' হিসেবে গণ্য হবে।";

	private static final String EN_WRITTEN_RULES = "1. Write your answers on paper, scan them as one PDF and upload it.\n"
			+ "2. You may upload again before the deadline; the last upload counts.\n"
			+ "3. Uploads after the deadline are refused. With no upload, the paper counts as not finished.";

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	private final LocalFileStorage files;

	private final StudentNoticeWriter notices;

	public DemoContentSeed(JdbcTemplate jdbc, PasswordEncoder passwords, LocalFileStorage files, StudentNoticeWriter notices) {
		this.jdbc = jdbc;
		this.passwords = passwords;
		this.files = files;
		this.notices = notices;
	}

	/**
	 * Loads the demo content and refreshes its schedule. {@code env} reads the {@code SEED_*} values; no password or account is
	 * written into code. The seed writes no mail rows.
	 */
	@Transactional
	public void seed(String[] profiles, Function<String, String> env, Instant now) {
		refuseProduction(profiles);
		if (Arrays.stream(profiles).noneMatch(profile -> "staging".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The demo seed runs on the staging or dev profile only.");
		}
		Account admin = account(env, "SEED_SYSTEM_ADMIN", SYSTEM_ADMIN, "SYSTEM_ADMIN");
		Account instructor = account(env, "SEED_INSTRUCTOR", INSTRUCTOR, "INSTRUCTOR");
		Account instructorB = optionalAccount(env, "SEED_INSTRUCTOR2", INSTRUCTOR_B, "INSTRUCTOR");
		String studentPassword = required("SEED_TEST_STUDENT_PASSWORD", env.apply("SEED_TEST_STUDENT_PASSWORD"));
		requirePassword("SEED_TEST_STUDENT_PASSWORD", studentPassword);
		UUID masterAdmin = masterAdmin();
		Schedule when = Schedule.at(now);
		List<ExamSpec> exams = exams(when);

		ensureUser(admin, now);
		ensureUser(instructor, now);
		if (instructorB != null) {
			ensureUser(instructorB, now);
		}
		ensureStudents(studentPassword, now);
		ensureCatalog(exams, now);
		refreshSchedule(exams, now);
		sittings(exams, now);
		orders(now);
		assignments(instructorB != null, now);
		roster(env.apply("SEED_STUDENT_EMAIL"), now);
		notices(exams, masterAdmin, now);
	}

	static void refuseProduction(String[] profiles) {
		if (Arrays.stream(profiles).anyMatch(profile -> "prod".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The demo seed cannot run on the prod profile.");
		}
	}

	static String required(String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is required.");
		}
		return value.trim();
	}

	static void requirePassword(String name, String password) {
		if (password.length() < 8 || password.length() > 72) {
			throw new IllegalStateException(name + " must be 8 to 72 characters.");
		}
	}

	private Account account(Function<String, String> env, String prefix, UUID id, String role) {
		String phoneName = prefix + "_PHONE";
		String passwordName = prefix + "_PASSWORD";
		String password = required(passwordName, env.apply(passwordName));
		requirePassword(passwordName, password);
		String phone;
		try {
			phone = PhoneNumbers.normalize(required(phoneName, env.apply(phoneName)));
		}
		catch (ApiException exception) {
			throw new IllegalStateException(phoneName + " is not a valid phone number.");
		}
		return new Account(id, phone, required(prefix + "_EMAIL", env.apply(prefix + "_EMAIL")).toLowerCase(Locale.ROOT),
				required(prefix + "_NAME", env.apply(prefix + "_NAME")), password, role);
	}

	/** A second instructor is optional: no phone set means no account. */
	private Account optionalAccount(Function<String, String> env, String prefix, UUID id, String role) {
		String phone = env.apply(prefix + "_PHONE");
		if (phone == null || phone.isBlank()) {
			return null;
		}
		return account(env, prefix, id, role);
	}

	private UUID masterAdmin() {
		List<UUID> found = jdbc.queryForList(
				"SELECT id FROM users WHERE role = 'MASTER_ADMIN' AND deleted_at IS NULL ORDER BY created_at LIMIT 1", UUID.class);
		if (found.isEmpty()) {
			throw new IllegalStateException("The Master Admin must exist before the demo seed runs (MASTER_ADMIN_*).");
		}
		return found.get(0);
	}

	private void ensureUser(Account account, Instant now) {
		Integer taken = jdbc.queryForObject("SELECT count(*) FROM users WHERE phone = ? AND id <> ? AND deleted_at IS NULL",
				Integer.class, account.phone(), account.id());
		if (taken != null && taken > 0) {
			throw new IllegalStateException("A seed phone is already in use by another account.");
		}
		jdbc.update(INSERT_USER + " ON CONFLICT DO NOTHING", account.id(), account.phone(), account.email(),
				passwords.encode(account.password()), account.role(), ts(now), ts(now), ts(now));
		jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?) ON CONFLICT DO NOTHING", account.id(),
				account.name());
	}

	private void ensureStudents(String studentPassword, Instant now) {
		for (int number = 1; number <= STUDENTS; number++) {
			UUID id = demo('5', number);
			jdbc.update(INSERT_USER + " ON CONFLICT DO NOTHING", id, "+880170000010" + number,
					"demo-student-" + number + "@example.com", NO_LOGIN_HASH, "STUDENT", null, ts(now), ts(now));
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
					id, STUDENT_NAMES[number - 1], String.format(Locale.ROOT, "DEMO-STU-%03d", number));
		}
		String hash = passwords.encode(studentPassword);
		for (LoginStudent student : LOGIN_STUDENTS) {
			jdbc.update(INSERT_USER + " ON CONFLICT DO NOTHING", student.id(), student.phone(), student.email(), hash, "STUDENT",
					ts(now), ts(now), ts(now));
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
					student.id(), student.name(), student.code());
		}
	}

	private void ensureCatalog(List<ExamSpec> exams, Instant now) {
		category(CATEGORY_ROOT, null, "BJS Exam", "বিজেএস এক্সাম", 0, now);
		category(CATEGORY_MCQ, CATEGORY_ROOT, "Preliminary MCQ", "প্রিলিমিনারি (এমসিকিউ)", 0, now);
		category(CATEGORY_WRITTEN, CATEGORY_ROOT, "Written", "রিটেন", 1, now);
		category(CATEGORY_FULL, CATEGORY_ROOT, "19th BJS full", "১৯তম বিজেএস (পূর্ণ)", 2, now);
		category(CATEGORY_BAR, null, "Bar Council Exam", "বার কাউন্সিল এক্সাম", 1, now);
		module(MODULE_MCQ, CATEGORY_MCQ, "BJS Preliminary MCQ Package", "বিজেএস প্রিলিমিনারি এমসিকিউ প্যাকেজ",
				"Five MCQ papers for the preliminary round: civil and criminal procedure, penal law, evidence and the constitution. "
						+ "Each paper is one timed sitting.",
				"প্রিলিমিনারি পর্যায়ের পাঁচটি এমসিকিউ পরীক্ষা: দেওয়ানি ও ফৌজদারি কার্যবিধি, দণ্ডবিধি, সাক্ষ্য আইন ও সংবিধান। "
						+ "প্রতিটি পরীক্ষা একটি সময়সীমার মধ্যে দেওয়া হয়।",
				1500, 0, now);
		module(MODULE_WRITTEN, CATEGORY_WRITTEN, "BJS Written Package", "বিজেএস রিটেন প্যাকেজ",
				"Four written papers on civil procedure, criminal procedure, penal law and the constitution. Write on paper and upload "
						+ "one PDF before the deadline; marks and comments appear after marking.",
				"দেওয়ানি ও ফৌজদারি কার্যবিধি, দণ্ডবিধি এবং সংবিধানের চারটি রিটেন পরীক্ষা। কাগজে লিখে সময়সীমার আগে একটি PDF আপলোড করুন; "
						+ "নম্বর ও মন্তব্য মূল্যায়নের পরে দেখা যাবে।",
				1800, 0, now);
		module(MODULE_FULL, CATEGORY_FULL, "19th BJS Full Package", "১৯তম বিজেএস পূর্ণ প্যাকেজ",
				"Three MCQ papers and two written papers for the 19th BJS: penal law, evidence, civil and criminal procedure. Buy the "
						+ "package once and sit every paper in it.",
				"১৯তম বিজেএসের তিনটি এমসিকিউ ও দুটি রিটেন পরীক্ষা: দণ্ডবিধি, সাক্ষ্য আইন, দেওয়ানি ও ফৌজদারি কার্যবিধি। "
						+ "পুরো প্যাকেজ একবার কিনলে এর সব পরীক্ষা দেওয়া যাবে।",
				2500, 1, now);
		for (ExamSpec spec : exams) {
			exam(spec, now);
		}
	}

	private void category(UUID id, UUID parent, String titleEn, String titleBn, int position, Instant now) {
		claim("live_exam_categories", id, titleEn);
		jdbc.update("""
				INSERT INTO live_exam_categories (id, parent_id, title_en, title_bn, position, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", id, parent, titleEn, titleBn, position, ts(now), ts(now));
	}

	private void module(UUID id, UUID category, String titleEn, String titleBn, String descEn, String descBn, int price,
			int position, Instant now) {
		claim("live_exam_modules", id, titleEn);
		jdbc.update("""
				INSERT INTO live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn, access_mode,
				  status, finally_saved, position, price, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', 'PUBLISHED', true, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", id, category, titleEn, titleBn, descEn, descBn, position, new BigDecimal(price).setScale(2), ts(now), ts(now));
	}

	private void exam(ExamSpec spec, Instant now) {
		claim("live_exams", spec.id(), spec.titleEn());
		Integer existing = jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id = ?", Integer.class, spec.id());
		if (existing != null && existing > 0) {
			return;
		}
		boolean written = LiveExam.WRITTEN.equals(spec.type());
		String storedName = null;
		String originalName = null;
		Long byteSize = null;
		String contentType = null;
		if (spec.questionPdf()) {
			LocalFileStorage.StoredFile stored = storePdf(spec.titleEn() + ".pdf", List.of("Demo question paper: " + spec.titleEn(),
					"Duration: " + spec.minutes() + " minutes; full marks: " + WRITTEN_FULL_MARKS,
					"Instructions: answer every question on paper and upload one PDF.", "1. Explain the main procedure for this topic.",
					"2. Describe one case where the rule applies, and one where it does not.", "3. Write short notes on two key terms."));
			storedName = stored.storedFileName();
			originalName = stored.originalFileName();
			byteSize = stored.byteSize();
			contentType = stored.contentType();
		}
		jdbc.update(INSERT_EXAM, spec.id(), spec.module(), spec.type(), spec.titleEn(), spec.titleBn(), spec.descEn(), spec.descBn(),
				spec.rulesEn(), spec.rulesBn(), "BN", ts(spec.liveAt()), spec.minutes(), PASS_PERCENT,
				written ? WRITTEN_FULL_MARKS : null, storedName, originalName, byteSize, contentType, spec.price(), ts(now), ts(now));
		int marks = written ? 1 : MCQ_MARKS;
		List<Question> questions = spec.questions();
		for (int index = 0; index < questions.size(); index++) {
			question(spec.number(), spec.id(), index + 1, questions.get(index), marks, now);
		}
	}

	private void question(int examNo, UUID examId, int number, Question question, int marks, Instant now) {
		UUID questionId = demo('f', examNo * 100L + number);
		jdbc.update(INSERT_QUESTION, questionId, examId, question.stem(), marks, question.explanation(), number - 1, ts(now), ts(now));
		for (int option = 0; option < question.options().size(); option++) {
			jdbc.update(INSERT_OPTION, demo('9', (examNo * 100L + number) * 10 + option + 1), questionId,
					question.options().get(option), option == question.correctIndex(), option, ts(now), ts(now));
		}
	}

	/** Every schedule value is set again on each run, so the live exam and the dates always follow the run time. */
	private void refreshSchedule(List<ExamSpec> exams, Instant now) {
		for (ExamSpec spec : exams) {
			jdbc.update("UPDATE live_exams SET live_at = ?, duration_minutes = ?, answers_released_at = ? WHERE id = ?",
					ts(spec.liveAt()), spec.minutes(), spec.released() ? ts(now.minus(Duration.ofDays(1))) : null, spec.id());
		}
	}

	/**
	 * MCQ and written sittings: scores, submissions, marks and publication, relative to each exam's start. A file is written
	 * only the first time its sitting is seen, so a re-run reuses it.
	 */
	private void sittings(List<ExamSpec> exams, Instant now) {
		ExamSpec e1 = spec(exams, 1);
		int[] scores = { 45, 40, 40, 35, 30, 30, 25, 15 };
		for (int number = 1; number <= STUDENTS; number++) {
			int score = scores[number - 1];
			sitting(new Sitting(demo('7', 200L + number), e1, demo('5', number), studentCode(number), e1.liveAt(),
					e1.liveAt().plus(Duration.ofMinutes(25)), score, passedMcq(score), LiveExamParticipant.SUBMITTED, null, null, null,
					null, false, false), now);
		}
		ExamSpec e10 = spec(exams, 10);
		sitting(new Sitting(demo('7', 301), e10, OWNER_PACKAGE, "DEMO-STU-101", e10.liveAt(), e10.liveAt().plus(Duration.ofMinutes(20)),
				30, passedMcq(30), LiveExamParticipant.SUBMITTED, null, null, null, null, false, false), now);

		ExamSpec e7 = spec(exams, 7);
		ExamSpec e13 = spec(exams, 13);
		Instant e7Start = e7.liveAt();
		sitting(new Sitting(demo('7', 601), e7, demo('5', 3), studentCode(3), e7Start, e7Start.plus(Duration.ofMinutes(30)), null, null,
				LiveExamParticipant.SUBMITTED, null, null, null, null, true, false), now);
		sitting(new Sitting(demo('7', 602), e7, demo('5', 4), studentCode(4), e7Start, e7Start.plus(Duration.ofMinutes(30)), 28,
				true, LiveExamParticipant.SUBMITTED, "আংশিক উত্তর, বিস্তারিত লিখুন।", INSTRUCTOR, e7Start.plus(Duration.ofHours(2)),
				null, true, true), now);
		sitting(new Sitting(demo('7', 603), e7, demo('5', 5), studentCode(5), e7Start, e7Start.plus(SITTING),
				0, false, LiveExamParticipant.FAILED_TO_SUBMIT, null, null, null, null, false, false), now);

		Instant e13Start = e13.liveAt();
		sitting(new Sitting(demo('7', 501), e13, OWNER_PACKAGE, "DEMO-STU-101", e13Start, e13Start.plus(Duration.ofMinutes(30)), 38,
				true, LiveExamParticipant.SUBMITTED, "ভালো উত্তর, তবে রেফারেন্স আরও লিখুন।", INSTRUCTOR,
				e13Start.plus(Duration.ofHours(2)), publishedAt(e13), true, true), now);
		sitting(new Sitting(demo('7', 502), e13, demo('5', 2), studentCode(2), e13Start, e13Start.plus(Duration.ofMinutes(30)), 30,
				true, LiveExamParticipant.SUBMITTED, "আংশিক উত্তর, বিস্তারিত লিখুন।", INSTRUCTOR, e13Start.plus(Duration.ofHours(2)),
				null, true, true), now);
	}

	private record Sitting(UUID id, ExamSpec exam, UUID student, String code, Instant started, Instant submitted, Integer score,
			Boolean passed, String status, String comment, UUID evaluatedBy, Instant evaluatedAt, Instant publishedAt,
			boolean answer, boolean marked) {
	}

	private void sitting(Sitting sitting, Instant now) {
		boolean fresh = !exists("live_exam_participants", sitting.id());
		Instant deadline = sitting.started().plus(Duration.ofMinutes(sitting.exam().minutes()));
		LocalFileStorage.StoredFile answer = fresh && sitting.answer() ? storePdf(sitting.exam().titleEn() + " answer " + sitting.code()
				+ ".pdf", List.of("Demo answer script", "Exam: " + sitting.exam().titleEn(), "Student code: " + sitting.code(),
						"Demo copy only; not a real answer.")) : null;
		LocalFileStorage.StoredFile marked = fresh && sitting.marked() ? storePdf(sitting.exam().titleEn() + " marked " + sitting.code()
				+ ".pdf", List.of("Demo marked script", "Exam: " + sitting.exam().titleEn(), "Marks: " + sitting.score() + " / "
						+ WRITTEN_FULL_MARKS, "Demo copy only; not a real result.")) : null;
		jdbc.update(UPSERT_SITTING, sitting.id(), sitting.exam().id(), sitting.student(), ts(sitting.started()), ts(deadline),
				ts(sitting.submitted()), sitting.score(), sitting.passed(), sitting.status(), sitting.comment(), sitting.evaluatedBy(),
				ts(sitting.evaluatedAt()), ts(sitting.publishedAt()),
				answer == null ? null : answer.storedFileName(), answer == null ? null : answer.originalFileName(),
				answer == null ? null : answer.byteSize(), answer == null ? null : "application/pdf",
				marked == null ? null : marked.storedFileName(), marked == null ? null : marked.originalFileName(),
				marked == null ? null : marked.byteSize(), marked == null ? null : "application/pdf", ts(now), ts(now));
	}

	/** Paid, failed and cancelled orders, and the stuck order. Paid orders carry a grant; the stuck one has none. */
	private void orders(Instant now) {
		Instant paid = now.minus(Duration.ofHours(1));
		Instant stuck = now.minus(Duration.ofDays(2));
		ExamSpec e2 = spec(examsFor(now), 2);
		List<Order> orders = List.of(
				new Order(1, "DEMO-PKG-B3-5101", OWNER_PACKAGE, "MODULE", MODULE_FULL, null, "2500.00", "SUCCEEDED", paid, true),
				new Order(2, "DEMO-EXM-E4-5102", OWNER_EXAM, "EXAM", MODULE_MCQ, demo('e', 4), "300.00", "SUCCEEDED", paid, true),
				new Order(3, "DEMO-EXM-E2-5103", LIVE_STUDENT, "EXAM", MODULE_MCQ, e2.id(), "300.00", "SUCCEEDED", paid, true),
				new Order(4, "DEMO-EXM-E3-5105", REMINDER_STUDENT, "EXAM", MODULE_MCQ, demo('e', 3), "350.00", "SUCCEEDED", paid, true),
				new Order(5, "DEMO-STUCK-E9-5001", demo('5', 1), "EXAM", MODULE_WRITTEN, demo('e', 9), "500.00", "INITIATED", stuck,
						false),
				new Order(6, "DEMO-PKG-B2-5001", demo('5', 1), "MODULE", MODULE_WRITTEN, null, "1800.00", "FAILED", paid, false),
				new Order(7, "DEMO-EXM-E9-5002", demo('5', 2), "EXAM", MODULE_WRITTEN, demo('e', 9), "500.00", "CANCELLED", paid, false));
		for (Order order : orders) {
			boolean succeeded = "SUCCEEDED".equals(order.status());
			jdbc.update(UPSERT_ORDER, demo('c', order.number()), order.student(), order.scope(), order.module(), order.exam(),
					new BigDecimal(order.amount()), order.tranId(), order.status(), succeeded ? "DEMO-VAL-" + order.number() : null,
					succeeded ? "DEMO" : null, ts(order.createdAt()), ts(order.createdAt()));
			if (order.grant()) {
				boolean module = "MODULE".equals(order.scope());
				jdbc.update("""
						INSERT INTO live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source)
						VALUES (?, ?, ?, ?, ?, ?, ?, 'ORDER') ON CONFLICT (id) DO NOTHING
						""", demo('d', order.number()), order.student(), order.scope(), order.module(), module ? null : order.exam(),
						demo('c', order.number()), ts(order.createdAt()));
			}
		}
	}

	private void assignments(boolean instructorBPresent, Instant now) {
		assign(MODULE_WRITTEN, INSTRUCTOR, now);
		assign(MODULE_FULL, INSTRUCTOR, now);
		if (instructorBPresent) {
			assign(MODULE_MCQ, INSTRUCTOR_B, now);
		}
	}

	private void assign(UUID module, UUID instructor, Instant now) {
		jdbc.update("INSERT INTO live_exam_instructors (module_id, instructor_id, assigned_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
				module, instructor, ts(now));
	}

	private void roster(String studentEmail, Instant now) {
		if (studentEmail == null || studentEmail.isBlank()) {
			return;
		}
		List<UUID> found = jdbc.queryForList(
				"SELECT id FROM users WHERE lower(email) = ? AND role = 'STUDENT' AND deleted_at IS NULL", UUID.class,
				studentEmail.trim().toLowerCase(Locale.ROOT));
		if (!found.isEmpty()) {
			jdbc.update("INSERT INTO live_exam_roster (module_id, student_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
					MODULE_MCQ, found.get(0), ts(now));
		}
	}

	/**
	 * Notices for the demo students, written again on each run. Reminders and the window-open notice go through the same
	 * writer the reminder job uses, for the demo exams only: the job's scan covers every exam in the database.
	 */
	private void notices(List<ExamSpec> exams, UUID masterAdmin, Instant now) {
		jdbc.update("DELETE FROM student_notices WHERE user_id::text LIKE ? OR announcement_id::text LIKE ?", demoLike('5'),
				demoLike('8'));
		List<Order> grants = List.of(
				new Order(1, null, OWNER_PACKAGE, "MODULE", MODULE_FULL, null, null, null, null, true),
				new Order(2, null, OWNER_EXAM, "EXAM", MODULE_MCQ, demo('e', 4), null, null, null, true),
				new Order(3, null, LIVE_STUDENT, "EXAM", MODULE_MCQ, demo('e', 2), null, null, null, true),
				new Order(4, null, REMINDER_STUDENT, "EXAM", MODULE_MCQ, demo('e', 3), null, null, null, true));
		for (Order grant : grants) {
			boolean module = "MODULE".equals(grant.scope());
			notices.purchaseGranted(grant.student(), module ? grant.module() : grant.exam(), grant.scope());
		}
		ExamSpec e3 = spec(exams, 3);
		ExamSpec e10 = spec(exams, 10);
		ExamSpec e13 = spec(exams, 13);
		notices.mcqScore(OWNER_PACKAGE, e10.id(), demo('7', 301));
		notices.writtenResult(OWNER_PACKAGE, e13.id(), demo('7', 501), publishedAt(e13));
		notices.remind(e3.id(), e3.liveAt(), accessStudents(MODULE_MCQ, e3.id()));
		notices.startingSoon(e3.id(), e3.liveAt(), accessStudents(MODULE_MCQ, e3.id()));
		notices.remind(demo('e', 4), spec(exams, 4).liveAt(), accessStudents(MODULE_MCQ, demo('e', 4)));
		notices.opened(demo('e', 2), spec(exams, 2).liveAt(), accessStudents(MODULE_MCQ, demo('e', 2)));
		announcement(masterAdmin, now);
	}

	/** One ANNOUNCEMENT per login student. Written here, not by AnnouncementService, which would reach every active student. */
	private void announcement(UUID masterAdmin, Instant now) {
		jdbc.update("""
				INSERT INTO announcements (id, title_en, title_bn, body_en, body_bn, audience, module_id, created_by, created_at)
				VALUES (?, ?, ?, ?, ?, 'ALL', NULL, ?, ?) ON CONFLICT (id) DO NOTHING
				""", ANNOUNCEMENT, "Demo announcement", "ডেমো ঘোষণা", "This is demo copy for the client review: the notice box and the bell.",
				"এটি ক্লায়েন্ট রিভিউয়ের জন্য ডেমো ঘোষণা।", masterAdmin, ts(now));
		for (LoginStudent student : LOGIN_STUDENTS) {
			jdbc.update(INSERT_ANNOUNCEMENT_NOTICE, UUID.randomUUID(), ANNOUNCEMENT, ts(now), ANNOUNCEMENT, student.id());
		}
	}

	private List<UUID> accessStudents(UUID module, UUID exam) {
		return jdbc.queryForList(ACCESS_STUDENTS, UUID.class, module, module, exam);
	}

	private void claim(String table, UUID id, String titleEn) {
		List<String> titles = jdbc.queryForList("SELECT title_en FROM " + table + " WHERE id = ?", String.class, id);
		if (!titles.isEmpty() && !titles.get(0).equals(titleEn)) {
			throw new IllegalStateException("A demo row from an older seed is in " + table + "; run demo-cleanup.sql first.");
		}
	}

	private boolean exists(String table, UUID id) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
		return count != null && count > 0;
	}

	private LocalFileStorage.StoredFile storePdf(String originalName, List<String> lines) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream content = new PDPageContentStream(document, page)) {
				content.beginText();
				content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				content.setLeading(18);
				content.newLineAtOffset(72, 720);
				for (String line : lines) {
					content.showText(line);
					content.newLine();
				}
				content.endText();
			}
			document.save(out);
			byte[] bytes = out.toByteArray();
			return files.save(originalName, "application/pdf", new ByteArrayInputStream(bytes), bytes.length, DEMO_PDF_MAX_BYTES);
		}
		catch (IOException exception) {
			throw new IllegalStateException("The demo PDF could not be written.", exception);
		}
	}

	private List<ExamSpec> examsFor(Instant now) {
		return exams(Schedule.at(now));
	}

	/** The fourteen exams. The schedule is relative to the run time; the rules and copy are drafts (D-d10). */
	List<ExamSpec> exams(Schedule s) {
		List<Question> evidence = concat(EVIDENCE, DemoContentSeedQuestions.EVIDENCE_EXTRA);
		List<Question> constitution = concat(GENERAL, DemoContentSeedQuestions.CONSTITUTION_EXTRA);
		return List.of(
				mcq(1, MODULE_MCQ, "CPC MCQ", "সিপিসি এমসিকিউ", "Ten questions on the Code of Civil Procedure, 1908: suits, pleadings, summons and decrees.",
						"দেওয়ানি কার্যবিধি, ১৯০৮-এর ওপর দশটি প্রশ্ন: মামলা শুরু, লিখিত বক্তব্য, সমন ও ডিক্রি।", s.noon(-2), 40, "300.00",
						DemoContentSeedQuestions.CPC, true),
				mcq(2, MODULE_MCQ, "CrPC MCQ", "সিআরপিসি এমসিকিউ", "Ten questions on the Code of Criminal Procedure, 1898: arrest, FIR, bail and the magistracy.",
						"ফৌজদারি কার্যবিধি, ১৮৯৮-এর ওপর দশটি প্রশ্ন: গ্রেফতার, এজাহার, জামিন ও ম্যাজিস্ট্রেসি।",
						s.now().minus(Duration.ofMinutes(5)), 30, "300.00", CRPC, false),
				mcq(3, MODULE_MCQ, "Penal Code MCQ", "পেনাল কোড এমসিকিউ", "Ten questions on the Penal Code, 1860: offences against the body, property and reputation.",
						"দণ্ডবিধি, ১৮৬০-এর ওপর দশটি প্রশ্ন: দেহ, সম্পত্তি ও সুনামের বিরুদ্ধে অপরাধ।", s.now().plus(Duration.ofMinutes(50)), 40,
						"350.00", PENAL, false),
				mcq(4, MODULE_MCQ, "Evidence MCQ", "সাক্ষ্য আইন এমসিকিউ", "Ten questions on the Evidence Act, 1872: admissions, confessions, documents and witnesses.",
						"সাক্ষ্য আইন, ১৮৭২-এর ওপর দশটি প্রশ্ন: স্বীকৃতি, স্বীকারোক্তি, দলিল ও সাক্ষী।", s.now().plus(Duration.ofHours(36)), 40,
						"300.00", evidence, false),
				mcq(5, MODULE_MCQ, "Constitution MCQ", "সংবিধান এমসিকিউ", "Ten questions on the Constitution of the People's Republic of Bangladesh.",
						"বাংলাদেশের সংবিধানের ওপর দশটি প্রশ্ন।", null, 40, null, constitution, false),
				written(6, MODULE_WRITTEN, "CPC written", "সিপিসি রিটেন", "A written paper on the Code of Civil Procedure. Upload the answer script as one PDF before the deadline. The date is announced later.",
						"দেওয়ানি কার্যবিধির ওপর রিটেন পরীক্ষা। নির্ধারিত সময়ের মধ্যে উত্তরপত্র একটি PDF হিসেবে জমা দিন। তারিখ পরে ঘোষণা করা হবে।", null, "500.00"),
				written(7, MODULE_WRITTEN, "CrPC written", "সিআরপিসি রিটেন", "A written paper on the Code of Criminal Procedure, 1898.",
						"ফৌজদারি কার্যবিধি, ১৮৯৮-এর ওপর রিটেন পরীক্ষা।", s.afternoon(-1), "500.00"),
				written(8, MODULE_WRITTEN, "Penal written", "পেনাল কোড রিটেন", "A written paper on the Penal Code, 1860.",
						"দণ্ডবিধি, ১৮৬০-এর ওপর রিটেন পরীক্ষা।", s.noon(10), "500.00"),
				written(9, MODULE_WRITTEN, "Constitution written", "সংবিধান রিটেন", "A written paper on the Constitution of Bangladesh.",
						"বাংলাদেশের সংবিধানের ওপর রিটেন পরীক্ষা।", s.noon(21), "500.00"),
				mcq(10, MODULE_FULL, "Penal MCQ (full)", "পেনাল কোড এমসিকিউ (পূর্ণ)", "Ten questions on the Penal Code, 1860, from the full package.",
						"দণ্ডবিধি, ১৮৬০-এর ওপর দশটি প্রশ্ন, পূর্ণ প্যাকেজের অংশ।", s.noon(-3), 40, "350.00",
						DemoContentSeedQuestions.PENAL_FULL, false),
				mcq(11, MODULE_FULL, "Evidence MCQ (full)", "সাক্ষ্য আইন এমসিকিউ (পূর্ণ)", "Ten questions on the Evidence Act, 1872, from the full package.",
						"সাক্ষ্য আইন, ১৮৭২-এর ওপর দশটি প্রশ্ন, পূর্ণ প্যাকেজের অংশ।", s.noon(4), 40, "300.00",
						DemoContentSeedQuestions.EVIDENCE_FULL, false),
				mcq(12, MODULE_FULL, "General law MCQ", "সাধারণ আইন এমসিকিউ", "Ten questions on general law: courts, bail, the rule of law and dispute resolution.",
						"সাধারণ আইনের ওপর দশটি প্রশ্ন: আদালত, জামিন, আইনের শাসন ও বিরোধ নিষ্পত্তি।", s.noon(8), 40, null,
						DemoContentSeedQuestions.GENERAL_LAW, false),
				written(13, MODULE_FULL, "CPC written (full)", "সিপিসি রিটেন (পূর্ণ)", "A written paper on the Code of Civil Procedure, from the full package.",
						"দেওয়ানি কার্যবিধির ওপর রিটেন পরীক্ষা, পূর্ণ প্যাকেজের অংশ।", s.afternoon(-1), "500.00"),
				written(14, MODULE_FULL, "CrPC written (full)", "সিআরপিসি রিটেন (পূর্ণ)", "A written paper on the Code of Criminal Procedure, from the full package.",
						"ফৌজদারি কার্যবিধির ওপর রিটেন পরীক্ষা, পূর্ণ প্যাকেজের অংশ।", s.noon(12), "600.00"));
	}

	private static boolean passedMcq(int score) {
		return score * 100 >= PASS_PERCENT * MCQ_FULL_MARKS;
	}

	private static ExamSpec mcq(int number, UUID module, String titleEn, String titleBn, String descEn, String descBn, Instant liveAt,
			int minutes, String price, List<Question> questions, boolean released) {
		return new ExamSpec(number, module, LiveExam.MCQ, titleEn, titleBn, descEn, descBn,
				String.format(Locale.ROOT, EN_MCQ_RULES, minutes), String.format(Locale.ROOT, BN_MCQ_RULES, bn(minutes)), minutes,
				price == null ? null : new BigDecimal(price), false, released, liveAt, questions);
	}

	private static ExamSpec written(int number, UUID module, String titleEn, String titleBn, String descEn, String descBn, Instant liveAt,
			String price) {
		return new ExamSpec(number, module, LiveExam.WRITTEN, titleEn, titleBn, descEn, descBn, EN_WRITTEN_RULES, BN_WRITTEN_RULES, 180,
				new BigDecimal(price), true, false, liveAt, List.of());
	}

	private static ExamSpec spec(List<ExamSpec> exams, int number) {
		return exams.stream().filter(exam -> exam.number() == number).findFirst().orElseThrow();
	}

	private static Instant publishedAt(ExamSpec exam) {
		return exam.liveAt().plus(Duration.ofHours(3));
	}

	private static String studentCode(int number) {
		return String.format(Locale.ROOT, "DEMO-STU-%03d", number);
	}

	private static String bn(int value) {
		StringBuilder out = new StringBuilder();
		for (char digit : String.valueOf(value).toCharArray()) {
			out.append((char) ('০' + (digit - '0')));
		}
		return out.toString();
	}

	private static List<Question> concat(List<Question> first, List<Question> second) {
		return Stream.concat(first.stream(), second.stream()).toList();
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	/** A LIKE pattern for every demo id whose first group starts with {@code prefix}. */
	static String demoLike(char prefix) {
		return String.format(Locale.ROOT, "%c0000000-0000-4000-8000-%%", prefix);
	}

	/** A deterministic demo id. {@code prefix} names the table; {@code n} is the row number. */
	static UUID demo(char prefix, long n) {
		return UUID.fromString(String.format(Locale.ROOT, "%c0000000-0000-4000-8000-%012d", prefix, n));
	}

	record Account(UUID id, String phone, String email, String name, String password, String role) {
	}

	record LoginStudent(UUID id, String phone, String email, String name, String code) {
	}

	record Order(int number, String tranId, UUID student, String scope, UUID module, UUID exam, String amount, String status,
			Instant createdAt, boolean grant) {
	}

	/** Run-relative times. {@code now} is the run time; the day offsets count from today in Dhaka. */
	record Schedule(Instant now, LocalDate today) {

		static Schedule at(Instant now) {
			return new Schedule(now, now.atZone(DHAKA).toLocalDate());
		}

		Instant noon(int days) {
			return today.plusDays(days).atTime(12, 0).atZone(DHAKA).toInstant();
		}

		Instant afternoon(int days) {
			return today.plusDays(days).atTime(14, 0).atZone(DHAKA).toInstant();
		}

	}

	record Question(String stem, String explanation, int correctIndex, List<String> options) {
	}

	record ExamSpec(int number, UUID module, String type, String titleEn, String titleBn, String descEn, String descBn,
			String rulesEn, String rulesBn, int minutes, BigDecimal price, boolean questionPdf, boolean released, Instant liveAt,
			List<Question> questions) {

		UUID id() {
			return DemoContentSeed.demo('e', number);
		}

	}

	static Question q(String stem, String explanation, int correctIndex, String... options) {
		return new Question(stem, explanation, correctIndex, List.of(options));
	}

}
