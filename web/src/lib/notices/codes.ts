import type { ToastTone } from "@/components/ui/toast";

/**
 * Notices carried across a redirect as `?notice=<code>`. Each code names its
 * copy key in the catalog messages (English and Bangla) and the toast tone.
 * A code not listed here is ignored.
 */
export const NOTICE_CODES = {
  submitted: { key: "catalog.notice.submitted", tone: "ok" },
  timeUp: { key: "catalog.notice.timeUp", tone: "info" },
  timeUpScript: { key: "catalog.notice.timeUpScript", tone: "info" },
  timeUpNoScript: { key: "catalog.notice.timeUpNoScript", tone: "danger" },
} as const satisfies Record<string, { key: string; tone: ToastTone }>;

export type NoticeCode = keyof typeof NOTICE_CODES;

export function isNoticeCode(value: string | null): value is NoticeCode {
  return value !== null && Object.prototype.hasOwnProperty.call(NOTICE_CODES, value);
}
