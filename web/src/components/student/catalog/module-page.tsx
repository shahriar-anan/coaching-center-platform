"use client";

import Link from "next/link";
import { useState } from "react";
import {
  useCategoryTree,
  useCheckoutEnabled,
  useMyModuleExams,
  usePublicModule,
  usePublicModuleExams,
  type PublicExam,
  type StudentExam,
} from "@/lib/api/exam/catalog";
import { ApiError } from "@/lib/api/errors";
import { useSession } from "@/lib/auth/session-context";
import { PurchaseSheet } from "@/components/student/purchase/purchase-sheet";
import { resolveAccess } from "@/components/student/purchase/access";
import { AccessBadge } from "@/components/ui/purchase/access-badge";
import { ConfigNotice } from "@/components/ui/purchase/config-notice";
import { BundleSelection } from "./bundle-selection";
import { bundleOrders } from "@/lib/config";
import { formatDate, formatMoney, formatNumber, formatTime, pickText, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import {
  bySchedule,
  categoryPath,
  doorLabelKey,
  doorPath,
  doorType,
  examPath,
  findModulePath,
  firstLine,
  isDone,
  modulePath,
  type Door,
} from "./catalog-helpers";
import { SectionCard } from "./lms-blocks";
import { ExamStatusPill, TypePill } from "./status-pill";

function scrollTo(id: string) {
  const reduce =
    typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  document.getElementById(id)?.scrollIntoView({ behavior: reduce ? "auto" : "smooth" });
}

function StripChip({
  icon,
  label,
  target,
  accent,
  children,
}: {
  icon: string;
  label: string;
  target: string;
  accent: "accent" | "warn" | "ok";
  children: React.ReactNode;
}) {
  const border = { accent: "border-t-accent", warn: "border-t-warn", ok: "border-t-ok" }[accent];
  return (
    <button
      type="button"
      onClick={() => scrollTo(target)}
      className={`grid min-h-11 content-start justify-items-center gap-1 rounded-card border border-t-4 border-border bg-card px-2 py-3 text-center text-text transition-colors hover:border-accent ${border}`}
    >
      <span aria-hidden className="text-title">
        {icon}
      </span>
      <span className="text-label font-bold">{label}</span>
      <span className="text-meta text-muted">{children}</span>
    </button>
  );
}

export function ProgressBar({ done, total }: { done: number; total: number }) {
  const { t, locale } = useLocale();
  if (total === 0) return null;
  const percent = Math.round((done / total) * 100);
  return (
    <div className="mt-3">
      <div className="flex items-center justify-between gap-3 text-body">
        <span className="text-muted">
          {t("catalog.progress", { done: formatNumber(done, locale), total: formatNumber(total, locale) })}
        </span>
        <span className="font-semibold tabular-nums">{formatNumber(percent, locale)}%</span>
      </div>
      <div
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={done}
        className="mt-2 h-2.5 overflow-hidden rounded-full bg-border"
      >
        <div className="h-full rounded-full bg-ok transition-[width] duration-500" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}

export function ModulePage({ door, moduleId }: { door: Door; moduleId: string }) {
  const { t, locale } = useLocale();
  const { session } = useSession();
  const now = useServerNow(30_000);
  const type = doorType(door);
  const signedIn = session.status === "authenticated";
  const isStudent = signedIn && session.user.role === "STUDENT";
  const phoneRequired = isStudent && Boolean(session.user.phoneRequired);

  const tree = useCategoryTree(type);
  const moduleQuery = usePublicModule(moduleId, signedIn);
  const exams = usePublicModuleExams(moduleId, type, signedIn);
  // Ownership comes from the access object on the responses. The student's exam reads are
  // only asked for when the student owns something here, so a visitor makes no such request.
  const moduleAccess = resolveAccess(moduleQuery.data?.access);
  const anyExamOwned = (exams.data ?? []).some((exam) => resolveAccess(exam.access).owned);
  const myExams = useMyModuleExams(moduleId, type, isStudent && (moduleAccess.owned || anyExamOwned));
  const [buying, setBuying] = useState(false);
  const checkoutOff = useCheckoutEnabled().data === false;

  const doorLabel = t(doorLabelKey(door));
  const baseCrumbs = [
    { label: t("catalog.home"), href: "/" },
    { label: doorLabel, href: doorPath(door) },
  ];

  if (moduleQuery.isPending || exams.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.loading")} breadcrumbs={baseCrumbs} />
        <Skeleton variant="card" count={4} />
      </>
    );
  }
  if (moduleQuery.isError && moduleQuery.error instanceof ApiError && moduleQuery.error.code === "RESOURCE_NOT_FOUND") {
    return (
      <>
        <PageHeader title={t("catalog.notFound")} breadcrumbs={baseCrumbs} />
        <EmptyState
          title={t("catalog.notFound")}
          body={t("catalog.notFoundBody")}
          action={
            <Button variant="secondary" href={doorPath(door)}>
              {t("catalog.backToDoor", { door: doorLabel })}
            </Button>
          }
        />
      </>
    );
  }
  if (!moduleQuery.data || (exams.isError && !exams.data)) {
    return (
      <>
        <PageHeader title={t("catalog.loadFailed")} breadcrumbs={baseCrumbs} />
        <ErrorState
          onRetry={() => {
            void moduleQuery.refetch();
            void exams.refetch();
          }}
        />
      </>
    );
  }

  const data = moduleQuery.data;
  const title = pickTitle(data, locale);
  const description = pickText({ ...data }, "description", locale);
  const path = tree.data ? findModulePath(tree.data, moduleId) ?? [] : [];
  const breadcrumbs = [
    ...baseCrumbs,
    ...path.map((c) => ({ label: pickTitle(c, locale), href: categoryPath(door, c.id) })),
    { label: title },
  ];

  const owned = new Map<string, StudentExam>((myExams.data ?? []).map((e) => [e.id, e]));
  const moduleOwned = moduleAccess.owned;
  const list: (PublicExam & { mine?: StudentExam })[] = (exams.data ?? [])
    .map((exam) => ({ ...exam, mine: owned.get(exam.id) }))
    .sort(bySchedule);

  const nextDated = list.find((e) => e.endAt && Date.parse(e.endAt) > now && e.liveAt);
  const mine = [...owned.values()];
  const scriptPill = mine.some((e) => e.myStatus === "AWAITING_RESULT")
    ? { tone: "warn" as const, key: "catalog.pending" }
    : mine.some((e) => e.myStatus === "SUBMITTED")
      ? { tone: "ok" as const, key: "catalog.submittedChip" }
      : { tone: "muted" as const, key: "catalog.remaining" };
  const resultReady = mine.some((e) => e.myStatus === "RESULT_READY");
  const showOwnStrip = isStudent && mine.length > 0;

  const price = data.price;
  const loginHref = `/login?next=${encodeURIComponent(modulePath(door, moduleId))}`;
  const countLine = type === "MCQ" ? t("catalog.countMcq", { n: formatNumber(list.length, locale) }) : t("catalog.countWritten", { n: formatNumber(list.length, locale) });
  const summaryLine = nextDated?.liveAt
    ? `${countLine} · ${t("catalog.summaryNext", { date: formatDate(nextDated.liveAt, locale, { year: "auto" }) })}`
    : countLine;

  return (
    <>
      <PageHeader title={title} breadcrumbs={breadcrumbs} />
      <ConfigNotice show={checkoutOff} />

      <section className="mb-4 grid gap-3 rounded-card border border-border bg-card p-5 md:p-6">
        <div className="flex flex-wrap items-center gap-2">
          <TypePill type={type} />
          {moduleOwned ? <Pill tone="ok">✓ {t("catalog.unlocked")}</Pill> : null}
        </div>
        {description ? <p className="whitespace-pre-line leading-relaxed text-muted">{description}</p> : null}
        {list.length > 0 ? (
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 border-t border-border pt-3 text-body font-semibold">
            <span aria-hidden="true">🗓️</span>
            <span>{summaryLine}</span>
          </p>
        ) : null}
      </section>

      <div className={`mb-4 grid gap-2.5 ${showOwnStrip ? (door === "written" ? "grid-cols-3" : "grid-cols-2") : "grid-cols-1"}`}>
        <StripChip icon="📅" label={t("catalog.stripRoutine")} target="module-routine" accent="accent">
          {nextDated?.liveAt ? formatDate(nextDated.liveAt, locale, { year: "auto" }) : t("catalog.dateTbd")}
        </StripChip>
        {showOwnStrip && door === "written" ? (
          <StripChip icon="📝" label={t("catalog.stripScript")} target="module-exams" accent="warn">
            <Pill tone={scriptPill.tone}>{t(scriptPill.key)}</Pill>
          </StripChip>
        ) : null}
        {showOwnStrip ? (
          <StripChip icon="📊" label={t("catalog.stripResult")} target="module-exams" accent="ok">
            <Pill tone="ok">{t(resultReady ? "catalog.published" : "catalog.soon")}</Pill>
          </StripChip>
        ) : null}
      </div>

      <section className="mb-4 grid gap-3 rounded-card border border-border bg-card p-5" aria-label={t("catalog.buyModule")}>
        {moduleOwned ? (
          <ProgressBar done={mine.filter((e) => isDone(e.myStatus)).length} total={mine.length} />
        ) : price === null || price === undefined ? (
          <p className="text-muted">{t("catalog.priceNotSet")}</p>
        ) : (
          <>
            <p className="text-card-title font-bold">{t("catalog.modulePrice", { price: formatMoney(price, locale) ?? "" })}</p>
            <p className="text-body text-muted">{t("catalog.buyModuleCovers")}</p>
            {!signedIn ? (
              <Button fullWidth href={loginHref}>
                {t("catalog.buyModule")}
              </Button>
            ) : phoneRequired || moduleAccess.blockedReason === "PHONE_REQUIRED" ? (
              <Button
                fullWidth
                href={`/set-phone?next=${encodeURIComponent(modulePath(door, moduleId))}`}
              >
                {t("catalog.exam.addPhone")}
              </Button>
            ) : isStudent ? (
              <Button
                fullWidth
                disabled={checkoutOff || moduleAccess.buyable === false}
                onClick={() => setBuying(true)}
              >
                {t("catalog.buyModule")}
              </Button>
            ) : null}
          </>
        )}
      </section>

      <div className="grid gap-4">
        <SectionCard id="module-routine" icon="📅" title={t("catalog.routineTitle")}>
          {list.length === 0 ? (
            <p className="text-muted">{t(type === "MCQ" ? "catalog.examListEmptyMcq" : "catalog.examListEmptyWritten")}</p>
          ) : (
            <div className="-mx-4 overflow-x-auto px-4 max-md:[mask-image:linear-gradient(to_right,#000_88%,transparent)] max-md:[-webkit-mask-image:linear-gradient(to_right,#000_88%,transparent)]">
              <table className="w-full min-w-[420px] border-collapse text-body">
                <thead>
                  <tr className="text-start text-meta uppercase tracking-wide text-muted">
                    <th scope="col" className="rounded-s-control bg-bg px-3 py-2.5 font-semibold">{t("catalog.colExam")}</th>
                    <th scope="col" className="bg-bg px-3 py-2.5 font-semibold">{t("catalog.colDate")}</th>
                    <th scope="col" className="rounded-e-control bg-bg px-3 py-2.5 font-semibold">{t("catalog.colTime")}</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((exam) => (
                    <tr key={exam.id} className="even:bg-bg/60">
                      <td className="border-b border-border px-3 py-3 font-semibold">{pickTitle(exam, locale)}</td>
                      <td className="border-b border-border px-3 py-3">
                        {exam.liveAt ? formatDate(exam.liveAt, locale, { year: "auto" }) : t("catalog.dateTbd")}
                      </td>
                      <td className="whitespace-nowrap border-b border-border px-3 py-3 tabular-nums">
                        {exam.liveAt && exam.endAt
                          ? `${formatTime(exam.liveAt, locale)} – ${formatTime(exam.endAt, locale)}`
                          : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </SectionCard>

        <SectionCard
          id="module-exams"
          icon="📚"
          title={t("catalog.examListTitle")}
          count={list.length > 0 ? formatNumber(list.length, locale) : undefined}
        >
          {list.length === 0 ? (
            <EmptyState
              title={t(type === "MCQ" ? "catalog.examListEmptyMcq" : "catalog.examListEmptyWritten")}
              body={t(type === "MCQ" ? "catalog.otherTypeHint" : "catalog.otherTypeHintWritten")}
            />
          ) : (
            bundleOrders && isStudent ? (
              <BundleSelection
                disabled={checkoutOff}
                items={list.map((exam) => ({ id: exam.id, title: pickTitle(exam, locale), meta: exam.liveAt ? formatDate(exam.liveAt, locale, { year: "auto" }) : t("catalog.dateTbd"), price: exam.price ?? null, owned: resolveAccess(exam.access).owned }))}
              />
            ) : (
            <ul className="grid gap-2.5">
              {list.map((exam) => {
                const subtitle = firstLine(pickText({ ...exam }, "description", locale));
                const examAccess = resolveAccess(exam.access);
                return (
                  <li key={exam.id}>
                    <Link
                      href={examPath(exam.id)}
                      prefetch
                      className="group flex items-center gap-3 rounded-card border border-border bg-card p-3.5 transition-colors hover:border-accent"
                    >
                      <span
                        aria-hidden="true"
                        className="flex size-11 shrink-0 items-center justify-center rounded-control bg-accent/15 text-title"
                      >
                        {type === "MCQ" ? "📝" : "📖"}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-card-title font-bold group-hover:text-text-accent">
                          {pickTitle(exam, locale)}
                        </span>
                        {subtitle ? <span className="block truncate text-meta text-muted">{subtitle}</span> : null}
                      </span>
                      <span className="shrink-0 text-end">
                        {exam.mine?.myStatus ? (
                          <ExamStatusPill
                            status={exam.mine.myStatus}
                            examType={exam.examType}
                            retakeAvailable={exam.mine.retakeAvailable}
                          />
                        ) : examAccess.via ? (
                          <AccessBadge via={examAccess.via} />
                        ) : exam.price !== null && exam.price !== undefined ? (
                          <span className="block text-meta">
                            <span className="block text-label font-bold">{formatMoney(exam.price, locale)}</span>
                            <span className="font-semibold text-text-accent">{t("catalog.buy")} ›</span>
                          </span>
                        ) : (
                          <span className="block max-w-28 text-meta text-muted">{t("catalog.priceNotSet")}</span>
                        )}
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
            )
          )}
        </SectionCard>
      </div>

      {isStudent && price !== null && price !== undefined && !moduleOwned ? (
        <PurchaseSheet
          open={buying}
          onOpenChange={setBuying}
          item={{ kind: "module", id: moduleId, title, price, door }}
        />
      ) : null}
    </>
  );
}
