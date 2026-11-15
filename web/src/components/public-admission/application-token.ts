/*
 * The token of an application started without signing in, kept per batch in the tab's sessionStorage. Storage is only a
 * convenience: when it is blocked or empty the form still works, and the application starts again from the email.
 */

const PREFIX = "exam-portal.public-admission.";

export function readApplicationToken(batchId: string): string | null {
  try {
    return window.sessionStorage.getItem(`${PREFIX}${batchId}`);
  } catch {
    return null;
  }
}

export function writeApplicationToken(batchId: string, token: string): void {
  try {
    window.sessionStorage.setItem(`${PREFIX}${batchId}`, token);
  } catch {
    /* storage blocked: the token lives in the page only */
  }
}

export function clearApplicationToken(batchId: string): void {
  try {
    window.sessionStorage.removeItem(`${PREFIX}${batchId}`);
  } catch {
    /* nothing stored */
  }
}
