"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";

// The staff pay ledger (staff pay plan, phase 3). Paths and field names follow the API contract. Money comes as JSON numbers.
// Keys live in this file and all start with "staffPay", so one invalidation refreshes every pay screen.

export type PayRole = "SYSTEM_ADMIN" | "INSTRUCTOR";
export type PayMethod = "BANK" | "BKASH" | "NAGAD" | "ROCKET";
export type LedgerKind = "MARKING" | "SALARY" | "ADJUSTMENT" | "PAYMENT";

/** A System Admin or instructor in the people table. `payNotSetUp` means a salary or a live payout account is missing. */
export interface PayPersonRow {
  id: string;
  fullName: string;
  role: PayRole;
  status: string;
  deleted: boolean;
  balance: number;
  overdue: number;
  lastPaymentOn: string | null;
  payNotSetUp: boolean;
}

export interface PaySummary {
  /** Earned or due, net of adjustments. */
  charged: number;
  paid: number;
  /** Charged minus paid. Negative when paid in advance. */
  balance: number;
  owedNow: number;
  overdue: number;
  lastPaymentOn: string | null;
}

export interface PaySettings {
  monthlySalary: number | null;
  payDay: number | null;
  salaryStart: string | null;
  salaryEnd: string | null;
  /** The instructor's own override; null when the default applies. */
  sheetRate: number | null;
  /** The instructor's rate now: the override, else the default. Null for a System Admin. */
  effectiveSheetRate: number | null;
}

export interface PayPayoutAccount {
  id: string;
  kind: PayMethod;
  accountName: string | null;
  accountNumber: string | null;
  bankName: string | null;
  branch: string | null;
  routingNumber: string | null;
  walletNumber: string | null;
  preferred: boolean;
}

export interface PayPersonDetail {
  id: string;
  fullName: string;
  role: PayRole;
  status: string;
  deleted: boolean;
  payNotSetUp: boolean;
  settings: PaySettings;
  summary: PaySummary;
  nextPayDate: string | null;
  preferredAccount: PayPayoutAccount | null;
}

export interface PayLedgerRow {
  id: string;
  kind: LedgerKind;
  amount: number;
  dueOn: string;
  createdAt: string;
  paidOn: string | null;
  method: PayMethod | null;
  accountSnapshot: string | null;
  reference: string | null;
  note: string | null;
  voided: boolean;
  voidedAt: string | null;
  voidReason: string | null;
}

export interface PayDefaults {
  sheetRate: number;
  unpaidReminderDays: number;
  salaryDueSoonDays: number;
  updatedAt: string;
}

export interface PaySelfSummary {
  role: PayRole;
  summary: PaySummary;
  monthlySalary: number | null;
  payDay: number | null;
  nextPayDate: string | null;
  /** What is due on the next pay date: the uncovered part of that month, or the salary when the month has no line yet. Null for an instructor. */
  nextPayAmount: number | null;
}

/** One salary month. `status` is PAID, DUE or OVERDUE; `paid` is the part of `amount` already covered. */
export interface PaySalaryMonth {
  period: string;
  dueOn: string;
  amount: number;
  paid: number;
  uncovered: number;
  status: "PAID" | "DUE" | "OVERDUE";
}

/** A System Admin's salary (phase 5). Dates are Dhaka dates; the months run newest first. */
export interface PaySalaryView {
  monthlySalary: number | null;
  payDay: number | null;
  salaryStart: string | null;
  salaryEnd: string | null;
  nextPayDate: string | null;
  nextPayAmount: number | null;
  months: PaySalaryMonth[];
}

export interface PaymentBody {
  amount: number;
  paidOn?: string;
  method: PayMethod;
  accountId?: string;
  reference?: string;
  note?: string;
  allowAdvance?: boolean;
}

export interface AdjustmentBody {
  amount: number;
  note: string;
}

export interface SettingsBody {
  monthlySalary?: number | null;
  payDay?: number | null;
  salaryStart?: string | null;
  salaryEnd?: string | null;
  sheetRate?: number | null;
}

export interface DefaultsBody {
  sheetRate: number;
  unpaidReminderDays: number;
  salaryDueSoonDays: number;
}

/** The ledger page size. The API default is 20. */
export const PAY_LEDGER_PAGE = 20;

export const staffPayKeys = {
  all: ["staffPay"] as const,
  people: (role: string, owing: boolean) => ["staffPay", "people", role, owing ? "OWING" : "ALL"] as const,
  person: (userId: string) => ["staffPay", "person", userId] as const,
  ledger: (userId: string, page: number) => ["staffPay", "ledger", userId, page] as const,
  accounts: (userId: string) => ["staffPay", "accounts", userId] as const,
  defaults: () => ["staffPay", "defaults"] as const,
  mySummary: () => ["staffPay", "me", "summary"] as const,
  myLedger: (page: number) => ["staffPay", "me", "ledger", page] as const,
  salary: (userId: string) => ["staffPay", "salary", userId] as const,
  mySalary: () => ["staffPay", "me", "salary"] as const,
};

/** The Master Admin's view of one System Admin's salary. */
export function paySalaryPath(userId: string): string {
  return `/api/v1/staff-pay/people/${userId}/salary`;
}

/** The signed-in System Admin's own salary. */
export const MY_SALARY_PATH = "/api/v1/me/pay/salary";

/** Paths for the people table: the role filter and the owing-only flag. */
export function payPeoplePath(role: PayRole | "ALL", owing: boolean): string {
  const query = new URLSearchParams();
  if (role !== "ALL") {
    query.set("role", role);
  }
  if (owing) {
    query.set("owing", "true");
  }
  const text = query.toString();
  return text ? `/api/v1/staff-pay/people?${text}` : "/api/v1/staff-pay/people";
}

export function payPersonPath(userId: string): string {
  return `/api/v1/staff-pay/people/${userId}`;
}

export function payLedgerPath(userId: string, page: number): string {
  return `/api/v1/staff-pay/people/${userId}/ledger?page=${page}&size=${PAY_LEDGER_PAGE}`;
}

export function myLedgerPath(page: number): string {
  return `/api/v1/me/pay/ledger?page=${page}&size=${PAY_LEDGER_PAGE}`;
}

export function usePayPeople(role: PayRole | "ALL", owing: boolean) {
  return useQuery({
    queryKey: staffPayKeys.people(role, owing),
    queryFn: ({ signal }) => apiRequest<PayPersonRow[]>(payPeoplePath(role, owing), { signal }),
    placeholderData: keepPreviousData,
  });
}

export function usePayPerson(userId: string) {
  return useQuery({
    queryKey: staffPayKeys.person(userId),
    queryFn: ({ signal }) => apiRequest<PayPersonDetail>(payPersonPath(userId), { signal }),
  });
}

export function usePayLedger(userId: string, page: number) {
  return useQuery({
    queryKey: staffPayKeys.ledger(userId, page),
    queryFn: ({ signal }) => apiRequest<PageResponse<PayLedgerRow>>(payLedgerPath(userId, page), { signal }),
    placeholderData: keepPreviousData,
  });
}

/** The person's payout accounts, read only, with the numbers in full (the Master Admin sees them; he never writes them). */
export function usePayPayoutAccounts(userId: string) {
  return useQuery({
    queryKey: staffPayKeys.accounts(userId),
    queryFn: ({ signal }) =>
      apiRequest<PayPayoutAccount[]>(`/api/v1/staff-pay/people/${userId}/payout-accounts`, { signal }),
  });
}

export function usePayDefaults() {
  return useQuery({
    queryKey: staffPayKeys.defaults(),
    queryFn: ({ signal }) => apiRequest<PayDefaults>("/api/v1/staff-pay/defaults", { signal }),
  });
}

export function useMyPaySummary() {
  return useQuery({
    queryKey: staffPayKeys.mySummary(),
    queryFn: ({ signal }) => apiRequest<PaySelfSummary>("/api/v1/me/pay/summary", { signal }),
  });
}

export function useMyPayLedger(page: number) {
  return useQuery({
    queryKey: staffPayKeys.myLedger(page),
    queryFn: ({ signal }) => apiRequest<PageResponse<PayLedgerRow>>(myLedgerPath(page), { signal }),
    placeholderData: keepPreviousData,
  });
}

/** A System Admin's salary months and next pay date, for the Master Admin's person page. */
export function usePaySalary(userId: string) {
  return useQuery({
    queryKey: staffPayKeys.salary(userId),
    queryFn: ({ signal }) => apiRequest<PaySalaryView>(paySalaryPath(userId), { signal }),
  });
}

/** The signed-in System Admin's salary months and next pay date. */
export function useMySalary() {
  return useQuery({
    queryKey: staffPayKeys.mySalary(),
    queryFn: ({ signal }) => apiRequest<PaySalaryView>(MY_SALARY_PATH, { signal }),
  });
}

function useInvalidatePay() {
  const client = useQueryClient();
  return () => client.invalidateQueries({ queryKey: staffPayKeys.all });
}

/** Records a payment. Every pay screen refreshes after it settles. */
export function useRecordPayment(userId: string) {
  const invalidate = useInvalidatePay();
  return useMutation({
    mutationFn: (body: PaymentBody) =>
      apiRequest<PayLedgerRow>(`/api/v1/staff-pay/people/${userId}/payments`, { method: "POST", body }),
    retry: false,
    onSettled: () => invalidate(),
  });
}

export function useAddAdjustment(userId: string) {
  const invalidate = useInvalidatePay();
  return useMutation({
    mutationFn: (body: AdjustmentBody) =>
      apiRequest<PayLedgerRow>(`/api/v1/staff-pay/people/${userId}/adjustments`, { method: "POST", body }),
    retry: false,
    onSettled: () => invalidate(),
  });
}

/** Voids a payment or an adjustment with a reason. */
export function useVoidLine() {
  const invalidate = useInvalidatePay();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiRequest<PayLedgerRow>(`/api/v1/staff-pay/ledger/${id}/void`, { method: "POST", body: { reason } }),
    retry: false,
    onSettled: () => invalidate(),
  });
}

export function useSavePaySettings(userId: string) {
  const invalidate = useInvalidatePay();
  return useMutation({
    mutationFn: (body: SettingsBody) =>
      apiRequest<PaySettings>(`/api/v1/staff-pay/people/${userId}/settings`, { method: "PUT", body }),
    retry: false,
    onSettled: () => invalidate(),
  });
}

export function useSavePayDefaults() {
  const invalidate = useInvalidatePay();
  return useMutation({
    mutationFn: (body: DefaultsBody) =>
      apiRequest<PayDefaults>("/api/v1/staff-pay/defaults", { method: "PUT", body }),
    retry: false,
    onSettled: () => invalidate(),
  });
}
