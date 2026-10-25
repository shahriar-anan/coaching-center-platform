"use client";

import Link from "next/link";
import {
  useMyModules,
  useMyModulesExams,
  useRoutine,
  type RoutineRow,
  type StudentExam,
} from "@/lib/api/exam/catalog";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { useServerNow } from "@/lib/time/clock";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { doorPath, isDone, modulePath } from "./catalog-helpers";
import { ProgressBar } from "./module-page";
import { TypePill } from "./status-pill";

export function MyModulesPage() {
  const { t, locale } = useLocale();
  const now = useServerNow(30_000);
  const modules = useMyModules(true);
  const routine = useRoutine(true);
  const list = modules.data ?? [];
  const exams = useMyModulesExams(
    list.map((m) => m.id),
    list.length > 0,
  );
  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: t("catalog.myModules.title") }];

  if (modules.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.myModules.title")} breadcrumbs={crumbs} />
        <Skeleton variant="card" count={3} />
      </>
    );
  }
  if (modules.isError && !modules.data) {
    return (
      <>
        <PageHeader title={t("catalog.myModules.title")} breadcrumbs={crumbs} />
        <ErrorState onRetry={() => void modules.refetch()} />
      </>
    );
  }

  return (
    <>
      <PageHeader title={t("catalog.myModules.title")} breadcrumbs={crumbs} />
      {list.length === 0 ? (
        <EmptyState
          title={t("catalog.myModules.empty")}
          body={t("catalog.myModules.emptyBody")}
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Button variant="secondary" href={doorPath("mcq")}>{t("catalog.myModules.openMcq")}</Button>
              <Button variant="secondary" href={doorPath("written")}>{t("catalog.myModules.openWritten")}</Button>
            </div>
          }
        />
      ) : (
        <ul className="grid gap-3">
          {list.map((module, index) => {
            const mine: StudentExam[] = (exams[index]?.data as StudentExam[] | undefined) ?? [];
            const done = mine.filter((e) => isDone(e.myStatus)).length;
            const nextRows: RoutineRow[] = (routine.data ?? [])
              .filter((row) => row.moduleId === module.id && Date.parse(row.endAt) > now)
              .sort((a, b) => Date.parse(a.liveAt) - Date.parse(b.liveAt))
              .slice(0, 2);
            return (
              <li key={module.id}>
                <Card padding="lg">
                  <p className="text-card font-bold">📚 {pickTitle(module, locale)}</p>
                  <ProgressBar done={done} total={mine.length} />
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Button variant="secondary" href={modulePath("mcq", module.id)}>{t("catalog.myModules.openMcq")}</Button>
                    <Button variant="secondary" href={modulePath("written", module.id)}>{t("catalog.myModules.openWritten")}</Button>
                  </div>
                  <div className="mt-3 grid gap-1 text-meta">
                    <p className="font-semibold text-muted">{t("catalog.myModules.next")}</p>
                    {nextRows.length === 0 ? (
                      <p className="text-muted">{t("catalog.myModules.noneNext")}</p>
                    ) : (
                      nextRows.map((row) => (
                        <p key={row.id} className="flex flex-wrap items-center gap-2">
                          <Link href={`/exam/${row.id}`} className="font-semibold text-accent">
                            {pickTitle(row, locale)}
                          </Link>
                          <TypePill type={row.examType} />
                          <span className="text-muted">
                            {formatDate(row.liveAt, locale, { year: "auto" })} · {formatTime(row.liveAt, locale)}
                          </span>
                        </p>
                      ))
                    )}
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
    </>
  );
}
