import type { ApiErrorBody } from "./types";

export class ApiError extends Error {
  readonly code: string;
  readonly requestId: string;
  readonly fieldErrors?: ApiErrorBody["fieldErrors"];
  readonly orderId?: string;
  readonly tranId?: string;
  readonly details?: unknown;
  /** HTTP status of the response; 0 when the error did not come from a response. */
  readonly status: number;

  constructor(body: ApiErrorBody, status = 0) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.requestId = body.requestId;
    this.fieldErrors = body.fieldErrors;
    this.orderId = body.orderId;
    this.tranId = body.tranId;
    this.details = body.details;
  }
}

/** The API says there is no such item: a 404 or RESOURCE_NOT_FOUND. */
export function isNotFoundError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.code === "RESOURCE_NOT_FOUND");
}

/**
 * The address names no item: a missing one, or an id that is not a UUID, such as /exam/123123. The API answers the second
 * with 400 VALIDATION_FAILED (a path value that does not parse), not with 404. Only a read by id should use this.
 */
export function isUnknownItemError(error: unknown): boolean {
  return (
    isNotFoundError(error) ||
    (error instanceof ApiError && error.status === 400 && error.code === "VALIDATION_FAILED")
  );
}

/** No response at all (offline, server down): fetch rejects with a TypeError, and such an error is not an ApiError. */
export function isNetworkError(error: unknown): boolean {
  return error instanceof TypeError;
}

/** The server failed on its side: any 5xx answer. */
export function isServerError(error: unknown): boolean {
  return error instanceof ApiError && error.status >= 500;
}

/** Maps stable API error codes to i18n keys under errors.* */
export function errorMessageKey(code: string): string {
  const known: Record<string, string> = {
    INVALID_CREDENTIALS: "errors.invalidCredentials",
    LOGIN_LOCKED: "errors.loginLocked",
    UNAUTHENTICATED: "errors.unauthenticated",
    ACCESS_DENIED: "errors.accessDenied",
    VALIDATION_FAILED: "errors.validationFailed",
    DUPLICATE_PHONE: "errors.duplicatePhone",
    DUPLICATE_EMAIL: "errors.duplicateEmail",
    PASSWORD_NOT_ACCEPTED: "errors.passwordNotAccepted",
    RESOURCE_NOT_FOUND: "errors.notFound",
    MASTER_ADMIN_PROTECTED: "errors.masterAdminProtected",
    INVALID_CODE: "errors.invalidCode",
    CURRENT_PASSWORD_INVALID: "errors.currentPasswordInvalid",
    EMAIL_RATE_LIMITED: "errors.emailRateLimited",
    CODE_EXPIRED: "errors.codeExpired",
    CODE_ALREADY_USED: "errors.codeAlreadyUsed",
    CODE_ATTEMPTS_EXCEEDED: "errors.codeAttemptsExceeded",
    SECTION_NOT_EMPTY: "errors.sectionNotEmpty",
    CATEGORY_NOT_EMPTY: "errors.categoryNotEmpty",
    INVALID_YOUTUBE_URL: "errors.invalidYouTube",
    PRICE_REQUIRED: "errors.priceRequired",
  };
  return known[code] ?? "errors.generic";
}
