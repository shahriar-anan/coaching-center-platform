import type { AdmissionState } from "@/lib/api/exam/offline-admission";
import type { PaymentStatus } from "@/lib/api/exam/offline-admin";

/*
 * The message keys for the states and payment statuses. Each key is written out in full so the catalogue check can find it.
 */

export function stateLabelKey(state: AdmissionState): string {
  switch (state) {
    case "AWAITING_FORM":
      return "staffOffline.states.AWAITING_FORM";
    case "SUBMITTED":
      return "staffOffline.states.SUBMITTED";
    case "CHANGES_REQUESTED":
      return "staffOffline.states.CHANGES_REQUESTED";
    case "READY":
      return "staffOffline.states.READY";
    case "ONBOARDED":
      return "staffOffline.states.ONBOARDED";
    case "CLOSED":
      return "staffOffline.states.CLOSED";
  }
}

export function paymentStatusKey(status: PaymentStatus): string {
  switch (status) {
    case "PENDING":
      return "staffOffline.paymentStatus.PENDING";
    case "APPROVED":
      return "staffOffline.paymentStatus.APPROVED";
    case "REJECTED":
      return "staffOffline.paymentStatus.REJECTED";
    case "VOIDED":
      return "staffOffline.paymentStatus.VOIDED";
  }
}

export function methodLabelKey(method: "CASH" | "BKASH" | "NAGAD" | "ROCKET" | "BANK"): string {
  switch (method) {
    case "CASH":
      return "staffOffline.method.CASH";
    case "BKASH":
      return "staffOffline.method.BKASH";
    case "NAGAD":
      return "staffOffline.method.NAGAD";
    case "ROCKET":
      return "staffOffline.method.ROCKET";
    case "BANK":
      return "staffOffline.method.BANK";
  }
}

export function dueStateKey(state: "PAID" | "UNPAID" | "PART_PAID"): string {
  switch (state) {
    case "PAID":
      return "staffOffline.dueState.PAID";
    case "UNPAID":
      return "staffOffline.dueState.UNPAID";
    case "PART_PAID":
      return "staffOffline.dueState.PART_PAID";
  }
}

export function checklistLabelKey(key: "form" | "batchId" | "monthlyFee" | "admittedOn" | "firstDueOn" | "courseEndsOn"): string {
  switch (key) {
    case "form":
      return "staffOffline.checklist.rows.form";
    case "batchId":
      return "staffOffline.checklist.rows.batchId";
    case "monthlyFee":
      return "staffOffline.checklist.rows.monthlyFee";
    case "admittedOn":
      return "staffOffline.checklist.rows.admittedOn";
    case "firstDueOn":
      return "staffOffline.checklist.rows.firstDueOn";
    case "courseEndsOn":
      return "staffOffline.checklist.rows.courseEndsOn";
  }
}
