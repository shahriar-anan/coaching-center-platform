"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRoutine, type RoutineRow } from "@/lib/api/exam/catalog";
import { useSession } from "@/lib/auth/session-context";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { Button } from "@/components/ui/button";
import { DoorIcon } from "@/components/student/door-icon";
import { Card } from "@/components/ui/card";
import { Countdown } from "@/components/ui/countdown";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { staffHref } from "@/lib/config";
import { examPath, isLive } from "@/components/student/catalog/catalog-helpers";
import { ExamStatusPill, TypePill } from "@/components/student/catalog/status-pill";
import { LandingPage } from "@/components/student/landing/landing-page";

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

/** One door: the mockup's large card, kept as it was. An icon, a title and one line, and nothing else. */
function DoorLink({ href, icon, title, sub }: { href: string; icon: "mcq" | "written" | "orders" | "packages"; title: string; sub: string }) {
  return (
    <Link
      href={href}
      className="block rounded-card border border-border bg-card p-7 text-center transition hover:-translate-y-0.5 hover:border-accent"
    >
      <DoorIcon kind={icon} className="size-12" />
      <span className="mt-1 block text-title font-bold">{title}</span>
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
      <Nudges phoneRequired={phoneRequired} emailVerified={emailVerified} live={liveRow} />

      <ExamDoors />
      <ShopDoors />

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

export function HomePage() {
  const { session } = useSession();
  if (session.status === "loading") {
    return <Skeleton variant="card" count={3} />;
  }
  if (session.status === "anonymous") {
    return <LandingPage />;
  }
  const user = session.user;
  if (user.role !== "STUDENT") {
    return (
      <>
        <div className="px-4 pt-6">
          <StaffStrip />
        </div>
        <LandingPage />
      </>
    );
  }
  return (
    <SignedInHome
      phoneRequired={Boolean(user.phoneRequired)}
      emailVerified={Boolean(user.emailVerifiedAt)}
      name={user.fullName}
    />
  );
}
