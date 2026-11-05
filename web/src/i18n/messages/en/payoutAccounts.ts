import type { MessageTree } from "../types";

/** Payout accounts on the staff account page (staff pay plan, phase 2): the list, the form and their messages. */
const payoutAccounts = {
  title: "Payout accounts",
  intro: "Where your pay is sent: a bank account or a mobile wallet. Only you can add or change them. The Master Admin is told whenever they change.",
  emptyTitle: "Not set yet",
  emptyBody: "Add a bank account or a bKash, Nagad or Rocket number so your payments can reach you.",
  add: "Add account",
  edit: "Edit",
  remove: "Remove",
  makePreferred: "Make preferred",
  preferred: "Preferred",
  limitReached: "You can keep up to 5 payout accounts. Remove one to add another.",
  saved: "Payout account saved.",
  removed: "Payout account removed.",
  preferredDone: "Preferred account changed.",
  kind: {
    BANK: "Bank account",
    BKASH: "bKash",
    NAGAD: "Nagad",
    ROCKET: "Rocket",
  },
  kindLabel: "Account type",
  field: {
    accountName: "Account name",
    accountNumber: "Account number",
    bankName: "Bank name",
    branch: "Branch",
    routingNumber: "Routing number",
    walletNumber: "Mobile number",
  },
  hint: {
    accountNumber: "6 to 20 digits.",
    routingNumber: "9 digits.",
    walletNumber: "The number registered with the wallet, such as 017XXXXXXXX.",
  },
  dialog: {
    addTitle: "Add a payout account",
    editTitle: "Edit payout account",
    save: "Save",
    cancel: "Cancel",
  },
  errors: {
    required: "This is needed.",
    accountNumber: "Enter 6 to 20 digits.",
    routingNumber: "Enter the 9-digit routing number.",
    walletNumber: "Enter a valid mobile number, such as 017XXXXXXXX.",
    invalid: "Check the details and try again.",
    limit: "You already have 5 payout accounts.",
    generic: "The account could not be saved. Try again.",
  },
  removeDialog: {
    title: "Remove this payout account?",
    body: "Payments will no longer go to it.",
    confirm: "Remove",
  },
} satisfies MessageTree;

export default payoutAccounts;
