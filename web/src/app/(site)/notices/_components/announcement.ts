/** An announcement's text, as the notice carries it (both languages; either may be blank). */
export type AnnouncementText = {
  titleEn: string | null;
  titleBn: string | null;
  bodyEn: string | null;
  bodyBn: string | null;
};

/**
 * The title and body in the student's language. A blank field falls back to the
 * other language, so a notice always reads, even when one language was left empty.
 */
export function announcementText(
  announcement: AnnouncementText | null,
  locale: "bn" | "en",
): { title: string; body: string } {
  if (!announcement) return { title: "", body: "" };
  const pick = (primary: string | null, fallback: string | null) =>
    primary?.trim() || fallback?.trim() || "";
  return locale === "bn"
    ? { title: pick(announcement.titleBn, announcement.titleEn), body: pick(announcement.bodyBn, announcement.bodyEn) }
    : { title: pick(announcement.titleEn, announcement.titleBn), body: pick(announcement.bodyEn, announcement.bodyBn) };
}

/** Lines shown before "আরও দেখুন". */
export const PREVIEW_LINES = 3;

/** Only a body longer than the preview needs the expand control. */
export function isLongBody(body: string): boolean {
  return body.split(/\r?\n/).length > PREVIEW_LINES;
}

export function previewBody(body: string): string {
  return body.split(/\r?\n/).slice(0, PREVIEW_LINES).join("\n");
}
