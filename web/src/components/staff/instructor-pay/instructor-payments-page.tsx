"use client";

import { useMemo } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { SummaryStrip, type SummaryItem } from "@/components/ui/summary-strip";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { TextInput } from "@/components/ui/text-input";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { methodLabelKey } from "@/components/staff/offline/labels";
import {
  useAllBatchClasses,
  useOfflineInstructors,
  useUnsettledClasses,
  useUnsettledCounts,
  type ClassRow,
} from "@/lib/api/exam/instructor-pay";
import { GroupedClasses } from "./class-list";
import {
  currentMonth,
  historyDate,
  historyRows,
  isHeld,
  isWaiting,
  notTakenIn,
  paidIn,
} from "./pay-model";

const PAGE = "/staff/offline-instructor-payments";
const TABS = ["settle", "waiting", "history"] as const;
type PayTab = (typeof TABS)[number];

/**
 * The offline instructor payments page (plan 8.2). Four cards at the top; three tabs in the URL: To settle (held classes, oldest first),
 * Waiting for approval, and History (paid and not taken). Filters by batch, instructor and date. Grouped by instructor by default.
 */
export function InstructorPayments() {
  const { t, locale } = useLocale();
  const { formatNumber, formatMoney } = useFormat();
  const role = useRole();
  const canSettle = role.canSettleOfflineClass;
  const canApprove = role.canApproveOfflineClass;
  const [params, setParams] = useListParams({ tab: "", batch: "", instructor: "", from: "", to: "" });
  const tab: PayTab = (TABS as readonly string[]).includes(params.tab) ? (params.tab as PayTab) : "settle";
  const filters = {
    batchId: params.batch || undefined,
    instructorId: params.instructor || undefined,
    from: params.from || undefined,
    to: params.to || undefined,
  };

  const unsettled = useUnsettledClasses(filters);
  const counts = useUnsettledCounts(canSettle, 30_000);
  const batchesAndClasses = useAllBatchClasses();
  const instructors = useOfflineInstructors(false);

  const batchNames = useMemo(() => {
    const names = new Map<string, string>();
    for (const batch of batchesAndClasses.batches) {
      names.set(batch.id, locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn);
    }
    return names;
  }, [batchesAndClasses.batches, locale]);

  const unsettledRows = useMemo(() => unsettled.data ?? [], [unsettled.data]);
  const settleRows = unsettledRows.filter(isHeld);
  const waitingRows = unsettledRows.filter(isWaiting);
  const month = currentMonth();
  const paid = paidIn(batchesAndClasses.rows, month);
  const notTaken = notTakenIn(batchesAndClasses.rows, month);
  const held = counts.data?.held ?? 0;
  const waiting = counts.data?.waitingApproval ?? 0;

  const cards: SummaryItem[] = [
    {
      label: t("instructorPay.cards.held"),
      value: `${formatNumber(held)} · ${formatMoney(counts.data?.heldAmount ?? 0) ?? ""}`,
      tone: held > 0 ? "warn" : "muted",
      href: `${PAGE}?tab=settle`,
    },
    {
      label: canApprove ? t("instructorPay.cards.waiting") : t("instructorPay.cards.waitingMine"),
      value: formatNumber(waiting),
      tone: canApprove && waiting > 0 ? "warn" : "muted",
      href: `${PAGE}?tab=waiting`,
    },
    {
      label: t("instructorPay.cards.paidMonth"),
      value: `${formatNumber(paid.count)} · ${formatMoney(paid.amount) ?? ""}`,
      tone: "ok",
      href: `${PAGE}?tab=history`,
    },
    {
      label: t("instructorPay.cards.notTakenMonth"),
      value: formatNumber(notTaken),
      tone: "muted",
      href: `${PAGE}?tab=history`,
    },
  ];

  const tabs = [
    { value: "settle", label: t("instructorPay.tabs.settle"), badge: held > 0 ? held : undefined },
    { value: "waiting", label: t("instructorPay.tabs.waiting"), badge: canApprove && waiting > 0 ? waiting : undefined },
    { value: "history", label: t("instructorPay.tabs.history") },
  ];

  const anyFilter = Boolean(params.batch || params.instructor || params.from || params.to);

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("instructorPay.payments.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("instructorPay.payments.title") }]}
      />

      <SummaryStrip items={cards} loading={counts.isPending} />

      <Tabs
        tabs={tabs}
        value={tab}
        onChange={(value) => setParams({ tab: value })}
        ariaLabel={t("instructorPay.tabs.label")}
        wrap
      />

      <div className="grid gap-3 rounded-card border border-border bg-card p-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label={t("instructorPay.filters.batch")}>
          <Select value={params.batch} onChange={(event) => setParams({ batch: event.target.value })}>
            <option value="">{t("instructorPay.filters.allBatches")}</option>
            {batchesAndClasses.batches.map((batch) => (
              <option key={batch.id} value={batch.id}>
                {batchNames.get(batch.id)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("instructorPay.filters.instructor")}>
          <Select value={params.instructor} onChange={(event) => setParams({ instructor: event.target.value })}>
            <option value="">{t("instructorPay.filters.allInstructors")}</option>
            {(instructors.data ?? []).map((instructor) => (
              <option key={instructor.id} value={instructor.id}>
                {instructor.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("instructorPay.filters.from")}>
          <TextInput type="date" value={params.from} onChange={(event) => setParams({ from: event.target.value })} />
        </Field>
        <Field label={t("instructorPay.filters.to")}>
          <TextInput type="date" value={params.to} onChange={(event) => setParams({ to: event.target.value })} />
        </Field>
        {anyFilter ? (
          <div className="sm:col-span-2 lg:col-span-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setParams({ batch: "", instructor: "", from: "", to: "" })}
            >
              {t("instructorPay.filters.clear")}
            </Button>
          </div>
        ) : null}
      </div>

      {tab === "history" ? (
        <HistoryTable
          rows={historyRows(batchesAndClasses.rows, {
            batchId: params.batch || undefined,
            instructorId: params.instructor || undefined,
            from: params.from || undefined,
            to: params.to || undefined,
          })}
          loading={batchesAndClasses.isPending}
          batchNames={batchNames}
          onError={batchesAndClasses.isError}
          onRetry={batchesAndClasses.refetch}
        />
      ) : unsettled.isError ? (
        <ErrorState onRetry={() => void unsettled.refetch()} error={unsettled.error} />
      ) : unsettled.isPending ? (
        <Skeleton variant="rows" count={4} />
      ) : (
        <GroupedClasses
          rows={tab === "settle" ? settleRows : waitingRows}
          mode={tab === "settle" ? "settle" : "waiting"}
          batchNames={batchNames}
          canSettle={canSettle}
          canApprove={canApprove}
        />
      )}
    </div>
  );
}

/** The paid and not-taken classes, newest first, with what was paid, how, and the reference. */
function HistoryTable({
  rows,
  loading,
  batchNames,
  onError,
  onRetry,
}: {
  rows: ClassRow[];
  loading: boolean;
  batchNames: Map<string, string>;
  onError: boolean;
  onRetry: () => void;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();

  if (onError) {
    return <ErrorState onRetry={onRetry} />;
  }

  const columns: Column<ClassRow>[] = [
    {
      key: "date",
      header: t("instructorPay.history.date"),
      mobileLabel: t("instructorPay.history.date"),
      nowrap: true,
      cell: (row) => formatDate(historyDate(row)),
    },
    {
      key: "class",
      header: t("instructorPay.history.class"),
      mobileLabel: t("instructorPay.history.class"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span className="text-text">{row.subject}</span>
          <span className="text-meta text-muted">{batchNames.get(row.batchId) ?? ""}</span>
        </div>
      ),
    },
    {
      key: "instructor",
      header: t("instructorPay.history.instructor"),
      mobileLabel: t("instructorPay.history.instructor"),
      cell: (row) => row.instructorName ?? row.instructorText ?? "",
    },
    {
      key: "amount",
      header: t("instructorPay.history.amount"),
      mobileLabel: t("instructorPay.history.amount"),
      align: "end",
      nowrap: true,
      cell: (row) =>
        row.status === "PAID" && row.payment ? (
          <span className="font-latin tabular-nums">{formatMoney(row.payment.amount)}</span>
        ) : (
          <span className="text-muted">—</span>
        ),
    },
    {
      key: "how",
      header: t("instructorPay.history.how"),
      mobileLabel: t("instructorPay.history.how"),
      cell: (row) =>
        row.payment ? (
          <div className="grid gap-0.5">
            <span>{t(methodLabelKey(row.payment.method))}</span>
            {row.payment.reference ? <span className="break-all text-meta text-muted">{row.payment.reference}</span> : null}
          </div>
        ) : null,
    },
    {
      key: "status",
      header: t("instructorPay.history.status"),
      mobileLabel: t("instructorPay.history.status"),
      cell: (row) => {
        const tone: PillTone = row.status === "PAID" ? "ok" : "muted";
        return <Pill tone={tone}>{t(row.status === "PAID" ? "instructorPay.history.paid" : "instructorPay.history.notTaken")}</Pill>;
      },
    },
  ];

  return (
    <Table
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
      loading={loading}
      empty={<EmptyState title={t("instructorPay.empty.historyTitle")} body={t("instructorPay.empty.historyBody")} />}
    />
  );
}
