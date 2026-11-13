import { ApiError } from "@/lib/api/errors";

/*
 * The API names each problem with a field and an English message. The student reads Bangla or English, so the messages the
 * form can get are mapped to the i18n keys here. Any other message is shown as the API wrote it.
 */

export type Translate = (key: string, params?: Record<string, string | number>) => string;

const KEY_BY_MESSAGE: Record<string, string> = {
  "This field is required.": "admission.errors.required",
  "Enter your date of birth.": "admission.errors.dateOfBirthMissing",
  "The date of birth cannot be in the future.": "admission.errors.dateInFuture",
  "Enter the date as year-month-day.": "admission.errors.dateFormat",
  "Enter a valid email address.": "admission.errors.email",
  "Choose male or female.": "admission.errors.gender",
  "The national ID number must have 10, 13 or 17 digits.": "admission.errors.nid",
  "Enter a Bangladeshi mobile number, such as 01711000012.": "admission.errors.mobile",
  "Add your photo. Take one with the camera or choose one from the gallery.": "admission.errors.photo",
};

const TOO_LONG = /^This is longer than (\d+) characters\.$/;

export function messageText(message: string, t: Translate): string {
  const key = KEY_BY_MESSAGE[message];
  if (key) {
    return t(key);
  }
  const long = TOO_LONG.exec(message);
  if (long) {
    return t("admission.errors.tooLong", { max: Number(long[1]) });
  }
  return message;
}

/** The field errors an API error carries, keyed by field. The first message for a field is kept. */
export function fieldErrorsOf(error: unknown, t: Translate): Record<string, string> {
  const out: Record<string, string> = {};
  if (!(error instanceof ApiError)) {
    return out;
  }
  for (const detail of error.fieldErrors ?? []) {
    if (!(detail.field in out)) {
      out[detail.field] = messageText(detail.message, t);
    }
  }
  return out;
}
