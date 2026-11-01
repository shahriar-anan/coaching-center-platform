package com.coachingcenter.api.exams;

/**
 * Who gets an announcement, and who may see one on a package or exam page (plan section 6). This is the one place that
 * defines a holder. The recipients of a send and the page check both use {@link #recipients(String)}, so they cannot drift.
 *
 * <p>Expiry is written as SQL, the same rule as {@link AccessWindow#end} and {@link LiveExamAccess} (the package rule is
 * {@code moduleEnd}): an exam's access ends {@code access_days} after the later of the grant and the window end (or the
 * publish time, for practice); a package's access ends when every paid exam it covers has run out, and a package with no
 * paid exam never ends. A revoked grant gives nothing. Every expression reads {@code :now}.
 *
 * <p>A started attempt makes a holder only on a free exam (a free package has no price, so it is never free; its free exams
 * count). A paid exam is open through a live grant or roster row, so an attempt on it does not outlive a revoked or expired
 * grant.
 */
final class AnnouncementAudience {

	static final String ALL = "ALL";

	static final String MODULE = "MODULE";

	static final String EXAM = "EXAM";

	static final String SELECTED = "SELECTED";

	/** An active, non-deleted student: the only kind of user an announcement ever reaches. */
	private static final String STUDENT = "u.role = 'STUDENT' AND u.status = 'ACTIVE' AND u.deleted_at IS NULL";

	private AnnouncementAudience() {
	}

	/**
	 * The condition over {@code users u} that picks the recipients of an audience. Named parameters: {@code :moduleId} for
	 * MODULE, {@code :examId} for EXAM, {@code :studentIds} for SELECTED, and {@code :now} for the expiry checks.
	 */
	static String recipients(String audience) {
		return switch (audience) {
			case ALL -> STUDENT;
			case MODULE -> STUDENT + " AND " + moduleHolder();
			case EXAM -> STUDENT + " AND " + examHolder();
			case SELECTED -> STUDENT + " AND u.id IN (:studentIds)";
			default -> throw new IllegalArgumentException("Unknown audience " + audience);
		};
	}

	/**
	 * Holds the package {@code :moduleId}: a roster row, a package grant, a grant on one of its exams, or a started attempt on
	 * one of its free exams. Each route must be live (see {@link #moduleLive}, {@link #examLive}).
	 */
	private static String moduleHolder() {
		return "(EXISTS (SELECT 1 FROM live_exam_roster r WHERE r.module_id = :moduleId AND r.student_id = u.id AND "
				+ moduleLive("r.created_at") + ")"
				+ " OR EXISTS (SELECT 1 FROM live_exam_grants g WHERE g.student_id = u.id AND g.scope = 'MODULE'"
				+ "   AND g.module_id = :moduleId AND g.revoked_at IS NULL AND " + moduleLive("g.created_at") + ")"
				+ " OR EXISTS (SELECT 1 FROM live_exam_grants g"
				+ "   JOIN live_exam_package_exams p ON p.exam_id = g.exam_id AND p.module_id = :moduleId AND p.removed_at IS NULL"
				+ "   JOIN live_exams x ON x.id = g.exam_id AND x.deleted_at IS NULL"
				+ "   WHERE g.student_id = u.id AND g.scope = 'EXAM' AND g.revoked_at IS NULL AND "
				+ examLive("g.created_at", "x") + ")"
				+ " OR EXISTS (SELECT 1 FROM live_exam_participants a"
				+ "   JOIN live_exam_package_exams p ON p.exam_id = a.exam_id AND p.module_id = :moduleId AND p.removed_at IS NULL"
				+ "   JOIN live_exams x ON x.id = a.exam_id AND x.deleted_at IS NULL AND x.is_free"
				+ "   WHERE a.student_id = u.id AND a.deleted_at IS NULL))";
	}

	/**
	 * Holds the exam {@code :examId}: a grant on the exam, a roster row or a package grant on a published package that contains
	 * it (or contained it when the student's access began, see {@link #stillCovers}), or a started attempt on it when it is free.
	 */
	private static String examHolder() {
		return "(EXISTS (SELECT 1 FROM live_exam_grants g JOIN live_exams te ON te.id = g.exam_id AND te.deleted_at IS NULL"
				+ "   WHERE g.student_id = u.id AND g.scope = 'EXAM' AND g.exam_id = :examId AND g.revoked_at IS NULL AND "
				+ examLive("g.created_at", "te") + ")"
				+ " OR EXISTS (SELECT 1 FROM live_exam_package_exams p"
				+ "   JOIN live_exam_modules m ON m.id = p.module_id AND m.deleted_at IS NULL AND m.status = 'PUBLISHED'"
				+ "   JOIN live_exam_roster r ON r.module_id = p.module_id AND r.student_id = u.id"
				+ "   JOIN live_exams te ON te.id = p.exam_id AND te.deleted_at IS NULL"
				+ "   WHERE p.exam_id = :examId AND " + stillCovers("r.created_at") + " AND " + examLive("r.created_at", "te") + ")"
				+ " OR EXISTS (SELECT 1 FROM live_exam_package_exams p"
				+ "   JOIN live_exam_modules m ON m.id = p.module_id AND m.deleted_at IS NULL AND m.status = 'PUBLISHED'"
				+ "   JOIN live_exam_grants g ON g.module_id = p.module_id AND g.scope = 'MODULE' AND g.student_id = u.id"
				+ "     AND g.revoked_at IS NULL"
				+ "   JOIN live_exams te ON te.id = p.exam_id AND te.deleted_at IS NULL"
				+ "   WHERE p.exam_id = :examId AND " + stillCovers("g.created_at") + " AND " + examLive("g.created_at", "te") + ")"
				+ " OR EXISTS (SELECT 1 FROM live_exam_participants a"
				+ "   JOIN live_exams te ON te.id = a.exam_id AND te.deleted_at IS NULL AND te.is_free"
				+ "   WHERE a.exam_id = :examId AND a.student_id = u.id AND a.deleted_at IS NULL))";
	}

	/**
	 * True when package membership row {@code p} still covers a roster row or grant that started at {@code start}: the exam is in
	 * the package now, or it was removed after the student's access began (LiveExamAccess.covers, R4). So a student who held the
	 * package before the exam was taken out of it keeps getting that exam's announcements, as they keep the exam itself.
	 */
	private static String stillCovers(String start) {
		return "(p.removed_at IS NULL OR " + start + " < p.removed_at)";
	}

	/**
	 * True while a package grant that started at {@code start} still runs (LiveExamAccess.moduleEnd). It runs when the package
	 * covers no paid exam (lifetime), or when one covered paid exam is still live.
	 */
	private static String moduleLive(String start) {
		return "(NOT EXISTS (SELECT 1 FROM live_exam_package_exams pc"
				+ "   JOIN live_exams xc ON xc.id = pc.exam_id AND xc.deleted_at IS NULL AND NOT xc.is_free"
				+ "   WHERE pc.module_id = :moduleId AND (pc.removed_at IS NULL OR " + start + " < pc.removed_at))"
				+ " OR EXISTS (SELECT 1 FROM live_exam_package_exams pl"
				+ "   JOIN live_exams xl ON xl.id = pl.exam_id AND xl.deleted_at IS NULL AND NOT xl.is_free"
				+ "   WHERE pl.module_id = :moduleId AND (pl.removed_at IS NULL OR " + start + " < pl.removed_at)"
				+ "   AND " + examLive(start, "xl") + "))";
	}

	/** True while access to exam {@code e} that started at {@code start} still runs (AccessWindow.end, not yet reached). */
	private static String examLive(String start, String e) {
		return "NOT " + examEnded(start, e);
	}

	/** True once the access has run out (AccessWindow.expired). A free exam has no end, and neither has a non-dated one. */
	private static String examEnded(String start, String e) {
		String anchor = "(CASE WHEN " + e + ".mode = 'PRACTICE' THEN " + e + ".published_at ELSE " + e + ".ends_at END)";
		return "(NOT " + e + ".is_free AND " + e + ".access_days IS NOT NULL AND " + anchor + " IS NOT NULL"
				+ " AND GREATEST(" + start + ", " + anchor + ") + " + e + ".access_days * INTERVAL '24 hours' <= :now)";
	}

}
