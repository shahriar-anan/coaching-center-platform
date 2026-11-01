import type { Door } from "@/components/student/catalog/catalog-helpers";

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

/** The page an announcement is about: one exam or one package. Null for an announcement to everyone or to chosen students. */
export type AnnouncementTarget = { kind: "exam"; id: string } | { kind: "package"; id: string };

/** The part of an announcement the target needs. A notice names at most one target, and the exam wins if a payload names both. */
export function announcementTarget(
  announcement: { moduleId: string | null; examId: string | null } | null,
): AnnouncementTarget | null {
  if (announcement?.examId) return { kind: "exam", id: announcement.examId };
  if (announcement?.moduleId) return { kind: "package", id: announcement.moduleId };
  return null;
}

/**
 * The door a package link opens. The package page lists the exams of one door, so a written-only package opens the written
 * door and does not land on an empty MCQ list. Every other package opens the MCQ door, as the other package links do.
 */
export function packageDoor(pkg: { mcqCount?: number; writtenCount?: number } | null | undefined): Door {
  return pkg?.writtenCount && !pkg.mcqCount ? "written" : "mcq";
}
