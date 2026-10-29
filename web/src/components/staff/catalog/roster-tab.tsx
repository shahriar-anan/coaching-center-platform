"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useAddToRoster, useRemoveFromRoster, useRoster, type RosterMember } from "@/lib/api/exam/staff-catalog";
import { OneStudentEnrol } from "@/components/staff/enrol/one-student-enrol";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Button } from "@/components/ui/button";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

/**
 * Master Admin only: who is enrolled in this package. A student is added one at a time, from the enrol panel; the list
 * below shows everyone enrolled, and a student is removed with a confirmation. A package that is not published takes no
 * new students; the students already on it keep their access.
 */
export function RosterTab({ moduleId, published }: { moduleId: string; published: boolean }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const { toast } = useToast();
  const [params, setParams] = useListParams({ q: "", page: 0 });
  const roster = useRoster(moduleId, params.q, params.page);
  const add = useAddToRoster(moduleId);
  const remove = useRemoveFromRoster(moduleId);
  const [removing, setRemoving] = useState<RosterMember | null>(null);

  const listed = new Set((roster.data?.content ?? []).map((m) => m.studentId));

  const columns: Column<RosterMember>[] = [
    { key: "name", header: t("staffCatalog.roster.columns.name"), cell: (m) => <span className="font-medium">{m.fullName}</span> },
    { key: "phone", header: t("staffCatalog.roster.columns.phone"), cell: (m) => <span dir="ltr">{m.phone ?? "—"}</span>, mobileLabel: t("staffCatalog.roster.columns.phone") },
    { key: "email", header: t("staffCatalog.roster.columns.email"), cell: (m) => <span dir="ltr">{m.email ?? "—"}</span> },
    { key: "code", header: t("staffCatalog.roster.columns.code"), cell: (m) => <span dir="ltr">{m.studentCode ?? "—"}</span> },
    { key: "added", header: t("staffCatalog.roster.columns.added"), cell: (m) => formatDate(m.addedAt) },
    {
      key: "remove",
      header: <span className="sr-only">{t("staffCatalog.roster.columns.actions")}</span>,
      align: "end",
      cell: (m) => (
        <Button variant="danger" className="min-h-10 px-2" onClick={() => setRemoving(m)}>
          {t("staffCatalog.roster.remove")}
        </Button>
      ),
    },
  ];

  async function enrolStudent(studentId: string) {
    await add.mutateAsync(studentId);
  }

  return (
    <div className="grid gap-6">
      <OneStudentEnrol
        headingId="roster-enrol-heading"
        enabled={published}
        disabledHint={t("staffCatalog.enrol.unpublishedPackage")}
        requireReason={false}
        enrolledIds={listed}
        enrol={enrolStudent}
      />

      <section aria-labelledby="roster-list-heading" className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 id="roster-list-heading" className="text-card-title font-semibold">
            {t("staffCatalog.roster.listHeading")}
          </h3>
          <div className="w-full sm:w-72">
            <SearchInput value={params.q} onChange={(q) => setParams({ q, page: 0 })} placeholder={t("staffCatalog.roster.listSearch")} />
          </div>
        </div>
        {roster.isError && !roster.data ? (
          <ErrorState onRetry={() => roster.refetch()} message={t("staffCatalog.roster.loadError")} />
        ) : (
          <>
            <Table
              columns={columns}
              rows={roster.data?.content ?? []}
              rowKey={(m) => m.studentId}
              loading={roster.isPending}
              empty={
                <EmptyState
                  title={params.q ? t("common.search.noResults") : t("staffCatalog.roster.emptyTitle")}
                  body={params.q ? undefined : t("staffCatalog.roster.emptyBody")}
                />
              }
            />
            <Pagination page={params.page} totalPages={roster.data?.totalPages ?? 0} onPage={(page) => setParams({ page })} />
          </>
        )}
      </section>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={t("staffCatalog.roster.removeTitle", { name: removing?.fullName ?? "" })}
        body={removing?.startedAny ? t("staffCatalog.roster.removeStartedBody") : t("staffCatalog.roster.removeBody")}
        confirmLabel={t("staffCatalog.roster.removeConfirm")}
        tone="danger"
        onConfirm={async () => {
          if (!removing) {
            return;
          }
          try {
            await remove.mutateAsync(removing.studentId);
            setRemoving(null);
            toast({ title: t("staffCatalog.roster.removed"), tone: "ok" });
          } catch (e) {
            throw new Error(t(catalogErrorKey(e)));
          }
        }}
      />
    </div>
  );
}
