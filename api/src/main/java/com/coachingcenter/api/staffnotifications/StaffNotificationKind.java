package com.coachingcenter.api.staffnotifications;

/**
 * The kinds of staff notification (staff pay plan, section 5, phase 1). The list is fixed in code: the web builds each row's
 * text from its kind and payload. The payload names the fields each kind carries. Names, amounts, counts and dates only:
 * never an account number, routing number or wallet number.
 */
public enum StaffNotificationKind {

	/** To the Master Admin: a person added, changed, removed or preferred a payout account. Payload: personName, change. */
	PAYOUT_ACCOUNT_CHANGED,

	/** To the Master Admin: results published and the instructor is owed. Payload: personName, examTitleEn, examTitleBn, sheets, amount, balance. */
	MARKING_PUBLISHED_OWED,

	/** To the instructor: results published and the sheets are earned. Payload: examTitleEn, examTitleBn, sheets, amount. */
	MARKING_PUBLISHED_EARNED,

	/** To the person paid: a payment was recorded. Payload: amount, method. */
	PAYMENT_RECORDED,

	/** To the person paid: a payment was voided. Payload: amount, method. */
	PAYMENT_VOIDED,

	/** To the Master Admin: a salary is due soon and not yet paid. Payload: personName, period, amount, dueOn. */
	SALARY_DUE_SOON,

	/** To the Master Admin: a salary is past its due date and not yet paid. Payload: personName, period, amount, dueOn. */
	SALARY_OVERDUE,

	/** To the Master Admin: an instructor's marking has been unpaid for too long. Payload: personName, amount, days. */
	MARKING_UNPAID_REMINDER,

	/** To the instructor: made the marker for a written exam. Payload: examTitleEn, examTitleBn. */
	EXAM_ASSIGNED,

	/** To the instructor no longer the marker for a written exam. Payload: examTitleEn, examTitleBn. */
	EXAM_UNASSIGNED,

	/**
	 * To the assigned instructor: the exam's pay per sheet changed. Payload: examTitleEn, examTitleBn, previousSheetRate,
	 * sheetRate.
	 */
	EXAM_RATE_CHANGED,

	/**
	 * To every Master Admin except the one who made the change: a written exam's marker or pay per sheet changed. Payload:
	 * personName, examTitleEn, examTitleBn, previousInstructorName, instructorName, previousSheetRate, sheetRate.
	 */
	EXAM_PAY_TERMS_CHANGED,

	/**
	 * To the Master Admin: an installment payment was recorded on a direct enrolment. Payload: studentName, titleEn, titleBn,
	 * amount, dueOn (the next installment's date, or null when nothing is left), due (what is still owed).
	 */
	INSTALLMENT_PAYMENT_RECORDED,

	/**
	 * To the Master Admin: an unpaid installment falls due soon. Payload: studentName, titleEn, titleBn, amount (what is
	 * uncovered on that installment), dueOn, due (what is still owed on the plan).
	 */
	INSTALLMENT_DUE_SOON,

	/**
	 * To the Master Admin: an unpaid installment is past its date. Same payload as {@link #INSTALLMENT_DUE_SOON}. Sent weekly.
	 */
	INSTALLMENT_OVERDUE,

	/** To the Master Admin and every System Admin: a student sent in an offline admission form. Payload: personName. */
	ADMISSION_SUBMITTED,

	/** To the Master Admin and every System Admin: a student sent a corrected offline admission form. Payload: personName. */
	ADMISSION_RESUBMITTED,

	/** To every Master Admin: staff marked an offline admission ready for onboarding. Payload: personName. */
	ADMISSION_READY,

	/** To whoever marked an offline admission ready, when someone else pressed "Back to review". Payload: personName. */
	ADMISSION_RETURNED,

	/** To whoever marked an offline admission ready, when the Master Admin onboarded it. Payload: personName. */
	ADMISSION_ONBOARDED,

	/** To the Master Admin: a staff payment is recorded and waits for approval. Payload: personName, amount, method. */
	OFFLINE_PAYMENT_PENDING,

	/** To the person who recorded an offline payment: the Master Admin approved it. Payload: personName, amount, method. */
	OFFLINE_PAYMENT_APPROVED,

	/** To the person who recorded an offline payment: the Master Admin rejected it. Payload: personName, amount, reason. */
	OFFLINE_PAYMENT_REJECTED,

	/** To every Master Admin and System Admin: an offline admission fee is 3 days overdue. Payload: personName, amount, dueOn. Sent weekly after that. */
	OFFLINE_FEE_OVERDUE,

	/**
	 * To every Master Admin and System Admin: offline classes are waiting for a payment or a not-taken mark (plan section 7, daily at 21:00
	 * Dhaka). Payload: count (the unsettled classes). The Master Admin also gets waiting (the entries that wait for approval) when there
	 * are any. Sent once per person per day.
	 */
	OFFLINE_CLASSES_TO_SETTLE,

	/**
	 * To every Master Admin and System Admin: held offline classes have waited more than a week, sent on Sunday at 21:00 Dhaka. Payload:
	 * count. Sent once per person per ISO week.
	 */
	OFFLINE_CLASSES_OVERDUE,

	/**
	 * To the person who recorded offline payments or not-taken marks: the Master Admin approved or rejected them. One notice per decision
	 * batch. Payload: approved, rejected (the counts in that batch).
	 */
	OFFLINE_ENTRIES_DECIDED
}
