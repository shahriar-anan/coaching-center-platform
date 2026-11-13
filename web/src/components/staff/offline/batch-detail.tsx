"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { FileDrop } from "@/components/ui/file-drop";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import {
  openAuthorizedFile,
  useArchiveBatch,
  useBatch,
  useBatchStudents,
  useUploadRoutine,
  type BatchStudent,
} from "@/lib/api/exam/offline-admin";
import { AdmissionMoneyPills } from "./money-pills";
import { BatchFormDialog } from "./batch-form-dialog";
import { offlineErrorText } from "./offline-error";
import { stateLabelKey } from "./labels";

/** The routine is a PDF of at most 20 MB, as the API checks it. */
const ROUTINE_MAX_BYTES = 20 * 1024 * 1024;

/**
 * One batch (plan 6.3): its details (the Master Admin edits them), the routine (staff and the Master Admin upload it), and the students with
 * their money pills. A batch with onboarded students cannot be archived.
 */
export function BatchDetail({ id }: { id: string }) {
  const { t } = useLocale();
  const role = useRole();
  const { formatDate, formatMoney, formatDateTime, pickTitle } = useFormat();
  const { toast } = useToast();
  const batch = useBatch(id);
  const students = useBatchStudents(id);
  const upload = useUploadRoutine(id);
  const archive = useArchiveBatch(id);
  const [editing, setEditing] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [routineError, setRoutineError] = useState<string | null>(null);

  if (batch.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (batch.isError) {
    return <ErrorState onRetry={() => batch.refetch()} error={batch.error} />;
  }
  const b = batch.data;
  const name = pickTitle({ titleEn: b.nameEn, titleBn: b.nameBn });
  const archived = b.status === "ARCHIVED";
  const onboarded = b.studentCount;

  const columns: Column<BatchStudent>[] = [
    {
      key: "name",
      header: t("staffOffline.batches.students.name"),
      cell: (row) => (
        <Link href={`/staff/admissions/${row.admissionId}`} className="font-medium text-text underline-offset-4 hover:underline">
          {row.fullName}
        </Link>
      ),
    },
    {
      key: "mobile",
      header: t("staffOffline.batches.students.mobile"),
      mobileLabel: t("staffOffline.batches.students.mobile"),
      cell: (row) => <span className="font-latin">{row.mobile}</span>,
    },
    {
      key: "state",
      header: t("staffOffline.batches.students.state"),
      mobileLabel: t("staffOffline.batches.students.state"),
      cell: (row) => <span>{t(stateLabelKey(row.status))}</span>,
    },
    {
      key: "money",
      header: t("staffOffline.batches.students.money"),
      mobileLabel: t("staffOffline.batches.students.money"),
      cell: (row) => <AdmissionMoneyPills admissionId={row.admissionId} enabled={row.status === "ONBOARDED" || row.status === "CLOSED"} />,
    },
  ];

  return (
    <div className="grid gap-6">
      <PageHeader
        title={name}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffOffline.batches.title"), href: "/staff/offline-batches" },
          { label: name },
        ]}
        actions={
          role.canManageOfflineBatches && !archived ? (
            <Button variant="secondary" onClick={() => setEditing(true)}>
              {t("staffOffline.batches.edit")}
            </Button>
          ) : undefined
        }
      />

      <section aria-labelledby="batch-details" className="grid gap-3 rounded-card border border-border-control bg-card p-4">
        <h3 id="batch-details" className="text-card-title text-text">
          {t("staffOffline.batches.details")}
        </h3>
        {!role.canManageOfflineBatches ? <p className="text-meta text-muted">{t("staffOffline.batches.readOnly")}</p> : null}
        <dl className="grid gap-3 sm:grid-cols-2">
          <Detail label={t("staffOffline.batches.nameEn")} value={b.nameEn} />
          <Detail label={t("staffOffline.batches.nameBn")} value={b.nameBn} />
          <Detail label={t("staffOffline.batches.startsOn")} value={b.startsOn ? formatDate(b.startsOn) : "—"} />
          <Detail label={t("staffOffline.batches.endsOn")} value={formatDate(b.endsOn)} />
          <Detail label={t("staffOffline.batches.fee")} value={formatMoney(Number(b.defaultMonthlyFee)) ?? "—"} />
          <Detail label={t("staffOffline.batches.studentsCount")} value={String(onboarded)} />
        </dl>
        {role.canManageOfflineBatches && !archived ? (
          <div>
            <Button
              variant="danger"
              disabled={onboarded > 0}
              onClick={() => setArchiving(true)}
            >
              {t("staffOffline.batches.archive")}
            </Button>
            {onboarded > 0 ? (
              <p className="mt-2 text-meta text-muted">{t("staffOffline.batches.archiveBlocked", { count: onboarded })}</p>
            ) : null}
          </div>
        ) : null}
      </section>

      <section aria-labelledby="batch-routine" className="grid gap-3 rounded-card border border-border-control bg-card p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 id="batch-routine" className="text-card-title text-text">
            {t("staffOffline.batches.routine")}
          </h3>
          {b.routineUploaded ? <Pill tone="ok">{t("staffOffline.batches.routineUploaded")}</Pill> : <Pill tone="warn">{t("staffOffline.batches.noRoutine")}</Pill>}
        </div>
        {b.routineUploaded ? (
          <div className="flex flex-wrap items-center gap-2">
            <span className="min-w-0 break-words text-body text-text">{b.routineFileName}</span>
            <span className="text-meta text-muted">
              {t("staffOffline.batches.routineOn", { date: b.routineUploadedAt ? formatDateTime(b.routineUploadedAt) : "" })}
            </span>
            <Button
              size="sm"
              variant="secondary"
              onClick={async () => {
                try {
                  await openAuthorizedFile(`/api/v1/offline-batches/${b.id}/routine.pdf`);
                } catch {
                  toast({ title: t("staffOffline.errors.generic"), tone: "danger" });
                }
              }}
            >
              {t("staffOffline.batches.viewRoutine")}
            </Button>
          </div>
        ) : (
          <p className="text-meta text-muted">{t("staffOffline.batches.routineEmpty")}</p>
        )}
        {b.routineUploaded && (b.routineUploadedBy || typeof b.studentsNotified === "number") ? (
          <dl className="grid gap-3 sm:grid-cols-2">
            {b.routineUploadedBy ? <Detail label={t("staffOffline.batches.uploadedBy")} value={b.routineUploadedBy} /> : null}
            {typeof b.studentsNotified === "number" ? (
              <Detail label={t("staffOffline.batches.studentsNotified")} value={String(b.studentsNotified)} />
            ) : null}
          </dl>
        ) : null}
        {!archived ? (
          <FileDrop
            accept="pdf"
            maxBytes={ROUTINE_MAX_BYTES}
            uploading={upload.isPending}
            error={routineError ?? undefined}
            onFile={(file) => {
              setRoutineError(null);
              upload.mutate(file, {
                onSuccess: () => toast({ title: t("staffOffline.batches.routineSaved"), tone: "ok" }),
                onError: (error) => setRoutineError(offlineErrorText(error, t)),
              });
            }}
          />
        ) : null}
        <p className="text-meta text-muted">{t("staffOffline.batches.routineNote")}</p>
      </section>

      <section aria-labelledby="batch-students" className="grid gap-3">
        <h3 id="batch-students" className="text-card-title text-text">
          {t("staffOffline.batches.students.title")}
        </h3>
        {students.isError ? (
          <ErrorState onRetry={() => students.refetch()} error={students.error} />
        ) : (
          <Table
            columns={columns}
            rows={students.data ?? []}
            rowKey={(row) => row.admissionId}
            loading={students.isPending}
            empty={<EmptyState title={t("staffOffline.batches.students.empty")} body={t("staffOffline.batches.students.emptyBody")} />}
          />
        )}
      </section>

      {role.canManageOfflineBatches ? (
        <>
          <BatchFormDialog batch={b} open={editing} onOpenChange={setEditing} />
          <ConfirmDialog
            open={archiving}
            onOpenChange={setArchiving}
            title={t("staffOffline.batches.archiveTitle", { name })}
            body={t("staffOffline.batches.archiveBody")}
            confirmLabel={t("staffOffline.batches.archiveConfirm")}
            tone="danger"
            onConfirm={async () => {
              try {
                await archive.mutateAsync();
                toast({ title: t("staffOffline.batches.archivedDone"), tone: "ok" });
              } catch (error) {
                throw new Error(offlineErrorText(error, t));
              }
            }}
          />
        </>
      ) : null}
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="break-words text-body text-text">{value || "—"}</dd>
    </div>
  );
}
