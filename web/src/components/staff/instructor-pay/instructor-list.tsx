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
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import {
  useAllBatchClasses,
  useArchiveInstructor,
  useOfflineInstructors,
  type OfflineInstructor,
} from "@/lib/api/exam/instructor-pay";
import { currentMonth, instructorFigures } from "./pay-model";
import { InstructorFormDialog } from "./instructor-form-dialog";

type Row = {
  instructor: OfflineInstructor;
  batchNames: string[];
  classesThisMonth: number;
  owed: number;
};

/**
 * The offline instructors (plan 8.3): name, phone, the batches they teach, this month's classes and what they are owed now. Add and
 * edit are open to both admin roles; archive is the Master Admin's, and the API refuses it while live classes still name the person.
 */
export function InstructorList() {
  const { t, locale } = useLocale();
  const { formatMoney, formatNumber } = useFormat();
  const role = useRole();
  const instructors = useOfflineInstructors(false);
  const all = useAllBatchClasses();
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<OfflineInstructor | null>(null);
  const [adding, setAdding] = useState(false);
  const [archiving, setArchiving] = useState<OfflineInstructor | null>(null);
  const archive = useArchiveInstructor();

  const batchNames = useMemo(() => {
    const names = new Map<string, string>();
    for (const batch of all.batches) {
      names.set(batch.id, locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn);
    }
    return names;
  }, [all.batches, locale]);

  const rows: Row[] = useMemo(() => {
    const figures = instructorFigures(all.rows, currentMonth());
    const needle = query.trim().toLowerCase();
    return (instructors.data ?? [])
      .filter((instructor) => {
        if (!needle) {
          return true;
        }
        return [instructor.name, instructor.phone ?? "", ...instructor.aliases].some((value) => value.toLowerCase().includes(needle));
      })
      .map((instructor) => {
        const entry = figures.get(instructor.id);
        return {
          instructor,
          batchNames: (entry?.batchIds ?? []).map((id) => batchNames.get(id) ?? "").filter(Boolean),
          classesThisMonth: entry?.classesThisMonth ?? 0,
          owed: entry?.owed ?? 0,
        };
      });
  }, [instructors.data, all.rows, batchNames, query]);

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
      key: "month",
      header: t("instructorPay.instructors.columns.month"),
      mobileLabel: t("instructorPay.instructors.columns.month"),
      align: "end",
      cell: (row) => formatNumber(row.classesThisMonth),
    },
    {
      key: "owed",
      header: t("instructorPay.instructors.columns.owed"),
      mobileLabel: t("instructorPay.instructors.columns.owed"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className="font-latin tabular-nums">{formatMoney(row.owed) ?? ""}</span>,
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
          {role.canManageOfflineBatches ? (
            <Button size="sm" variant="ghost" onClick={() => setArchiving(row.instructor)}>
              {t("instructorPay.instructors.archive")}
            </Button>
          ) : null}
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

      {instructors.isPending || all.isPending ? (
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
