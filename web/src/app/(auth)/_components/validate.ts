/** Client-side checks that mirror the API rules. The server decides; these only help the student sooner. */

const PHONE = /^(01\d{9}|\+8801\d{9})$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function normalizePhone(value: string): string {
  return value.trim().replace(/[\s-]/g, "");
}

export function isPhone(value: string): boolean {
  return PHONE.test(normalizePhone(value));
}

export function isEmail(value: string): boolean {
  return EMAIL.test(value.trim());
}

/** Passwords are 8 to 72 characters, counted as the student typed them. */
export function isPasswordLength(value: string): boolean {
  return value.length >= 8 && value.length <= 72;
}

/**
 * Groups a phone number as the student types it: 017 1234 5678, or +880 and
 * the digits. Digits past the limit are dropped so the field never grows.
 */
export function formatPhone(value: string): string {
  const compact = normalizePhone(value);
  if (compact.startsWith("+")) {
    return "+" + compact.slice(1).replace(/\D/g, "").slice(0, 13);
  }
  const digits = compact.replace(/\D/g, "").slice(0, 11);
  const match = digits.match(/^(\d{1,3})(\d{0,4})(\d{0,4})$/);
  return match ? [match[1], match[2], match[3]].filter(Boolean).join(" ") : digits;
}

/** How far the password is from the 8 to 72 rule, for a live hint. */
export function passwordStatus(value: string): { length: number; short: number; ok: boolean } {
  const length = value.length;
  return { length, short: Math.max(0, 8 - length), ok: isPasswordLength(value) };
}
