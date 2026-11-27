package com.coachingcenter.api.accounting;

import java.util.UUID;

/**
 * The fixed ids of the seeded accounting rows (V107_1, plan 5.3). The migration and these constants must agree. Groups are numbered 1 to
 * 5; a type's id is its group's digit then its position; accounts are 201 to 205.
 */
public final class AccIds {

	public static final UUID GROUP_OFFLINE_INSTRUCTORS = UUID.fromString("a0000000-0000-4000-8000-000000000001");

	public static final UUID GROUP_STAFF = UUID.fromString("a0000000-0000-4000-8000-000000000002");

	public static final UUID GROUP_SUPPLIERS = UUID.fromString("a0000000-0000-4000-8000-000000000003");

	public static final UUID GROUP_PERSONAL = UUID.fromString("a0000000-0000-4000-8000-000000000004");

	public static final UUID GROUP_OTHER_INCOME = UUID.fromString("a0000000-0000-4000-8000-000000000005");

	public static final UUID TYPE_LECTURE = UUID.fromString("a0000000-0000-4000-8000-000000000111");

	public static final UUID TYPE_EXAM = UUID.fromString("a0000000-0000-4000-8000-000000000112");

	public static final UUID TYPE_ANSWER_SHEET = UUID.fromString("a0000000-0000-4000-8000-000000000113");

	public static final UUID TYPE_QUESTION_MAKING = UUID.fromString("a0000000-0000-4000-8000-000000000114");

	public static final UUID TYPE_INSTRUCTOR_BONUS = UUID.fromString("a0000000-0000-4000-8000-000000000115");

	public static final UUID TYPE_INSTRUCTOR_DEDUCTION = UUID.fromString("a0000000-0000-4000-8000-000000000116");

	public static final UUID TYPE_INSTRUCTOR_OTHER = UUID.fromString("a0000000-0000-4000-8000-000000000117");

	public static final UUID TYPE_MONTHLY_SALARY = UUID.fromString("a0000000-0000-4000-8000-000000000121");

	public static final UUID TYPE_STAFF_OTHER = UUID.fromString("a0000000-0000-4000-8000-000000000126");

	public static final UUID TYPE_PERSONAL_OTHER = UUID.fromString("a0000000-0000-4000-8000-000000000146");

	public static final UUID ACCOUNT_CASH = UUID.fromString("a0000000-0000-4000-8000-000000000201");

	public static final UUID ACCOUNT_BKASH = UUID.fromString("a0000000-0000-4000-8000-000000000202");

	public static final UUID ACCOUNT_NAGAD = UUID.fromString("a0000000-0000-4000-8000-000000000203");

	public static final UUID ACCOUNT_ROCKET = UUID.fromString("a0000000-0000-4000-8000-000000000204");

	public static final UUID ACCOUNT_BANK = UUID.fromString("a0000000-0000-4000-8000-000000000205");

	private AccIds() {
	}

}
