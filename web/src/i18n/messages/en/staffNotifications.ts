import type { MessageTree } from "../types";

/** The staff notification centre: the bell, the list page, its tabs, and the text of each kind (staff pay plan, phase 1). */
const staffNotifications = {
  title: "Notifications",
  bell: "Notifications",
  bellCount: "Notifications, {count} unread",
  tabs: {
    label: "Notification list",
    UNREAD: "Unread",
    ALL: "All",
  },
  markAll: "Mark all read",
  markedAll: "All notifications are marked as read.",
  markAllFailed: "The notifications could not be marked as read. Try again.",
  unreadDot: "Unread",
  empty: "No notifications yet.",
  emptyBody: "Notices about payments and results will show here.",
  emptyUnread: "No unread notifications.",
  emptyUnreadBody: "You are all caught up.",
  sheetsOne: "1 sheet",
  sheetsMany: "{count} sheets",
  change: {
    ADDED: "added",
    UPDATED: "changed",
    REMOVED: "removed",
    PREFERRED: "set a preferred",
    other: "changed",
  },
  method: {
    BANK: "bank transfer",
    BKASH: "bKash",
    NAGAD: "Nagad",
    ROCKET: "Rocket",
  },
  kind: {
    PAYOUT_ACCOUNT_CHANGED: "{person} {change} a payout account.",
    MARKING_PUBLISHED_OWED: "Results published for {exam}. You owe {person} {amount} for {sheets}. Balance now {balance}.",
    MARKING_PUBLISHED_EARNED: "Published for {exam}: {sheets}. You earned {amount}.",
    PAYMENT_RECORDED: "You were paid {amount} by {method}.",
    PAYMENT_VOIDED: "The payment of {amount} by {method} was cancelled.",
    SALARY_DUE_SOON: "{person}'s salary for {month} ({amount}) is due on {date}.",
    SALARY_OVERDUE: "{person}'s salary for {month} ({amount}) is overdue. It was due on {date}.",
    MARKING_UNPAID_REMINDER: "{person} has been waiting {days} days to be paid {amount} for marked sheets.",
    EXAM_ASSIGNED: "You are now the marker for {exam}.",
    EXAM_UNASSIGNED: "You are no longer the marker for {exam}. Sheets you already marked are still paid.",
    EXAM_RATE_CHANGED: "Pay for {exam} changed from {from} to {to} per sheet, for sheets published from now on.",
    EXAM_PAY_TERMS_CHANGED: "{person} changed {exam}: marker {fromMarker} → {toMarker}, pay {from} → {to} per sheet.",
    NO_MARKER: "none",
    UNKNOWN: "You have a new notification.",
  },
} satisfies MessageTree;

export default staffNotifications;
