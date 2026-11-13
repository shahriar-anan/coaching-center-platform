import { ApiError } from "@/lib/api/errors";

/**
 * The sentence for a failed offline call. The API's own messages are English and are not shown: a conflict (someone else changed the
 * admission first), a refusal and anything else each get a Bangla or English sentence from the offline error messages.
 */
export function offlineErrorText(error: unknown, t: (key: string) => string): string {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      return t("staffOffline.errors.conflict");
    }
    if (error.status === 403) {
      return t("staffOffline.errors.denied");
    }
    if (error.status === 422 || error.status === 400) {
      return t("staffOffline.errors.check");
    }
  }
  return t("staffOffline.errors.generic");
}
