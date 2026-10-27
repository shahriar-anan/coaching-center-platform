"use client";

import { Suspense, useState, type ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/error-state";
import { SearchInput } from "@/components/ui/search-input";
import { Tabs } from "@/components/ui/tabs";
import { useLocale } from "@/i18n/locale-context";
import { useModuleSearch, useUpcomingExams, type PublicModule, type UpcomingExam } from "@/lib/api/exam/catalog";
import type { ExamType } from "@/lib/api/exam/types";
import { formatDateTime, formatMoney, pickTitle } from "@/lib/format";
import { useListParams } from "@/lib/url/list-params";
import { useServerNow } from "@/lib/time/clock";
import { doorType, examPath, modulePath, moduleTypeCount, type Door } from "./catalog-helpers";
import { isMissing } from "@/lib/api/missing";

/**
 * Modules whose title matches `term` and that have exams of this door's type.
 * An unknown count (older API) is kept.
 */
export function searchMatches(modules: PublicModule[], term: string, type: ExamType): PublicModule[] {
  const needle = term.toLowerCase();
  return modules.filter((module) => {
    const titleMatch = [module.titleEn, module.titleBn].some((title) => title?.toLowerCase().includes(needle));
    const count = moduleTypeCount(module, type);
    return titleMatch && (count === null || count > 0);
  });
}

/**
 * Search, then the door's exam categories. The upcoming exams sit in a second
 * tab, so they never push the categories off the screen. With no `categories`,
 * the upcoming list shows on its own.
 */
export function DoorDiscovery({ door, categories }: { door: Door; categories?: ReactNode }) {
  return (
    <Suspense fallback={null}>
      <Discovery door={door} categories={categories} />
    </Suspense>
  );
}

type DiscoveryTab = "categories" | "upcoming";

function Discovery({ door, categories }: { door: Door; categories?: ReactNode }) {
  const { t, locale } = useLocale();
  const [params, setParams] = useListParams({ q: "" });
  const [tab, setTab] = useState<DiscoveryTab>("categories");
  const term = params.q.trim();
  const results = useModuleSearch(term);
  const type = doorType(door);
  const upcoming = useUpcomingExams(type);

  const matches = searchMatches(results.data ?? [], term, type);
  const upcomingExams = upcoming.data ?? [];
  const hasUpcoming = upcomingExams.length > 0;

  const upcomingSection = hasUpcoming ? (
    <section aria-labelledby="door-upcoming" className="grid gap-2.5">
      <h2 id="door-upcoming" className="text-card-title font-bold">{t("catalog.upcoming.title")}</h2>
      {upcomingExams.map((exam) => (
        <Card key={exam.id} href={examPath(exam.id)}>
          <UpcomingRow exam={exam} />
        </Card>
      ))}
    </section>
  ) : null;

  // Two tabs only when both lists have something to show; otherwise one list, with no tab bar.
  const tabs =
    categories && hasUpcoming
      ? [
          { value: "categories", label: t("catalog.tabCategories") },
          { value: "upcoming", label: t("catalog.upcoming.title"), badge: upcomingExams.length },
        ]
      : null;

  let body: ReactNode;
  if (tabs) body = tab === "upcoming" ? upcomingSection : categories;
  else body = categories ?? upcomingSection;

  return (
    <div className="mb-5 grid gap-5">
      <SearchInput
        id="door-search"
        value={params.q}
        onChange={(q) => setParams({ q })}
        placeholder={t("catalog.search.placeholder")}
      />

      {term ? (
        <section aria-labelledby="door-search-results" className="grid gap-2.5">
          <h2 id="door-search-results" className="text-card-title font-bold">{t("catalog.search.results")}</h2>
          {results.isPending ? null : results.isError && !results.data ? (
            <ErrorState onRetry={() => void results.refetch()} />
          ) : matches.length === 0 ? (
            <p className="text-body text-muted">{t("catalog.search.empty")}</p>
          ) : (
            matches.map((module) => (
              <Card key={module.id} href={modulePath(door, module.id)}>
                <p className="text-card-title font-bold">📚 {pickTitle(module, locale)}</p>
              </Card>
            ))
          )}
        </section>
      ) : null}

      {tabs ? (
        <Tabs
          tabs={tabs}
          value={tab}
          onChange={(value) => setTab(value as DiscoveryTab)}
          ariaLabel={t("catalog.tabs.label")}
        />
      ) : null}
      {body}
    </div>
  );
}

/**
 * What a row says in place of a price: "চালু আছে" when the exam is live now, the price when it has
 * one, "শুধু মডিউলে কেনা যাবে" for a module-only exam. Otherwise nothing: never "price not set" here.
 */
export function upcomingPriceNote(
  exam: { price: number | null; moduleOnly?: boolean; liveAt: string | null; endAt: string | null },
  now: number,
): { kind: "live" } | { kind: "price"; price: number } | { kind: "moduleOnly" } | null {
  const live = !isMissing(exam.liveAt) && !isMissing(exam.endAt) && Date.parse(exam.liveAt) <= now && now < Date.parse(exam.endAt);
  if (live) return { kind: "live" };
  if (exam.price !== null && exam.price !== undefined) return { kind: "price", price: exam.price };
  if (exam.moduleOnly) return { kind: "moduleOnly" };
  return null;
}

function UpcomingRow({ exam }: { exam: UpcomingExam }) {
  const { t, locale } = useLocale();
  const now = useServerNow(30_000);
  const moduleTitle = { titleEn: exam.moduleTitleEn ?? "", titleBn: exam.moduleTitleBn ?? null };
  const note = upcomingPriceNote(exam, now);
  return (
    <>
      <p className="font-semibold text-text">{pickTitle(exam, locale)}</p>
      <p className="text-body text-muted">{pickTitle(moduleTitle, locale)}</p>
      <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-body">
        <span className="text-muted">{exam.liveAt ? formatDateTime(exam.liveAt, locale) : t("catalog.dateTbd")}</span>
        {note?.kind === "live" ? <span className="font-semibold text-ok">{t("catalog.upcoming.live")}</span> : null}
        {note?.kind === "price" ? <span className="font-semibold">{formatMoney(note.price, locale)}</span> : null}
        {note?.kind === "moduleOnly" ? <span className="text-meta text-muted">{t("catalog.upcoming.moduleOnly")}</span> : null}
      </p>
    </>
  );
}
