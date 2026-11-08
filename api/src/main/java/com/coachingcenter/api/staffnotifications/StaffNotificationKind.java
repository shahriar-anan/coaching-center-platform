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
	INSTALLMENT_OVERDUE

}
