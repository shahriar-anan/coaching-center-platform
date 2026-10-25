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
