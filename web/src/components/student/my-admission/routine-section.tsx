"use client";

import { useEffect, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, Eye, EyeOff, FileText } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import {
  dhakaParts,
  formatDate,
  formatTimeRange,
  fromDhakaParts,
  pickTitle,
  toBengaliDigits,
  type DateInput,
} from "@/lib/format";
import { serverNow, useServerNow } from "@/lib/time/clock";
import { useMyAdmissionRoutine, loadAdmissionRoutinePdf, type StudentClass } from "@/lib/api/exam/my-admission";
import type { AdmissionView } from "@/lib/api/exam/offline-admission";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { ErrorState } from "@/components/ui/error-state";
import { IconButton } from "@/components/ui/icon-button";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { SectionCard, SectionEmpty, dhakaDate } from "./section-card";

/** How many months back the switcher goes: the current month and the three before it. */
const MONTHS_BACK = 3;
/** How many months ahead: the next month's routine is often uploaded before the month starts. */
const MONTHS_AHEAD = 1;

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"] as const;
type Weekday = (typeof WEEKDAYS)[number];

/** The yyyy-MM month of an instant, in Dhaka. */
export function monthOf(value: DateInput): string {
  const p = dhakaParts(value);
  return `${p.year}-${String(p.month + 1).padStart(2, "0")}`;
}

/** The yyyy-MM month `delta` months away from `month` (a negative delta goes back). */
export function shiftMonth(month: string, delta: number): string {
  const [year, number] = month.split("-").map(Number);
  const shifted = new Date(Date.UTC(year, number - 1 + delta, 1));
  return `${shifted.getUTCFullYear()}-${String(shifted.getUTCMonth() + 1).padStart(2, "0")}`;
}

/** "November 2026" / "নভেম্বর ২০২৬". The day that formatDate puts first is left out. */
export function monthLabel(month: string, locale: "en" | "bn"): string {
  return formatDate(`${month}-01`, locale, { year: "always" }).replace(/^\S+\s/, "");
}

/** The weekday of a plain date (yyyy-MM-dd), from the API's `day` when it is a known name, otherwise from the date. */
export function weekdayOf(item: Pick<StudentClass, "date" | "day">): Weekday {
  if ((WEEKDAYS as readonly string[]).includes(item.day)) {
    return item.day as Weekday;
  }
  const [y, m, d] = item.date.split("-").map(Number);
  return WEEKDAYS[new Date(Date.UTC(y, m - 1, d)).getUTCDay()];
}

/** The Dhaka instants of a class: its date with the start and the end time. Null when a part cannot be read. */
export function classInstants(item: StudentClass): { start: Date; end: Date } | null {
  const [year, month, day] = item.date.split("-").map(Number);
  const start = item.startsAt.split(":").map(Number);
  const end = item.endsAt.split(":").map(Number);
  if ([year, month, day, ...start, ...end].some((part) => Number.isNaN(part))) {
    return null;
  }
  const at = (clock: number[]) => fromDhakaParts({ year, month: month - 1, day, hour: clock[0], minute: clock[1] ?? 0 });
  return { start: at(start), end: at(end) };
}

/** The first class that has not ended yet, in time order, or null. */
export function nextClassOf(classes: readonly StudentClass[], now: number): StudentClass | null {
  let best: { item: StudentClass; start: number } | null = null;
  for (const item of classes) {
    const instants = classInstants(item);
    if (!instants || instants.end.getTime() <= now) continue;
    if (!best || instants.start.getTime() < best.start) {
      best = { item, start: instants.start.getTime() };
    }
  }
  return best?.item ?? null;
}

/** The classes grouped by date, the dates in order and each day's classes by start time. */
function byDay(classes: readonly StudentClass[]): { date: string; classes: StudentClass[] }[] {
  const days = new Map<string, StudentClass[]>();
  for (const item of [...classes].sort((a, b) => `${a.date}${a.startsAt}`.localeCompare(`${b.date}${b.startsAt}`))) {
    const list = days.get(item.date) ?? [];
    list.push(item);
    days.set(item.date, list);
  }
  return [...days.entries()].map(([date, list]) => ({ date, classes: list }));
}

/** "6:00 PM – 7:10 PM" / "সন্ধ্যা ৬:০০ – ৭:১০", or "" when the times cannot be read. */
export function classTimeText(item: StudentClass, locale: "en" | "bn"): string {
  const instants = classInstants(item);
  return instants ? formatTimeRange(instants.start, instants.end, locale) : "";
}

/** One class in a day: the time, the markers, the subject and the topic. Exams are tinted so they stand out in the month. */
function ClassRow({ item, isNext }: { item: StudentClass; isNext: boolean }) {
  const { t, locale } = useLocale();
  const isExam = item.kind === "EXAM";
  const topic = item.topic?.trim();
  return (
    <li
      data-class={item.subject}
      className={cn(
        "grid min-w-0 gap-0.5 rounded-control px-3 py-2",
        isExam ? "border-s-4 border-s-accent bg-accent/10" : "bg-bg",
        isNext && "ring-2 ring-ok/60",
      )}
    >
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className="text-meta tabular-nums text-muted">{classTimeText(item, locale)}</span>
        {isNext ? <Pill tone="ok">{t("myAdmission.routine.next")}</Pill> : null}
        {isExam ? <Pill tone="accent">{t("myAdmission.routine.exam")}</Pill> : null}
      </div>
      <p className="min-w-0 break-words font-semibold text-text">{item.subject}</p>
      {topic ? <p className="min-w-0 break-words text-meta text-muted">{topic}</p> : null}
    </li>
  );
}

/** The next exam that has not ended, from the classes given, or null. */
export function nextExamOf(classes: readonly StudentClass[], now: number): StudentClass | null {
  return nextClassOf(
    classes.filter((item) => item.kind === "EXAM"),
    now,
  );
}

/** Where the reader's choice to hide the routine is kept on this device. A missing or blocked store means "shown". */
const HIDDEN_KEY = "myAdmission.routineHidden";

function readHidden(): boolean {
  try {
    return window.localStorage.getItem(HIDDEN_KEY) === "1";
  } catch {
    return false;
  }
}

function writeHidden(hidden: boolean) {
  try {
    window.localStorage.setItem(HIDDEN_KEY, hidden ? "1" : "0");
  } catch {
    // A blocked store only means the choice is not remembered.
  }
}

/**
 * The routine folded away (15 Nov): only the next class and the next exam, from this month or the next, so a long routine does not make
 * the reader scroll. The same rows as the agenda, with their date.
 */
function NextOnly({ admissionId, current, now }: { admissionId?: string; current: string; now: number }) {
  const { t, locale } = useLocale();
  const thisMonth = useMyAdmissionRoutine(current, { admissionId });
  const nextMonth = useMyAdmissionRoutine(shiftMonth(current, 1), { admissionId, enabled: thisMonth.isSuccess });
  if (thisMonth.isPending || (thisMonth.isSuccess && nextMonth.isPending)) {
    return <Skeleton variant="card" />;
  }
  const classes = [...(thisMonth.data ?? []), ...(nextMonth.data ?? [])];
  const nextClass = nextClassOf(classes, now);
  const nextExam = nextExamOf(classes, now);
  const shown = [nextClass, nextExam && nextExam !== nextClass ? nextExam : null].filter((item): item is StudentClass => item != null);
  if (shown.length === 0) {
    return <SectionEmpty>{t("myAdmission.routine.nothingNext")}</SectionEmpty>;
  }
  return (
    <ol className="grid gap-2">
      {shown.map((item) => (
        <li key={`${item.date}-${item.startsAt}-${item.subject}`} className="grid gap-1">
          <span className="text-meta font-semibold text-muted">
            {formatDate(item.date, locale, { year: "auto" })} · {t(`myAdmission.routine.days.${item.day}`)}
          </span>
          <ul className="grid">
            <ClassRow item={item} isNext={item === nextClass} />
          </ul>
        </li>
      ))}
    </ol>
  );
}

/**
 * The month as an agenda: one row per day with a date tile (the weekday and the day of the month), then that day's classes.
 * Today is filled in the accent colour and the days already gone are dimmed. The same markup serves the phone and the desktop.
 */
function Agenda({ classes, now }: { classes: StudentClass[]; now: number }) {
  const { t, locale } = useLocale();
  const today = dhakaDate(now);
  const next = nextClassOf(classes, now);
  const digits = (text: string) => (locale === "bn" ? toBengaliDigits(text) : text);
  return (
    <ol className="grid divide-y divide-border">
      {byDay(classes).map(({ date, classes: dayClasses }) => {
        const weekday = weekdayOf(dayClasses[0]);
        const isToday = date === today;
        const isPast = date < today;
        const fullDate = formatDate(date, locale, { year: "auto" });
        return (
          <li
            key={date}
            className={cn("grid grid-cols-[3.5rem_minmax(0,1fr)] gap-3 py-3 first:pt-0 last:pb-0", isPast && "opacity-60")}
          >
            <div
              aria-hidden="true"
              className={cn(
                "flex h-fit flex-col items-center rounded-control border px-1 py-1.5 text-center leading-tight",
                isToday ? "border-accent bg-accent text-on-accent" :"border-border bg-card text-text",
              )}
            >
              <span className={cn("text-pill", isToday ? "" : "text-muted")}>
                {isToday ? t("myAdmission.routine.today") : t(`myAdmission.routine.daysShort.${weekday}`)}
              </span>
              <span className="text-section font-bold tabular-nums">{digits(date.slice(8).replace(/^0/, ""))}</span>
            </div>
            <div className="grid min-w-0 gap-2">
              <h3 className="sr-only">
                {t(`myAdmission.routine.days.${weekday}`)}, {fullDate}
                {isToday ? ` (${t("myAdmission.routine.today")})` : ""}
              </h3>
              <ul className="grid gap-2">
                {dayClasses.map((item, index) => (
                  <ClassRow key={`${item.startsAt}-${index}`} item={item} isNext={item === next} />
                ))}
              </ul>
            </div>
          </li>
        );
      })}
    </ol>
  );
}

/**
 * The routine of My admission (plan 8.5): the batch's classes for one month as an agenda, with a month switcher and the student
 * copy of the routine PDF. Only the fields of the routine JSON are shown: never an instructor, an amount or a pay status.
 */
export function RoutineSection({ admission, admissionId }: { admission: AdmissionView; admissionId?: string }) {
  const { t, locale } = useLocale();
  // The current month is fixed when the section mounts, so the switcher's limits do not move under the reader.
  const [current] = useState(() => monthOf(serverNow()));
  const [month, setMonth] = useState(current);
  const [pdfOpen, setPdfOpen] = useState(false);
  // Hidden: only the next class and the next exam. Remembered on this device; the first render is "shown", then the stored choice.
  const [hidden, setHidden] = useState(false);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the stored choice is only readable in the browser, after load
    setHidden(readHidden());
  }, []);
  const now = useServerNow(60_000);
  const hasRoutine = admission.hasRoutine === true;
  const routine = useMyAdmissionRoutine(month, { enabled: hasRoutine, admissionId });
  const id = "my-admission-routine";

  if (!hasRoutine) {
    return (
      <SectionCard id={id} icon={CalendarDays} tint="routine" title={t("myAdmission.routine.title")}>
        <SectionEmpty>{t("myAdmission.routine.empty")}</SectionEmpty>
      </SectionCard>
    );
  }

  const batch = pickTitle({ titleEn: admission.batchNameEn, titleBn: admission.batchNameBn }, locale);
  const oldest = shiftMonth(current, -MONTHS_BACK);
  const newest = shiftMonth(current, MONTHS_AHEAD);
  const label = monthLabel(month, locale);
  const digitsOf = (text: string) => (locale === "bn" ? toBengaliDigits(text) : text);

  let body;
  if (routine.isPending) {
    body = <Skeleton variant="card" />;
  } else if (routine.isError || !routine.data) {
    body = <ErrorState error={routine.error} onRetry={() => void routine.refetch()} />;
  } else if (routine.data.length === 0) {
    body = <SectionEmpty>{t("myAdmission.routine.noClasses")}</SectionEmpty>;
  } else {
    body = <Agenda classes={routine.data} now={now} />;
  }

  return (
    <SectionCard
      id={id}
      icon={CalendarDays}
      tint="routine"
      title={t("myAdmission.routine.title")}
      action={
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            size="sm"
            aria-expanded={!hidden}
            onClick={() => {
              const next = !hidden;
              setHidden(next);
              writeHidden(next);
              if (next) setPdfOpen(false);
            }}
          >
            {hidden ? <Eye aria-hidden="true" className="size-4" /> : <EyeOff aria-hidden="true" className="size-4" />}
            {hidden ? t("myAdmission.routine.show") : t("myAdmission.routine.hide")}
          </Button>
          {hidden ? null : (
            <Button variant="secondary" size="sm" onClick={() => setPdfOpen((open) => !open)} aria-expanded={pdfOpen}>
              <FileText aria-hidden="true" className="size-4" />
              {pdfOpen ? t("myAdmission.routine.closePdf") : t("myAdmission.routine.openPdf")}
            </Button>
          )}
        </div>
      }
    >
      {hidden ? (
        <NextOnly admissionId={admissionId} current={current} now={now} />
      ) : (
      <div className="grid gap-4">
        <div className="flex items-center justify-between gap-2 rounded-full border border-border bg-bg p-0.5">
          <IconButton
            label={t("myAdmission.routine.previousMonth")}
            icon={<ChevronLeft aria-hidden="true" className="size-5" />}
            className="rounded-full"
            disabled={month <= oldest}
            onClick={() => setMonth((value) => shiftMonth(value, -1))}
          />
          <span aria-live="polite" className="font-semibold text-text">
            {digitsOf(label)}
          </span>
          <IconButton
            label={t("myAdmission.routine.nextMonth")}
            icon={<ChevronRight aria-hidden="true" className="size-5" />}
            className="rounded-full"
            disabled={month >= newest}
            onClick={() => setMonth((value) => shiftMonth(value, 1))}
          />
        </div>

        {pdfOpen ? (
          <PdfViewer
            key={month}
            load={() => loadAdmissionRoutinePdf(month, admissionId)}
            allowDownload
            fileName={`${batch || t("myAdmission.routine.title")} ${month}.pdf`}
            label={t("myAdmission.routine.pdfLabel", { month: label })}
          />
        ) : null}

        {body}
      </div>
      )}
    </SectionCard>
  );
}
