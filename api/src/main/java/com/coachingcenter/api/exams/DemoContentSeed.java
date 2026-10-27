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
import java.util.ArrayList;
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
 * The client-review seed: one staff account, one instructor and three students, two root categories with subcategories, four
 * packages, and twenty-minute exams of both kinds. Student A owns the mixed package with two published results, one paper
 * awaiting marking and an 11 PM exam on each of the next seven days; the instructor has four papers to mark. Staging and dev only.
 *
 * <p>Every demo row has a fixed UUID whose first group names its table: {@code a} categories, {@code b} modules, {@code e}
 * exams, {@code f} questions, {@code 9} options, {@code 7} sittings, {@code 5} students, {@code 6} the instructor, {@code 4}
 * the staff account, {@code c} orders, {@code d} grants, {@code 8} the announcement. The cleanup script deletes by those prefixes.
 *
 * <p>The seed only inserts, and only once: when the demo catalog is already there a run does nothing, so the client's marking and
 * purchases are never undone. Dates are set from the run date; a reset is the cleanup script and a new run. Copy in this file
 * is a draft for the client's content review (decision D-d10).
 */
@Component
public class DemoContentSeed {

	static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	/** Every exam is this long, MCQ and written alike. */
	static final int EXAM_MINUTES = 20;

	/** How many days after the run date get an 11 PM exam. */
	static final int DAILY_EXAMS = 7;

	static final int MCQ_MARKS = 5;

	static final int MCQ_FULL_MARKS = 50;

	static final int WRITTEN_FULL_MARKS = 50;

	static final int PASS_PERCENT = 40;

	/** The demo papers are a few kilobytes; the app-wide material limit is set separately and can be smaller. */
	private static final long DEMO_PDF_MAX_BYTES = 1024L * 1024L;

	static final UUID STAFF = demo('4', 1);

	static final UUID INSTRUCTOR = demo('6', 1);

	static final UUID STUDENT_A = demo('5', 1);

	static final UUID STUDENT_B = demo('5', 2);

	static final UUID STUDENT_C = demo('5', 3);

	static final UUID CATEGORY_ROOT = demo('a', 1);

	static final UUID CATEGORY_MCQ = demo('a', 2);

	static final UUID CATEGORY_WRITTEN = demo('a', 3);

	static final UUID CATEGORY_FULL = demo('a', 4);

	static final UUID CATEGORY_BAR = demo('a', 5);

	static final UUID CATEGORY_BAR_MCQ = demo('a', 6);

	/** b1: three MCQ exams, owned by nobody. */
	static final UUID MODULE_MCQ = demo('b', 1);

	/** b2: four written exams. */
	static final UUID MODULE_WRITTEN = demo('b', 2);

	/** b3: the mixed package with the daily 11 PM exams. */
	static final UUID MODULE_FULL = demo('b', 3);

	/** b4: two Bar Council MCQ exams, owned by nobody. */
	static final UUID MODULE_BAR = demo('b', 4);

	static final UUID ANNOUNCEMENT = demo('8', 1);

	/** Phones and emails are fixed so the client can sign in with either. */
	static final List<Account> ACCOUNTS = List.of(
			new Account(STAFF, "01711000001", "staff@example.com", "ডেমো স্টাফ", "SYSTEM_ADMIN", null),
			new Account(INSTRUCTOR, "01711000002", "instructor@example.com", "ডেমো ইন্সট্রাক্টর", "INSTRUCTOR", null),
			new Account(STUDENT_A, "01711000011", "student1@example.com", "সুমন আহমেদ", "STUDENT", "DEMO-STU-001"),
			new Account(STUDENT_B, "01711000012", "student2@example.com", "রিনা বেগম", "STUDENT", "DEMO-STU-002"),
			new Account(STUDENT_C, "01711000013", "student3@example.com", "করিম উদ্দিন", "STUDENT", "DEMO-STU-003"));

	static final List<Account> STUDENTS = ACCOUNTS.stream().filter(account -> "STUDENT".equals(account.role())).toList();

	private static final String INSERT_USER = """
			INSERT INTO users (id, phone, email, password_hash, role, status, email_verified_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?) ON CONFLICT DO NOTHING
			""";

	private static final String INSERT_EXAM = """
			INSERT INTO live_exams (id, module_id, exam_type, title_en, title_bn, description_en, description_bn, rules_en,
			  rules_bn, paper_language, live_at, duration_minutes, pass_percent, written_maximum, question_stored_file_name,
			  question_original_file_name, question_byte_size, question_content_type, status, finally_saved, price,
			  answers_released_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', true, ?, ?, ?, ?)
			""";

	private static final String INSERT_SITTING = """
			INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, score,
			  passed, status, comment, evaluated_by, evaluated_at, published_at, answer_stored_file_name,
			  answer_original_file_name, answer_byte_size, answer_content_type, evaluated_stored_file_name,
			  evaluated_original_file_name, evaluated_byte_size, evaluated_content_type, created_at, updated_at)
			VALUES (?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
			""";

	private static final String INSERT_ORDER = """
			INSERT INTO live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id, status,
			  val_id, payment_channel, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, 'BDT', 'SSLCOMMERZ', ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
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

	private static final String BN_MCQ_RULES = "১. উত্তর দেওয়ার আগে প্রতিটি প্রশ্ন পড়ুন।\n২. সময়সীমা %s মিনিট; পরীক্ষা একই সময় পর্যন্ত খোলা থাকে।\n"
			+ "৩. সময় শেষ হলে উত্তর নিজে থেকে জমা হয় এবং জমার পর বদলানো যায় না।";

	private static final String EN_MCQ_RULES = "1. Read every question before you answer.\n2. The time limit is %d minutes, and the paper stays open for the same time.\n"
			+ "3. Answers are submitted automatically when the time ends, and cannot be changed after you submit.";

	private static final String BN_WRITTEN_RULES = "১. উত্তর কাগজে লিখে একটি PDF ফাইলে স্ক্যান করে আপলোড করুন।\n"
			+ "২. সময়সীমা %s মিনিট; এর মধ্যে যতবার খুশি আপলোড করা যায়, শেষেরটি গণ্য হবে।\n"
			+ "৩. সময়সীমার পরে আপলোড নেওয়া হবে না। আপলোড না থাকলে পরীক্ষা 'শেষ করা যায়নি' হিসেবে গণ্য হবে।";

	private static final String EN_WRITTEN_RULES = "1. Write your answers on paper, scan them as one PDF and upload it.\n"
			+ "2. The time limit is %d minutes. You may upload again inside it; the last upload counts.\n"
			+ "3. Uploads after the deadline are refused. With no upload, the paper counts as not finished.";

	/** The daily 11 PM exams: odd days are MCQ, even days are written. */
	private static final String[][] DAILY_TOPICS = {
			{ "Evidence", "সাক্ষ্য আইন" }, { "Penal Code", "পেনাল কোড" }, { "General law", "সাধারণ আইন" },
			{ "Constitution", "সংবিধান" }, { "CrPC", "সিআরপিসি" }, { "CPC", "সিপিসি" }, { "Penal Code", "পেনাল কোড" } };

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
	 * Loads the demo content once. {@code env} reads {@code SEED_DEMO_PASSWORD}; no password is written into code. The seed
	 * writes no mail rows. When the demo catalog is already present the run does nothing.
	 */
	@Transactional
	public void seed(String[] profiles, Function<String, String> env, Instant now) {
		refuseProduction(profiles);
		if (Arrays.stream(profiles).noneMatch(profile -> "staging".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The demo seed runs on the staging or dev profile only.");
		}
		String password = required("SEED_DEMO_PASSWORD", env.apply("SEED_DEMO_PASSWORD"));
		requirePassword("SEED_DEMO_PASSWORD", password);
		UUID masterAdmin = masterAdmin();
		refuseOlderCatalog();
		List<ExamSpec> exams = exams(Schedule.at(now));
		if (exists("live_exams", spec(exams, 1).id())) {
			return;
		}

		String hash = passwords.encode(password);
		for (Account account : ACCOUNTS) {
			ensureAccount(account, hash, now);
		}
		ensureCatalog(exams, now);
		sittings(exams, now);
		orders(now);
		assign(MODULE_WRITTEN, now);
		assign(MODULE_FULL, now);
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

	private UUID masterAdmin() {
		List<UUID> found = jdbc.queryForList(
				"SELECT id FROM users WHERE role = 'MASTER_ADMIN' AND deleted_at IS NULL ORDER BY created_at LIMIT 1", UUID.class);
		if (found.isEmpty()) {
			throw new IllegalStateException("The Master Admin must exist before the demo seed runs (MASTER_ADMIN_*).");
		}
		return found.get(0);
	}

	/** Exams e4, e5, e13 and e14 belong to the earlier fourteen-exam demo; its student ids overlap this seed's. */
	private void refuseOlderCatalog() {
		for (int number : new int[] { 4, 5, 13, 14 }) {
			if (exists("live_exams", demo('e', number))) {
				throw new IllegalStateException("A demo row from an older seed is in live_exams; run demo-cleanup.sql first.");
			}
		}
	}

	private void ensureAccount(Account account, String hash, Instant now) {
		String phone;
		try {
			phone = PhoneNumbers.normalize(account.phone());
		}
		catch (ApiException exception) {
			throw new IllegalStateException("A demo phone number is not valid.");
		}
		Integer taken = jdbc.queryForObject("""
				SELECT count(*) FROM users WHERE id <> ? AND deleted_at IS NULL AND (phone = ? OR lower(email) = ?)
				""", Integer.class, account.id(), phone, account.email());
		if (taken != null && taken > 0) {
			throw new IllegalStateException("A demo phone or email is already in use by another account.");
		}
		jdbc.update(INSERT_USER, account.id(), phone, account.email(), hash, account.role(), ts(now), ts(now), ts(now));
		if ("STUDENT".equals(account.role())) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
					account.id(), account.name(), account.code());
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?) ON CONFLICT DO NOTHING", account.id(),
					account.name());
		}
	}

	private void ensureCatalog(List<ExamSpec> exams, Instant now) {
		category(CATEGORY_ROOT, null, "BJS Exam", "বিজেএস এক্সাম", 0, now);
		category(CATEGORY_MCQ, CATEGORY_ROOT, "Preliminary MCQ", "প্রিলিমিনারি (এমসিকিউ)", 0, now);
		category(CATEGORY_WRITTEN, CATEGORY_ROOT, "Written", "রিটেন", 1, now);
		category(CATEGORY_FULL, CATEGORY_ROOT, "Full mock", "ফুল মক (এমসিকিউ + রিটেন)", 2, now);
		category(CATEGORY_BAR, null, "Bar Council Exam", "বার কাউন্সিল এক্সাম", 1, now);
		category(CATEGORY_BAR_MCQ, CATEGORY_BAR, "MCQ practice", "এমসিকিউ প্র্যাকটিস", 0, now);
		module(MODULE_MCQ, CATEGORY_MCQ, "BJS Preliminary MCQ Package", "বিজেএস প্রিলিমিনারি এমসিকিউ প্যাকেজ",
				"Three MCQ papers for the preliminary round: civil and criminal procedure and the penal code. Each paper is one "
						+ "timed sitting of twenty minutes.",
				"প্রিলিমিনারি পর্যায়ের তিনটি এমসিকিউ পরীক্ষা: দেওয়ানি ও ফৌজদারি কার্যবিধি এবং দণ্ডবিধি। "
						+ "প্রতিটি পরীক্ষা বিশ মিনিটের একটি সময়সীমার মধ্যে দেওয়া হয়।",
				1500, 0, now);
		module(MODULE_WRITTEN, CATEGORY_WRITTEN, "BJS Written Package", "বিজেএস রিটেন প্যাকেজ",
				"Four written papers on civil procedure, criminal procedure, penal law and the constitution. Write on paper and "
						+ "upload one PDF inside the twenty minutes; marks and comments appear after marking.",
				"দেওয়ানি ও ফৌজদারি কার্যবিধি, দণ্ডবিধি এবং সংবিধানের চারটি রিটেন পরীক্ষা। কাগজে লিখে বিশ মিনিটের মধ্যে একটি PDF আপলোড করুন; "
						+ "নম্বর ও মন্তব্য মূল্যায়নের পরে দেখা যাবে।",
				1800, 0, now);
		module(MODULE_FULL, CATEGORY_FULL, "BJS Full Mock Package", "বিজেএস ফুল মক প্যাকেজ",
				"MCQ and written papers together, with a new paper at 11 PM every night for the next week. Buy the package once and "
						+ "sit every paper in it.",
				"এমসিকিউ ও রিটেন একসাথে, আগামী এক সপ্তাহ প্রতি রাত ১১টায় নতুন পরীক্ষা। পুরো প্যাকেজ একবার কিনলে এর সব পরীক্ষা দেওয়া যাবে।",
				2500, 0, now);
		module(MODULE_BAR, CATEGORY_BAR_MCQ, "Bar Council MCQ Package", "বার কাউন্সিল এমসিকিউ প্যাকেজ",
				"Two MCQ practice papers for the Bar Council exam.", "বার কাউন্সিল পরীক্ষার জন্য দুটি এমসিকিউ অনুশীলন পরীক্ষা।", 1200, 0,
				now);
		for (ExamSpec spec : exams) {
			exam(spec, now);
		}
	}

	private void category(UUID id, UUID parent, String titleEn, String titleBn, int position, Instant now) {
		jdbc.update("""
				INSERT INTO live_exam_categories (id, parent_id, title_en, title_bn, position, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", id, parent, titleEn, titleBn, position, ts(now), ts(now));
	}

	private void module(UUID id, UUID category, String titleEn, String titleBn, String descEn, String descBn, int price,
			int position, Instant now) {
		jdbc.update("""
				INSERT INTO live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn, access_mode,
				  status, finally_saved, position, price, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', 'PUBLISHED', true, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", id, category, titleEn, titleBn, descEn, descBn, position, new BigDecimal(price).setScale(2), ts(now), ts(now));
	}

	private void exam(ExamSpec spec, Instant now) {
		boolean written = LiveExam.WRITTEN.equals(spec.type());
		String storedName = null;
		String originalName = null;
		Long byteSize = null;
		String contentType = null;
		if (spec.questionPdf()) {
			LocalFileStorage.StoredFile stored = storePdf(spec.titleEn() + ".pdf", List.of("Demo question paper: " + spec.titleEn(),
					"Duration: " + EXAM_MINUTES + " minutes; full marks: " + WRITTEN_FULL_MARKS,
					"Instructions: answer every question on paper and upload one PDF.", "1. Explain the main procedure for this topic.",
					"2. Describe one case where the rule applies, and one where it does not.", "3. Write short notes on two key terms."));
			storedName = stored.storedFileName();
			originalName = stored.originalFileName();
			byteSize = stored.byteSize();
			contentType = stored.contentType();
		}
		jdbc.update(INSERT_EXAM, spec.id(), spec.module(), spec.type(), spec.titleEn(), spec.titleBn(), spec.descEn(), spec.descBn(),
				spec.rulesEn(), spec.rulesBn(), "BN", ts(spec.liveAt()), EXAM_MINUTES, PASS_PERCENT,
				written ? WRITTEN_FULL_MARKS : null, storedName, originalName, byteSize, contentType, spec.price(),
				spec.released() ? ts(now.minus(Duration.ofDays(1))) : null, ts(now), ts(now));
		int marks = written ? 1 : MCQ_MARKS;
		List<Question> questions = spec.questions();
		for (int index = 0; index < questions.size(); index++) {
			question(spec.number(), spec.id(), index + 1, questions.get(index), marks, now);
		}
	}

	private void question(int examNo, UUID examId, int number, Question question, int marks, Instant now) {
		jdbc.update(INSERT_QUESTION, questionId(examNo, number), examId, question.stem(), marks, question.explanation(), number - 1,
				ts(now), ts(now));
		for (int option = 0; option < question.options().size(); option++) {
			jdbc.update(INSERT_OPTION, optionId(examNo, number, option), questionId(examNo, number), question.options().get(option),
					option == question.correctIndex(), option, ts(now), ts(now));
		}
	}

	private static UUID questionId(int examNo, int number) {
		return demo('f', examNo * 100L + number);
	}

	private static UUID optionId(int examNo, int number, int option) {
		return demo('9', (examNo * 100L + number) * 10 + option + 1);
	}

	/**
	 * Student A's two published results and one paper awaiting marking, and the papers B and C handed in. Every sitting starts at
	 * its exam's time and ends 15 minutes later, inside the 20-minute window. Files are written for fresh rows only.
	 */
	private void sittings(List<ExamSpec> exams, Instant now) {
		ExamSpec e10 = spec(exams, 10);
		ExamSpec e11 = spec(exams, 11);
		Instant e11Start = e11.liveAt();
		sitting(new Sitting(demo('7', 1), e10, STUDENT_A, e10.liveAt(), 35, passedMcq(35), null, null, null, null, false, false), now);
		answersOf(demo('7', 1), e10, 7);
		sitting(new Sitting(demo('7', 2), e11, STUDENT_A, e11Start, 32, true, "ভালো উত্তর, তবে রেফারেন্স আরও লিখুন।", INSTRUCTOR,
				e11Start.plus(Duration.ofHours(2)), publishedAt(e11), true, true), now);
		sitting(new Sitting(demo('7', 3), spec(exams, 12), STUDENT_A, spec(exams, 12).liveAt(), null, null, null, null, null, null,
				true, false), now);
		sitting(new Sitting(demo('7', 4), spec(exams, 6), STUDENT_B, spec(exams, 6).liveAt(), null, null, null, null, null, null,
				true, false), now);
		sitting(new Sitting(demo('7', 5), spec(exams, 6), STUDENT_C, spec(exams, 6).liveAt(), null, null, null, null, null, null,
				true, false), now);
		sitting(new Sitting(demo('7', 6), spec(exams, 7), STUDENT_B, spec(exams, 7).liveAt(), null, null, null, null, null, null,
				true, false), now);
	}

	private record Sitting(UUID id, ExamSpec exam, UUID student, Instant started, Integer score, Boolean passed, String comment,
			UUID evaluatedBy, Instant evaluatedAt, Instant publishedAt, boolean answer, boolean marked) {
	}

	private void sitting(Sitting sitting, Instant now) {
		String code = codeOf(sitting.student());
		Instant deadline = sitting.started().plus(Duration.ofMinutes(EXAM_MINUTES));
		Instant submitted = sitting.started().plus(Duration.ofMinutes(15));
		LocalFileStorage.StoredFile answer = sitting.answer() ? storePdf(sitting.exam().titleEn() + " answer " + code + ".pdf",
				List.of("Demo answer script", "Exam: " + sitting.exam().titleEn(), "Student code: " + code,
						"Demo copy only; not a real answer.")) : null;
		LocalFileStorage.StoredFile marked = sitting.marked() ? storePdf(sitting.exam().titleEn() + " marked " + code + ".pdf",
				List.of("Demo marked script", "Exam: " + sitting.exam().titleEn(), "Marks: " + sitting.score() + " / "
						+ WRITTEN_FULL_MARKS, "Demo copy only; not a real result.")) : null;
		jdbc.update(INSERT_SITTING, sitting.id(), sitting.exam().id(), sitting.student(), ts(sitting.started()), ts(deadline),
				ts(submitted), sitting.score(), sitting.passed(), LiveExamParticipant.SUBMITTED, sitting.comment(),
				sitting.evaluatedBy(), ts(sitting.evaluatedAt()), ts(sitting.publishedAt()),
				answer == null ? null : answer.storedFileName(), answer == null ? null : answer.originalFileName(),
				answer == null ? null : answer.byteSize(), answer == null ? null : "application/pdf",
				marked == null ? null : marked.storedFileName(), marked == null ? null : marked.originalFileName(),
				marked == null ? null : marked.byteSize(), marked == null ? null : "application/pdf", ts(now), ts(now));
	}

	/** The picks behind an MCQ score: the first {@code correct} questions right, the rest wrong. */
	private void answersOf(UUID participant, ExamSpec exam, int correct) {
		List<Question> questions = exam.questions();
		for (int index = 0; index < questions.size(); index++) {
			int right = questions.get(index).correctIndex();
			int pick = index < correct ? right : (right + 1) % questions.get(index).options().size();
			jdbc.update("INSERT INTO live_exam_answers (participant_id, question_id, option_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
					participant, questionId(exam.number(), index + 1), optionId(exam.number(), index + 1, pick));
		}
	}

	/** Paid orders carry a grant; the failed one has none. No order is left pending, so the recheck job has nothing to query. */
	private void orders(Instant now) {
		Instant paid = now.minus(Duration.ofHours(1));
		List<Order> orders = List.of(
				new Order(1, "DEMO-PKG-B3-A", STUDENT_A, "MODULE", MODULE_FULL, null, "2500.00", "SUCCEEDED", paid, true),
				new Order(2, "DEMO-PKG-B2-B", STUDENT_B, "MODULE", MODULE_WRITTEN, null, "1800.00", "SUCCEEDED", paid, true),
				new Order(3, "DEMO-EXM-E6-C", STUDENT_C, "EXAM", MODULE_WRITTEN, demo('e', 6), "500.00", "SUCCEEDED", paid, true),
				new Order(4, "DEMO-PKG-B1-C", STUDENT_C, "MODULE", MODULE_MCQ, null, "1500.00", "FAILED", paid, false));
		for (Order order : orders) {
			boolean succeeded = "SUCCEEDED".equals(order.status());
			jdbc.update(INSERT_ORDER, demo('c', order.number()), order.student(), order.scope(), order.module(), order.exam(),
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

	private void assign(UUID module, Instant now) {
		jdbc.update("INSERT INTO live_exam_instructors (module_id, instructor_id, assigned_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
				module, INSTRUCTOR, ts(now));
	}

	/**
	 * Notices for the three students, written once. Reminders for the upcoming exams are left to the reminder job, which writes
	 * them on its own for students with access.
	 */
	private void notices(List<ExamSpec> exams, UUID masterAdmin, Instant now) {
		notices.purchaseGranted(STUDENT_A, MODULE_FULL, "MODULE");
		notices.purchaseGranted(STUDENT_B, MODULE_WRITTEN, "MODULE");
		notices.purchaseGranted(STUDENT_C, demo('e', 6), "EXAM");
		notices.mcqScore(STUDENT_A, spec(exams, 10).id(), demo('7', 1));
		notices.writtenResult(STUDENT_A, spec(exams, 11).id(), demo('7', 2), publishedAt(spec(exams, 11)));
		announcement(masterAdmin, now);
	}

	/** One ANNOUNCEMENT per student. Written here, not by AnnouncementService, which would reach every active student. */
	private void announcement(UUID masterAdmin, Instant now) {
		jdbc.update("""
				INSERT INTO announcements (id, title_en, title_bn, body_en, body_bn, audience, module_id, created_by, created_at)
				VALUES (?, ?, ?, ?, ?, 'ALL', NULL, ?, ?) ON CONFLICT (id) DO NOTHING
				""", ANNOUNCEMENT, "Demo announcement", "ডেমো ঘোষণা", "This is demo copy for the client review: the notice box and the bell.",
				"এটি ক্লায়েন্ট রিভিউয়ের জন্য ডেমো ঘোষণা।", masterAdmin, ts(now));
		for (Account student : STUDENTS) {
			jdbc.update(INSERT_ANNOUNCEMENT_NOTICE, UUID.randomUUID(), ANNOUNCEMENT, ts(now), ANNOUNCEMENT, student.id());
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

	/** The exams: three in b1, four in b2, the mixed package b3 with its daily 11 PM papers, and two in b4. */
	List<ExamSpec> exams(Schedule s) {
		List<Question> evidence = concat(EVIDENCE, DemoContentSeedQuestions.EVIDENCE_EXTRA);
		List<Question> constitution = concat(GENERAL, DemoContentSeedQuestions.CONSTITUTION_EXTRA);
		List<ExamSpec> exams = new ArrayList<>(List.of(
				mcq(1, MODULE_MCQ, "CPC MCQ", "সিপিসি এমসিকিউ", "Ten questions on the Code of Civil Procedure, 1908: suits, pleadings, summons and decrees.",
						"দেওয়ানি কার্যবিধি, ১৯০৮-এর ওপর দশটি প্রশ্ন: মামলা শুরু, লিখিত বক্তব্য, সমন ও ডিক্রি।", s.at23(2), "300.00",
						DemoContentSeedQuestions.CPC, false),
				mcq(2, MODULE_MCQ, "CrPC MCQ", "সিআরপিসি এমসিকিউ", "Ten questions on the Code of Criminal Procedure, 1898: arrest, FIR, bail and the magistracy.",
						"ফৌজদারি কার্যবিধি, ১৮৯৮-এর ওপর দশটি প্রশ্ন: গ্রেফতার, এজাহার, জামিন ও ম্যাজিস্ট্রেসি।", s.at23(5), "300.00", CRPC, false),
				mcq(3, MODULE_MCQ, "Penal Code MCQ", "পেনাল কোড এমসিকিউ", "Ten questions on the Penal Code, 1860: offences against the body, property and reputation.",
						"দণ্ডবিধি, ১৮৬০-এর ওপর দশটি প্রশ্ন: দেহ, সম্পত্তি ও সুনামের বিরুদ্ধে অপরাধ।", null, null, PENAL, false),
				written(6, MODULE_WRITTEN, "CPC written", "সিপিসি রিটেন", "A written paper on the Code of Civil Procedure. Upload the answer script as one PDF before the deadline.",
						"দেওয়ানি কার্যবিধির ওপর রিটেন পরীক্ষা। নির্ধারিত সময়ের মধ্যে উত্তরপত্র একটি PDF হিসেবে জমা দিন।", s.at23(-1), "500.00"),
				written(7, MODULE_WRITTEN, "CrPC written", "সিআরপিসি রিটেন", "A written paper on the Code of Criminal Procedure, 1898.",
						"ফৌজদারি কার্যবিধি, ১৮৯৮-এর ওপর রিটেন পরীক্ষা।", s.at23(-2), "500.00"),
				written(8, MODULE_WRITTEN, "Penal written", "পেনাল কোড রিটেন", "A written paper on the Penal Code, 1860.",
						"দণ্ডবিধি, ১৮৬০-এর ওপর রিটেন পরীক্ষা।", s.at23(4), "500.00"),
				written(9, MODULE_WRITTEN, "Constitution written", "সংবিধান রিটেন", "A written paper on the Constitution of Bangladesh. The date is announced later.",
						"বাংলাদেশের সংবিধানের ওপর রিটেন পরীক্ষা। তারিখ পরে ঘোষণা করা হবে।", null, "500.00"),
				mcq(10, MODULE_FULL, "Penal MCQ (full)", "পেনাল কোড এমসিকিউ (পূর্ণ)", "Ten questions on the Penal Code, 1860, from the full mock package.",
						"দণ্ডবিধি, ১৮৬০-এর ওপর দশটি প্রশ্ন, ফুল মক প্যাকেজের অংশ।", s.at23(-3), "300.00", DemoContentSeedQuestions.PENAL_FULL, true),
				written(11, MODULE_FULL, "CPC written (full)", "সিপিসি রিটেন (পূর্ণ)", "A written paper on the Code of Civil Procedure, from the full mock package.",
						"দেওয়ানি কার্যবিধির ওপর রিটেন পরীক্ষা, ফুল মক প্যাকেজের অংশ।", s.at23(-2), "500.00"),
				written(12, MODULE_FULL, "CrPC written (full)", "সিআরপিসি রিটেন (পূর্ণ)", "A written paper on the Code of Criminal Procedure, from the full mock package.",
						"ফৌজদারি কার্যবিধির ওপর রিটেন পরীক্ষা, ফুল মক প্যাকেজের অংশ।", s.at23(-1), "500.00"),
				mcq(30, MODULE_BAR, "Evidence MCQ", "সাক্ষ্য আইন এমসিকিউ", "Ten questions on the Evidence Act, 1872: admissions, confessions, documents and witnesses.",
						"সাক্ষ্য আইন, ১৮৭২-এর ওপর দশটি প্রশ্ন: স্বীকৃতি, স্বীকারোক্তি, দলিল ও সাক্ষী।", s.at23(3), "250.00", evidence, false),
				mcq(31, MODULE_BAR, "Constitution MCQ", "সংবিধান এমসিকিউ", "Ten questions on the Constitution of the People's Republic of Bangladesh.",
						"বাংলাদেশের সংবিধানের ওপর দশটি প্রশ্ন।", s.at23(6), "250.00", constitution, false)));
		List<List<Question>> dailyLists = List.of(DemoContentSeedQuestions.EVIDENCE_FULL, List.of(), DemoContentSeedQuestions.GENERAL_LAW,
				List.of(), CRPC, List.of(), DemoContentSeedQuestions.CPC);
		for (int day = 1; day <= DAILY_EXAMS; day++) {
			String topicEn = DAILY_TOPICS[day - 1][0];
			String topicBn = DAILY_TOPICS[day - 1][1];
			boolean mcq = day % 2 == 1;
			String titleEn = topicEn + (mcq ? " MCQ" : " written") + " (11 PM)";
			String titleBn = topicBn + (mcq ? " এমসিকিউ" : " রিটেন") + " (রাত ১১টা)";
			String descEn = "A twenty-minute " + (mcq ? "MCQ" : "written") + " paper on " + topicEn + ", at 11 PM.";
			String descBn = topicBn + "-এর ওপর বিশ মিনিটের " + (mcq ? "এমসিকিউ" : "রিটেন") + " পরীক্ষা, রাত ১১টায়।";
			exams.add(mcq
					? mcq(19 + day, MODULE_FULL, titleEn, titleBn, descEn, descBn, s.at23(day), "300.00", dailyLists.get(day - 1), false)
					: written(19 + day, MODULE_FULL, titleEn, titleBn, descEn, descBn, s.at23(day), "500.00"));
		}
		return exams;
	}

	private static boolean passedMcq(int score) {
		return score * 100 >= PASS_PERCENT * MCQ_FULL_MARKS;
	}

	private static ExamSpec mcq(int number, UUID module, String titleEn, String titleBn, String descEn, String descBn, Instant liveAt,
			String price, List<Question> questions, boolean released) {
		return new ExamSpec(number, module, LiveExam.MCQ, titleEn, titleBn, descEn, descBn,
				String.format(Locale.ROOT, EN_MCQ_RULES, EXAM_MINUTES), String.format(Locale.ROOT, BN_MCQ_RULES, bn(EXAM_MINUTES)),
				price == null ? null : new BigDecimal(price), false, released, liveAt, questions);
	}

	private static ExamSpec written(int number, UUID module, String titleEn, String titleBn, String descEn, String descBn, Instant liveAt,
			String price) {
		return new ExamSpec(number, module, LiveExam.WRITTEN, titleEn, titleBn, descEn, descBn,
				String.format(Locale.ROOT, EN_WRITTEN_RULES, EXAM_MINUTES), String.format(Locale.ROOT, BN_WRITTEN_RULES, bn(EXAM_MINUTES)),
				new BigDecimal(price), true, false, liveAt, List.of());
	}

	private static ExamSpec spec(List<ExamSpec> exams, int number) {
		return exams.stream().filter(exam -> exam.number() == number).findFirst().orElseThrow();
	}

	private static Instant publishedAt(ExamSpec exam) {
		return exam.liveAt().plus(Duration.ofHours(3));
	}

	private static String codeOf(UUID student) {
		return STUDENTS.stream().filter(account -> account.id().equals(student)).findFirst().orElseThrow().code();
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

	/** A deterministic demo id. {@code prefix} names the table; {@code n} is the row number. */
	static UUID demo(char prefix, long n) {
		return UUID.fromString(String.format(Locale.ROOT, "%c0000000-0000-4000-8000-%012d", prefix, n));
	}

	record Account(UUID id, String phone, String email, String name, String role, String code) {
	}

	record Order(int number, String tranId, UUID student, String scope, UUID module, UUID exam, String amount, String status,
			Instant createdAt, boolean grant) {
	}

	/** Run-relative times. {@code now} is the run time; day offsets count from today in Dhaka. */
	record Schedule(Instant now, LocalDate today) {

		static Schedule at(Instant now) {
			return new Schedule(now, now.atZone(DHAKA).toLocalDate());
		}

		/** 11 PM in Dhaka on the day {@code days} from today. */
		Instant at23(int days) {
			return today.plusDays(days).atTime(23, 0).atZone(DHAKA).toInstant();
		}

	}

	record Question(String stem, String explanation, int correctIndex, List<String> options) {
	}

	record ExamSpec(int number, UUID module, String type, String titleEn, String titleBn, String descEn, String descBn,
			String rulesEn, String rulesBn, BigDecimal price, boolean questionPdf, boolean released, Instant liveAt,
			List<Question> questions) {

		UUID id() {
			return DemoContentSeed.demo('e', number);
		}

	}

	static Question q(String stem, String explanation, int correctIndex, String... options) {
		return new Question(stem, explanation, correctIndex, List.of(options));
	}

}
