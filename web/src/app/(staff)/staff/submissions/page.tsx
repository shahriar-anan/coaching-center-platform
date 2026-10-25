"use client";

import { Suspense, useMemo } from "react";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { ALL_STAFF, RequireStaff } from "@/lib/auth/guards";
import { useParticipation, type ParticipationRow } from "@/lib/api/exam/staff-people";
import { useFormat } from "@/lib/format/use-format";
import { useLocale } from "@/i18n/locale-context";

export default function StaffSubmissionsPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={3} />}>
      <RequireStaff roles={ALL_STAFF}>
        <Landing />
      </RequireStaff>
    </Suspense>
  );
}

type ModuleGroup = {
  moduleId: string;
  titleEn: string;
  titleBn: string;
  awaiting: number;
  drafts: number;
  exams: number;
};

/** The written exams grouped by module, with the two counts a marker needs. */
function groupByModule(rows: ParticipationRow[]): ModuleGroup[] {
  const groups = new Map<string, ModuleGroup>();
  for (const row of rows) {
    if (row.examType !== "WRITTEN") {
      continue;
    }
    const group = groups.get(row.moduleId) ?? {
      moduleId: row.moduleId,
      titleEn: row.moduleTitleEn,
      titleBn: row.moduleTitleBn,
      awaiting: 0,
      drafts: 0,
      exams: 0,
    };
    group.awaiting += row.awaitingMarking ?? 0;
    group.drafts += row.draftsAwaitingPublish ?? 0;
    group.exams += 1;
    groups.set(row.moduleId, group);
  }
  return [...groups.values()];
}

function Landing() {
  const { t } = useLocale();
  const format = useFormat();
  const written = useParticipation({
    page: 0,
    size: 100,
    moduleId: "",
    type: "WRITTEN",
    from: "",
    to: "",
    q: "",
    sort: "title",
  });
  const groups = useMemo(() => groupByModule(written.data?.content ?? []), [written.data]);

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPeople.submissions.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffPeople.submissions.title") }]}
      />

      {written.isError ? (
        <ErrorState onRetry={() => written.refetch()} />
      ) : written.isPending ? (
        <Skeleton variant="card" count={3} />
      ) : groups.length === 0 ? (
        <EmptyState title={t("staffPeople.submissions.empty")} body={t("staffPeople.submissions.emptyBody")} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {groups.map((group) => (
            <Card key={group.moduleId} href={`/staff/submissions/m/${group.moduleId}`}>
              <h2 className="text-lg font-semibold text-text">
                {format.pickTitle({ titleEn: group.titleEn, titleBn: group.titleBn })}
              </h2>
              <p className="mt-2 text-sm text-muted">
                {t("staffPeople.submissions.awaitingLine", { count: format.formatNumber(group.awaiting) })}
              </p>
              <p className="text-sm text-muted">
                {t("staffPeople.submissions.publishLine", { count: format.formatNumber(group.drafts) })}
              </p>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
