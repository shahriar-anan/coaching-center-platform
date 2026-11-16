"use client";

import { RequireStudent } from "@/lib/auth/guards";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useRoutine, type RoutineRow } from "@/lib/api/exam/catalog";
import { useSession } from "@/lib/auth/session-context";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { Button } from "@/components/ui/button";
import { GraduationCap, ListChecks, Package, PenLine, School, ShoppingBag, type LucideIcon } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Countdown } from "@/components/ui/countdown";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { offlineAdmissionEnabled, staffHref } from "@/lib/config";
import { useMyAdmissions } from "@/lib/api/exam/my-admission";
import { admissionStep } from "@/lib/admission/gate";
import { usePublicBatches, type PublicBatch } from "@/lib/api/exam/public-admission";
import { examPath, isLive } from "@/components/student/catalog/catalog-helpers";
import { ExamStatusPill, TypePill } from "@/components/student/catalog/status-pill";
import { LandingPage } from "@/components/student/landing/landing-page";
import { HomeAdmissionCard } from "@/components/student/my-admission/admission-links";

const DISMISS_KEY = "exam-portal.home.dismissed";
/** Set to "1" by WS2 on registration. Shown once, then cleared. */
const WELCOME_KEY = "exam-portal.welcome";

/** One-time welcome after registration. Storage is only a per-browser convenience. */
/** Reads the flag once on mount; the card is client-only, so no server markup differs. */
function readWelcome(): boolean {
  try {
    return window.localStorage.getItem(WELCOME_KEY) === "1";
  } catch {
    return false;
  }
}

function WelcomeCard({ name }: { name: string }) {
  const { t } = useLocale();
  const [show, setShow] = useState(readWelcome);
  useEffect(() => {
    try {
      window.localStorage.removeItem(WELCOME_KEY);
    } catch {
      /* storage blocked: the flag was never set */
    }
  }, []);
  if (!show) return null;
  return (
    <section aria-label={t("home.welcomeTitle", { name })} className="mb-4 grid gap-2 rounded-card border border-ok bg-card p-4">
      <p className="font-bold">{t("home.welcomeTitle", { name })}</p>
      <p className="text-body text-muted">{t("home.welcomeBody")}</p>
      <div>
        <Button variant="ghost" className="px-0" onClick={() => setShow(false)}>
          {t("home.dismiss")}
        </Button>
      </div>
    </section>
  );
}

function readDismissed(): string[] {
  try {
    return JSON.parse(window.sessionStorage.getItem(DISMISS_KEY) ?? "[]") as string[];
  } catch {
    return [];
  }
}

function writeDismissed(list: string[]) {
  try {
    window.sessionStorage.setItem(DISMISS_KEY, JSON.stringify(list));
  } catch {
    /* storage blocked: the card simply returns next visit */
  }
}

/** Each door's line icon and its own soft tint, so the four read apart at a glance in both themes. */
const DOORS: Record<"mcq" | "written" | "orders" | "packages" | "myBatches" | "batchOffers", { Icon: LucideIcon; tone: string }> = {
  myBatches: { Icon: GraduationCap, tone: "bg-rose-50 text-rose-600 dark:bg-rose-500/15 dark:text-rose-300" },
  batchOffers: { Icon: School, tone: "bg-sky-50 text-sky-600 dark:bg-sky-500/15 dark:text-sky-300" },
  mcq: { Icon: ListChecks, tone: "bg-blue-50 text-blue-600 dark:bg-blue-500/15 dark:text-blue-300" },
  written: { Icon: PenLine, tone: "bg-violet-50 text-violet-600 dark:bg-violet-500/15 dark:text-violet-300" },
  orders: { Icon: ShoppingBag, tone: "bg-amber-50 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300" },
  packages: { Icon: Package, tone: "bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300" },
};

/** One door: the mockup's large card. An icon tile, a title and one line, and nothing else. */
function DoorLink({ href, icon, title, sub }: { href: string; icon: keyof typeof DOORS; title: string; sub: string }) {
  const { Icon, tone } = DOORS[icon];
  return (
    <Link
      href={href}
      className="group block rounded-card border border-border bg-card p-7 text-center transition hover:-translate-y-0.5 hover:border-accent hover:shadow-[var(--card-shadow)]"
    >
      <span aria-hidden="true" className={`mx-auto mb-3 flex size-16 items-center justify-center rounded-2xl transition-transform group-hover:scale-105 ${tone}`}>
        <Icon className="size-8" strokeWidth={1.75} />
      </span>
      <span className="block text-title font-bold">{title}</span>
      <span className="block text-meta text-muted">{sub}</span>
    </Link>
  );
}

/**
 * The exam segment: a heading and the two exam doors, MCQ and Written (the same category tree, filtered by exam type). The doors
 * are the mockup's large cards, as they were.
 */
function ExamDoors() {
  const { t } = useLocale();
  return (
    <section aria-labelledby="exam-doors">
      <h2 id="exam-doors" className="mb-3 text-card-title font-bold">
        {t("home.examsTitle")}
      </h2>
      <div className="grid grid-cols-2 gap-3">
        <DoorLink href="/mcq" icon="mcq" title={t("home.doorMcqTitle")} sub={t("home.doorMcqSub")} />
        <DoorLink href="/written" icon="written" title={t("home.doorWrittenTitle")} sub={t("home.doorWrittenSub")} />
      </div>
    </section>
  );
}

/** A segment of its own, set apart from the exams by a line: the student's orders and all the packages. Each door leads to its page. */
function ShopDoors() {
  const { t } = useLocale();
  return (
    <section aria-labelledby="shop-doors" className="mt-8 border-t border-border pt-6">
      <h2 id="shop-doors" className="mb-3 text-card-title font-bold">
        {t("home.shopTitle")}
      </h2>
      <div className="grid grid-cols-2 gap-3">
        <DoorLink href="/my-modules" icon="orders" title={t("home.doorOrdersTitle")} sub={t("home.doorOrdersSub")} />
        <DoorLink href="/packages" icon="packages" title={t("home.doorPackagesTitle")} sub={t("home.doorPackagesSub")} />
      </div>
    </section>
  );
}

/** The admission states that are still on their way: the form, the review, and Ready before the join code is used. */
const IN_PROGRESS_STATES = ["AWAITING_FORM", "SUBMITTED", "CHANGES_REQUESTED", "READY"] as const;

/**
 * "Offline activities" on the dashboard (16 Nov): a segment like the exam and shop ones, two doors with one line each. My offline
 * batches says how many batches the student is in (and a join code waiting); Batches on offer says how many are open. Each opens its
 * tab of the offline activities page. Hidden while the offline switch is off.
 */
export function OfflineActivitiesTile() {
  const { t } = useLocale();
  const admissions = useMyAdmissions({ enabled: offlineAdmissionEnabled });
  const batches = usePublicBatches(offlineAdmissionEnabled);
  if (!offlineAdmissionEnabled) {
    return null;
  }
  // A list that is not an array (an unexpected answer) is shown as empty: the dashboard must not fail on the tile.
  const admissionList = Array.isArray(admissions.data) ? admissions.data : [];
  const batchList: PublicBatch[] = Array.isArray(batches.data) ? batches.data : [];
  const onboarded = admissionList.filter((admission) => admission.state === "ONBOARDED").length;
  const joining = admissionList.filter((admission) => admissionStep(admission) === "JOIN").length;
  const inProgress = admissionList.filter(
    (admission) => (IN_PROGRESS_STATES as readonly string[]).includes(admission.state) && admissionStep(admission) !== "JOIN",
  ).length;
  const mine = [
    onboarded > 0 ? t(onboarded === 1 ? "publicAdmission.tile.onboardedOne" : "publicAdmission.tile.onboardedMany", { count: onboarded }) : null,
    joining > 0 ? t("publicAdmission.tile.joinWaiting", { count: joining }) : null,
    onboarded === 0 && joining === 0 && inProgress > 0 ? t("publicAdmission.tile.inProgress") : null,
  ].filter(Boolean).join(" · ") || t("publicAdmission.tile.noBatch");
  const open = batchList.filter((batch) => batch.admissionsOpen).length;
  const offers = batches.isPending ? "…" : open > 0 ? t("publicAdmission.tile.openCount", { count: open }) : t("publicAdmission.tile.none");

  return (
    <section aria-labelledby="offline-activities" className="mt-8 border-t border-border pt-6">
      <h2 id="offline-activities" className="mb-3 text-card-title font-bold">
        {t("publicAdmission.tile.title")}
      </h2>
      <div className="grid grid-cols-2 gap-3">
        <DoorLink href="/offline-activities?tab=mine" icon="myBatches" title={t("publicAdmission.tile.mineTitle")} sub={mine} />
        <DoorLink href="/offline-activities?tab=offers" icon="batchOffers" title={t("publicAdmission.tile.onOffer")} sub={offers} />
      </div>
    </section>
  );
}

function Nudges({ phoneRequired, emailVerified, live }: {
  phoneRequired: boolean;
  emailVerified: boolean;
  live: RoutineRow | undefined;
}) {
  const { t, locale } = useLocale();
  const [dismissed, setDismissed] = useState<string[]>(() => readDismissed());
  const items: { key: string; body: string; action: string; href: string }[] = [];
  if (live) {
    items.push({ key: "resume", body: t("home.nudgeResume", { title: pickTitle(live, locale) }), action: t("home.resume"), href: `${examPath(live.id)}/sit` });
  }
  if (!emailVerified) {
    items.push({ key: "email", body: t("home.nudgeEmail"), action: t("home.verifyEmail"), href: "/account" });
  }
  if (phoneRequired) {
    items.push({ key: "phone", body: t("home.nudgePhone"), action: t("home.addPhone"), href: "/set-phone" });
  }
  const visible = items.filter((item) => !dismissed.includes(item.key));
  if (!visible.length) return null;
  return (
    <section aria-label={t("home.nudgeLabel")} className="mb-4 grid gap-2">
      {visible.map((item) => (
        <div key={item.key} className="flex flex-wrap items-center justify-between gap-2 rounded-card border border-warn bg-card p-3">
          <p className="text-body">{item.body}</p>
          <div className="flex items-center gap-2">
            <Link href={item.href} className="text-label font-bold text-accent underline">{item.action}</Link>
            <button
              type="button"
              aria-label={t("home.dismiss")}
              className="min-h-11 min-w-11 text-muted"
              onClick={() => {
                const next = [...dismissed, item.key];
                setDismissed(next);
                writeDismissed(next);
              }}
            >
              ×
            </button>
          </div>
        </div>
      ))}
    </section>
  );
}

/** One exam in a list card: the title, then the time and the type; the student's own status pill sits on the right. */
function RoutineLine({ row }: { row: RoutineRow & { liveAt: string; endAt: string } }) {
  const { locale } = useLocale();
  return (
    <Card href={examPath(row.id)}>
      <p className="text-body text-muted">
        {formatDate(row.liveAt, locale, { year: "auto" })} · {formatTime(row.liveAt, locale)} – {formatTime(row.endAt, locale)}
      </p>
      <p className="font-bold">{pickTitle(row, locale)}</p>
      <p className="mt-1 flex flex-wrap gap-2 text-body">
        <TypePill type={row.examType} />
        {row.myStatus ? <ExamStatusPill status={row.myStatus} examType={row.examType} retakeAvailable={row.retakeAvailable} /> : null}
      </p>
    </Card>
  );
}

function SignedInHome({ phoneRequired, emailVerified, name }: {
  phoneRequired: boolean;
  emailVerified: boolean;
  name: string;
}) {
  const { t, locale } = useLocale();
  const now = useServerNow(1000);
  const routine = useRoutine(true);

  const rows = routine.data ?? [];
  // Only exams with a date count as upcoming here; undated ones wait on the routine.
  const upcoming = rows
    .filter((row): row is typeof row & { liveAt: string; endAt: string } =>
      Boolean(row.liveAt && row.endAt && Date.parse(row.endAt) > now),
    )
    .sort((a, b) => Date.parse(a.liveAt) - Date.parse(b.liveAt));
  const next = upcoming[0];
  const nowRows = upcoming.filter((row) => isLive(row, now));
  const soon = upcoming.filter((row) => !isLive(row, now)).slice(0, 3);
  const liveRow = nowRows.find((row) => row.myStatus === "IN_PROGRESS");

  return (
    <>
      <WelcomeCard name={name} />
      <HomeAdmissionCard />
      <Nudges phoneRequired={phoneRequired} emailVerified={emailVerified} live={liveRow} />

      <ExamDoors />
      <ShopDoors />
      <OfflineActivitiesTile />

      <section className="mt-6" aria-labelledby="next-exam">
        <h2 id="next-exam" className="mb-2 text-card-title font-bold">{t("home.nextExam")}</h2>
        {routine.isPending ? (
          <Skeleton variant="card" />
        ) : routine.isError && !routine.data ? (
          <ErrorState error={routine.error} onRetry={() => void routine.refetch()} />
        ) : !next ? (
          <p className="text-muted">{t("home.noUpcoming")}</p>
        ) : isLive(next, now) ? (
          <Card href={examPath(next.id)}>
            <Pill tone="warn" pulse>{t("home.liveNow")}</Pill>
            <p className="mt-1 font-bold">{pickTitle(next, locale)}</p>
            <p className="mt-1 text-body font-semibold text-text-accent">{t("home.sitNow")} ›</p>
          </Card>
        ) : (
          <Card className="border-s-4 border-s-accent">
            <p className="text-body text-muted">{t("home.startsIn", { title: pickTitle(next, locale) })}</p>
            <p className="text-card-title font-bold"><Countdown deadline={next.liveAt} /></p>
            <p className="text-body text-muted">{formatDate(next.liveAt, locale, { year: "auto" })} · {formatTime(next.liveAt, locale)}</p>
          </Card>
        )}
      </section>

      <section className="mt-6" aria-labelledby="upcoming-section">
        <div className="mb-2 flex items-center justify-between">
          <h2 id="upcoming-section" className="text-card-title font-bold">{t("home.upcomingSection")}</h2>
          <Link href="/routine" className="text-label font-semibold text-accent">{t("home.seeAll")}</Link>
        </div>
        {soon.length === 0 ? (
          <p className="text-muted">{t("home.noUpcoming")}</p>
        ) : (
          <div className="grid gap-2.5">{soon.map((row) => <RoutineLine key={row.id} row={row} />)}</div>
        )}
      </section>

    </>
  );
}

/** Staff see the public landing page too, with a strip that links to the admin panel. */
function StaffStrip() {
  const { t } = useLocale();
  return (
    <div className="mx-auto mb-6 flex max-w-6xl flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-card p-4">
      <p className="text-body text-muted">{t("home.staffNote")}</p>
      <Button variant="secondary" href={staffHref("/staff")}>{t("home.staffPanel")}</Button>
    </div>
  );
}

/** A signed-in student on the landing page (16 Nov): the way back to their dashboard. */
function StudentStrip() {
  const { t } = useLocale();
  return (
    <div className="mx-auto mb-6 flex max-w-6xl flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-card p-4">
      <p className="text-body text-muted">{t("home.studentNote")}</p>
      <Button href="/dashboard">{t("home.studentDashboard")}</Button>
    </div>
  );
}

/** The student's dashboard (16 Nov): what "/" showed a signed-in student before. Login lands here. */
export function DashboardPage() {
  return (
    <RequireStudent>
      <StudentDashboard />
    </RequireStudent>
  );
}

function StudentDashboard() {
  const { session } = useSession();
  if (session.status !== "authenticated") {
    return <Skeleton variant="card" count={3} />;
  }
  const user = session.user;
  return (
    <SignedInHome
      phoneRequired={Boolean(user.phoneRequired)}
      emailVerified={Boolean(user.emailVerifiedAt)}
      name={user.fullName}
    />
  );
}

/** "/": the landing page for everyone (16 Nov). A signed-in student gets a strip back to the dashboard, staff one to the admin panel. */
export function HomePage() {
  const { session } = useSession();
  if (session.status === "loading") {
    return <Skeleton variant="card" count={3} />;
  }
  if (session.status === "anonymous") {
    return <LandingPage />;
  }
  return (
    <>
      <div className="px-4 pt-6">{session.user.role === "STUDENT" ? <StudentStrip /> : <StaffStrip />}</div>
      <LandingPage />
    </>
  );
}
