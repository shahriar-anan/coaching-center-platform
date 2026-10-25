"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useModules } from "@/lib/api/exam/staff-catalog";
import { useParticipationRows } from "@/lib/api/exam/staff-home";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";

/** Only assigned modules, as cards. The server already limits them to the instructor's own. */
export function InstructorHome() {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  const modules = useModules();
  const participation = useParticipationRows({ sort: "date", size: 100 });

  const totals = useMemo(() => {
    const byModule = new Map<string, { awaiting: number; submitted: number; sitting: number }>();
    for (const row of participation.data?.content ?? []) {
      const current = byModule.get(row.moduleId) ?? { awaiting: 0, submitted: 0, sitting: 0 };
      current.awaiting += row.awaitingMarking ?? 0;
      current.submitted += row.submitted;
      current.sitting += row.sitting;
      byModule.set(row.moduleId, current);
    }
    return byModule;
  }, [participation.data]);

  if (modules.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (modules.isError && !modules.data) {
    return <ErrorState onRetry={() => modules.refetch()} message={t("staffCatalog.home.modulesError")} />;
  }

  const list = modules.data?.content ?? [];
  if (list.length === 0) {
    return <EmptyState title={t("staffCatalog.home.instructorEmptyTitle")} body={t("staffCatalog.home.instructorEmptyBody")} />;
  }

  return (
    <ul className="grid gap-4 sm:grid-cols-2">
      {list.map((module) => {
        const counts = totals.get(module.id) ?? { awaiting: 0, submitted: 0, sitting: 0 };
        return (
          <li key={module.id}>
            <Card className="grid h-full gap-3">
              <h2 className="text-base font-semibold">{locale === "bn" ? module.titleBn : module.titleEn}</h2>
              <dl className="grid grid-cols-3 gap-2 text-sm">
                <div>
                  <dt className="text-muted">{t("staffCatalog.home.counts.sitting")}</dt>
                  <dd className="text-lg font-semibold tabular-nums">{formatNumber(counts.sitting)}</dd>
                </div>
                <div>
                  <dt className="text-muted">{t("staffCatalog.home.counts.submitted")}</dt>
                  <dd className="text-lg font-semibold tabular-nums">{formatNumber(counts.submitted)}</dd>
                </div>
                <div>
                  <dt className="text-muted">{t("staffCatalog.home.counts.awaiting")}</dt>
                  <dd className="text-lg font-semibold tabular-nums">{formatNumber(counts.awaiting)}</dd>
                </div>
              </dl>
              <div className="flex flex-wrap gap-4 text-sm">
                <Link href={`/staff/submissions/m/${module.id}`} className="font-medium text-action hover:underline">
                  {t("staffCatalog.home.instructor.submissions")}
                </Link>
                <Link href={`/staff/participation?moduleId=${module.id}`} className="font-medium text-action hover:underline">
                  {t("staffCatalog.home.instructor.participation")}
                </Link>
              </div>
            </Card>
          </li>
        );
      })}
    </ul>
  );
}
