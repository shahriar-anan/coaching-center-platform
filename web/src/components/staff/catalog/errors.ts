import { ApiError } from "@/lib/api/errors";

/** Codes the catalog screens put a sentence to. Anything else shows the generic line. */
const KNOWN_CODES = new Set([
  "EXAM_WINDOW_CONFLICT",
  "EXAM_WINDOW_ENDED",
  "CATEGORY_NOT_EMPTY",
  "CATEGORY_CYCLE",
  "CATEGORY_ARCHIVED",
  "EXAM_HAS_PARTICIPANTS",
  "EXAM_HAS_PURCHASES",
  "INSTRUCTOR_ALREADY_ASSIGNED",
  "FINAL_SAVE_REQUIRED",
  "QUESTIONS_REQUIRED",
  "PASS_PERCENT_REQUIRED",
  "WRITTEN_PAPER_REQUIRED",
  "MODULE_ARCHIVED",
  "CONTENT_LOCKED",
  "PRICE_REQUIRED",
  "PRICE_NOT_ALLOWED",
  "FILE_TOO_LARGE",
  "ACCESS_DENIED",
  "RESOURCE_NOT_FOUND",
  "VALIDATION_FAILED",
]);

/** The message key for an error from a catalog call. */
export function catalogErrorKey(error: unknown): string {
  const code = error instanceof ApiError && KNOWN_CODES.has(error.code) ? error.code : "GENERIC";
  return `staffCatalog.errors.${code}`;
}

/** The message for a field the server named, or `null` when no field matched. */
export function fieldErrorFor(error: unknown, field: string): string | null {
  if (!(error instanceof ApiError) || !error.fieldErrors) {
    return null;
  }
  return error.fieldErrors.find((item) => item.field === field)?.message ?? null;
}

export function isApiError(error: unknown, code: string): boolean {
  return error instanceof ApiError && error.code === code;
}
