"use client";

import Link from "next/link";
import { useRoutine, type RoutineRow } from "@/lib/api/exam/catalog";
import { formatDate, formatTime, pickTitle } from "./_tmp/format";
import { useLocale } from "./_tmp/locale";
import { useServerNow } from "./_tmp/clock";
import { Button, EmptyState, ErrorState, PageHeader, Pill, Skeleton } from "./_tmp/ui";
import { buildIcs, downloadIcs, examPath, groupRoutine, isLive } from "./catalog-helpers";
import { ExamStatusPill, TypePill } from "./status-pill";

function fileName(row: RoutineRow): string {
  return `exam-${row.id}.ics`;
}

function RoutineItem({ row, live }: { row: RoutineRow; live: boolean }) {
  const { t, locale } = useLocale();
  const url = `${typeof window === "undefined" ? "" : window.location.origin}${examPath(row.id)}`;
  return (
    <li className="grid gap-2 rounded-card border border-border bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-muted">
          {formatTime(row.liveAt, locale)} – {formatTime(row.endAt, locale)}
        </p>
        {live ? (
          <Pill tone="warn" pulse>{t("catalog.routine.live")}</Pill>
        ) : row.myStatus ? (
          <ExamStatusPill status={row.myStatus} examType={row.examType} retakeAvailable={row.retakeAvailable} />
        ) : null}
      </div>
      <Link href={examPath(row.id)} className="text-[17px] font-bold text-text">
        {pickTitle(row, locale)}
      </Link>
      <p className="flex flex-wrap items-center gap-2 text-sm text-muted">
        <span>{pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn }, locale)}</span>
        <TypePill type={row.examType} />
      </p>
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" href={examPath(row.id)}>{t("catalog.routine.open")}</Button>
        <Button
          variant="ghost"
          onClick={() =>
            downloadIcs(
              fileName(row),
              buildIcs({ id: row.id, title: pickTitle(row, "en"), start: row.liveAt, end: row.endAt, url }),
            )
          }
        >
          {t("catalog.routine.addCalendar")}
        </Button>
      </div>
    </li>
  );
}

export function RoutinePage() {
  const { t, locale } = useLocale();
  const now = useServerNow(30_000);
  const routine = useRoutine(true);
  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: t("catalog.routine.title") }];

  if (routine.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.routine.title")} breadcrumbs={crumbs} />
        <Skeleton variant="card" count={3} />
      </>
    );
  }
  if (routine.isError && !routine.data) {
    return (
      <>
        <PageHeader title={t("catalog.routine.title")} breadcrumbs={crumbs} />
        <ErrorState onRetry={() => void routine.refetch()} />
      </>
    );
  }
  const { live, days } = groupRoutine(routine.data ?? [], now);
  const empty = live.length === 0 && days.length === 0;

  return (
    <>
      <PageHeader title={t("catalog.routine.title")} breadcrumbs={crumbs} />
      {empty ? (
        <EmptyState title={t("catalog.routine.empty")} body={t("catalog.routine.emptyBody")} action={<Button variant="secondary" href="/mcq">{t("catalog.doorMcq")}</Button>} />
      ) : null}
      {live.length > 0 ? (
        <section aria-label={t("catalog.routine.live")} className="mb-6">
          <ul className="grid gap-3">
            {live.map((row) => (
              <RoutineItem key={row.id} row={row} live />
            ))}
          </ul>
        </section>
      ) : null}
      {days.map((day) => (
        <section key={day.key} aria-label={day.key} className="mb-6">
          <h2 className="mb-2 text-[17px] font-bold">
            {day.relative === "today"
              ? t("catalog.routine.today")
              : day.relative === "tomorrow"
                ? t("catalog.routine.tomorrow")
                : formatDate(day.firstStart, locale, { weekday: true })}
          </h2>
          <ul className="grid gap-3">
            {day.rows.map((row) => (
              <RoutineItem key={row.id} row={row} live={isLive(row, now)} />
            ))}
          </ul>
        </section>
      ))}
      {!empty ? <p className="text-sm text-muted">{t("catalog.routine.footer")}</p> : null}
    </>
  );
}
