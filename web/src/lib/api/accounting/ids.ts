/**
 * Fixed ids of the seeded groups and money accounts (docs/accounting/accounting-plan.md, 5.3). The same values as AccIds.java, so the
 * web can send people to a built-in group without a lookup. The Offline instructors group is the one the old addresses point at.
 */
export const ACC_GROUP_IDS = {
  OFFLINE_INSTRUCTORS: "a0000000-0000-4000-8000-000000000001",
  STAFF: "a0000000-0000-4000-8000-000000000002",
  SUPPLIERS: "a0000000-0000-4000-8000-000000000003",
  PERSONAL: "a0000000-0000-4000-8000-000000000004",
  OTHER_INCOME: "a0000000-0000-4000-8000-000000000005",
} as const;

export const ACC_ACCOUNT_IDS = {
  CASH: "a0000000-0000-4000-8000-000000000201",
  BKASH: "a0000000-0000-4000-8000-000000000202",
  NAGAD: "a0000000-0000-4000-8000-000000000203",
  ROCKET: "a0000000-0000-4000-8000-000000000204",
  BANK: "a0000000-0000-4000-8000-000000000205",
} as const;
