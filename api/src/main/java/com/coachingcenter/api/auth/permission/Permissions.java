package com.coachingcenter.api.auth.permission;

public final class Permissions {

	public static final String STUDENT_READ = "STUDENT_READ";

	public static final String STUDENT_CREATE = "STUDENT_CREATE";

	public static final String STUDENT_UPDATE = "STUDENT_UPDATE";

	public static final String STUDENT_STATUS = "STUDENT_STATUS";

	public static final String STUDENT_ISSUE_ACTIVATION_CODE = "STUDENT_ISSUE_ACTIVATION_CODE";

	public static final String STUDENT_DELETE = "STUDENT_DELETE";

	/** Give a student a password reset code to hand over in person, while email does not arrive (16 Nov). The Master Admin only. */
	public static final String STUDENT_PASSWORD_RESET = "STUDENT_PASSWORD_RESET";

	public static final String ADMIN_MANAGE = "ADMIN_MANAGE";

	public static final String ADMIN_DELETE = "ADMIN_DELETE";

	public static final String AUDIT_READ = "AUDIT_READ";

	/** The Master Admin's configuration status page: names and yes or no only. */
	public static final String SYSTEM_STATUS_READ = "SYSTEM_STATUS_READ";

	public static final String COURSE_READ = "COURSE_READ";

	public static final String COURSE_MANAGE = "COURSE_MANAGE";

	public static final String COURSE_PRICE = "COURSE_PRICE";

	public static final String COURSE_CLOSE = "COURSE_CLOSE";

	public static final String COURSE_DELETE = "COURSE_DELETE";

	public static final String BOOK_READ = "BOOK_READ";

	public static final String BOOK_MANAGE = "BOOK_MANAGE";

	public static final String BOOK_PRICE = "BOOK_PRICE";

	public static final String BOOK_DELETE = "BOOK_DELETE";

	public static final String CATALOG_BROWSE = "CATALOG_BROWSE";

	public static final String CONTENT_READ = "CONTENT_READ";

	public static final String PURCHASE_CREATE = "PURCHASE_CREATE";

	public static final String PAYMENT_VERIFY = "PAYMENT_VERIFY";

	public static final String CASH_RECEIVE = "CASH_RECEIVE";

	public static final String ENROLLMENT_GRANT = "ENROLLMENT_GRANT";

	public static final String ORDER_FULFILL = "ORDER_FULFILL";

	public static final String EXAM_READ = "EXAM_READ";

	public static final String EXAM_MANAGE = "EXAM_MANAGE";

	/** Publish an exam into the catalog. The Master Admin only; the System Admin keeps EXAM_MANAGE without it. */
	public static final String EXAM_PUBLISH = "EXAM_PUBLISH";

	/** Upload and remove a module's cover image. The Master Admin only. */
	public static final String MODULE_COVER_MANAGE = "MODULE_COVER_MANAGE";

	public static final String EXAM_PRICE = "EXAM_PRICE";

	public static final String EXAM_DELETE = "EXAM_DELETE";

	public static final String EXAM_EVALUATE = "EXAM_EVALUATE";

	public static final String QUESTION_BANK_MANAGE = "QUESTION_BANK_MANAGE";

	public static final String LIVE_EXAM_BROWSE = "LIVE_EXAM_BROWSE";

	public static final String LIVE_EXAM_ROSTER = "LIVE_EXAM_ROSTER";

	public static final String LIVE_EXAM_ASSIGN = "LIVE_EXAM_ASSIGN";

	public static final String LIVE_EXAM_EVALUATE = "LIVE_EXAM_EVALUATE";

	public static final String LIVE_EXAM_PUBLISH = "LIVE_EXAM_PUBLISH";

	public static final String LIVE_EXAM_ORDER_READ = "LIVE_EXAM_ORDER_READ";

	public static final String LIVE_EXAM_GRANT = "LIVE_EXAM_GRANT";

	public static final String LIVE_EXAM_REVOKE = "LIVE_EXAM_REVOKE";

	public static final String LIVE_EXAM_ORDER_CONFIRM = "LIVE_EXAM_ORDER_CONFIRM";

	/** Records a refund already made in the SSLCommerz merchant panel (plan section 5). The site never refunds money itself. */
	public static final String LIVE_EXAM_ORDER_REFUND = "LIVE_EXAM_ORDER_REFUND";

	public static final String LIVE_EXAM_RELEASE = "LIVE_EXAM_RELEASE";

	/** Direct enrolment payments and installment plans (plan direct-enrolment-installments). Master Admin only. */
	public static final String LIVE_EXAM_INSTALLMENT = "LIVE_EXAM_INSTALLMENT";

	public static final String INSTRUCTOR_STATUS = "INSTRUCTOR_STATUS";

	public static final String INSTRUCTOR_DELETE = "INSTRUCTOR_DELETE";

	public static final String LIVE_EXAM_ANNOUNCE = "LIVE_EXAM_ANNOUNCE";

	public static final String LIVE_EXAM_POPULAR = "LIVE_EXAM_POPULAR";

	public static final String LIVE_EXAM_RETAKE_GRANT = "LIVE_EXAM_RETAKE_GRANT";

	public static final String LIVE_EXAM_PACKAGE_EDIT = "LIVE_EXAM_PACKAGE_EDIT";

	public static final String LIVE_EXAM_FAVOURITE = "LIVE_EXAM_FAVOURITE";

	/** Write a review of an exam or package the student has sat. The student only. */
	public static final String LIVE_EXAM_REVIEW_WRITE = "LIVE_EXAM_REVIEW_WRITE";

	/** Read the review queue, and approve, hide or feature reviews. System Admins and the Master Admin. */
	public static final String LIVE_EXAM_REVIEW_MODERATE = "LIVE_EXAM_REVIEW_MODERATE";

	/** Read the reviews of the instructor's own exams. Read only. */
	public static final String LIVE_EXAM_REVIEW_READ = "LIVE_EXAM_REVIEW_READ";

	/** Create, read and edit drafts of the public notice board, and delete drafts. System Admins and the Master Admin. */
	public static final String SITE_NOTICE_EDIT = "SITE_NOTICE_EDIT";

	/** Publish, unpublish, edit or delete a published notice. The Master Admin only. */
	public static final String SITE_NOTICE_PUBLISH = "SITE_NOTICE_PUBLISH";

	/** Read one's own staff notifications and mark them read. Every staff role; students are not granted it. */
	public static final String STAFF_NOTICE_READ = "STAFF_NOTICE_READ";

	/** Everyone's pay: settings, payments, adjustments, voids and the defaults. The Master Admin only. */
	public static final String STAFF_PAY_MANAGE = "STAFF_PAY_MANAGE";

	/** Read and change one's own pay pages and payout accounts. System Admins and instructors; the Master Admin is not paid through it. */
	public static final String STAFF_PAY_SELF = "STAFF_PAY_SELF";

	/** Offline admission: the queue, the form, the setup, printing and the signed copy. Master Admin and System Admin. */
	public static final String OFFLINE_ADMISSION_READ = "OFFLINE_ADMISSION_READ";

	/** Offline admission: send back, edit the setup, mark ready, back to review, upload the signed copy. Master Admin and System Admin. */
	public static final String OFFLINE_ADMISSION_REVIEW = "OFFLINE_ADMISSION_REVIEW";

	/** Offline admission: onboard, change terms after onboarding, add or remove items, close. The Master Admin only. */
	public static final String OFFLINE_ADMISSION_ONBOARD = "OFFLINE_ADMISSION_ONBOARD";

	/** Offline batches: create, edit and archive. The Master Admin only. */
	public static final String OFFLINE_BATCH_MANAGE = "OFFLINE_BATCH_MANAGE";

	/** Offline batches: view, and upload or replace the routine. Master Admin and System Admin. */
	public static final String OFFLINE_BATCH_ROUTINE = "OFFLINE_BATCH_ROUTINE";

	/** Offline exam results: upload a result sheet, discard a staged one, and read the sheets. Master Admin and System Admin. */
	public static final String OFFLINE_RESULT_UPLOAD = "OFFLINE_RESULT_UPLOAD";

	/** Offline exam results: publish a staged sheet, which students then see. The Master Admin only. */
	public static final String OFFLINE_RESULT_PUBLISH = "OFFLINE_RESULT_PUBLISH";

	/** Offline fees: record a payment (a System Admin's waits for approval). Master Admin and System Admin. */
	public static final String OFFLINE_FEE_RECORD = "OFFLINE_FEE_RECORD";

	/** Offline fees: approve, reject and void payments. The Master Admin only. */
	public static final String OFFLINE_FEE_APPROVE = "OFFLINE_FEE_APPROVE";

	/**
	 * Offline instructors and their pay (24 Nov): add, edit and archive an instructor, and record, edit and void a payment to one. The
	 * Master Admin only. Pay no longer comes from the classes.
	 */
	public static final String OFFLINE_INSTRUCTOR_PAY = "OFFLINE_INSTRUCTOR_PAY";

	/** Finance: the income and expenditure pages, online and offline. The Master Admin only (plan G3). */
	public static final String FINANCE_READ = "FINANCE_READ";

	/** Accounting (plan 6.1): every accounting page and route. The Master Admin only; System Admins have none of it. */
	public static final String ACCOUNTING = "ACCOUNTING";

	private Permissions() {
	}

}
