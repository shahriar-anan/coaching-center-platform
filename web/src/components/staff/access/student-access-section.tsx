"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { staffHref } from "@/lib/config";
import {
  useRevokeGrant,
  useStudentAccessRows,
  type AdminAccessRow,
} from "@/lib/api/exam/staff-admin";
import { adminErrorText } from "./admin-error";
import { GrantAccessForm } from "./grant-access-form";
import { ReasonDialog } from "./reason-dialog";

/**
 * The access list on a student page: every module and exam the student can
 * sit, and why. Master Admin can revoke a grant and add the student to a module's
 * roster; System Admin can grant by hand and sees who can revoke. Any other role
 * gets nothing, and no request is made.
 */
export function StudentAccessSection({ studentId, studentName }: { studentId: string; studentName: string }) {
  const { isMasterAdmin, isSystemAdmin } = useRole();
  if (!isMasterAdmin && !isSystemAdmin) {
    return null;
  }
  return <StudentAccessContent studentId={studentId} studentName={studentName} />;
}

function StudentAccessContent({ studentId, studentName }: { studentId: string; studentName: string }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const { toast } = useToast();
  const { isMasterAdmin } = useRole();
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [revoking, setRevoking] = useState<AdminAccessRow | null>(null);

  const access = useStudentAccessRows(studentId, page);
  const revoke = useRevokeGrant(studentId);
  // Manual grants are the Master Admin's. The System Admin sees the access rows but not the grant control.
  const canGrant = isMasterAdmin;

  const moduleTitle = (row: AdminAccessRow) => pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn });

  const columns: Column<AdminAccessRow>[] = [
    {
      key: "source",
      header: t("staffAdmin.access.columns.source"),
      cell: (row) => t(`staffAdmin.access.via.${row.via}`),
    },
    {
      key: "module",
      header: t("staffAdmin.access.columns.module"),
      cell: moduleTitle,
    },
    {
      key: "exams",
      header: t("staffAdmin.access.columns.exams"),
      cell: (row) => (row.exams.length > 0 ? row.exams.map((exam) => pickTitle(exam)).join(", ") : "—"),
    },
    {
      key: "tranId",
      header: t("staffAdmin.access.columns.tranId"),
      cell: (row) =>
        row.tranId ? (
          <Link href={staffHref(`/staff/payments/${row.tranId}`)} className="text-action underline underline-offset-4">
            {row.tranId}
          </Link>
        ) : (
          t("staffAdmin.access.noTranId")
        ),
    },
  ];
  if (isMasterAdmin) {
    columns.push({
      key: "action",
      header: t("staffAdmin.access.columns.action"),
      align: "end",
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-2">
          {row.grantId ? (
            <Button variant="danger" size="sm" onClick={() => setRevoking(row)}>
              {t("staffAdmin.access.revoke")}
            </Button>
          ) : null}
          <Link
            href={staffHref(`/staff/modules/${row.moduleId}?tab=roster`)}
            className="text-label text-action underline-offset-4 hover:underline"
          >
            {/* A row already on the roster is managed there (remove is on that tab), so it does not say "add". */}
            {row.via === "ROSTER" ? t("staffAdmin.access.manageRoster") : t("staffPeople.student.addToRoster")}
          </Link>
        </div>
      ),
    });
  }

  const rows = access.data?.content ?? [];

  return (
    <section aria-labelledby="student-access-title" className="grid gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="grid gap-1">
          <h2 id="student-access-title" className="text-section font-semibold text-text">
            {t("staffAdmin.access.title")}
          </h2>
          <p className="text-meta text-muted">{t("staffAdmin.access.intro")}</p>
        </div>
        {canGrant && !adding ? (
          <Button variant="secondary" onClick={() => setAdding(true)}>
            {t("staffAdmin.access.grantOpen")}
          </Button>
        ) : null}
      </div>

      {access.isError ? (
        <ErrorState message={t("staffAdmin.access.loadError")} onRetry={() => void access.refetch()} />
      ) : access.isPending ? (
        <Skeleton variant="rows" count={3} />
      ) : rows.length === 0 ? (
        <EmptyState title={t("staffAdmin.access.empty")} />
      ) : (
        <Table
          columns={columns}
          rows={rows}
          rowKey={(row) => `${row.via}:${row.grantId ?? row.orderId ?? row.moduleId}`}
          loading={access.isFetching}
        />
      )}

      {access.data && access.data.totalPages > 1 ? (
        <Pagination page={page} totalPages={access.data.totalPages} onPage={setPage} />
      ) : null}

      {isMasterAdmin ? null : <p className="text-meta text-muted">{t("staffAdmin.access.revokeOnlyMaster")}</p>}

      {canGrant && adding ? (
        <Card as="section" padding="md">
          <GrantAccessForm studentId={studentId} onDone={() => setAdding(false)} />
        </Card>
      ) : null}

      <ReasonDialog
        open={revoking !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRevoking(null);
          }
        }}
        title={t("staffAdmin.revoke.title", { name: studentName })}
        body={
          revoking
            ? t("staffAdmin.revoke.body", {
                module: moduleTitle(revoking),
                source: t(`staffAdmin.access.via.${revoking.via}`),
              })
            : undefined
        }
        label={t("staffAdmin.revoke.reason")}
        requiredMessage={t("staffAdmin.revoke.reasonRequired")}
        confirmLabel={t("staffAdmin.revoke.confirm")}
        tone="danger"
        onConfirm={async (reason) => {
          const row = revoking;
          if (!row?.grantId) {
            return;
          }
          try {
            await revoke.mutateAsync({ grantId: row.grantId, reason });
            toast({ title: t("staffAdmin.revoke.done"), tone: "ok" });
          } catch (e) {
            throw new Error(adminErrorText(e, t));
          }
        }}
      />
    </section>
  );
}
