"use client";

import { Suspense } from "react";
import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/error-state";
import { SearchInput } from "@/components/ui/search-input";
import { useLocale } from "@/i18n/locale-context";
import { useModuleSearch, useUpcomingExams, type UpcomingExam } from "@/lib/api/exam/catalog";
import { formatDateTime, formatMoney, pickTitle } from "@/lib/format";
import { useListParams } from "@/lib/url/list-params";
import { doorType, examPath, modulePath, type Door } from "./catalog-helpers";

/** Search and the upcoming strip above a door's categories. Uses the URL for the search text. */
export function DoorDiscovery({ door }: { door: Door }) {
  return (
    <Suspense fallback={null}>
      <Discovery door={door} />
    </Suspense>
  );
}

function Discovery({ door }: { door: Door }) {
  const { t, locale } = useLocale();
  const [params, setParams] = useListParams({ q: "" });
  const term = params.q.trim();
  const results = useModuleSearch(term);
  const upcoming = useUpcomingExams(doorType(door));

  const matches = (results.data ?? []).filter((module) => {
    const needle = term.toLowerCase();
    return [module.titleEn, module.titleBn].some((title) => title?.toLowerCase().includes(needle));
  });

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
          <h2 id="door-search-results" className="text-card font-bold">{t("catalog.search.results")}</h2>
          {results.isPending ? null : results.isError && !results.data ? (
            <ErrorState onRetry={() => void results.refetch()} />
          ) : matches.length === 0 ? (
            <p className="text-body text-muted">{t("catalog.search.empty")}</p>
          ) : (
            matches.map((module) => (
              <Card key={module.id} href={modulePath(door, module.id)}>
                <p className="text-card font-bold">📚 {pickTitle(module, locale)}</p>
              </Card>
            ))
          )}
        </section>
      ) : null}

      {upcoming.data && upcoming.data.length > 0 ? (
        <section aria-labelledby="door-upcoming" className="grid gap-2.5">
          <h2 id="door-upcoming" className="text-card font-bold">{t("catalog.upcoming.title")}</h2>
          {upcoming.data.map((exam) => (
            <Card key={exam.id} href={examPath(exam.id)}>
              <UpcomingRow exam={exam} />
            </Card>
          ))}
        </section>
      ) : null}
    </div>
  );
}

function UpcomingRow({ exam }: { exam: UpcomingExam }) {
  const { t, locale } = useLocale();
  const moduleTitle = { titleEn: exam.moduleTitleEn ?? "", titleBn: exam.moduleTitleBn ?? null };
  const price = exam.price !== null && exam.price !== undefined ? formatMoney(exam.price, locale) : null;
  return (
    <>
      <p className="font-semibold text-text">{pickTitle(exam, locale)}</p>
      <p className="text-body text-muted">{pickTitle(moduleTitle, locale)}</p>
      <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-body">
        <span className="text-muted">{exam.liveAt ? formatDateTime(exam.liveAt, locale) : t("catalog.dateTbd")}</span>
        <span className="font-semibold">{price ?? t("catalog.priceNotSet")}</span>
      </p>
    </>
  );
}
