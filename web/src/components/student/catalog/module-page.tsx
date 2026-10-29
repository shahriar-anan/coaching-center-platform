"use client";

import { useEffect, useRef, useState } from "react";
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
import { OpenOrderLine, PurchasedLine, useBoughtGrant } from "@/components/student/purchase/order-lines";
import { resolveAccess } from "@/components/student/purchase/access";
import { AccessBadge } from "@/components/ui/purchase/access-badge";
import { ConfigNotice } from "@/components/ui/purchase/config-notice";
import { BundleSelection } from "./bundle-selection";
import { ActionBar, DetailLayout, barActionOf, detailFrame } from "./detail-layout";
import { PriceLine } from "./price-line";
import { chargedAmount, priceLabel, pricingOf } from "./price-view";
import { bundleOrders } from "@/lib/config";
import { formatDate, formatNumber, formatTime, pickText, pickTitle } from "@/lib/format";
import { plainExcerpt } from "@/lib/format/rich-text";
import { RichTextView } from "@/components/ui/rich-text-view";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { cn } from "@/components/ui/cn";
import { Breadcrumbs } from "@/components/ui/breadcrumbs";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { PackageCover, coverSrc } from "@/components/ui/package/package-cover";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import {
  bySchedule,
  categoryPath,
  doorLabelKey,
  doorPath,
  doorType,
  examPath,
  findModulePath,
  isDone,
  modulePath,
  type Door,
} from "./catalog-helpers";
import { ExamRow } from "./exam-row";
import { FavouriteButton } from "./favourite-button";
import { IncludedList } from "./included-list";
import { ExamStatusPill, TypePill } from "./status-pill";

type ModuleTab = "exams" | "routine" | "about";

export function ProgressBar({ done, total }: { done: number; total: number }) {
  const { t, locale } = useLocale();
  if (total === 0) return null;
  const percent = Math.round((done / total) * 100);
  return (
    <div className="grid gap-2">
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
        className="h-2.5 overflow-hidden rounded-full bg-border"
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
  // The purchase of this package, when the student bought it (S4.6): its date and a link to the payment.
  const bought = useBoughtGrant({ moduleId }, isStudent && moduleAccess.owned);
  const [buying, setBuying] = useState(false);
  const [tab, setTab] = useState<ModuleTab>("exams");
  const [buyOffscreen, setBuyOffscreen] = useState(false);
  const buyRef = useRef<HTMLElement | null>(null);
  const checkoutOff = useCheckoutEnabled().data === false;

  // The sticky bar appears once the buy block has scrolled out of view. Only a phone needs it.
  const ready = Boolean(moduleQuery.data && exams.data);
  useEffect(() => {
    const element = buyRef.current;
    if (!ready || !element || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver(([entry]) => setBuyOffscreen(!entry.isIntersecting), { threshold: 0 });
    observer.observe(element);
    return () => observer.disconnect();
  }, [ready]);

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
  const price = data.price;
  const loginHref = `/login?next=${encodeURIComponent(modulePath(door, moduleId))}`;
  const countLine = type === "MCQ" ? t("catalog.countMcq", { n: formatNumber(list.length, locale) }) : t("catalog.countWritten", { n: formatNumber(list.length, locale) });
  const summaryLine = nextDated?.liveAt
    ? `${countLine} · ${t("catalog.summaryNext", { date: formatDate(nextDated.liveAt, locale, { year: "auto" }) })}`
    : countLine;
  const included = [t("catalog.package.currentExams", { n: formatNumber(list.length, locale) }), t("catalog.package.futureExams")];
  const mine = [...owned.values()];

  // The one buy action, shared by the buy block and the sticky bar.
  function buyAction(fullWidth: boolean) {
    if (!signedIn) {
      return <Button fullWidth={fullWidth} href={loginHref}>{t("catalog.buyModule")}</Button>;
    }
    if (phoneRequired || moduleAccess.blockedReason === "PHONE_REQUIRED") {
      return (
        <Button fullWidth={fullWidth} href={`/set-phone?next=${encodeURIComponent(modulePath(door, moduleId))}`}>
          {t("catalog.exam.addPhone")}
        </Button>
      );
    }
    if (isStudent) {
      return (
        <Button
          fullWidth={fullWidth}
          disabled={checkoutOff || moduleAccess.buyable === false}
          onClick={() => setBuying(true)}
        >
          {t("catalog.buyModule")}
        </Button>
      );
    }
    return null;
  }
  const barAction = price !== null && price !== undefined && !moduleOwned ? barActionOf(buyAction(false)) : null;
  const showBar = buyOffscreen && barAction !== null;

  const tabItems: { value: ModuleTab; label: string; badge?: number }[] = [
    { value: "exams", label: t("catalog.examListTitle"), badge: list.length },
    { value: "routine", label: t("catalog.routineTitle") },
    ...(description ? [{ value: "about" as const, label: t("catalog.package.about") }] : []),
  ];
  const activeTab = tabItems.some((item) => item.value === tab) ? tab : "exams";

  return (
    <div className={cn(detailFrame, barAction !== null && "pb-24 md:pb-0")}>
      <Breadcrumbs items={breadcrumbs} />
      <ConfigNotice show={checkoutOff} />

      <DetailLayout
        header={
          <header className="relative grid gap-2 pe-12">
            <div className="flex flex-wrap items-center gap-2">
              <Pill tone="muted">{doorLabel}</Pill>
              {moduleOwned ? <Pill tone="ok">✓ {t("catalog.unlocked")}</Pill> : null}
            </div>
            <h1 className="text-2xl font-extrabold leading-tight tracking-tight md:text-3xl">{title}</h1>
            <p className="text-body text-muted">{summaryLine}</p>
            <FavouriteButton kind="package" id={moduleId} initial={data.favourite} next={modulePath(door, moduleId)} />
          </header>
        }
        card={
          <section
            ref={buyRef}
            aria-label={t("catalog.buyModule")}
            className="grid gap-4 rounded-card border border-border bg-card p-5"
          >
            {moduleOwned ? (
              <>
                <ProgressBar done={mine.filter((e) => isDone(e.myStatus)).length} total={mine.length} />
                {bought ? <PurchasedLine grant={bought} /> : null}
              </>
            ) : price === null || price === undefined ? (
              <p className="text-muted">{t("catalog.priceNotSet")}</p>
            ) : (
              <>
                <div className="grid gap-1">
                  <span className="text-meta font-semibold uppercase tracking-wide text-muted">{t("catalog.buyModule")}</span>
                  <PriceLine label={priceLabel(pricingOf(data.pricing, price))} size="detail" />
                </div>
                <p className="text-body text-muted">{t("catalog.buyModuleCovers")}</p>
                <IncludedList items={included} />
                {buyAction(true)}
                <OpenOrderLine filter={{ moduleId }} enabled={isStudent} />
              </>
            )}
          </section>
        }
        cover={
          <PackageCover
            band
            src={coverSrc(data.coverUrl)}
            alt={t("catalog.package.coverAlt", { title })}
            type={type}
            title={title}
            className="rounded-card"
          />
        }
      >
        <div className="grid gap-5">
          {/* The tabs wrap at 320 px, so "About" is never cut off at the edge (Phase 4, S4.4). */}
          <Tabs
            tabs={tabItems}
            value={activeTab}
            onChange={(value) => setTab(value as ModuleTab)}
            ariaLabel={t("catalog.tabs.label")}
            wrap
          />

          <div>
            {activeTab === "exams" ? (
              list.length === 0 ? (
                <EmptyState
                  title={t(type === "MCQ" ? "catalog.examListEmptyMcq" : "catalog.examListEmptyWritten")}
                  body={t(type === "MCQ" ? "catalog.otherTypeHint" : "catalog.otherTypeHintWritten")}
                />
              ) : bundleOrders && isStudent ? (
                <BundleSelection
                  disabled={checkoutOff}
                  items={list.map((exam) => ({ id: exam.id, title: pickTitle(exam, locale), meta: exam.liveAt ? formatDate(exam.liveAt, locale, { year: "auto" }) : t("catalog.dateTbd"), price: chargedAmount(exam.pricing, exam.price), owned: resolveAccess(exam.access).owned }))}
                />
              ) : (
                <ul className="grid gap-3">
                  {list.map((exam) => {
                    const summary = plainExcerpt(pickText({ ...exam }, "description", locale), 160);
                    const examAccess = resolveAccess(exam.access);
                    return (
                      <li key={exam.id} className="min-w-0">
                        <ExamRow
                          href={examPath(exam.id)}
                          title={pickTitle(exam, locale)}
                          meta={
                            <>
                              <TypePill type={exam.examType} />
                              <span className="tabular-nums">{exam.liveAt ? formatDate(exam.liveAt, locale, { year: "auto" }) : t("catalog.dateTbd")}</span>
                            </>
                          }
                          summary={summary || null}
                          right={
                            exam.mine?.myStatus ? (
                              <ExamStatusPill
                                status={exam.mine.myStatus}
                                examType={exam.examType}
                                retakeAvailable={exam.mine.retakeAvailable}
                              />
                            ) : examAccess.via ? (
                              <AccessBadge via={examAccess.via} />
                            ) : exam.price !== null && exam.price !== undefined ? (
                              <span className="grid justify-items-end gap-0.5 text-meta">
                                <PriceLine label={priceLabel(pricingOf(exam.pricing, exam.price))} />
                                <span className="font-semibold text-text-accent">{t("catalog.buy")} ›</span>
                              </span>
                            ) : (
                              <span className="block max-w-28 text-end text-meta text-muted">{t("catalog.priceNotSet")}</span>
                            )
                          }
                          favourite={<FavouriteButton kind="exam" id={exam.id} initial={exam.favourite} next={examPath(exam.id)} />}
                        />
                      </li>
                    );
                  })}
                </ul>
              )
            ) : null}

            {activeTab === "routine" ? (
              list.length === 0 ? (
                <p className="text-muted">{t(type === "MCQ" ? "catalog.examListEmptyMcq" : "catalog.examListEmptyWritten")}</p>
              ) : (
                <>
                  <ul className="grid gap-3 md:hidden">
                    {list.map((exam) => (
                      <li key={exam.id} className="grid gap-1 rounded-card border border-border bg-card p-4">
                        <span className="text-label font-bold tabular-nums">
                          {exam.liveAt ? formatDate(exam.liveAt, locale, { year: "auto" }) : t("catalog.dateTbd")}
                        </span>
                        <span className="text-body text-muted tabular-nums">
                          {exam.liveAt && exam.endAt ? `${formatTime(exam.liveAt, locale)} – ${formatTime(exam.endAt, locale)}` : "—"}
                        </span>
                        <span className="font-semibold">{pickTitle(exam, locale)}</span>
                      </li>
                    ))}
                  </ul>
                  <div className="hidden overflow-hidden rounded-card border border-border bg-card md:block">
                    <table className="w-full border-collapse text-body">
                      <thead>
                        <tr className="text-start text-meta uppercase tracking-wide text-muted">
                          <th scope="col" className="bg-bg px-4 py-3 font-semibold">{t("catalog.colExam")}</th>
                          <th scope="col" className="bg-bg px-4 py-3 font-semibold">{t("catalog.colDate")}</th>
                          <th scope="col" className="bg-bg px-4 py-3 font-semibold">{t("catalog.colTime")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {list.map((exam) => (
                          <tr key={exam.id} className="border-t border-border">
                            <td className="px-4 py-3 font-semibold">{pickTitle(exam, locale)}</td>
                            <td className="px-4 py-3 tabular-nums">
                              {exam.liveAt ? formatDate(exam.liveAt, locale, { year: "auto" }) : t("catalog.dateTbd")}
                            </td>
                            <td className="whitespace-nowrap px-4 py-3 tabular-nums">
                              {exam.liveAt && exam.endAt ? `${formatTime(exam.liveAt, locale)} – ${formatTime(exam.endAt, locale)}` : "—"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )
            ) : null}

            {activeTab === "about" && description ? (
              <RichTextView value={description} className="lg:max-w-[70ch]" />
            ) : null}
          </div>
        </div>
      </DetailLayout>

      {barAction !== null ? (
        <ActionBar show={showBar} label={t("catalog.buyModule")} action={barAction}>
          <span className="text-meta text-muted">{t("catalog.buyModule")}</span>
          <PriceLine label={priceLabel(pricingOf(data.pricing, price))} size="detail" />
        </ActionBar>
      ) : null}

      {isStudent && price !== null && price !== undefined && !moduleOwned ? (
        <PurchaseSheet
          open={buying}
          onOpenChange={setBuying}
          item={{
            kind: "module",
            id: moduleId,
            title,
            price: chargedAmount(data.pricing, price) ?? price,
            listPrice: data.pricing?.price ?? price,
            discountPercent: data.pricing?.discountPercent ?? null,
            discountEndsAt: data.pricing?.discountEndsAt ?? null,
            door,
            coverUrl: data.coverUrl ?? null,
            examCount: list.length,
          }}
        />
      ) : null}
    </div>
  );
}
