"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { PageHeader } from "@/components/ui/page-header";
import { auditActionKey } from "@/components/staff/ops/ops-copy";
import { RequireStaff } from "@/lib/auth/guards";
import { useAuditLog, type AuditRow } from "@/lib/api/exam/staff-ops";
import { useListParams } from "@/lib/url/list-params";
import { useFormat } from "@/lib/format/use-format";
import { useLocale } from "@/i18n/locale-context";

export default function StaffAuditPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <AuditLog />
      </RequireStaff>
    </Suspense>
  );
}

/** The activity log: paged and newest first. There is no search, so no filter is drawn. */
function AuditLog() {
  const { t } = useLocale();
  const format = useFormat();
  const [params, setParams] = useListParams({ page: 0 });
  const audit = useAuditLog(params.page);

  const columns: Column<AuditRow>[] = [
    {
      key: "time",
      header: t("staffOps.audit.columns.time"),
      cell: (row) => format.formatDateTime(row.createdAt),
    },
    {
      key: "action",
      header: t("staffOps.audit.columns.action"),
      cell: (row) => {
        const key = auditActionKey(row.action);
        return key ? t(key) : row.action;
      },
    },
    {
      key: "entityType",
      header: t("staffOps.audit.columns.entityType"),
      cell: (row) => <span className="font-latin">{row.entityType}</span>,
    },
    {
      key: "entityId",
      header: t("staffOps.audit.columns.entityId"),
      cell: (row) => <span className="font-latin break-all text-meta">{row.entityId ?? "—"}</span>,
    },
    {
      key: "actor",
      header: t("staffOps.audit.columns.actor"),
      cell: (row) => <span className="font-latin break-all text-meta">{row.actorUserId ?? "—"}</span>,
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffOps.audit.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffOps.audit.title") }]}
      />
      <p className="text-meta text-muted">{t("staffOps.audit.newestFirst")}</p>

      {audit.isError ? (
        <ErrorState onRetry={() => audit.refetch()} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={audit.data?.content ?? []}
            rowKey={(row) => row.id}
            loading={audit.isPending}
            empty={<EmptyState title={t("staffOps.audit.empty")} />}
          />
          {audit.data ? (
            <Pagination
              page={audit.data.page}
              totalPages={audit.data.totalPages}
              onPage={(page) => setParams({ page })}
            />
          ) : null}
        </>
      )}
    </div>
  );
}
