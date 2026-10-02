import type { ApiErrorBody } from "./types";

export class ApiError extends Error {
  readonly code: string;
  readonly requestId: string;
  readonly fieldErrors?: ApiErrorBody["fieldErrors"];

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.code = body.code;
    this.requestId = body.requestId;
    this.fieldErrors = body.fieldErrors;
  }
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
    CODE_EXPIRED: "errors.codeExpired",
    CODE_ALREADY_USED: "errors.codeAlreadyUsed",
    CODE_ATTEMPTS_EXCEEDED: "errors.codeAttemptsExceeded",
  };
  return known[code] ?? "errors.generic";
}
