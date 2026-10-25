import type {
  CategoryNode,
  ExamType,
  MyStatus,
  PublicModule,
  RoutineRow,
} from "@/lib/api/exam/catalog";
import { dhakaParts } from "@/lib/format";

/** Calendar day (YYYY-MM-DD) of an instant, as seen in Dhaka. */
function dhakaDayKey(iso: string): string {
  const p = dhakaParts(iso);
  return `${p.year}-${String(p.month + 1).padStart(2, "0")}-${String(p.day).padStart(2, "0")}`;
}

export type Door = "mcq" | "written";

export function doorType(door: Door): ExamType {
  return door === "mcq" ? "MCQ" : "WRITTEN";
}

export function typeDoor(type: ExamType): Door {
  return type === "MCQ" ? "mcq" : "written";
}

export const doorLabelKey = (door: Door) => (door === "mcq" ? "catalog.doorMcq" : "catalog.doorWritten");
export const typeLabelKey = (type: ExamType) => (type === "MCQ" ? "catalog.typeMcq" : "catalog.typeWritten");

export const doorPath = (door: Door) => `/${door}`;
export const categoryPath = (door: Door, id: string) => `/${door}/c/${id}`;
export const modulePath = (door: Door, id: string) => `/${door}/m/${id}`;
export const examPath = (id: string) => `/exam/${id}`;

/** Path from a root to the category with this id, or null. */
export function findCategoryPath(tree: CategoryNode[], id: string): CategoryNode[] | null {
  for (const node of tree) {
    if (node.id === id) return [node];
    const below = findCategoryPath(node.children ?? [], id);
    if (below) return [node, ...below];
  }
  return null;
}

/** Category path down to the category that holds this module, or null. */
export function findModulePath(tree: CategoryNode[], moduleId: string): CategoryNode[] | null {
  for (const node of tree) {
    if ((node.modules ?? []).some((m) => m.id === moduleId)) return [node];
    const below = findModulePath(node.children ?? [], moduleId);
    if (below) return [node, ...below];
  }
  return null;
}

export function countModules(node: CategoryNode): number {
  return (node.modules ?? []).length + (node.children ?? []).reduce((n, c) => n + countModules(c), 0);
}

export function moduleTypeCount(module: PublicModule, type: ExamType): number | null {
  const value = type === "MCQ" ? module.mcqCount : module.writtenCount;
  return typeof value === "number" ? value : null;
}

export function firstLine(text: string): string {
  return text.split(/\r?\n/).find((line) => line.trim())?.trim() ?? "";
}

export type PillTone = "muted" | "accent" | "warn" | "ok" | "danger";

export interface StatusPill {
  key: string;
  tone: PillTone;
  pulse: boolean;
}

/** The status pill for an owned exam, from the brief's mapping table. */
export function statusPill(
  status: MyStatus,
  options: { examType: ExamType; retakeAvailable?: boolean | null },
): StatusPill {
  switch (status) {
    case "NOT_ANNOUNCED":
      return { key: "catalog.status.NOT_ANNOUNCED", tone: "muted", pulse: false };
    case "UPCOMING":
      return { key: "catalog.status.UPCOMING", tone: "muted", pulse: false };
    case "OPEN":
      return { key: "catalog.status.OPEN", tone: "warn", pulse: true };
    case "IN_PROGRESS":
      return { key: "catalog.status.IN_PROGRESS", tone: "warn", pulse: true };
    case "SUBMITTED":
      return { key: "catalog.status.SUBMITTED", tone: "ok", pulse: false };
    case "AWAITING_RESULT":
      return { key: "catalog.status.AWAITING_RESULT", tone: "warn", pulse: false };
    case "RESULT_READY":
      return { key: "catalog.status.RESULT_READY", tone: "ok", pulse: false };
    case "FAILED_TO_SUBMIT":
      return {
        key:
          options.examType === "WRITTEN"
            ? "catalog.status.FAILED_TO_SUBMIT_WRITTEN"
            : "catalog.status.FAILED_TO_SUBMIT",
        tone: "danger",
        pulse: false,
      };
    case "MISSED":
      return {
        key: options.retakeAvailable ? "catalog.status.MISSED_RETAKE" : "catalog.status.MISSED",
        tone: "muted",
        pulse: false,
      };
  }
}

const DONE: MyStatus[] = ["SUBMITTED", "AWAITING_RESULT", "RESULT_READY", "FAILED_TO_SUBMIT"];

export function isDone(status: MyStatus | null | undefined): boolean {
  return Boolean(status && DONE.includes(status));
}

export function isLive(row: { liveAt: string | null; endAt: string | null }, now: number): boolean {
  if (!row.liveAt || !row.endAt) return false;
  return Date.parse(row.liveAt) <= now && now < Date.parse(row.endAt);
}

/** Dated first (soonest first), undated last. */
export function bySchedule<T extends { liveAt: string | null }>(a: T, b: T): number {
  if (!a.liveAt && !b.liveAt) return 0;
  if (!a.liveAt) return 1;
  if (!b.liveAt) return -1;
  return Date.parse(a.liveAt) - Date.parse(b.liveAt);
}

export interface RoutineDay {
  key: string;
  /** "today", "tomorrow", or null for a plain date. */
  relative: "today" | "tomorrow" | null;
  firstStart: string;
  rows: RoutineRow[];
}

/** Rows still to come, grouped by Asia/Dhaka day; live rows first. */
export function groupRoutine(rows: RoutineRow[], now: number): { live: RoutineRow[]; days: RoutineDay[] } {
  const pending = rows.filter((row) => Date.parse(row.endAt) > now).sort(bySchedule);
  const live = pending.filter((row) => isLive(row, now));
  const later = pending.filter((row) => !isLive(row, now));
  const today = dhakaDayKey(new Date(now).toISOString());
  const tomorrow = dhakaDayKey(new Date(now + 86_400_000).toISOString());
  const days: RoutineDay[] = [];
  for (const row of later) {
    const key = dhakaDayKey(row.liveAt);
    let day = days.find((d) => d.key === key);
    if (!day) {
      day = {
        key,
        relative: key === today ? "today" : key === tomorrow ? "tomorrow" : null,
        firstStart: row.liveAt,
        rows: [],
      };
      days.push(day);
    }
    day.rows.push(row);
  }
  return { live, days };
}

function icsDate(iso: string): string {
  return new Date(iso).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

function icsText(text: string): string {
  return text.replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\r?\n/g, "\\n");
}

/** One VEVENT calendar file for an exam window. */
export function buildIcs(event: { id: string; title: string; start: string; end: string; url: string }): string {
  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Exam Portal//Routine//BN",
    "CALSCALE:GREGORIAN",
    "BEGIN:VEVENT",
    `UID:${event.id}@exam-portal`,
    `DTSTAMP:${icsDate(new Date().toISOString())}`,
    `DTSTART:${icsDate(event.start)}`,
    `DTEND:${icsDate(event.end)}`,
    `SUMMARY:${icsText(event.title)}`,
    `URL:${event.url}`,
    `DESCRIPTION:${icsText(event.url)}`,
    "END:VEVENT",
    "END:VCALENDAR",
    "",
  ].join("\r\n");
}

export function downloadIcs(fileName: string, content: string): void {
  const blob = new Blob([content], { type: "text/calendar;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
