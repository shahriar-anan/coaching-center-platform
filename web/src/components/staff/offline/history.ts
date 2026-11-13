/*
 * The History tab (plan 6.3). The API builds the timeline: each audit event, payment and submission of the admission, newest first, with
 * who did it. This file only names each action for the screen. An action it does not know shows as a generic line.
 */

const LABEL_KEY: Record<string, string> = {
  OFFLINE_ADMISSION_CREATED: "staffOffline.history.kinds.created",
  OFFLINE_ADMISSION_SUBMITTED: "staffOffline.history.kinds.submitted",
  OFFLINE_ADMISSION_RESUBMITTED: "staffOffline.history.kinds.resubmitted",
  OFFLINE_ADMISSION_CHANGES_REQUESTED: "staffOffline.history.kinds.returned",
  OFFLINE_ADMISSION_SETUP_CHANGED: "staffOffline.history.kinds.setupChanged",
  OFFLINE_ADMISSION_ITEM_ADDED: "staffOffline.history.kinds.itemAdded",
  OFFLINE_ADMISSION_ITEM_REMOVED: "staffOffline.history.kinds.itemRemoved",
  OFFLINE_ADMISSION_READY: "staffOffline.history.kinds.ready",
  OFFLINE_ADMISSION_BACK_TO_REVIEW: "staffOffline.history.kinds.backToReview",
  OFFLINE_ADMISSION_ONBOARDED: "staffOffline.history.kinds.onboarded",
  OFFLINE_ADMISSION_TERMS_CHANGED: "staffOffline.history.kinds.termsChanged",
  OFFLINE_ADMISSION_CLOSED: "staffOffline.history.kinds.closed",
  OFFLINE_ADMISSION_SIGNED_UPLOADED: "staffOffline.history.kinds.signed",
  OFFLINE_FEE_PAYMENT_RECORDED: "staffOffline.history.kinds.payment",
  OFFLINE_FEE_PAYMENT_APPROVED: "staffOffline.history.kinds.paymentApproved",
  OFFLINE_FEE_PAYMENT_REJECTED: "staffOffline.history.kinds.paymentRejected",
  OFFLINE_FEE_PAYMENT_VOIDED: "staffOffline.history.kinds.paymentVoided",
};

/** The i18n key of one history action, as the API names it. */
export function historyLabelKey(action: string): string {
  return LABEL_KEY[action] ?? "staffOffline.history.kinds.other";
}
