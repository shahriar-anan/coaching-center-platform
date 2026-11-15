"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { SummaryStrip, type SummaryItem } from "@/components/ui/summary-strip";
import { Table, type Column } from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { useAllBatchClasses, useOfflineInstructors, type ClassRow } from "@/lib/api/exam/instructor-pay";
import { methodLabelKey } from "@/components/staff/offline/labels";
import { classEndIso, classStartIso, currentMonth, dueOf, instructorFigures } from "./pay-model";
import { InstructorFormDialog } from "./instructor-form-dialog";

type StatusTone = { key: string; tone: PillTone };

/** How each class status reads on this page: the words, and the tone the money colours use. */
const STATUS: Record<ClassRow["status"], StatusTone> = {
  SCHEDULED: { key: "instructorPay.status.SCHEDULED", tone: "muted" },
  HELD: { key: "instructorPay.status.HELD", tone: "warn" },
  PAID_PENDING: { key: "instructorPay.status.PAID_PENDING", tone: "accent" },
  PAID: { key: "instructorPay.status.PAID", tone: "ok" },
  NOT_TAKEN_PENDING: { key: "instructorPay.status.NOT_TAKEN_PENDING", tone: "accent" },
  NOT_TAKEN: { key: "instructorPay.status.NOT_TAKEN", tone: "muted" },
  CANCELLED: { key: "instructorPay.status.CANCELLED", tone: "muted" },
};

/**
 * One offline instructor (plan 8.3): the names they are known by, their classes across every batch with what is owed, paid and
 * waiting. Merging two records is not offered by the API yet, so the page says so instead of a merge button.
 */
export function InstructorDetail({ id }: { id: string }) {
  const { t, locale } = useLocale();
  const { formatDate, formatMoney, formatNumber, formatTimeRange, formatTime } = useFormat();
  const role = useRole();
  const instructors = useOfflineInstructors(true);
  const all = useAllBatchClasses();
  const [editing, setEditing] = useState(false);

  const instructor = (instructors.data ?? []).find((candidate) => candidate.id === id);
  const rows = useMemo(
    () => all.rows.filter((row) => row.instructorId === id).sort((a, b) => (a.heldOn < b.heldOn ? 1 : a.heldOn > b.heldOn ? -1 : 0)),
    [all.rows, id],
  );
  const batchNames = useMemo(() => {
    const names = new Map<string, string>();
    for (const batch of all.batches) {
      names.set(batch.id, locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn);
    }
    return names;
  }, [all.batches, locale]);

  if (instructors.isError || all.isError) {
    return <ErrorState onRetry={() => { void instructors.refetch(); all.refetch(); }} error={instructors.error ?? undefined} />;
  }
  if (instructors.isPending || all.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (!instructor) {
    return <EmptyState title={t("instructorPay.detail.notFoundTitle")} body={t("instructorPay.detail.notFoundBody")} />;
  }

  const figures = instructorFigures(rows, currentMonth()).get(id) ?? { batchIds: [], classesThisMonth: 0, owed: 0, paid: 0, pending: 0 };
  const summary: SummaryItem[] = [
    { label: t("instructorPay.detail.owed"), value: formatMoney(figures.owed) ?? "", tone: figures.owed > 0 ? "warn" : "muted" },
    { label: t("instructorPay.detail.paid"), value: formatMoney(figures.paid) ?? "", tone: "ok" },
    { label: t("instructorPay.detail.pending"), value: formatMoney(figures.pending) ?? "", tone: figures.pending > 0 ? "warn" : "muted" },
    { label: t("instructorPay.detail.batches"), value: formatNumber(figures.batchIds.length), tone: "muted" },
  ];

  const columns: Column<ClassRow>[] = [
    {
      key: "date",
      header: t("instructorPay.detail.columns.date"),
      mobileLabel: t("instructorPay.detail.columns.date"),
      nowrap: true,
      cell: (row) => (
        <div className="grid gap-0.5">
          <span>{formatDate(row.heldOn)}</span>
          {row.startsAt ? (
            <span className="text-meta text-muted">
              {row.endsAt ? formatTimeRange(classStartIso(row), classEndIso(row)) : formatTime(classStartIso(row))}
            </span>
          ) : null}
        </div>
      ),
    },
    {
      key: "batch",
      header: t("instructorPay.detail.columns.batch"),
      mobileLabel: t("instructorPay.detail.columns.batch"),
      cell: (row) => batchNames.get(row.batchId) ?? "",
    },
    {
      key: "subject",
      header: t("instructorPay.detail.columns.subject"),
      mobileLabel: t("instructorPay.detail.columns.subject"),
      cell: (row) => (
        <span>
          {row.subject}
          {row.topic ? <span className="text-muted"> · {row.topic}</span> : null}
        </span>
      ),
    },
    {
      key: "amount",
      header: t("instructorPay.detail.columns.amount"),
      mobileLabel: t("instructorPay.detail.columns.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => {
        const paid = row.payment && (row.status === "PAID" || row.status === "PAID_PENDING");
        return <span className="font-latin tabular-nums">{formatMoney(paid && row.payment ? row.payment.amount : dueOf(row)) ?? ""}</span>;
      },
    },
    {
      key: "status",
      header: t("instructorPay.detail.columns.status"),
      mobileLabel: t("instructorPay.detail.columns.status"),
      cell: (row) => {
        const status = STATUS[row.status];
        const detail =
          row.status === "PAID" || row.status === "PAID_PENDING"
            ? row.payment
              ? `${t(methodLabelKey(row.payment.method))} · ${formatDate(row.payment.paidOn)}`
              : null
            : null;
        return (
          <div className="grid gap-1">
            <Pill tone={status.tone}>{t(status.key)}</Pill>
            {detail ? <span className="text-meta text-muted">{detail}</span> : null}
          </div>
        );
      },
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={instructor.name}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("instructorPay.instructors.title"), href: "/staff/offline-instructors" },
          { label: instructor.name },
        ]}
        actions={
          role.canSettleOfflineClass ? (
            <Button variant="secondary" onClick={() => setEditing(true)}>
              {t("instructorPay.instructors.edit")}
            </Button>
          ) : null
        }
      />

      <Card>
        <dl className="grid gap-3 text-body sm:grid-cols-2">
          <div className="grid gap-0.5">
            <dt className="text-meta text-muted">{t("instructorPay.instructors.phone")}</dt>
            <dd className="font-latin">{instructor.phone ?? t("instructorPay.detail.none")}</dd>
          </div>
          <div className="grid gap-0.5">
            <dt className="text-meta text-muted">{t("instructorPay.instructors.note")}</dt>
            <dd>{instructor.note ?? t("instructorPay.detail.none")}</dd>
          </div>
          <div className="grid gap-1 sm:col-span-2">
            <dt className="text-meta text-muted">{t("instructorPay.detail.knownAs")}</dt>
            <dd>
              {instructor.aliases.length > 0 ? (
                <ul className="flex flex-wrap gap-1.5">
                  {instructor.aliases.map((alias) => (
                    <li key={alias}>
                      <Pill tone="muted">{alias}</Pill>
                    </li>
                  ))}
                </ul>
              ) : (
                <span className="text-muted">{t("instructorPay.detail.noAliases")}</span>
              )}
            </dd>
          </div>
        </dl>
      </Card>

      <SummaryStrip items={summary} />

      <section className="grid gap-3">
        <h2 className="text-section font-semibold text-text">{t("instructorPay.detail.classes")}</h2>
        <Table
          columns={columns}
          rows={rows}
          rowKey={(row) => row.id}
          empty={<EmptyState title={t("instructorPay.detail.noClassesTitle")} body={t("instructorPay.detail.noClassesBody")} />}
        />
      </section>

      <Card padding="sm">
        <p className="text-meta text-muted">{t("instructorPay.detail.mergeNote")}</p>
      </Card>

      <InstructorFormDialog key={editing ? instructor.version : "closed"} instructor={instructor} open={editing} onOpenChange={setEditing} />
    </div>
  );
}
