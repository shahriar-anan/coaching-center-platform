import { ApiError } from "@/lib/api/errors";
import { staffAdminErrorKey } from "@/lib/api/exam/staff-admin";

/**
 * The sentence to show for a failed admin call. A known API code gets its own
 * `staffAdmin` message; anything else gets the generic one, never the raw code.
 */
export function adminErrorText(error: unknown, t: (key: string) => string): string {
  if (error instanceof ApiError) {
    return t(`staffAdmin.${staffAdminErrorKey(error.code)}`);
  }
  return t("staffAdmin.errors.generic");
}
