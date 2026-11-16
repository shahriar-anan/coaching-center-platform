"use client";

import type { ReactNode } from "react";
import { WhatsAppJoinLink } from "@/components/student/whatsapp-join-link";
import type { UseQueryResult } from "@tanstack/react-query";
import { BookOpen, CalendarDays, ChevronRight, CircleAlert, Trophy, Wallet, type LucideIcon } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { formatDate, formatMoney, formatNumber, pickTitle } from "@/lib/format";
import { nextDueOf, overdueLineOf } from "@/lib/admission/my-admission";
import type { AdmissionView } from "@/lib/api/exam/offline-admission";
import type { AdmissionItem, StudentClass, StudentSchedule } from "@/lib/api/exam/my-admission";
import type { MyAdmissionResults } from "@/lib/api/exam/my-results";
import { ProfileAvatar } from "@/components/student/profile-avatar";
import { Card } from "@/components/ui/card";
import { cn } from "@/components/ui/cn";
import { Pill, type PillTone } from "@/components/ui/pill";
import { classTimeText, weekdayOf } from "./routine-section";
import { IconTile, daysBetween, dhakaDate, type SectionTint } from "./section-card";
import { lastMarkedExam, rankText } from "./results-section";

const STATE_TONE: Record<"ONBOARDED" | "CLOSED", PillTone> = { ONBOARDED: "ok", CLOSED: "muted" };

/** One end of the course timeline: the label over the date. */
function TimelineEnd({ label, date, end = false }: { label: string; date: string; end?: boolean }) {
  return (
    <div className={cn("grid min-w-0 gap-0.5", end && "text-end")}>
      <span className="text-meta text-muted">{label}</span>
      <span className="font-semibold tabular-nums text-text">{date}</span>
    </div>
  );
}

/**
 * The course from admission to the end date, with how far along it is and the days left. The bar and the count show only for an
 * open admission with both dates; a closed one keeps the two dates as history.
 */
function CourseTimeline({ admission, now }: { admission: AdmissionView; now: number }) {
  const { t, locale } = useLocale();
  const start = admission.admittedOn ?? null;
  const end = admission.courseEndsOn ?? null;
  if (!start && !end) {
    return null;
  }
  const open = admission.state !== "CLOSED";
  let progress: { percent: number; caption: string } | null = null;
  if (open && start && end) {
    const today = dhakaDate(now);
    const length = Math.max(1, daysBetween(start, end));
    const percent = Math.min(100, Math.max(0, Math.round((daysBetween(start, today) / length) * 100)));
    const left = daysBetween(today, end);
    const caption =
      daysBetween(today, start) > 0
        ? t("myAdmission.course.startsOn", { date: formatDate(start, locale, { year: "auto" }) })
        : left > 1
          ? t("myAdmission.course.daysLeft", { n: formatNumber(left, locale) })
          : left === 1
            ? t("myAdmission.course.oneDayLeft")
            : t("myAdmission.course.endsToday");
    progress = { percent, caption };
  }
  return (
    <div className="grid gap-2.5 border-t border-border pt-4">
      <div className="flex items-end justify-between gap-3">
        {start ? <TimelineEnd label={t("myAdmission.admittedOn")} date={formatDate(start, locale, { year: "auto" })} /> : <span />}
        {end ? <TimelineEnd end label={t("myAdmission.courseEndsOn")} date={formatDate(end, locale, { year: "auto" })} /> : null}
      </div>
      {progress ? (
        <>
          <div
            role="progressbar"
            aria-label={t("myAdmission.course.progress")}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={progress.percent}
            className="h-2 overflow-hidden rounded-full bg-bg"
          >
            <div className="h-full rounded-full bg-accent" style={{ width: `${progress.percent}%` }} />
          </div>
          <p className="text-meta font-semibold text-text-accent">{progress.caption}</p>
        </>
      ) : null}
    </div>
  );
}

/** The admission card: the student's photo and name, the state, the batch, and the course timeline. */
export function AdmissionCard({ admission, now }: { admission: AdmissionView; now: number }) {
  const { t, locale } = useLocale();
  const { session } = useSession();
  const user = session.status === "authenticated" ? session.user : null;
  const stateKey = admission.state === "CLOSED" ? "CLOSED" : "ONBOARDED";
  const batch = pickTitle({ titleEn: admission.batchNameEn, titleBn: admission.batchNameBn }, locale);
  const name = admission.form?.fullName || user?.fullName || "";
  return (
    <Card padding="lg" className="grid gap-4">
      <div className="flex items-start gap-4">
        <ProfileAvatar name={name} photoPath={user?.photoUrl} size={56} />
        <div className="grid min-w-0 flex-1 gap-1">
          <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
            <p className="min-w-0 break-words text-card-title font-bold text-text">{name}</p>
            <Pill tone={STATE_TONE[stateKey]}>{t(`myAdmission.state.${stateKey}`)}</Pill>
          </div>
          {batch ? (
            <p className="flex min-w-0 flex-wrap items-baseline gap-x-2">
              <span className="text-meta text-muted">{t("myAdmission.batch")}</span>
              <span className="min-w-0 break-words font-semibold text-text-accent">{batch}</span>
            </p>
          ) : null}
          {admission.rollNo ? (
            <p className="flex min-w-0 flex-wrap items-baseline gap-x-2">
              <span className="text-meta text-muted">{t("myAdmission.roll")}</span>
              <span className="font-latin font-semibold text-text">{admission.rollNo}</span>
            </p>
          ) : null}
          {admission.whatsappUrl ? <WhatsAppJoinLink url={admission.whatsappUrl} /> : null}
        </div>
      </div>
      <CourseTimeline admission={admission} now={now} />
    </Card>
  );
}

/** The one overdue warning (D21), right under the admission card so it is the first thing read. Hidden when nothing is overdue. */
export function OverdueAlert({ schedule }: { schedule: StudentSchedule | undefined }) {
  const { t, locale } = useLocale();
  const overdue = schedule ? overdueLineOf(schedule) : null;
  if (!overdue) {
    return null;
  }
  return (
    <div className="mt-4 flex items-start gap-3 rounded-card border border-warn/50 bg-warn/10 p-4">
      <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warn" />
      <div className="grid min-w-0 gap-0.5">
        <p className="font-bold text-text">{t("myAdmission.overdue.title")}</p>
        <p className="text-body font-semibold text-warn">
          {t("myAdmission.payments.overdue", {
            amount: formatMoney(overdue.amount, locale) ?? "",
            date: formatDate(overdue.dueOn, locale, { year: "auto" }),
          })}
        </p>
      </div>
    </div>
  );
}

type TileTone = "accent" | "ok" | "warn";

const TILE_TOP: Record<TileTone, string> = {
  accent: "border-t-accent",
  ok: "border-t-ok",
  warn: "border-t-warn",
};

/** A placeholder bar while a tile's figure loads, so the strip keeps its height. */
function TileLoading() {
  return <span aria-hidden="true" className="inline-block h-5 w-24 animate-pulse rounded bg-border" />;
}

/** One tile of the glance strip, the mockup's `.sc`: a coloured top edge, an icon, the label, the figure and a line under it. */
function GlanceTile({
  href,
  onOpen,
  icon,
  tint,
  tone,
  label,
  value,
  sub,
  subTone,
  loading,
}: {
  href: string;
  /** Opens the tile's tab instead of jumping to an anchor, when the sections are tabs (16 Nov). */
  onOpen?: () => void;
  icon: LucideIcon;
  tint: SectionTint;
  tone: TileTone;
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  subTone?: "warn" | "ok";
  loading?: boolean;
}) {
  return (
    <li className="min-w-0">
      <a
        href={href}
        onClick={
          onOpen
            ? (event) => {
                event.preventDefault();
                onOpen();
              }
            : undefined
        }
        className={cn(
          "group flex h-full min-w-0 items-center gap-3 rounded-card border border-border border-t-4 bg-card p-3 text-text shadow-[var(--card-shadow)] transition-colors hover:bg-text/[0.03] focus-visible:outline-2 focus-visible:outline-accent",
          TILE_TOP[tone],
        )}
      >
        <IconTile icon={icon} tint={tint} />
        <span className="grid min-w-0 flex-1 gap-0.5">
          <span className="text-meta text-muted">{label}</span>
          <span className="break-words text-label font-bold tabular-nums">{loading ? <TileLoading /> : value}</span>
          {sub && !loading ? (
            <span
              className={cn(
                "break-words text-meta",
                subTone === "warn" ? "font-semibold text-warn" : subTone === "ok" ? "text-ok-text" : "text-muted",
              )}
            >
              {sub}
            </span>
          ) : null}
        </span>
        <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted transition-transform group-hover:translate-x-0.5" />
      </a>
    </li>
  );
}

/**
 * The glance strip: the next class, the next payment and the exams and packages, each a link down to its section. A closed
 * admission has no next class, so its tile is left out.
 */
export function GlanceStrip({
  admission,
  nextClass,
  classesLoading,
  fees,
  items,
  results,
  now,
  onOpen,
}: {
  /** With tabs (16 Nov), a tile opens its section's tab: routine, payments, items or results. */
  onOpen?: (section: "routine" | "payments" | "items" | "results") => void;
  admission: AdmissionView;
  nextClass: StudentClass | null;
  classesLoading: boolean;
  fees: UseQueryResult<StudentSchedule>;
  items: UseQueryResult<AdmissionItem[]>;
  results: UseQueryResult<MyAdmissionResults>;
  now: number;
}) {
  const { t, locale } = useLocale();
  const open = admission.state !== "CLOSED";

  // The last result: the newest exam with marks, shown once the results have loaded and only when there is one.
  const lastResult = results.data ? lastMarkedExam(results.data.exams) : null;
  const tileCount = (open ? 3 : 2) + (lastResult ? 1 : 0);

  // The next class: "Today" or the short weekday with the date, then the time and the subject.
  let classValue: ReactNode = t("myAdmission.glance.noClass");
  let classSub: ReactNode = admission.hasRoutine ? t("myAdmission.glance.seeRoutine") : t("myAdmission.glance.routineSoon");
  if (nextClass) {
    classValue =
      nextClass.date === dhakaDate(now)
        ? t("myAdmission.glance.today")
        : `${t(`myAdmission.routine.daysShort.${weekdayOf(nextClass)}`)}, ${formatDate(nextClass.date, locale, { year: "auto" })}`;
    const time = classTimeText(nextClass, locale);
    classSub = time ? `${time} · ${nextClass.subject}` : nextClass.subject;
  }

  // The next payment: the earliest unpaid due; amber when something is overdue, green when everything is paid.
  const schedule = fees.data;
  const next = schedule ? nextDueOf(schedule.dues) : null;
  const overdue = schedule ? overdueLineOf(schedule) : null;
  let payTone: TileTone = "accent";
  let payValue: ReactNode = t("myAdmission.glance.allPaid");
  let paySub: ReactNode = t("myAdmission.glance.nothingDue");
  let paySubTone: "warn" | "ok" | undefined = "ok";
  if (fees.isError) {
    payValue = "—";
    paySub = t("myAdmission.payments.loadFailed");
    paySubTone = undefined;
  } else if (next) {
    payValue = formatMoney(next.owed, locale);
    if (overdue) {
      payTone = "warn";
      paySub = t("myAdmission.glance.overdueSince", { date: formatDate(overdue.dueOn, locale, { year: "auto" }) });
      paySubTone = "warn";
    } else {
      paySub = t("myAdmission.glance.dueOn", { date: formatDate(next.dueOn, locale, { year: "auto" }) });
      paySubTone = undefined;
    }
  } else if (schedule) {
    payTone = "ok";
  }

  // The exams and packages: how many of each.
  const rows = items.data ?? [];
  const packages = rows.filter((item) => item.scope === "MODULE").length;
  const exams = rows.length - packages;
  const itemsValue =
    items.isError
      ? "—"
      : rows.length === 0
        ? t("myAdmission.glance.itemsNone")
        : [
            packages > 0
              ? packages === 1
                ? t("myAdmission.glance.packagesOne")
                : t("myAdmission.glance.packagesMany", { n: formatNumber(packages, locale) })
              : null,
            exams > 0
              ? exams === 1
                ? t("myAdmission.glance.examsOne")
                : t("myAdmission.glance.examsMany", { n: formatNumber(exams, locale) })
              : null,
          ]
            .filter(Boolean)
            .join(" · ");

  return (
    <section aria-label={t("myAdmission.glance.label")} className="mt-4">
      <ul
        className={cn(
          "grid gap-3",
          tileCount >= 4 ? "sm:grid-cols-2 lg:grid-cols-4" : tileCount === 3 ? "sm:grid-cols-3" : "sm:grid-cols-2",
        )}
      >
        {open ? (
          <GlanceTile
            href="#my-admission-routine"
            onOpen={onOpen ? () => onOpen("routine") : undefined}
            icon={CalendarDays}
            tint="routine"
            tone="accent"
            label={t("myAdmission.glance.nextClass")}
            value={classValue}
            sub={classSub}
            loading={classesLoading}
          />
        ) : null}
        <GlanceTile
          href="#my-admission-payments"
          onOpen={onOpen ? () => onOpen("payments") : undefined}
          icon={Wallet}
          tint="payments"
          tone={payTone}
          label={t("myAdmission.glance.nextPayment")}
          value={payValue}
          sub={paySub}
          subTone={paySubTone}
          loading={fees.isPending}
        />
        <GlanceTile
          href="#my-admission-items"
          onOpen={onOpen ? () => onOpen("items") : undefined}
          icon={BookOpen}
          tint="items"
          tone="accent"
          label={t("myAdmission.glance.items")}
          value={itemsValue}
          loading={items.isPending}
        />
        {lastResult ? (
          <GlanceTile
            href={`#results-${admission.id}`}
            onOpen={onOpen ? () => onOpen("results") : undefined}
            icon={Trophy}
            tint="results"
            tone="accent"
            label={t("myAdmission.glance.lastResult")}
            value={t("myAdmission.results.score", {
              total: formatNumber(lastResult.total ?? 0, locale),
              full: formatNumber(lastResult.fullMarks, locale),
            })}
            sub={
              lastResult.rank != null && lastResult.rankOf != null
                ? t("myAdmission.results.rankOf", {
                    rank: rankText(lastResult.rank, locale),
                    of: formatNumber(lastResult.rankOf, locale),
                  })
                : undefined
            }
          />
        ) : null}
      </ul>
    </section>
  );
}
