"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useArchiveInstructor, useOfflineInstructors, usePayFigures, type OfflineInstructor } from "@/lib/api/exam/instructor-pay";
import { toNumber } from "@/components/staff/offline/offline-model";
import { InstructorFormDialog } from "./instructor-form-dialog";

type Row = {
  instructor: OfflineInstructor;
  batchNames: string[];
  paidThisMonth: number;
  lastPaidOn: string | null;
};

/**
 * The offline instructors (plan 8.3; 24 Nov): name, phone, the batches they teach, what they were paid this month and their last
 * payment. The Master Admin only: they add, edit and archive instructors here; the API refuses an archive while live classes still name
 * the person.
 */
export function InstructorList() {
  const { t, locale } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const instructors = useOfflineInstructors(false);
  const figures = usePayFigures();
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<OfflineInstructor | null>(null);
  const [adding, setAdding] = useState(false);
  const [archiving, setArchiving] = useState<OfflineInstructor | null>(null);
  const archive = useArchiveInstructor();

  const rows: Row[] = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return (instructors.data ?? [])
      .filter((instructor) => {
        if (!needle) {
          return true;
        }
        return [instructor.name, instructor.phone ?? "", ...instructor.aliases].some((value) => value.toLowerCase().includes(needle));
      })
      .map((instructor) => {
        const entry = figures.data?.[instructor.id];
        return {
          instructor,
          batchNames: (entry?.batches ?? []).map((batch) => (locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn)),
          paidThisMonth: toNumber(entry?.paidThisMonth),
          lastPaidOn: entry?.lastPaidOn ?? null,
        };
      });
  }, [instructors.data, figures.data, locale, query]);

  const columns: Column<Row>[] = [
    {
      key: "name",
      header: t("instructorPay.instructors.columns.name"),
      mobileLabel: t("instructorPay.instructors.columns.name"),
      cell: (row) => (
        <Link href={`/staff/offline-instructors/${row.instructor.id}`} prefetch className="font-medium text-text underline-offset-4 hover:underline">
          {row.instructor.name}
        </Link>
      ),
    },
    {
      key: "phone",
      header: t("instructorPay.instructors.columns.phone"),
      mobileLabel: t("instructorPay.instructors.columns.phone"),
      nowrap: true,
      cell: (row) => row.instructor.phone ?? <span className="text-muted">—</span>,
    },
    {
      key: "batches",
      header: t("instructorPay.instructors.columns.batches"),
      mobileLabel: t("instructorPay.instructors.columns.batches"),
      cell: (row) => (row.batchNames.length > 0 ? row.batchNames.join(", ") : <span className="text-muted">—</span>),
    },
    {
      key: "paidMonth",
      header: t("instructorPay.instructors.columns.paidMonth"),
      mobileLabel: t("instructorPay.instructors.columns.paidMonth"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="font-latin tabular-nums">{formatMoney(row.paidThisMonth) ?? ""}</span>,
    },
    {
      key: "lastPaid",
      header: t("instructorPay.instructors.columns.lastPaid"),
      mobileLabel: t("instructorPay.instructors.columns.lastPaid"),
      nowrap: true,
      cell: (row) => (row.lastPaidOn ? formatDate(row.lastPaidOn) : <span className="text-muted">—</span>),
    },
    {
      key: "actions",
      header: "",
      mobileLabel: t("instructorPay.instructors.columns.actions"),
      align: "end",
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-2">
          <Button size="sm" variant="secondary" onClick={() => setEditing(row.instructor)}>
            {t("instructorPay.instructors.edit")}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setArchiving(row.instructor)}>
            {t("instructorPay.instructors.archive")}
          </Button>
        </div>
      ),
    },
  ];

  if (instructors.isError) {
    return <ErrorState onRetry={() => void instructors.refetch()} error={instructors.error} />;
  }

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("instructorPay.instructors.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("instructorPay.instructors.title") }]}
        actions={
          <Button onClick={() => setAdding(true)}>{t("instructorPay.instructors.add")}</Button>
        }
      />

      <div className="grid gap-1.5 sm:max-w-sm">
        <label htmlFor="instructor-search" className="text-meta text-muted">
          {t("instructorPay.instructors.search")}
        </label>
        <TextInput id="instructor-search" type="search" value={query} onChange={(event) => setQuery(event.target.value)} />
      </div>

      {instructors.isPending || figures.isPending ? (
        <Skeleton variant="rows" count={4} />
      ) : (
        <Table
          columns={columns}
          rows={rows}
          rowKey={(row) => row.instructor.id}
          empty={<EmptyState title={t("instructorPay.instructors.emptyTitle")} body={t("instructorPay.instructors.emptyBody")} />}
        />
      )}

      <InstructorFormDialog instructor={null} open={adding} onOpenChange={setAdding} />
      {editing ? (
        <InstructorFormDialog key={editing.id} instructor={editing} open onOpenChange={(open) => !open && setEditing(null)} />
      ) : null}
      <ConfirmDialog
        open={archiving !== null}
        onOpenChange={(open) => !open && setArchiving(null)}
        title={t("instructorPay.instructors.archiveTitle", { name: archiving?.name ?? "" })}
        body={t("instructorPay.instructors.archiveBody")}
        confirmLabel={t("instructorPay.instructors.archiveConfirm")}
        tone="danger"
        onConfirm={async () => {
          if (archiving) {
            // The API refuses while live classes name the instructor; its message is shown in the dialog.
            await archive.mutateAsync(archiving.id);
            setArchiving(null);
          }
        }}
      />
    </div>
  );
}
