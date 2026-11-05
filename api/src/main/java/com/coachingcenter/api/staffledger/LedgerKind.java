package com.coachingcenter.api.staffledger;

/**
 * The kinds of money line (staff pay plan, section 3). The names are stored in {@code staff_ledger.kind}; a row's kind is
 * never changed after it is written.
 */
public enum LedgerKind {

	/** One published written answer sheet, paid at the rate it was earned at. Phase 4 writes these. */
	MARKING,

	/** One month of a System Admin's salary. Phase 5 writes these. */
	SALARY,

	/** A one-off bonus, advance, deduction or part month. Plus or minus, with a note. */
	ADJUSTMENT,

	/** Money paid to the person. Positive; it is subtracted from the balance. */
	PAYMENT

}
