"use client";

import Link from "next/link";
import { CalendarPlus } from "lucide-react";
import { useRoutine, type RoutineRow } from "@/lib/api/exam/catalog";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { cn } from "@/components/ui/cn";
import { DoorActions } from "./door-actions";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { IconButton } from "@/components/ui/icon-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { buildIcs, downloadIcs, examPath, groupRoutine, isLive } from "./catalog-helpers";
import { ExamStatusPill, TypePill } from "./status-pill";

/** One exam of the routine and whether it is live now. */
type Line = { row: RoutineRow; live: boolean };

/** A band of the routine: Live now, one day, or the exams with no date yet. */
type Band = { key: string; title: string; lines: Line[] };

/** Saves the exam's start and end as a calendar entry. Needs both times, so an undated exam has no button. */
function addToCalendar(row: RoutineRow) {
  const url = `${typeof window === "undefined" ? "" : window.location.origin}${examPath(row.id)}`;
  downloadIcs(
    `exam-${row.id}.ics`,
    buildIcs({ id: row.id, title: pickTitle(row, "en"), start: row.liveAt as string, end: row.endAt as string, url }),
  );
}

/** The start and end of an exam, or a dash while it has no date. */
function timeOf(row: RoutineRow, locale: "en" | "bn"): string {
  return row.liveAt && row.endAt ? `${formatTime(row.liveAt, locale)} – ${formatTime(row.endAt, locale)}` : "—";
}

function StatusCell({ line }: { line: Line }) {
  const { t } = useLocale();
  if (line.live) {
    return <Pill tone="warn" pulse>{t("catalog.routine.live")}</Pill>;
  }
  return line.row.myStatus ? (
    <ExamStatusPill status={line.row.myStatus} examType={line.row.examType} retakeAvailable={line.row.retakeAvailable} />
  ) : (
    <span className="text-muted">—</span>
  );
}

function CalendarButton({ row }: { row: RoutineRow }) {
  const { t } = useLocale();
  if (!row.liveAt || !row.endAt) {
    return null;
  }
  return <IconButton label={t("catalog.routine.addCalendar")} icon={<CalendarPlus className="size-5" />} onClick={() => addToCalendar(row)} />;
}

/** The exam's name as a link, with its package under it. */
function ExamCell({ row }: { row: RoutineRow }) {
  const { locale } = useLocale();
  const pkg = pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn }, locale);
  return (
    <div className="grid gap-0.5">
      <Link href={examPath(row.id)} className="font-semibold text-text underline-offset-4 hover:underline">
        {pickTitle(row, locale)}
      </Link>
      {pkg ? <span className="text-meta text-muted">{pkg}</span> : null}
    </div>
  );
}

/**
 * The exam routine as a timetable. On a desktop it is one table: a header, then a band for each day (Live now first, exams with
 * no date last), and a row per exam with its time, name, type and status. On a phone each exam is a compact card under its day.
 */
export function RoutinePage() {
  const { t, locale } = useLocale();
  const now = useServerNow(30_000);
  const routine = useRoutine(true);
  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: t("catalog.routine.title") }];

  if (routine.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.routine.title")} breadcrumbs={crumbs} />
        <Skeleton variant="rows" count={5} />
      </>
    );
  }
  if (routine.isError && !routine.data) {
    return (
      <>
        <PageHeader title={t("catalog.routine.title")} breadcrumbs={crumbs} />
        <ErrorState error={routine.error} onRetry={() => void routine.refetch()} />
      </>
    );
  }
  const { live, days, undated } = groupRoutine(routine.data ?? [], now);
  const bands: Band[] = [
    ...(live.length > 0 ? [{ key: "live", title: t("catalog.routine.live"), lines: live.map((row) => ({ row, live: true })) }] : []),
    ...days.map((day) => ({
      key: String(day.key),
      title:
        day.relative === "today"
          ? t("catalog.routine.today")
          : day.relative === "tomorrow"
            ? t("catalog.routine.tomorrow")
            : formatDate(day.firstStart, locale, { weekday: true, year: "auto" }),
      lines: day.rows.map((row) => ({ row, live: isLive(row, now) })),
    })),
    ...(undated.length > 0 ? [{ key: "undated", title: t("catalog.dateTbd"), lines: undated.map((row) => ({ row, live: false })) }] : []),
  ];

  return (
    <>
      <PageHeader title={t("catalog.routine.title")} breadcrumbs={crumbs} />
      {bands.length === 0 ? (
        <EmptyState title={t("catalog.routine.empty")} body={t("catalog.routine.emptyBody")} action={<DoorActions />} />
      ) : (
        <>
          {/* Desktop: one timetable. */}
          <div className="hidden overflow-hidden rounded-card border border-border bg-card md:block">
            <table className="w-full border-collapse text-body">
              <thead className="bg-bg text-meta text-muted">
                <tr>
                  <th scope="col" className="w-52 px-4 py-3 text-start font-semibold">{t("catalog.routine.columns.time")}</th>
                  <th scope="col" className="px-4 py-3 text-start font-semibold">{t("catalog.routine.columns.exam")}</th>
                  <th scope="col" className="w-28 px-4 py-3 text-start font-semibold">{t("catalog.routine.columns.type")}</th>
                  <th scope="col" className="w-44 px-4 py-3 text-start font-semibold">{t("catalog.routine.columns.status")}</th>
                  <th scope="col" className="w-16 px-4 py-3">
                    <span className="sr-only">{t("catalog.routine.columns.calendar")}</span>
                  </th>
                </tr>
              </thead>
              {bands.map((band) => (
                <tbody key={band.key}>
                  <tr>
                    <th scope="colgroup" colSpan={5} className="border-y border-border bg-field px-4 py-2 text-start text-label font-semibold text-text">
                      {band.title}
                    </th>
                  </tr>
                  {band.lines.map((line) => (
                    <tr key={line.row.id} className={cn("border-b border-border/60 last:border-0", line.live && "bg-warn/5")}>
                      <td className="whitespace-nowrap px-4 py-3 align-middle font-semibold tabular-nums text-muted">{timeOf(line.row, locale)}</td>
                      <td className="px-4 py-3 align-middle">
                        <ExamCell row={line.row} />
                      </td>
                      <td className="px-4 py-3 align-middle">
                        <TypePill type={line.row.examType} />
                      </td>
                      <td className="px-4 py-3 align-middle">
                        <StatusCell line={line} />
                      </td>
                      <td className="px-4 py-3 text-end align-middle">
                        <CalendarButton row={line.row} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              ))}
            </table>
          </div>

          {/* Phone: a card per exam under its day. */}
          <div className="grid gap-5 md:hidden">
            {bands.map((band) => (
              <section key={band.key} aria-label={band.title}>
                <h2 className="mb-2 text-label font-semibold text-muted">{band.title}</h2>
                <ul className="grid gap-2">
                  {band.lines.map((line) => (
                    <li
                      key={line.row.id}
                      className={cn("grid gap-2 rounded-card border border-border bg-card p-4", line.live && "border-warn/50 bg-warn/5")}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-meta font-semibold tabular-nums text-muted">{timeOf(line.row, locale)}</span>
                        <StatusCell line={line} />
                      </div>
                      <ExamCell row={line.row} />
                      <div className="flex items-center justify-between gap-2">
                        <TypePill type={line.row.examType} />
                        <CalendarButton row={line.row} />
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
          <p className="mt-4 text-body text-muted">{t("catalog.routine.footer")}</p>
        </>
      )}
    </>
  );
}
