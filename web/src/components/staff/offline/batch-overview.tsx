"use client";

import type { ReactNode } from "react";
import { CalendarCheck, CalendarClock, FileText, Users, type LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { ErrorState } from "@/components/ui/error-state";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useBatchClasses, useBatchRoutines } from "@/lib/api/exam/offline-batch-pay";
import { useFormat } from "@/lib/format/use-format";

type Tone = "neutral" | "accent" | "ok" | "warn" | "danger";

/** The coloured top edge and the icon tint of each tile: a quick read of what the figure means. */
const EDGE: Record<Tone, string> = {
  neutral: "border-t-border-control",
  accent: "border-t-accent",
  ok: "border-t-ok",
  warn: "border-t-warn",
  danger: "border-t-danger",
};

const ICON_TINT: Record<Tone, string> = {
  neutral: "bg-bg text-muted",
  accent: "bg-accent/15 text-text-accent",
  ok: "bg-ok/10 text-ok-text",
  warn: "bg-warn/10 text-warn",
  danger: "bg-danger/10 text-danger",
};

/** One figure: an icon tile, the label, the big number, and the amount or note under it. */
function Tile({
  icon: Icon,
  label,
  value,
  sub,
  tone = "neutral",
}: {
  icon: LucideIcon;
  label: string;
  value: string;
  sub?: ReactNode;
  tone?: Tone;
}) {
  return (
    <div
      className={cn(
        "grid min-w-0 content-start gap-3 rounded-card border border-border bg-card p-4 shadow-[var(--card-shadow)] border-t-4",
        EDGE[tone],
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-meta font-medium text-muted">{label}</span>
        <span aria-hidden="true" className={cn("flex size-8 shrink-0 items-center justify-center rounded-control", ICON_TINT[tone])}>
          <Icon className="size-4" strokeWidth={1.9} />
        </span>
      </div>
      <div className="grid min-w-0 gap-1">
        <span className="text-section font-bold tabular-nums text-text">{value}</span>
        {sub ? <span className="break-words text-meta tabular-nums text-muted">{sub}</span> : null}
      </div>
    </div>
  );
}

/**
 * The batch's Overview, above its details: the current routine, the classes coming and held, the instructors, and the default class
 * length. Everything is read from the routines and classes the other tabs use, so nothing here is kept in two places. Classes carry no
 * pay since 24 Nov, so no money is shown here.
 */
export function BatchOverview({ batchId, minutes, onGo }: { batchId: string; minutes: number; onGo: (tab: string) => void }) {
  const { t } = useLocale();
  const { formatDate, formatNumber } = useFormat();
  const routines = useBatchRoutines(batchId);
  const classes = useBatchClasses(batchId);

  if (routines.isPending || classes.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (routines.isError || classes.isError) {
    return <ErrorState onRetry={() => void Promise.all([routines.refetch(), classes.refetch()])} error={routines.error ?? classes.error} />;
  }

  const current = routines.data.find((routine) => routine.status === "CONFIRMED");
  const waiting = routines.data.find((routine) => routine.status === "PREVIEW");
  const items = classes.data;
  const count = (...statuses: string[]) => items.filter((item) => statuses.includes(item.status)).length;
  const teaching = new Set(items.filter((item) => item.instructorId && item.status !== "CANCELLED").map((item) => item.instructorId));
  const pillTone: PillTone = current?.studentCopy ? "ok" : "muted";

  return (
    <section aria-labelledby="batch-overview" className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="batch-overview" className="text-card-title text-text">
          {t("batchPay.overview.title")}
        </h3>
        <span className="text-meta text-muted">
          {t("batchPay.overview.classLength")}: {t("batchPay.overview.minutes", { count: formatNumber(minutes) })}
        </span>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Tile icon={CalendarClock} label={t("batchPay.overview.scheduled")} value={formatNumber(count("SCHEDULED"))} tone="accent" />
        <Tile icon={CalendarCheck} label={t("batchPay.overview.held")} value={formatNumber(count("HELD", "PAID", "NOT_TAKEN"))} tone="ok" />
        <Tile icon={Users} label={t("batchPay.overview.instructors")} value={formatNumber(teaching.size)} tone="accent" />
      </div>

      <div className="grid gap-3 rounded-card border border-border bg-card p-4 shadow-[var(--card-shadow)] sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-3">
            <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-accent/15 text-text-accent">
              <FileText className="size-5" strokeWidth={1.75} />
            </span>
            <div className="grid min-w-0 gap-0.5">
              <span className="text-meta text-muted">{t("batchPay.overview.routine")}</span>
              <span className="text-card-title font-bold text-text">
                {current ? t("batchPay.overview.fromDate", { date: formatDate(current.effectiveFrom, { year: "auto" }) }) : t("batchPay.overview.none")}
              </span>
            </div>
          </div>
          {current ? (
            <div className="flex flex-wrap items-center gap-2">
              <Pill tone="accent">{t("batchPay.overview.classesIn", { count: current.rowCount })}</Pill>
              <Pill tone={pillTone}>{t(current.studentCopy ? "batchPay.overview.studentCopy" : "batchPay.overview.noStudentCopy")}</Pill>
            </div>
          ) : null}
        </div>

        {waiting ? (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-warn/50 bg-warn/5 px-3 py-2.5">
            <span className="text-body text-text">{t("batchPay.overview.waiting")}</span>
            <Button size="sm" href={`/staff/offline-batches/${batchId}/routines/${waiting.id}`}>
              {t("batchPay.overview.checkRoutine")}
            </Button>
          </div>
        ) : null}

        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-border pt-3">
          <Button size="sm" variant="ghost" className="px-0 text-text-accent" onClick={() => onGo("routines")}>
            {t("batchPay.overview.viewRoutines")}
          </Button>
          <Button size="sm" variant="ghost" className="px-0 text-text-accent" onClick={() => onGo("classes")}>
            {t("batchPay.overview.viewClasses")}
          </Button>
        </div>
      </div>
    </section>
  );
}
