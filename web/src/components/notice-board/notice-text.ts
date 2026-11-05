import type { Locale } from "@/i18n/locale-context";
import type { NoticeTexts } from "@/lib/api/exam/site-notices";

// Rules and reading helpers for the notice board, shared by the staff editor and the public pages.
// Limits follow the API: a title is at most 300 characters, a description at most 10,000.

export const NOTICE_TITLE_MAX = 300;

export const NOTICE_BODY_MAX = 10_000;

export type NoticeField = keyof NoticeTexts;

export const NOTICE_FIELDS: NoticeField[] = ["titleEn", "titleBn", "bodyEn", "bodyBn"];

/** The longest a field may be, in characters. */
export function noticeFieldMax(field: NoticeField): number {
  return field.startsWith("title") ? NOTICE_TITLE_MAX : NOTICE_BODY_MAX;
}

/** The characters in a value, counting a Bangla or emoji character once. */
export function noticeLength(value: string): number {
  return [...value].length;
}

/** The fields that are blank, in the order the form shows them. A published notice needs all four. */
export function missingNoticeFields(texts: Partial<Record<NoticeField, string | null | undefined>>): NoticeField[] {
  return NOTICE_FIELDS.filter((field) => !texts[field]?.trim());
}

/** The fields longer than their limit. */
export function overlongNoticeFields(texts: Partial<Record<NoticeField, string | null | undefined>>): NoticeField[] {
  return NOTICE_FIELDS.filter((field) => noticeLength(texts[field] ?? "") > noticeFieldMax(field));
}

/** The text the form sends: titles trimmed, blank fields as null, descriptions kept as typed. */
export function noticeTextsForApi(draft: Record<NoticeField, string>): NoticeTexts {
  const clean = (field: NoticeField) => {
    const value = draft[field];
    if (!value.trim()) {
      return null;
    }
    return field.startsWith("title") ? value.trim() : value;
  };
  return { titleEn: clean("titleEn"), titleBn: clean("titleBn"), bodyEn: clean("bodyEn"), bodyBn: clean("bodyBn") };
}

/** The title in the reader's language, or the other language when that one is blank. */
export function noticeTitle(notice: Pick<NoticeTexts, "titleEn" | "titleBn">, locale: Locale): string {
  const primary = locale === "bn" ? notice.titleBn : notice.titleEn;
  const fallback = locale === "bn" ? notice.titleEn : notice.titleBn;
  return primary?.trim() || fallback?.trim() || "";
}

/** The description in the reader's language, or the other language when that one is blank. */
export function noticeBody(notice: Pick<NoticeTexts, "bodyEn" | "bodyBn">, locale: Locale): string {
  const primary = locale === "bn" ? notice.bodyBn : notice.bodyEn;
  const fallback = locale === "bn" ? notice.bodyEn : notice.bodyBn;
  return primary?.trim() ? primary : fallback ?? "";
}
