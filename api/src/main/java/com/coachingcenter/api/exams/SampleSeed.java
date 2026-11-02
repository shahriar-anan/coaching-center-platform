package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.PhoneNumbers;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.files.LocalFileStorage;

/**
 * The sample seed (docs/exam-site/plans/ui-delivery/sample-seed-plan.md, sections 4 to 9): one small, realistic data set for the
 * dev database and the client review. Eight categories, three packages, twenty exams (fourteen MCQ, six written, one draft), six
 * students, two staff, two teachers, 22 sittings, eight orders, seven grants, seven reviews, six announcements, the notices they
 * write, and one question bank of twelve items. The data is the tables of the plan, in this one file.
 *
 * <p>Ids follow the plan's scheme: the first hex group names the table ({@code a} categories, {@code b} packages, {@code e} exams,
 * {@code f} questions, {@code 9} options, {@code 7} sittings, {@code c} orders, {@code d} grants, {@code 8} announcements, {@code 5}
 * students, {@code 6} teachers, {@code 4} staff, {@code 1} package membership, {@code 2} question papers, {@code 3} reviews, {@code 0}
 * retake grants). Ids are unique within their table only.
 *
 * <p>The seed only inserts, and only once. A run on a database that already holds these rows does nothing. A database that holds
 * rows of another seed (an id this seed uses, with other content) is refused, so the cleanup runs first. Dates move with the run
 * day in Asia/Dhaka: the schedule covers about sixty days after it. The Master Admin is found, not created.
 */
@Component
public class SampleSeed {

	/** The sample is 20 minutes per attempt, MCQ and written alike. */
	static final int EXAM_MINUTES = 20;

	static final int MCQ_QUESTIONS = 10;

	static final int MCQ_MARKS = 5;

	static final int MCQ_FULL_MARKS = MCQ_QUESTIONS * MCQ_MARKS;

	static final int WRITTEN_FULL_MARKS = 50;

	static final int PASS_PERCENT = 40;

	/** The title of exam 1: the marker that says this sample is already in the database. */
	static final String TITLE_CHECK_EXAM = "Civil Procedure";

	/** How many days of schedule an open exam runs ahead of the run day. */
	private static final int OPEN_DAYS_AHEAD = 59;

	/** The discounts end thirty days after the run day, at the end of that day in Dhaka. */
	private static final int DISCOUNT_DAYS = 30;

	private static final String BANK_NAME = "Sample question bank";

	private static final int BANK_ITEMS = 12;

	// ------------------------------------------------------------------------------------------------------------------------------
	// The spec. Sections 4 to 6 of the plan, one table each.
	// ------------------------------------------------------------------------------------------------------------------------------

	/** Section 4. Phones and emails are fixed so the client can sign in with either. Everyone uses the seed password. */
	static final List<DemoContentSeed.Account> ACCOUNTS = List.of(
			new DemoContentSeed.Account(id('4', 1), "+8801711000001", "staff1@example.com", "স্টাফ এক", "SYSTEM_ADMIN", null),
			new DemoContentSeed.Account(id('4', 2), "+8801711000002", "staff2@example.com", "স্টাফ দুই", "SYSTEM_ADMIN", null),
			new DemoContentSeed.Account(id('6', 1), "+8801711000003", "instructor1@example.com", "শিক্ষক এক", "INSTRUCTOR", null),
			new DemoContentSeed.Account(id('6', 2), "+8801711000004", "instructor2@example.com", "শিক্ষক দুই", "INSTRUCTOR", null),
			new DemoContentSeed.Account(id('5', 1), "+8801711000011", "student1@example.com", "সুমন আহমেদ", "STUDENT", "DEMO-STU-001"),
			new DemoContentSeed.Account(id('5', 2), "+8801711000012", "student2@example.com", "রিনা বেগম", "STUDENT", "DEMO-STU-002"),
			new DemoContentSeed.Account(id('5', 3), "+8801711000013", "student3@example.com", "করিম উদ্দিন", "STUDENT", "DEMO-STU-003"),
			new DemoContentSeed.Account(id('5', 4), "+8801711000014", "student4@example.com", "তানভীর হাসান", "STUDENT", "DEMO-STU-004"),
			new DemoContentSeed.Account(id('5', 5), "+8801711000015", "student5@example.com", "নাসরিন সুলতানা", "STUDENT", "DEMO-STU-005"),
			new DemoContentSeed.Account(id('5', 6), "+8801711000016", "student6@example.com", "আরিফ হোসেন", "STUDENT", "DEMO-STU-006"));

	/** Section 5, categories: three levels, one of them empty on purpose. {@code parent} 0 is a root; {@code position} orders siblings. */
	static final List<CategorySpec> CATEGORIES = List.of(
			new CategorySpec(1, 0, 0, "Bangladesh Judicial Service (BJS)", "বাংলাদেশ জুডিশিয়াল সার্ভিস (বিজেএস)"),
			new CategorySpec(2, 1, 0, "Assistant Judge Recruitment Exam", "সহকারী জজ নিয়োগ পরীক্ষা"),
			new CategorySpec(3, 1, 1, "BJS Preliminary", "বিজেএস প্রিলিমিনারি"),
			new CategorySpec(4, 0, 1, "Bar Council Enrolment", "বার কাউন্সিল এনরোলমেন্ট"),
			new CategorySpec(5, 4, 0, "Advocate Enrolment Exam", "অ্যাডভোকেট এনরোলমেন্ট পরীক্ষা"),
			new CategorySpec(6, 5, 0, "Preliminary", "প্রিলিমিনারি"),
			new CategorySpec(7, 5, 1, "Written", "রিটেন"),
			new CategorySpec(8, 4, 1, "Short Courses", "স্বল্পমেয়াদি কোর্স"));

	/**
	 * Section 5, packages. {@code discount} is a percent until thirty days after the run day (0 for none). A package has no
	 * instructor of its own: each exam names its own, so {@code teacher} on a package is not written.
	 */
	static final List<PackageSpec> PACKAGES = List.of(
			new PackageSpec(1, 3, 1500, 25, false, 0, "BJS Preliminary Package", "বিজেএস প্রিলিমিনারি প্যাকেজ",
					"Six preliminary MCQ papers: civil and criminal procedure, evidence and the penal code. Each paper is one timed sitting "
							+ "of twenty minutes.",
					"প্রিলিমিনারি পর্যায়ের এমসিকিউ পরীক্ষাগুলো। প্রতিটি পরীক্ষা বিশ মিনিটের একটি সময়সীমার মধ্যে দেওয়া হয়।"),
			new PackageSpec(2, 5, 2500, 0, true, 2, "Bar Council Complete Preparation Package", "বার কাউন্সিল সম্পূর্ণ প্রস্তুতি প্যাকেজ",
					"MCQ and written papers together for the advocate enrolment exam. Buy the package once and sit every paper in it.",
					"অ্যাডভোকেট এনরোলমেন্ট পরীক্ষার জন্য এমসিকিউ ও রিটেন একসাথে। পুরো প্যাকেজ একবার কিনলে এর সব পরীক্ষা দেওয়া যাবে।"),
			new PackageSpec(3, 7, 800, 0, false, 1, "Advocate Written Pack", "অ্যাডভোকেট রিটেন প্যাক",
					"Written papers only. Write on paper and upload one PDF inside the twenty minutes.",
					"শুধু রিটেন পরীক্ষা। কাগজে লিখে বিশ মিনিটের মধ্যে একটি PDF আপলোড করুন।"));

	/**
	 * Section 5, exams: twenty of them, in number order. The builder methods read like the plan's columns: the window, the price,
	 * the packages, the teacher and the special cases. Written exams always get a question paper PDF.
	 */
	static final List<Exam> EXAMS = List.of(
			mcq(1, 3, "Civil Procedure", "সিভিল প্রসিডিওর", "Ten questions on the Code of Civil Procedure, 1908.",
					"দেওয়ানি কার্যবিধি, ১৯০৮-এর ওপর দশটি প্রশ্ন।").dated(-14).free().released().questions(ten(DemoContentSeedQuestions.CPC)),
			mcq(2, 3, "Penal Code", "পেনাল কোড", "Ten questions on the Penal Code, with negative marking.",
					"দণ্ডবিধির ওপর দশটি প্রশ্ন, ভুল উত্তরে নেগেটিভ মার্ক।").open().free().negative("0.25")
				.questions(ten(DemoContentSeedQuestions.PENAL_FULL)),
			mcq(3, 3, "Evidence", "সাক্ষ্য আইন", "Ten questions on the Evidence Act, 1872.", "সাক্ষ্য আইন, ১৮৭২-এর ওপর দশটি প্রশ্ন।")
				.dated(3).price(300).in(1).questions(ten(DemoContentSeedQuestions.EVIDENCE_FULL)),
			mcq(4, 3, "Constitution", "সংবিধান", "Ten questions on the Constitution of Bangladesh.",
					"বাংলাদেশের সংবিধানের ওপর দশটি প্রশ্ন।").dated(10).price(300).in(1)
				.questions(ten(concat(DemoContentSeedQuestions.CONSTITUTION_EXTRA, DemoContentSeedQuestions.GENERAL_LAW))),
			mcq(5, 3, "CrPC", "ফৌজদারি কার্যবিধি", "Ten questions on the Code of Criminal Procedure, 1898.",
					"ফৌজদারি কার্যবিধি, ১৮৯৮-এর ওপর দশটি প্রশ্ন।").open().price(300).in(1)
				.questions(ten(DemoContentSeedQuestions.GENERAL_LAW)),
			mcq(6, 3, "CPC Revision", "সিপিসি রিভিশন", "A discounted single MCQ paper on civil procedure.",
					"দেওয়ানি কার্যবিধির ওপর একটি ছাড়ের এমসিকিউ পরীক্ষা।").dated(1).price(400).discount(25)
				.questions(ten(DemoContentSeedQuestions.CPC)),
			written(7, 2, "Civil Law Written", "দেওয়ানি আইন রিটেন", "A written paper with a question paper PDF; preview is on.",
					"প্রশ্নপত্র PDF সহ একটি রিটেন পরীক্ষা; প্রিভিউ চালু।").dated(7).price(500).teacher(1).preview(),
			mcq(8, 6, "Penal Code", "পেনাল কোড", "Ten questions on the Penal Code, from the Bar Council package.",
					"বার কাউন্সিল প্যাকেজের দণ্ডবিধির ওপর দশটি প্রশ্ন।").dated(-7).price(300).in(2).released()
				.questions(ten(DemoContentSeedQuestions.PENAL_FULL)),
			mcq(9, 6, "Evidence", "সাক্ষ্য আইন", "Ten questions on evidence, open now for package holders.",
					"সাক্ষ্য আইনের ওপর দশটি প্রশ্ন; প্যাকেজ ধারীদের জন্য এখন খোলা।").open().price(300).in(2)
				.questions(ten(concat(DemoContentSeedQuestions.EVIDENCE_EXTRA, DemoContentSeedQuestions.EVIDENCE_FULL))),
			mcq(10, 6, "General Law", "সাধারণ আইন", "Ten general law questions. A popular paper.",
					"সাধারণ আইনের দশটি প্রশ্ন। জনপ্রিয় পরীক্ষা।").dated(5).price(300).in(2).popular()
				.questions(ten(DemoContentSeedQuestions.GENERAL_LAW)),
			mcq(11, 6, "Free Sample Test", "বিনামূল্যে নমুনা পরীক্ষা", "A free paper, open now, for anyone to try.",
					"যে কেউ বিনামূল্যে দিতে পারেন এমন একটি পরীক্ষা, এখন খোলা।").open().free()
				.questions(ten(DemoContentSeedQuestions.GENERAL_LAW)),
			written(12, 7, "CrPC Written", "সিআরপিসি রিটেন", "A written paper in two packages, marked and published for one student.",
					"দুটি প্যাকেজে থাকা একটি রিটেন পরীক্ষা।").dated(-10).price(500).in(2, 3).teacher(1),
			written(13, 7, "Penal Code Written", "দণ্ডবিধি রিটেন", "A written paper, marked but not released.",
					"একটি রিটেন পরীক্ষা, মূল্যায়িত কিন্তু প্রকাশিত নয়।").dated(-8).price(500).in(3).teacher(1),
			written(14, 7, "Constitution Written", "সংবিধান রিটেন", "A written paper waiting for marking.",
					"একটি রিটেন পরীক্ষা, মূল্যায়নের অপেক্ষায়।").dated(-5).price(500).in(2).teacher(2),
			written(15, 7, "CPC Written", "সিপিসি রিটেন", "A written paper open now for upload.", "একটি রিটেন পরীক্ষা, এখন আপলোড খোলা।")
				.open().price(500).in(3).teacher(2),
			mcq(16, 4, "General Law Test", "সাধারণ আইন পরীক্ষা", "A free paper, finished. Students may sit it once more.",
					"একটি বিনামূল্যের সমাপ্ত পরীক্ষা।").dated(-3).free().released()
				.questions(ten(DemoContentSeedQuestions.PENAL_FULL)),
			written(17, 4, "Criminal Law Mock", "ফৌজদারি আইন মক", "A written mock with a question paper. Access lasts 30 days after the window.",
					"প্রশ্নপত্রসহ একটি রিটেন মক; উইন্ডো শেষ হওয়ার ৩০ দিন পর্যন্ত অ্যাক্সেস।").dated(-27).price(500).access(30).teacher(2),
			mcq(18, 2, "General Knowledge", "সাধারণ জ্ঞান", "A free paper with no date yet.", "তারিখ এখনো ঘোষণা হয়নি এমন একটি বিনামূল্যের পরীক্ষা।")
				.undated().free().questions(ten(concat(DemoContentSeedQuestions.CONSTITUTION_EXTRA, DemoContentSeedQuestions.GENERAL_LAW))),
			mcq(19, 5, "Practice Set", "অনুশীলন সেট", "A practice paper with no window: start it any time.",
					"কোনো নির্দিষ্ট সময় নেই; যেকোনো সময় শুরু করা যায়।").practice().price(300)
				.questions(ten(DemoContentSeedQuestions.EVIDENCE_FULL)),
			mcq(20, 6, "Draft Paper", "খসড়া প্রশ্নপত্র", "A draft that staff see and students never do.",
					"একটি খসড়া, কর্মীরা দেখেন, শিক্ষার্থীরা দেখেন না।").dated(20).price(300).draft()
				.questions(ten(DemoContentSeedQuestions.CPC)));

	// ------------------------------------------------------------------------------------------------------------------------------
	// Section 6, activity.
	// ------------------------------------------------------------------------------------------------------------------------------

	/** Section 6, MCQ sittings: sitting ids 1 to 15, in this order. A right and a wrong pick make the score (negative marks apply). */
	static final List<McqSitting> MCQ_SITTINGS = List.of(
			new McqSitting(1, 1, 8, 2), new McqSitting(1, 2, 3, 7), new McqSitting(1, 3, 7, 3), new McqSitting(1, 4, 2, 8),
			new McqSitting(1, 5, 6, 4),
			new McqSitting(2, 2, 7, 3), new McqSitting(2, 4, 3, 2),
			new McqSitting(5, 3, 7, 3),
			new McqSitting(8, 1, 8, 2), new McqSitting(8, 5, 3, 7), new McqSitting(8, 4, 6, 4),
			new McqSitting(11, 5, 6, 4),
			new McqSitting(16, 1, 5, 5), new McqSitting(16, 2, 6, 4), new McqSitting(16, 5, 3, 7));

	/** Section 6, written sittings: sitting ids 16 to 22, in this order. */
	static final List<WrittenSitting> WRITTEN_SITTINGS = List.of(
			new WrittenSitting(12, 1, Stage.PUBLISHED, 32, "Good structure; add more references to the Code."),
			new WrittenSitting(12, 3, Stage.PUBLISHED, 18, "Revise the rules on summons and service."),
			new WrittenSitting(12, 2, Stage.NOT_SUBMITTED, 0, null),
			new WrittenSitting(13, 2, Stage.MARKED, 28, "Sound answers; the second question needs an example."),
			new WrittenSitting(13, 3, Stage.AWAITING, 0, null),
			new WrittenSitting(14, 1, Stage.AWAITING, 0, null),
			new WrittenSitting(17, 2, Stage.AWAITING, 0, null));

	/**
	 * Section 6, orders. {@code target} is a package number for MODULE and an exam number for EXAM. {@code day} is days from the run
	 * day, {@code hour} the Dhaka hour. A succeeded order gets a grant and a receipt; the others get neither.
	 */
	static final List<Order> ORDERS = List.of(
			new Order(1, 1, "MODULE", 2, 2500, 0, 0, "SUCCEEDED", -16, 10),
			new Order(2, 5, "MODULE", 2, 2500, 0, 0, "SUCCEEDED", -6, 10),
			new Order(3, 4, "MODULE", 2, 2500, 0, 0, "SUCCEEDED", -12, 10),
			new Order(4, 2, "MODULE", 3, 800, 0, 0, "SUCCEEDED", -11, 10),
			new Order(5, 3, "MODULE", 1, 1125, 1500, 25, "SUCCEEDED", -9, 10),
			new Order(6, 6, "MODULE", 3, 800, 0, 0, "INITIATED", 0, 9),
			new Order(7, 5, "MODULE", 1, 1125, 1500, 25, "FAILED", -6, 11),
			new Order(8, 6, "EXAM", 7, 500, 0, 0, "CANCELLED", -2, 10));

	/** Section 6, manual grants by staff: S3 on package 3 (direct enrolment), S2 on exam 17 (access ends in three days). */
	static final List<ManualGrant> MANUAL_GRANTS = List.of(
			new ManualGrant(6, 3, "MODULE", 3, -4, 10),
			new ManualGrant(7, 2, "EXAM", 17, -27, 10));

	/** Section 6, retake grant: S3 on exam 12, unused. */
	static final List<RetakeGrant> RETAKES = List.of(new RetakeGrant(1, 3, 12, -2, 10));

	/** Section 6, reviews (seven). Averages come from the public rule: a review counts unless it is hidden, and needs three. */
	static final List<Review> REVIEWS = List.of(
			new Review(1, "EXAM", 1, 1, 5, "Clear questions and good explanations.", "APPROVED", null, true, -12),
			new Review(2, "EXAM", 1, 2, 4, "A fair paper, a little long for the time.", "PENDING", null, false, -11),
			new Review(3, "EXAM", 1, 3, 5, null, "APPROVED", null, false, -10),
			new Review(4, "EXAM", 1, 4, 2, "Too many questions on one act.", "HIDDEN", "Off topic for this exam", false, -9),
			new Review(5, "PACKAGE", 2, 1, 5, "Complete coverage for the Bar Council.", "APPROVED", null, true, -5),
			new Review(6, "PACKAGE", 2, 5, 4, "Good value for the price.", "APPROVED", null, false, -4),
			new Review(7, "PACKAGE", 2, 4, 5, "Well organised.", "PENDING", null, false, -3));

	/** Section 6, wish list: S1 has exam 2 and package 2; S5 has exam 11. */
	static final List<Favourite> FAVOURITES = List.of(
			new Favourite(1, "EXAM", 2), new Favourite(1, "PACKAGE", 2), new Favourite(5, "EXAM", 11));

	/**
	 * Section 6, announcements (six). Recipients come from {@link AnnouncementAudience#recipients}, the same rule the product uses.
	 * {@code target} is the package or exam number; {@code students} is for SELECTED only.
	 */
	static final List<Announcement> ANNOUNCEMENTS = List.of(
			new Announcement(1, "ALL", 0, new int[0], "Welcome to the sample exam site", "নমুনা পরীক্ষা সাইটে স্বাগতম",
					"This sample has fictional classmates and sample papers. Nothing here is a real result or a real payment.",
					"এই নমুনায় কাল্পনিক সহপাঠী ও নমুনা পরীক্ষা আছে। এখানে কোনো আসল ফল বা পেমেন্ট নেই।", -10, false),
			new Announcement(2, "MODULE", 2, new int[0], "Bar Council package: new written paper", "বার কাউন্সিল প্যাকেজ: নতুন রিটেন পরীক্ষা",
					"A written paper has been added to the package. Check the routine.", "প্যাকেজে একটি নতুন রিটেন পরীক্ষা যোগ হয়েছে।", -6, false),
			new Announcement(3, "EXAM", 17, new int[0], "Criminal Law Mock: access ends soon", "ফৌজদারি আইন মক: অ্যাক্সেস শীঘ্রই শেষ",
					"Your access to this mock ends in three days.", "এই মকে আপনার অ্যাক্সেস তিন দিনের মধ্যে শেষ হবে।", -3, false),
			new Announcement(4, "SELECTED", 0, new int[] { 1, 2 }, "Result review session", "ফল পর্যালোচনা সেশন",
					"A short review session is planned for the students named here.", "এখানে নামের শিক্ষার্থীদের জন্য একটি সেশন পরিকল্পিত।", -2, false),
			new Announcement(5, "EXAM", 11, new int[0], "Free sample test is open", "বিনামূল্যে নমুনা পরীক্ষা খোলা",
					"The free sample test is open now.", "বিনামূল্যে নমুনা পরীক্ষা এখন খোলা।", -1, false),
			new Announcement(6, "MODULE", 1, new int[0], "Preliminary package update", "প্রিলিমিনারি প্যাকেজ আপডেট",
					"This notice was withdrawn after it was sent.", "এই নোটিশ পাঠানোর পর প্রত্যাহার করা হয়েছে।", -8, true));

	/** Section 5, the question bank: twelve items for the staff create-exam screens. */
	static List<DemoContentSeed.Question> bankItems() {
		List<DemoContentSeed.Question> items = concat(DemoContentSeedQuestions.CPC, DemoContentSeedQuestions.GENERAL_LAW);
		return items.subList(0, BANK_ITEMS);
	}

	// ------------------------------------------------------------------------------------------------------------------------------

	private final JdbcTemplate jdbc;

	private final NamedParameterJdbcTemplate named;

	private final PasswordEncoder passwords;

	private final DemoContentSeed pdfs;

	private final StudentNoticeWriter notices;

	public SampleSeed(JdbcTemplate jdbc, PasswordEncoder passwords, DemoContentSeed pdfs, StudentNoticeWriter notices) {
		this.jdbc = jdbc;
		this.named = new NamedParameterJdbcTemplate(jdbc);
		this.passwords = passwords;
		this.pdfs = pdfs;
		this.notices = notices;
	}

	/**
	 * Loads the sample once. {@code env} reads {@code SEED_SAMPLE_PASSWORD}; no password is written into code. The Master Admin must
	 * already exist (it comes from MASTER_ADMIN_* at startup). Writes no mail rows.
	 */
	@Transactional
	public void seed(String[] profiles, Function<String, String> env, Instant now) {
		refuseProduction(profiles);
		if (Arrays.stream(profiles).noneMatch(profile -> "staging".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The sample seed runs on the staging or dev profile only.");
		}
		String password = required("SEED_SAMPLE_PASSWORD", env.apply("SEED_SAMPLE_PASSWORD"));
		requirePassword("SEED_SAMPLE_PASSWORD", password);
		masterAdmin();
		if (loaded()) {
			return;
		}
		refuseOtherSeeds();
		String hash = passwords.encode(password);
		for (DemoContentSeed.Account account : ACCOUNTS) {
			ensureAccount(account, hash, now);
		}
		Schedule s = new Schedule(now, now.atZone(DemoContentSeed.DHAKA).toLocalDate());
		insertCatalog(now);
		insertExams(s, now);
		insertPackageMembership(now);
		insertQuestionBank(now);
		insertFavourites(now);
		insertSittings(s, now);
		insertOrders(s, now);
		insertReviews(s, now);
		insertAnnouncements(s, now);
	}

	static void refuseProduction(String[] profiles) {
		if (Arrays.stream(profiles).anyMatch(profile -> "prod".equalsIgnoreCase(profile))) {
			throw new IllegalStateException("The sample seed cannot run on the prod profile.");
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
			throw new IllegalStateException("The Master Admin must exist before the sample seed runs (MASTER_ADMIN_*).");
		}
		return found.get(0);
	}

	/** True when this sample is already in the database: the first exam, with its title. Nothing is written then. */
	private boolean loaded() {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM live_exams WHERE id = ? AND title_en = ?", Integer.class,
				exam(1).id(), TITLE_CHECK_EXAM);
		return count != null && count > 0;
	}

	/**
	 * Refuses a database that holds a row with one of this seed's ids but other content: rows an older seed wrote (the demo or dev
	 * seed share these ids). Run the cleanup first, or use an empty database.
	 */
	private void refuseOtherSeeds() {
		for (CategorySpec category : CATEGORIES) {
			if (exists("live_exam_categories", category.id())) {
				throw otherSeed();
			}
		}
		for (PackageSpec pkg : PACKAGES) {
			if (exists("live_exam_modules", pkg.id())) {
				throw otherSeed();
			}
		}
		for (Exam exam : EXAMS) {
			if (exists("live_exams", exam.id())) {
				throw otherSeed();
			}
		}
		for (DemoContentSeed.Account account : ACCOUNTS) {
			if (exists("users", account.id())) {
				throw otherSeed();
			}
		}
	}

	private static IllegalStateException otherSeed() {
		return new IllegalStateException("Rows of another seed are in the database (a sample id is taken). Run demo-cleanup.sql"
				+ " or the cleanup first, or use an empty database.");
	}

	private boolean exists(String table, UUID id) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ?", Integer.class, id);
		return count != null && count > 0;
	}

	private void ensureAccount(DemoContentSeed.Account account, String hash, Instant now) {
		String phone;
		try {
			phone = PhoneNumbers.normalize(account.phone());
		}
		catch (ApiException exception) {
			throw new IllegalStateException("A sample phone number is not valid.");
		}
		Integer taken = jdbc.queryForObject("""
				SELECT count(*) FROM users WHERE id <> ? AND deleted_at IS NULL AND (phone = ? OR lower(email) = ?)
				""", Integer.class, account.id(), phone, account.email());
		if (taken != null && taken > 0) {
			throw new IllegalStateException("A sample phone or email is already in use by another account.");
		}
		jdbc.update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, email_verified_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?) ON CONFLICT DO NOTHING
				""", account.id(), phone, account.email(), hash, account.role(), ts(now), ts(now), ts(now));
		if ("STUDENT".equals(account.role())) {
			jdbc.update("INSERT INTO student_profiles (user_id, full_name, student_code) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
					account.id(), account.name(), account.code());
		}
		else {
			jdbc.update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, ?) ON CONFLICT DO NOTHING", account.id(),
					account.name());
		}
	}

	private void insertCatalog(Instant now) {
		for (CategorySpec category : CATEGORIES) {
			jdbc.update("""
					INSERT INTO live_exam_categories (id, parent_id, title_en, title_bn, position, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
					""", category.id(), category.parent() == 0 ? null : id('a', category.parent()), category.titleEn(), category.titleBn(),
					category.position(), ts(now), ts(now));
		}
		for (PackageSpec pkg : PACKAGES) {
			jdbc.update("""
					INSERT INTO live_exam_modules (id, category_id, title_en, title_bn, description_en, description_bn, access_mode,
					  status, finally_saved, position, price, discount_percent, discount_ends_at, popular, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, 'MANUAL', 'PUBLISHED', true, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
					""", pkg.id(), pkg.category() == 0 ? null : id('a', pkg.category()), pkg.titleEn(), pkg.titleBn(), pkg.descEn(),
					pkg.descBn(), pkg.number() - 1, new BigDecimal(pkg.price()).setScale(2), pkg.discount() == 0 ? null : pkg.discount(),
					pkg.discount() == 0 ? null : ts(discountEnds(s(now))), pkg.popular(), ts(now), ts(now));
		}
	}

	private void insertExams(Schedule s, Instant now) {
		for (Exam exam : EXAMS) {
			StoredPaper paper = exam.written ? paper(exam) : null;
			Instant liveAt = exam.liveAt(s);
			Instant endsAt = exam.endsAt(s);
			boolean practice = exam.window == Window.PRACTICE;
			boolean draft = exam.draft;
			jdbc.update(INSERT_EXAM, exam.id(), exam.packages.isEmpty() ? null : id('b', exam.packages.get(0)),
					id('a', exam.category), exam.written ? "WRITTEN" : "MCQ", exam.titleEn, exam.titleBn, exam.descEn, exam.descBn,
					rules(exam, true), rules(exam, false), ts(liveAt), ts(endsAt), practice ? "PRACTICE" : "LIVE",
					exam.written ? WRITTEN_FULL_MARKS : null, paper == null ? null : paper.stored(),
					paper == null ? null : paper.original(), paper == null ? null : paper.size(),
					paper == null ? null : "application/pdf", draft ? "DRAFT" : "PUBLISHED",
					exam.price == null ? null : new BigDecimal(exam.price).setScale(2), exam.free, exam.discount == 0 ? null : exam.discount,
					exam.discount == 0 ? null : ts(discountEnds(s)), exam.accessDays, exam.popular, exam.preview,
					exam.negative, exam.teacher == 0 ? null : id('6', exam.teacher), draft ? null : ts(exam.released && liveAt != null ? liveAt.plus(Duration.ofDays(1)) : null),
					draft ? null : ts(now), ts(now), ts(now));
			if (paper != null) {
				jdbc.update("INSERT INTO live_exam_question_papers (id, exam_id, version, stored_file_name, original_file_name,"
						+ " byte_size, content_type, created_at) VALUES (?, ?, 1, ?, ?, ?, 'application/pdf', ?) ON CONFLICT DO NOTHING",
						id('2', exam.number), exam.id(), paper.stored(), paper.original(), paper.size(), ts(now));
				jdbc.update("UPDATE live_exams SET question_paper_id = ? WHERE id = ?", id('2', exam.number), exam.id());
			}
			if (!exam.questions.isEmpty()) {
				insertQuestions(exam, now);
			}
		}
	}

	private static final String INSERT_EXAM = """
			INSERT INTO live_exams (id, module_id, category_id, exam_type, title_en, title_bn, description_en, description_bn,
			  rules_en, rules_bn, paper_language, live_at, ends_at, mode, duration_minutes, pass_percent, written_maximum,
			  question_stored_file_name, question_original_file_name, question_byte_size, question_content_type, status,
			  finally_saved, price, is_free, discount_percent, discount_ends_at, access_days, popular, preview_enabled,
			  negative_marks, instructor_id, answers_released_at, published_at, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BN', ?, ?, ?, 20, 40, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private void insertQuestions(Exam exam, Instant now) {
		List<DemoContentSeed.Question> questions = exam.questions;
		for (int index = 0; index < questions.size(); index++) {
			DemoContentSeed.Question question = questions.get(index);
			jdbc.update("""
					INSERT INTO live_exam_questions (id, exam_id, stem, marks, explanation, position, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
					""", questionId(exam.number, index + 1), exam.id(), question.stem(), MCQ_MARKS, question.explanation(), index,
					ts(now), ts(now));
			for (int option = 0; option < question.options().size(); option++) {
				jdbc.update("""
						INSERT INTO live_exam_options (id, question_id, option_text, correct, position, created_at, updated_at)
						VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
						""", optionId(exam.number, index + 1, option), questionId(exam.number, index + 1),
						question.options().get(option), option == question.correctIndex(), option, ts(now), ts(now));
			}
		}
	}

	private void insertPackageMembership(Instant now) {
		Map<Integer, Integer> seen = new HashMap<>();
		for (Exam exam : EXAMS) {
			for (int k = 0; k < exam.packages.size(); k++) {
				int pkg = exam.packages.get(k);
				UUID membership = k == 0 ? id('1', exam.number) : id('1', 100 + exam.number);
				int position = seen.merge(pkg, 1, Integer::sum) - 1;
				jdbc.update("INSERT INTO live_exam_package_exams (id, module_id, exam_id, position, added_at) VALUES (?, ?, ?, ?, ?)"
						+ " ON CONFLICT DO NOTHING", membership, id('b', pkg), exam.id(), position, ts(now));
			}
		}
	}

	private void insertQuestionBank(Instant now) {
		UUID bank = id('f', 9000);
		jdbc.update("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
				bank, BANK_NAME, ts(now), ts(now));
		List<DemoContentSeed.Question> items = bankItems();
		for (int n = 1; n <= items.size(); n++) {
			DemoContentSeed.Question item = items.get(n - 1);
			UUID question = id('f', 9000 + n);
			jdbc.update("""
					INSERT INTO questions (id, question_type, stem, explanation, marks, subject, created_at, updated_at)
					VALUES (?, 'MCQ', ?, ?, ?, 'Procedure', ?, ?) ON CONFLICT DO NOTHING
					""", question, item.stem(), item.explanation(), MCQ_MARKS, ts(now), ts(now));
			for (int option = 0; option < item.options().size(); option++) {
				jdbc.update("""
						INSERT INTO question_options (id, question_id, text, correct, position, created_at, updated_at)
						VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
						""", id('9', 9000L * 10 + n * 10L + option + 1), question, item.options().get(option),
						option == item.correctIndex(), option, ts(now), ts(now));
			}
			jdbc.update("""
					INSERT INTO question_bank_items (id, bank_id, question_id, position, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
					""", id('f', 9100 + n), bank, question, n - 1, ts(now), ts(now));
		}
	}

	private void insertFavourites(Instant now) {
		for (int k = 0; k < FAVOURITES.size(); k++) {
			Favourite favourite = FAVOURITES.get(k);
			boolean isExam = "EXAM".equals(favourite.kind());
			jdbc.update("""
					INSERT INTO student_favourites (id, student_id, exam_id, module_id, created_at) VALUES (?, ?, ?, ?, ?)
					ON CONFLICT DO NOTHING
					""", id('1', 101 + k), id('5', favourite.student()), isExam ? exam(favourite.target()).id() : null,
					isExam ? null : id('b', favourite.target()), ts(now));
		}
	}

	private void insertSittings(Schedule s, Instant now) {
		int nextId = 1;
		for (McqSitting sitting : MCQ_SITTINGS) {
			Exam exam = exam(sitting.exam());
			Instant start = exam.liveAt(s);
			BigDecimal score = BigDecimal.valueOf((long) MCQ_MARKS * sitting.right())
				.subtract(exam.negativeOrZero().multiply(BigDecimal.valueOf(sitting.wrong()))).setScale(2);
			UUID participant = id('7', nextId++);
			insertParticipant(participant, exam, sitting.student(), start, score, passed(score), null, null, null, null,
					Stage.SUBMITTED, now);
			for (int index = 0; index < MCQ_QUESTIONS; index++) {
				int right = exam.questions.get(index).correctIndex();
				int options = exam.questions.get(index).options().size();
				int pick;
				if (index < sitting.right()) {
					pick = right;
				}
				else if (index < sitting.right() + sitting.wrong()) {
					pick = (right + 1) % options;
				}
				else {
					continue;
				}
				jdbc.update("INSERT INTO live_exam_answers (participant_id, question_id, option_id) VALUES (?, ?, ?)"
						+ " ON CONFLICT DO NOTHING", participant, questionId(exam.number, index + 1),
						optionId(exam.number, index + 1, pick));
			}
			notices.mcqScore(id('5', sitting.student()), exam.id(), participant);
		}
		for (WrittenSitting sitting : WRITTEN_SITTINGS) {
			Exam exam = exam(sitting.exam());
			Instant start = exam.liveAt(s);
			UUID participant = id('7', nextId++);
			boolean marked = sitting.stage() == Stage.PUBLISHED || sitting.stage() == Stage.MARKED;
			BigDecimal score = marked ? BigDecimal.valueOf(sitting.score()).setScale(2) : null;
			Boolean passed = marked ? passed(score) : null;
			Instant published = sitting.stage() == Stage.PUBLISHED ? start.plus(Duration.ofHours(3)) : null;
			insertParticipant(participant, exam, sitting.student(), start, score, passed, sitting.comment(),
					marked ? id('6', exam.teacher) : null, marked ? start.plus(Duration.ofDays(1)) : null,
					published, sitting.stage(), now);
			if (sitting.stage() == Stage.PUBLISHED) {
				notices.writtenResult(id('5', sitting.student()), exam.id(), participant, published);
			}
		}
		// The retake: S3 may sit exam 12 again. It is granted on the published fail and not used yet.
		for (RetakeGrant retake : RETAKES) {
			jdbc.update("""
					INSERT INTO live_exam_retake_grants (id, exam_id, student_id, granted_by, reason, created_at)
					VALUES (?, ?, ?, ?, 'Sample retake after a published fail', ?) ON CONFLICT DO NOTHING
					""", id('0', retake.number()), exam(retake.exam()).id(), id('5', retake.student()), masterAdmin(),
					ts(at(s, retake.day(), retake.hour())));
			notices.retakeAvailable(exam(retake.exam()).id(), id('5', retake.student()));
		}
	}

	/** One sitting row. Written answers and marked scripts are sample PDFs; MCQ rows carry no files. */
	private void insertParticipant(UUID id, Exam exam, int student, Instant start, BigDecimal score, Boolean passed, String comment,
			UUID evaluatedBy, Instant evaluatedAt, Instant publishedAt, Stage stage, Instant now) {
		boolean submitted = stage != Stage.NOT_SUBMITTED;
		boolean written = exam.written;
		StoredPaper answer = written && (stage == Stage.PUBLISHED || stage == Stage.MARKED || stage == Stage.AWAITING)
				? store(exam.titleEn + " answer " + student + ".pdf", List.of("Sample answer script", "Exam: " + exam.titleEn,
						"Student number: " + student, "Sample copy only; not a real answer."))
				: null;
		StoredPaper marked = written && (stage == Stage.PUBLISHED || stage == Stage.MARKED) ? store(exam.titleEn + " marked " + student + ".pdf", List.of("Sample marked script", "Exam: " + exam.titleEn,
						"Marks: " + score + " / " + WRITTEN_FULL_MARKS, "Sample copy only; not a real result."))
				: null;
		jdbc.update(INSERT_SITTING, id, exam.id(), id('5', student), ts(start), ts(start.plus(Duration.ofMinutes(EXAM_MINUTES))),
				ts(submitted ? start.plus(Duration.ofMinutes(15)) : null), score, passed,
				submitted ? "SUBMITTED" : "FAILED_TO_SUBMIT", comment, evaluatedBy, ts(evaluatedAt), ts(publishedAt),
				answer == null ? null : answer.stored(), answer == null ? null : answer.original(),
				answer == null ? null : answer.size(), answer == null ? null : "application/pdf",
				marked == null ? null : marked.stored(), marked == null ? null : marked.original(),
				marked == null ? null : marked.size(), marked == null ? null : "application/pdf", ts(now), ts(now));
	}

	private static final String INSERT_SITTING = """
			INSERT INTO live_exam_participants (id, exam_id, student_id, kind, started_at, deadline_at, submitted_at, score,
			  passed, status, comment, evaluated_by, evaluated_at, published_at, answer_stored_file_name,
			  answer_original_file_name, answer_byte_size, answer_content_type, evaluated_stored_file_name,
			  evaluated_original_file_name, evaluated_byte_size, evaluated_content_type, created_at, updated_at)
			VALUES (?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
			""";

	private void insertOrders(Schedule s, Instant now) {
		for (Order order : ORDERS) {
			UUID id = id('c', order.number());
			boolean module = "MODULE".equals(order.scope());
			UUID student = id('5', order.student());
			Instant created = at(s, order.day(), order.hour());
			boolean succeeded = "SUCCEEDED".equals(order.status());
			jdbc.update(INSERT_ORDER, id, student, order.scope(), module ? id('b', order.target()) : null,
					module ? null : exam(order.target()).id(), new BigDecimal(order.amount()).setScale(2), "SAMPLE-O" + order.number(),
					order.status(), succeeded ? "SAMPLE-VAL-" + order.number() : null, succeeded ? "BKASH-BKash" : null, ts(created),
					ts(created), order.listPrice() == 0 ? null : new BigDecimal(order.listPrice()).setScale(2),
					order.discount() == 0 ? null : order.discount());
			if (succeeded) {
				jdbc.update("""
						UPDATE live_exam_orders SET paid_at = created_at,
						  receipt_no = 'RCPT-' || to_char(created_at AT TIME ZONE 'Asia/Dhaka', 'YYYY') || '-'
						    || lpad(nextval('live_exam_receipt_seq')::text, 6, '0')
						WHERE id = ? AND receipt_no IS NULL
						""", id);
				jdbc.update(INSERT_GRANT, id('d', order.number()), student, order.scope(), module ? id('b', order.target()) : null,
						module ? null : exam(order.target()).id(), id, ts(created), "ORDER");
				notices.purchaseGranted(student, module ? id('b', order.target()) : exam(order.target()).id(), order.scope());
			}
		}
		for (int k = 0; k < MANUAL_GRANTS.size(); k++) {
			ManualGrant grant = MANUAL_GRANTS.get(k);
			boolean module = "MODULE".equals(grant.scope());
			UUID target = module ? id('b', grant.target()) : exam(grant.target()).id();
			jdbc.update(INSERT_GRANT, id('d', grant.number()), id('5', grant.student()), grant.scope(), module ? target : null,
					module ? null : target, null, ts(at(s, grant.day(), grant.hour())), "MANUAL");
			notices.accessGranted(id('5', grant.student()), target, grant.scope());
		}
	}

	private static final String INSERT_ORDER = """
			INSERT INTO live_exam_orders (id, student_id, scope, module_id, exam_id, amount, currency, method, tran_id, status,
			  val_id, payment_channel, created_at, updated_at, list_price, discount_percent)
			VALUES (?, ?, ?, ?, ?, ?, 'BDT', 'SSLCOMMERZ', ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private static final String INSERT_GRANT = """
			INSERT INTO live_exam_grants (id, student_id, scope, module_id, exam_id, order_id, created_at, source)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
			""";

	private void insertReviews(Schedule s, Instant now) {
		for (Review review : REVIEWS) {
			boolean isExam = "EXAM".equals(review.kind());
			UUID target = isExam ? exam(review.target()).id() : id('b', review.target());
			boolean moderated = !"PENDING".equals(review.status());
			jdbc.update("""
					INSERT INTO live_exam_reviews (id, exam_id, module_id, student_id, rating, comment, status, hide_reason, featured,
					  moderated_by, moderated_at, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
					""", id('3', review.number()), isExam ? target : null, isExam ? null : target, id('5', review.student()),
					review.rating(), review.comment(), review.status(), review.hideReason(), review.featured(),
					moderated ? masterAdmin() : null, moderated ? ts(at(s, review.day(), 12)) : null,
					ts(at(s, review.day(), 12)), ts(at(s, review.day(), 12)));
		}
	}

	private void insertAnnouncements(Schedule s, Instant now) {
		UUID master = masterAdmin();
		for (Announcement announcement : ANNOUNCEMENTS) {
			UUID id = id('8', announcement.number());
			boolean module = "MODULE".equals(announcement.audience());
			boolean isExam = "EXAM".equals(announcement.audience());
			Instant created = at(s, announcement.day(), 10);
			jdbc.update("""
					INSERT INTO announcements (id, title_en, title_bn, body_en, body_bn, audience, module_id, exam_id, created_by,
					  created_at, withdrawn_at, withdrawn_by)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
					""", id, announcement.titleEn(), announcement.titleBn(), announcement.bodyEn(), announcement.bodyBn(),
					announcement.audience(), module ? id('b', announcement.target()) : null,
					isExam ? exam(announcement.target()).id() : null, master, ts(created),
					announcement.withdrawn() ? ts(at(s, -1, 9)) : null, announcement.withdrawn() ? master : null);
			// One ANNOUNCEMENT notice per recipient, by the product's holder rule. The rule is the SQL condition the send uses.
			List<UUID> students = Arrays.stream(announcement.students()).mapToObj(n -> id('5', n)).toList();
			MapSqlParameterSource params = new MapSqlParameterSource().addValue("announcementId", id)
				.addValue("createdAt", ts(created))
				.addValue("moduleId", module ? id('b', announcement.target()) : null)
				.addValue("examId", isExam ? exam(announcement.target()).id() : null)
				.addValue("studentIds", students)
				.addValue("now", ts(now));
			String audience = announcement.audience();
			named.update("INSERT INTO student_notices (id, user_id, kind, message_key, exam_id, module_id, participant_id, created_at,"
					+ " announcement_id)"
					+ " SELECT gen_random_uuid(), u.id, 'ANNOUNCEMENT', CAST(u.id AS VARCHAR) || ':' || CAST(:announcementId AS VARCHAR),"
					+ " " + (isExam ? ":examId" : "NULL") + ", " + (module ? ":moduleId" : "NULL") + ", NULL, :createdAt, :announcementId"
					+ " FROM users u WHERE " + AnnouncementAudience.recipients(audience)
					+ " ON CONFLICT (kind, message_key) DO NOTHING", params);
		}
	}

	// ------------------------------------------------------------------------------------------------------------------------------
	// Helpers.
	// ------------------------------------------------------------------------------------------------------------------------------

	private static Schedule s(Instant now) {
		return new Schedule(now, now.atZone(DemoContentSeed.DHAKA).toLocalDate());
	}

	/** The discount end: the last second of the thirtieth day after the run day, in Dhaka time. */
	private static Instant discountEnds(Schedule s) {
		return s.today().plusDays(DISCOUNT_DAYS).atTime(23, 59, 59).atZone(DemoContentSeed.DHAKA).toInstant();
	}

	private static Instant at(Schedule s, int days, int hour) {
		return s.today().plusDays(days).atTime(hour, 0).atZone(DemoContentSeed.DHAKA).toInstant();
	}

	private static boolean passed(BigDecimal score) {
		return score.compareTo(BigDecimal.valueOf(MCQ_FULL_MARKS * PASS_PERCENT / 100)) >= 0;
	}

	private static String rules(Exam exam, boolean english) {
		if (exam.written) {
			return english
					? "1. Write your answers on paper, scan them as one PDF and upload it.\n2. The time limit is 20 minutes. You may upload"
						+ " again inside it; the last upload counts.\n3. Uploads after the deadline are refused."
					: "১. উত্তর কাগজে লিখে একটি PDF ফাইলে স্ক্যান করে আপলোড করুন।\n২. সময়সীমা ২০ মিনিট; এর মধ্যে আবার আপলোড করা যায়, শেষেরটি গণ্য।\n"
						+ "৩. সময়সীমার পরে আপলোড নেওয়া হবে না।";
		}
		String negative = exam.negative == null ? "" : " Each wrong answer loses " + exam.negative + " marks.";
		String negativeBn = exam.negative == null ? "" : " প্রতি ভুল উত্তরে " + exam.negative + " নম্বর কাটা যাবে।";
		return english
				? "1. Read every question before you answer.\n2. The time limit is 20 minutes, and the paper stays open for the same time."
					+ "\n3. Answers are submitted automatically when the time ends." + negative
				: "১. উত্তর দেওয়ার আগে প্রতিটি প্রশ্ন পড়ুন।\n২. সময়সীমা ২০ মিনিট; পরীক্ষা একই সময় পর্যন্ত খোলা থাকে।\n"
					+ "৩. সময় শেষ হলে উত্তর নিজে থেকে জমা হয়।" + negativeBn;
	}

	private StoredPaper paper(Exam exam) {
		return store(exam.titleEn + ".pdf", List.of("Sample question paper: " + exam.titleEn,
				"Duration: " + EXAM_MINUTES + " minutes; full marks: " + WRITTEN_FULL_MARKS,
				"Instructions: answer every question on paper and upload one PDF.", "1. Explain the main procedure for this topic.",
				"2. Describe one case where the rule applies, and one where it does not.", "3. Write short notes on two key terms."));
	}

	/** A sample PDF, written through the shared helper of the demo seed (storePdf), as a row-ready stored file. */
	private StoredPaper store(String name, List<String> lines) {
		LocalFileStorage.StoredFile file = pdfs.storePdf(name, lines);
		return new StoredPaper(file.storedFileName(), file.originalFileName(), file.byteSize());
	}

	/** An MCQ paper's questions: the first ten of a list that must have ten. */
	private static List<DemoContentSeed.Question> ten(List<DemoContentSeed.Question> questions) {
		if (questions.size() < MCQ_QUESTIONS) {
			throw new IllegalStateException("A sample MCQ paper needs ten questions.");
		}
		return questions.subList(0, MCQ_QUESTIONS);
	}

	private static List<DemoContentSeed.Question> concat(List<DemoContentSeed.Question> first, List<DemoContentSeed.Question> second) {
		return Stream.concat(first.stream(), second.stream()).toList();
	}

	static Exam exam(int number) {
		return EXAMS.stream().filter(exam -> exam.number == number).findFirst().orElseThrow();
	}

	static UUID id(char table, long n) {
		return DemoContentSeed.demo(table, n);
	}

	private static UUID questionId(int examNo, int number) {
		return id('f', examNo * 100L + number);
	}

	private static UUID optionId(int examNo, int number, int option) {
		return id('9', (examNo * 100L + number) * 10 + option + 1);
	}

	private static Timestamp ts(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	/** The dated instants of the schedule. The window is open from yesterday to 59 days ahead, or dated on its day. */
	record Schedule(Instant now, java.time.LocalDate today) {
	}

	enum Window { DATED, OPEN, UNDATED, PRACTICE }

	enum Stage { SUBMITTED, PUBLISHED, MARKED, AWAITING, NOT_SUBMITTED }

	record CategorySpec(int number, int parent, int position, String titleEn, String titleBn) {

		UUID id() {
			return SampleSeed.id('a', number);
		}
	}

	record PackageSpec(int number, int category, int price, int discount, boolean popular, int teacher, String titleEn, String titleBn,
			String descEn, String descBn) {

		UUID id() {
			return SampleSeed.id('b', number);
		}
	}

	record McqSitting(int exam, int student, int right, int wrong) {
	}

	record WrittenSitting(int exam, int student, Stage stage, int score, String comment) {
	}

	record Order(int number, int student, String scope, int target, int amount, int listPrice, int discount, String status, int day,
			int hour) {

		boolean grant() {
			return "SUCCEEDED".equals(status);
		}
	}

	record ManualGrant(int number, int student, String scope, int target, int day, int hour) {
	}

	record RetakeGrant(int number, int student, int exam, int day, int hour) {

		UUID examId() {
			return SampleSeed.exam(exam).id();
		}
	}

	record Review(int number, String kind, int target, int student, int rating, String comment, String status, String hideReason,
			boolean featured, int day) {
	}

	record Favourite(int student, String kind, int target) {
	}

	record Announcement(int number, String audience, int target, int[] students, String titleEn, String titleBn, String bodyEn,
			String bodyBn, int day, boolean withdrawn) {
	}

	record StoredPaper(String stored, String original, Long size) {
	}

	/** One exam in the spec. Built with the methods of section 5; see {@link #EXAMS}. */
	static final class Exam {

		final int number;

		final int category;

		final boolean written;

		final String titleEn;

		final String titleBn;

		final String descEn;

		final String descBn;

		Window window = Window.DATED;

		int day;

		boolean free;

		Integer price;

		int discount;

		boolean popular;

		boolean preview;

		Integer accessDays;

		BigDecimal negative;

		int teacher;

		boolean released;

		boolean draft;

		List<Integer> packages = List.of();

		List<DemoContentSeed.Question> questions = List.of();

		private Exam(int number, int category, boolean written, String titleEn, String titleBn, String descEn, String descBn) {
			this.number = number;
			this.category = category;
			this.written = written;
			this.titleEn = titleEn;
			this.titleBn = titleBn;
			this.descEn = descEn;
			this.descBn = descBn;
		}

		UUID id() {
			return SampleSeed.id('e', number);
		}

		Exam dated(int days) {
			this.window = Window.DATED;
			this.day = days;
			return this;
		}

		Exam open() {
			this.window = Window.OPEN;
			return this;
		}

		Exam undated() {
			this.window = Window.UNDATED;
			return this;
		}

		Exam practice() {
			this.window = Window.PRACTICE;
			return this;
		}

		Exam free() {
			this.free = true;
			return this;
		}

		Exam price(int amount) {
			this.price = amount;
			return this;
		}

		Exam discount(int percent) {
			this.discount = percent;
			return this;
		}

		Exam popular() {
			this.popular = true;
			return this;
		}

		Exam preview() {
			this.preview = true;
			return this;
		}

		Exam access(int days) {
			this.accessDays = days;
			return this;
		}

		Exam negative(String marks) {
			this.negative = new BigDecimal(marks);
			return this;
		}

		Exam teacher(int teacherNumber) {
			this.teacher = teacherNumber;
			return this;
		}

		Exam released() {
			this.released = true;
			return this;
		}

		Exam draft() {
			this.draft = true;
			return this;
		}

		Exam in(int... packageNumbers) {
			this.packages = Arrays.stream(packageNumbers).boxed().toList();
			return this;
		}

		Exam questions(List<DemoContentSeed.Question> list) {
			this.questions = list;
			return this;
		}

		Instant liveAt(Schedule s) {
			return switch (window) {
				case DATED -> at(s, day, 23);
				case OPEN -> at(s, -1, 23);
				case UNDATED, PRACTICE -> null;
			};
		}

		Instant endsAt(Schedule s) {
			Instant start = liveAt(s);
			if (start == null) {
				return null;
			}
			if (window == Window.OPEN) {
				return at(s, OPEN_DAYS_AHEAD, 23).plus(Duration.ofMinutes(EXAM_MINUTES));
			}
			return start.plus(Duration.ofMinutes(EXAM_MINUTES));
		}

		BigDecimal negativeOrZero() {
			return negative == null ? BigDecimal.ZERO : negative;
		}
	}

	private static Exam mcq(int number, int category, String titleEn, String titleBn, String descEn, String descBn) {
		return new Exam(number, category, false, titleEn, titleBn, descEn, descBn);
	}

	private static Exam written(int number, int category, String titleEn, String titleBn, String descEn, String descBn) {
		return new Exam(number, category, true, titleEn, titleBn, descEn, descBn);
	}

}
