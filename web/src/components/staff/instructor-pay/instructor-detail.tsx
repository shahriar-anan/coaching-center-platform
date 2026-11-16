"use client";

import { useMemo, useState } from "react";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { useListParams } from "@/lib/url/list-params";
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
import { classEndIso, classStartIso, currentMonth, dueOf, hasAmount, hasOwnRate, instructorFigures } from "./pay-model";
import { SetRateDialog } from "./set-rate-dialog";
import { InstructorFormDialog } from "./instructor-form-dialog";
import { ClassPaymentDialog } from "./class-payment-dialog";
import { NotTakenDialog } from "./not-taken-dialog";
import { InstructorAddClassDialog } from "./instructor-add-class-dialog";
import { OtherPaymentsSection } from "./other-payments";

type StatusTone = { key: string; tone: PillTone };

/** A class whose amount can still change: not finished yet, or finished and not settled. */
function unsettled(row: ClassRow): boolean {
  return row.status === "SCHEDULED" || row.status === "HELD";
}

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
  // 17 Nov: the class list filters by batch, by held or upcoming, and by kind. The summary above counts every class.
  const [filters, setFilters] = useListParams({ batch: "", when: "", kind: "" });
  // 17 Nov: the instructor's page settles their classes and adds the ones the routine does not have.
  const [adding, setAdding] = useState(false);
  const [acting, setActing] = useState<{ kind: "pay" | "notTaken" | "rate"; row: ClassRow } | null>(null);

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
        <span className="grid gap-1">
          <span>
            {row.subject}
            {row.topic ? <span className="text-muted"> · {row.topic}</span> : null}
          </span>
          {row.inRoutine === false ? (
            <span>
              <Pill tone="muted">{t("instructorPay.detail.payOnly")}</Pill>
            </span>
          ) : null}
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
        // 17 Nov: a class with no rate says so, instead of a bare ৳0.00 beside a button that cannot be pressed.
        if (!paid && !hasAmount(row) && unsettled(row)) {
          return <span className="text-meta font-medium text-warn">{t("instructorPay.detail.noRate")}</span>;
        }
        const money = <span className="font-latin tabular-nums">{formatMoney(paid && row.payment ? row.payment.amount : dueOf(row)) ?? ""}</span>;
        // A class priced on its own says so: a change of the batch rate does not move it.
        return !paid && unsettled(row) && hasOwnRate(row) ? (
          <span className="grid justify-items-end gap-1">
            {money}
            <Pill tone="accent">{t("instructorPay.detail.ownRate")}</Pill>
          </span>
        ) : (
          money
        );
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
    {
      key: "actions",
      header: "",
      mobileLabel: t("instructorPay.detail.columns.actions"),
      align: "end",
      cell: (row) => {
        // 17 Nov: the rate is set here, for the batch or for this class, so the admin need not open the batch's rates first.
        const rateButton =
          role.canUploadRoutine && unsettled(row) ? (
            <Button size="sm" variant={hasAmount(row) ? "ghost" : row.status === "HELD" ? "primary" : "secondary"} onClick={() => setActing({ kind: "rate", row })}>
              {t(hasAmount(row) ? "instructorPay.row.changeRate" : "instructorPay.row.setRate")}
            </Button>
          ) : null;
        if (role.canSettleOfflineClass && row.status === "HELD") {
          return (
            <div className="flex flex-wrap justify-end gap-2">
              {hasAmount(row) ? (
                <Button size="sm" onClick={() => setActing({ kind: "pay", row })} disabled={dueOf(row) <= 0}>
                  {t("instructorPay.row.record")}
                </Button>
              ) : null}
              {rateButton}
              <Button size="sm" variant="secondary" onClick={() => setActing({ kind: "notTaken", row })}>
                {t("instructorPay.row.notTaken")}
              </Button>
            </div>
          );
        }
        return rateButton ? <div className="flex flex-wrap justify-end gap-2">{rateButton}</div> : null;
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
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => setAdding(true)}>{t("instructorPay.addClass.open")}</Button>
              <Button variant="secondary" onClick={() => setEditing(true)}>
                {t("instructorPay.instructors.edit")}
              </Button>
            </div>
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
        <div className="grid gap-3 rounded-card border border-border bg-card p-4 sm:grid-cols-3">
          <Field label={t("instructorPay.filters.batch")}>
            <Select value={filters.batch} onChange={(event) => setFilters({ batch: event.target.value })}>
              <option value="">{t("instructorPay.filters.allBatches")}</option>
              {[...new Set(rows.map((row) => row.batchId))].map((batchId) => (
                <option key={batchId} value={batchId}>
                  {batchNames.get(batchId) ?? ""}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("instructorPay.detail.filters.when")}>
            <Select value={filters.when} onChange={(event) => setFilters({ when: event.target.value })}>
              <option value="">{t("instructorPay.detail.filters.allWhen")}</option>
              <option value="upcoming">{t("instructorPay.detail.filters.upcoming")}</option>
              <option value="held">{t("instructorPay.detail.filters.held")}</option>
            </Select>
          </Field>
          <Field label={t("instructorPay.detail.filters.kind")}>
            <Select value={filters.kind} onChange={(event) => setFilters({ kind: event.target.value })}>
              <option value="">{t("instructorPay.detail.filters.allKinds")}</option>
              <option value="LECTURE">{t("instructorPay.addClass.kinds.LECTURE")}</option>
              <option value="EXAM">{t("instructorPay.addClass.kinds.EXAM")}</option>
              <option value="OTHER">{t("instructorPay.addClass.kinds.OTHER")}</option>
            </Select>
          </Field>
        </div>
        <Table
          columns={columns}
          rows={rows.filter(
            (row) =>
              (!filters.batch || row.batchId === filters.batch) &&
              (!filters.kind || row.kind === filters.kind) &&
              (filters.when === "upcoming"
                ? row.status === "SCHEDULED"
                : filters.when === "held"
                  ? row.status !== "SCHEDULED" && row.status !== "CANCELLED"
                  : true),
          )}
          rowKey={(row) => row.id}
          empty={<EmptyState title={t("instructorPay.detail.noClassesTitle")} body={t("instructorPay.detail.noClassesBody")} />}
        />
      </section>

      <OtherPaymentsSection instructorId={instructor.id} instructorName={instructor.name} />

      <Card padding="sm">
        <p className="text-meta text-muted">{t("instructorPay.detail.mergeNote")}</p>
      </Card>

      {adding ? <InstructorAddClassDialog instructorId={instructor.id} open onOpenChange={setAdding} /> : null}
      {acting?.kind === "pay" ? (
        <ClassPaymentDialog
          row={acting.row}
          batchName={batchNames.get(acting.row.batchId) ?? ""}
          open
          onOpenChange={(open) => !open && setActing(null)}
          canApprove={role.canApproveOfflineClass}
        />
      ) : null}
      {acting?.kind === "rate" ? (
        <SetRateDialog
          row={acting.row}
          classes={rows}
          instructorName={instructor.name}
          batchName={batchNames.get(acting.row.batchId) ?? ""}
          onOpenChange={(open) => !open && setActing(null)}
        />
      ) : null}
      {acting?.kind === "notTaken" ? (
        <NotTakenDialog
          row={acting.row}
          batchName={batchNames.get(acting.row.batchId) ?? ""}
          open
          onOpenChange={(open) => !open && setActing(null)}
          canApprove={role.canApproveOfflineClass}
        />
      ) : null}

      <InstructorFormDialog key={editing ? instructor.version : "closed"} instructor={instructor} open={editing} onOpenChange={setEditing} />
    </div>
  );
}
