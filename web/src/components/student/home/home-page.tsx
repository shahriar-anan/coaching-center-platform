"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useMyModules, useRoutine, type RoutineRow } from "@/lib/api/exam/catalog";
import { useSession } from "@/lib/auth/session-context";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { Button } from "@/components/ui/button";
import { DoorActions } from "@/components/student/catalog/door-actions";
import { Card } from "@/components/ui/card";
import { Countdown } from "@/components/ui/countdown";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { staffHref } from "@/lib/config";
import { examPath, isLive, modulePath } from "@/components/student/catalog/catalog-helpers";
import { ExamStatusPill, TypePill } from "@/components/student/catalog/status-pill";

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

/** Two doors: the same category tree, filtered by exam type. */
function Doors() {
  const { t } = useLocale();
  return (
    <div className="grid grid-cols-2 gap-3">
      <Link
        href="/mcq"
        className="block rounded-card border border-border bg-card p-7 text-center transition hover:-translate-y-0.5 hover:border-accent"
      >
        <span aria-hidden className="block text-4xl">✅</span>
        <span className="mt-1 block text-title font-bold">{t("home.doorMcqTitle")}</span>
        <span className="block text-meta text-muted">{t("home.doorMcqSub")}</span>
      </Link>
      <Link
        href="/written"
        className="block rounded-card border border-border bg-card p-7 text-center transition hover:-translate-y-0.5 hover:border-accent"
      >
        <span aria-hidden className="block text-4xl">✍️</span>
        <span className="mt-1 block text-title font-bold">{t("home.doorWrittenTitle")}</span>
        <span className="block text-meta text-muted">{t("home.doorWrittenSub")}</span>
      </Link>
    </div>
  );
}

function Hero() {
  const { t } = useLocale();
  return (
    <div className="px-3 pb-[30px] pt-[42px] text-center">
      <h1 className="bg-gradient-to-br from-accent to-text bg-clip-text text-display font-extrabold text-transparent">
        {t("home.heroWord")}
      </h1>
      <p className="mt-1.5 text-muted">{t("home.tagline")}</p>
    </div>
  );
}

function SignedOutHome() {
  const { t } = useLocale();
  return (
    <>
      <Hero />
      <Doors />
      <ul className="mt-5 grid gap-2 text-body text-muted">
        <li>• {t("home.valueLive")}</li>
        <li>• {t("home.valueScore")}</li>
        <li>• {t("home.valueMarked")}</li>
      </ul>
      <div className="mt-6 grid gap-2">
        <Button fullWidth href="/register">{t("home.signUp")}</Button>
        <p className="text-center text-body text-muted">
          {t("home.haveAccount")} <Link className="font-semibold text-accent underline" href="/login">{t("home.login")}</Link>
        </p>
      </div>
    </>
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

function RoutineLine({ row }: { row: RoutineRow }) {
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

function SignedInHome({ isStaff, phoneRequired, emailVerified, name }: {
  isStaff: boolean;
  phoneRequired: boolean;
  emailVerified: boolean;
  name: string;
}) {
  // Nudges and the next-exam card only apply to students.
  const { t, locale } = useLocale();
  const now = useServerNow(1000);
  const routine = useRoutine(true);
  const modules = useMyModules(true);

  if (isStaff) {
    return (
      <>
        <SignedOutHome />
        <p className="mt-4 text-center text-body text-muted">{t("home.staffNote")}</p>
        <div className="mt-3 text-center">
          <Button variant="secondary" href={staffHref("/staff")}>{t("home.staffPanel")}</Button>
        </div>
      </>
    );
  }

  const rows = routine.data ?? [];
  const upcoming = rows
    .filter((row) => Date.parse(row.endAt) > now)
    .sort((a, b) => Date.parse(a.liveAt) - Date.parse(b.liveAt));
  const next = upcoming[0];
  const nowRows = upcoming.filter((row) => isLive(row, now));
  const soon = upcoming.filter((row) => !isLive(row, now)).slice(0, 3);
  const liveRow = nowRows.find((row) => row.myStatus === "IN_PROGRESS");

  return (
    <>
      <div className="pb-4 pt-2">
        <p className="text-body text-muted">{name}</p>
      </div>
      <WelcomeCard name={name} />
      <Nudges phoneRequired={phoneRequired} emailVerified={emailVerified} live={liveRow} />

      <Doors />

      <section className="mt-6" aria-labelledby="next-exam">
        <h2 id="next-exam" className="mb-2 text-card font-bold">{t("home.nextExam")}</h2>
        {routine.isPending ? (
          <Skeleton variant="card" />
        ) : routine.isError && !routine.data ? (
          <ErrorState onRetry={() => void routine.refetch()} />
        ) : !next ? (
          <p className="text-muted">{t("home.noUpcoming")}</p>
        ) : isLive(next, now) ? (
          <Card href={examPath(next.id)}>
            <Pill tone="warn" pulse>{t("home.liveNow")}</Pill>
            <p className="mt-1 font-bold">{pickTitle(next, locale)}</p>
            <p className="mt-1 text-body font-semibold text-accent">{t("home.sitNow")} ›</p>
          </Card>
        ) : (
          <Card className="border-s-4 border-s-accent">
            <p className="text-body text-muted">{t("home.startsIn", { title: pickTitle(next, locale) })}</p>
            <p className="text-card font-bold"><Countdown deadline={next.liveAt} /></p>
            <p className="text-body text-muted">{formatDate(next.liveAt, locale, { year: "auto" })} · {formatTime(next.liveAt, locale)}</p>
          </Card>
        )}
      </section>

      {nowRows.length > 0 ? (
        <section className="mt-6" aria-labelledby="now-section">
          <h2 id="now-section" className="mb-2 text-card font-bold">{t("home.nowSection")}</h2>
          <div className="grid gap-2.5">{nowRows.map((row) => <RoutineLine key={row.id} row={row} />)}</div>
        </section>
      ) : null}

      <section className="mt-6" aria-labelledby="upcoming-section">
        <div className="mb-2 flex items-center justify-between">
          <h2 id="upcoming-section" className="text-card font-bold">{t("home.upcomingSection")}</h2>
          <Link href="/routine" className="text-label font-semibold text-accent">{t("home.seeAll")}</Link>
        </div>
        {soon.length === 0 ? (
          <p className="text-muted">{t("home.noUpcoming")}</p>
        ) : (
          <div className="grid gap-2.5">{soon.map((row) => <RoutineLine key={row.id} row={row} />)}</div>
        )}
      </section>

      <section className="mt-6" aria-labelledby="my-modules">
        <div className="mb-2 flex items-center justify-between">
          <h2 id="my-modules" className="text-card font-bold">{t("home.myModules")}</h2>
          <Link href="/my-modules" className="text-label font-semibold text-accent">{t("home.seeAll")}</Link>
        </div>
        {modules.isPending ? (
          <Skeleton variant="card" count={2} />
        ) : (modules.data ?? []).length === 0 ? (
          <EmptyState title={t("home.emptyTitle")} body={t("home.emptyBody")} action={<DoorActions />} />
        ) : (
          <div className="grid gap-2.5">
            {(modules.data ?? []).slice(0, 4).map((module) => (
              <Card key={module.id} href={modulePath("mcq", module.id)}>
                <p className="font-bold">{pickTitle(module, locale)}</p>
                <p className="mt-1"><Pill tone="ok">{t("catalog.unlocked")}</Pill></p>
              </Card>
            ))}
          </div>
        )}
      </section>
    </>
  );
}

export function HomePage() {
  const { session } = useSession();
  if (session.status === "loading") {
    return <Skeleton variant="card" count={3} />;
  }
  if (session.status === "anonymous") {
    return <SignedOutHome />;
  }
  const user = session.user;
  return (
    <SignedInHome
      isStaff={user.role !== "STUDENT"}
      phoneRequired={Boolean(user.phoneRequired)}
      emailVerified={Boolean(user.emailVerifiedAt)}
      name={user.fullName}
    />
  );
}
