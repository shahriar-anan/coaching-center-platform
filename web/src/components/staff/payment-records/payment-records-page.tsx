"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { SearchInput } from "@/components/ui/search-input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Select } from "@/components/ui/select";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useBatches } from "@/lib/api/exam/offline-admin";
import { useCategoryTree, type CategoryTreeNode } from "@/lib/api/exam/staff-catalog";
import {
  useFinalizePaymentEdit,
  usePaymentEdits,
  usePaymentRecords,
  useProposePaymentEdit,
  useRejectPaymentEdit,
  type PaymentEdit,
  type PaymentEditStatus,
  type PaymentKind,
  type PaymentMethod,
  type RecordKind,
  type PaymentRecordStatus,
  INCOME_KINDS,
  EXPENSE_KINDS,
  type PaymentRow,
  type PaymentValues,
} from "@/lib/api/exam/payment-records";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { dhakaToday, PAYMENT_TONE, toNumber } from "@/components/staff/offline/offline-model";
import { methodLabelKey, paymentStatusKey } from "@/components/staff/offline/labels";
import { offlineErrorText } from "@/components/staff/offline/offline-error";

const METHODS: PaymentMethod[] = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK"];
const REFERENCE = /^[ -~]{1,100}$/;
const AMOUNT = /^\d+(\.\d{1,2})?$/;
const TABS = ["records", "waiting", "history"] as const;
type RecordsTab = (typeof TABS)[number];
export const PERIODS = ["today", "month", "last", "custom"] as const;
export type Period = (typeof PERIODS)[number];

/** The dates a period covers, in Dhaka. A custom period keeps the dates in the URL. */
export function periodRange(period: Period, today: string = dhakaToday()): { from: string; to: string } | null {
  const [year, month] = today.split("-").map(Number);
  const pad = (n: number) => String(n).padStart(2, "0");
  const lastDay = (y: number, m: number) => new Date(Date.UTC(y, m, 0)).getUTCDate();
  switch (period) {
    case "today":
      return { from: today, to: today };
    case "month":
      return { from: `${year}-${pad(month)}-01`, to: today };
    case "last": {
      const y = month === 1 ? year - 1 : year;
      const m = month === 1 ? 12 : month - 1;
      return { from: `${y}-${pad(m)}-01`, to: `${y}-${pad(m)}-${pad(lastDay(y, m))}` };
    }
    default:
      return null;
  }
}

const EDIT_TONE: Record<PaymentEditStatus, PillTone> = { PENDING: "accent", FINALIZED: "ok", REJECTED: "danger" };

/**
 * Payment records (payment-records-edit-plan.md): every payment the admin may see, filtered by period, kind, batch, instructor and a
 * search. Edit sends a correction: a System Admin's waits for the Master Admin, the Master Admin's applies at once. The Master Admin
 * finalizes or rejects the waiting ones. A finalized correction changes the payment itself, so Finance and the receipts follow it.
 */
export function PaymentRecordsPage() {
  const { t } = useLocale();
  const role = useRole();
  const isMaster = role.canApproveOfflineFee;
  const [params, setParams] = useListParams({
    tab: "",
    period: "month",
    from: "",
    to: "",
    kind: "",
    batchId: "",
    instructorId: "",
    q: "",
    direction: "",
    item: "",
    method: "",
    status: "",
    categoryId: "",
  });
  const tab: RecordsTab = (TABS as readonly string[]).includes(params.tab) ? (params.tab as RecordsTab) : "records";
  const pending = usePaymentEdits("PENDING");
  const waitingCount = pending.data?.length ?? 0;

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("paymentRecords.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("paymentRecords.title") }]}
      />
      <p className="max-w-3xl text-body text-muted">
        {t("paymentRecords.intro")} {isMaster ? t("paymentRecords.introMaster") : t("paymentRecords.introStaff")}
      </p>
      <Tabs
        tabs={[
          { value: "records", label: t("paymentRecords.tabs.records") },
          { value: "waiting", label: t("paymentRecords.tabs.waiting"), badge: waitingCount || undefined },
          { value: "history", label: t("paymentRecords.tabs.history") },
        ]}
        value={tab}
        onChange={(value) => setParams({ tab: value })}
        ariaLabel={t("paymentRecords.tabs.label")}
        wrap
      />
      {tab === "records" ? <RecordsView params={params} setParams={setParams} isMaster={isMaster} /> : null}
      {tab === "waiting" ? <EditsList status="PENDING" isMaster={isMaster} /> : null}
      {tab === "history" ? <EditsList status="DECIDED" isMaster={isMaster} /> : null}
    </div>
  );
}

type Params = {
  period: string;
  from: string;
  to: string;
  kind: string;
  batchId: string;
  instructorId: string;
  q: string;
  direction: string;
  item: string;
  method: string;
  status: string;
  categoryId: string;
};

/** The catalogue's categories in tree order, with their depth for the indent. */
function flattenCategories(nodes: CategoryTreeNode[], depth = 0): { node: CategoryTreeNode; depth: number }[] {
  return nodes.flatMap((node) => [{ node, depth }, ...flattenCategories(node.children ?? [], depth + 1)]);
}

const RECORD_KINDS: readonly RecordKind[] = ["ONLINE", "DIRECT", "FEE", "STAFF"];
const FILTER_METHODS = ["CASH", "BKASH", "NAGAD", "ROCKET", "BANK", "ONLINE"] as const;
const FILTER_STATUSES: readonly PaymentRecordStatus[] = ["APPROVED", "PENDING", "REJECTED", "VOIDED", "REFUNDED"];

const STATUS_TONE: Record<PaymentRecordStatus, PillTone> = { ...PAYMENT_TONE, REFUNDED: "warn" };

/** The kinds the caller sees, as the API decides it: the Master Admin every stream; a System Admin fees and online purchases. */
function kindsFor(isMaster: boolean): RecordKind[] {
  return isMaster ? [...RECORD_KINDS] : ["ONLINE", "FEE"];
}

function statusLabel(status: PaymentRecordStatus, t: (key: string) => string): string {
  return status === "REFUNDED" ? t("paymentRecords.status.REFUNDED") : t(paymentStatusKey(status));
}

function methodLabel(method: string, t: (key: string) => string): string {
  return method === "ONLINE" ? t("paymentRecords.method.ONLINE") : t(methodLabelKey(method as PaymentMethod));
}

/**
 * The payments list (16 Nov): every money stream of the site, filtered as Finance counts them. Money in: online purchases through the
 * gateway, direct enrolment (an exam or package paid at the office), offline course fees. Money out: offline instructor pay and staff
 * pay. The filters that do not apply to the chosen source are not shown. Only the payments the office records by hand can be corrected
 * here; an online purchase is refunded from Orders, and staff pay is changed on Staff pay.
 */
function RecordsView({
  params,
  setParams,
  isMaster,
}: {
  params: Params;
  setParams: (next: Partial<Record<keyof Params, string>>) => void;
  isMaster: boolean;
}) {
  const { t, locale } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const period: Period = (PERIODS as readonly string[]).includes(params.period) ? (params.period as Period) : "month";
  const range = periodRange(period) ?? { from: params.from, to: params.to };
  const backwards = Boolean(range.from && range.to && range.from > range.to);
  const visible = kindsFor(isMaster);
  const direction = params.direction === "INCOME" || params.direction === "EXPENSE" ? params.direction : "";
  const offered = visible.filter((value) =>
    direction === "INCOME" ? INCOME_KINDS.includes(value) : direction === "EXPENSE" ? EXPENSE_KINDS.includes(value) : true,
  );
  const kind = offered.find((value) => value === params.kind) ?? "";
  const item = params.item === "PACKAGE" || params.item === "EXAM" ? params.item : "";
  const method = (FILTER_METHODS as readonly string[]).includes(params.method) ? params.method : "";
  const status = (FILTER_STATUSES as readonly string[]).includes(params.status) ? (params.status as PaymentRecordStatus) : "";
  // Which narrower filters apply: a package or exam for online and direct payments; a batch for fees. With no source chosen, each
  // shows when one of its sources is in the list.
  const sources = kind ? [kind] : offered;
  const showsItem = sources.some((value) => value === "ONLINE" || value === "DIRECT");
  const showsBatch = sources.some((value) => value === "FEE");
  const records = usePaymentRecords({
    from: backwards ? "" : range.from,
    to: backwards ? "" : range.to,
    kind,
    batchId: showsBatch ? params.batchId : "",
    q: params.q,
    direction,
    item: showsItem ? item : "",
    method,
    status,
    categoryId: showsItem ? params.categoryId : "",
  });
  const categories = useCategoryTree();
  const batches = useBatches();
  const [editing, setEditing] = useState<PaymentRow | null>(null);
  const rows = useMemo(() => records.data ?? [], [records.data]);
  // The totals count what is paid and stands: approved payments, less what was refunded of an online purchase.
  const net = (row: PaymentRow) =>
    row.status === "APPROVED" ? toNumber(row.amount) : row.status === "REFUNDED" ? toNumber(row.amount) - toNumber(row.refundedAmount ?? 0) : 0;
  const moneyIn = rows.filter((row) => row.direction === "INCOME").reduce((sum, row) => sum + net(row), 0);
  const moneyOut = rows.filter((row) => row.direction === "EXPENSE").reduce((sum, row) => sum + net(row), 0);

  const itemName = (row: PaymentRow) =>
    row.itemType === "BATCH"
      ? (locale === "bn" ? row.batchNameBn || row.batchNameEn : row.batchNameEn) || null
      : (locale === "bn" ? row.itemNameBn || row.itemNameEn : row.itemNameEn) || null;

  const columns: Column<PaymentRow>[] = [
    { key: "date", header: t("paymentRecords.columns.date"), nowrap: true, cell: (row) => formatDate(row.paidOn) },
    {
      key: "source",
      header: t("paymentRecords.columns.source"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span className="font-medium text-text">{t(`paymentRecords.kind.${row.kind}`)}</span>
          <span className="text-meta text-muted">
            {t(`paymentRecords.item.${row.itemType}`)}
            {itemName(row) ? `: ${itemName(row)}` : ""}
          </span>
        </div>
      ),
    },
    {
      key: "who",
      header: t("paymentRecords.columns.who"),
      cell: (row) => <span className="font-medium text-text">{row.personName || "—"}</span>,
    },
    {
      key: "amount",
      header: t("paymentRecords.columns.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => (
        <div className="grid justify-items-end gap-0.5">
          <span className={row.direction === "EXPENSE" ? "font-latin text-danger" : "font-latin text-ok-text"}>
            {row.direction === "EXPENSE" ? "−" : "+"}
            {formatMoney(toNumber(row.amount))}
          </span>
          {row.status === "REFUNDED" && row.refundedAmount != null ? (
            <span className="text-meta text-muted">{t("paymentRecords.refunded", { amount: formatMoney(toNumber(row.refundedAmount)) ?? "" })}</span>
          ) : null}
        </div>
      ),
    },
    {
      key: "method",
      header: t("paymentRecords.columns.method"),
      cell: (row) => (
        <div className="grid gap-0.5">
          <span>{methodLabel(row.method, t)}</span>
          {row.reference ? <span className="break-all text-meta text-muted">{row.reference}</span> : null}
        </div>
      ),
    },
    {
      key: "receipt",
      header: t("paymentRecords.columns.receipt"),
      mobileHidden: true,
      cell: (row) => <span className="font-latin">{row.receiptNo || "—"}</span>,
    },
    {
      key: "status",
      header: t("paymentRecords.columns.status"),
      cell: (row) => (
        <div className="flex flex-wrap gap-1">
          <Pill tone={STATUS_TONE[row.status]}>{statusLabel(row.status, t)}</Pill>
          {row.correctedAt ? (
            <span title={t("paymentRecords.correctedOn", { date: formatDate(row.correctedAt) })}>
              <Pill tone="muted">{t("paymentRecords.corrected")}</Pill>
            </span>
          ) : null}
          {row.pendingEdit ? <Pill tone="warn">{t("paymentRecords.waitingBadge")}</Pill> : null}
        </div>
      ),
    },
    {
      key: "actions",
      header: "",
      mobileLabel: t("paymentRecords.columns.actions"),
      align: "end",
      cell: (row) =>
        !row.editable ? (
          <Button size="sm" variant="ghost" href={row.kind === "ONLINE" ? "/staff/payments" : "/staff/pay"}>
            {t(row.kind === "ONLINE" ? "paymentRecords.openOrders" : "paymentRecords.openStaffPay")}
          </Button>
        ) : row.status === "VOIDED" || row.status === "REJECTED" || row.pendingEdit ? null : (
          <Button size="sm" variant="secondary" onClick={() => setEditing(row)}>
            {t("paymentRecords.edit")}
          </Button>
        ),
    },
  ];

  return (
    <div className="grid gap-4">
      <section aria-label={t("paymentRecords.filters.label")} className="grid gap-4 rounded-card border border-border bg-card p-4">
        <div className="grid gap-2">
          <span className="text-meta font-medium text-muted">{t("paymentRecords.filters.period")}</span>
          <SegmentedControl
            options={PERIODS.map((value) => ({ value, label: t(`paymentRecords.filters.${value}`) }))}
            value={period}
            onChange={(value) => {
              const next = periodRange(value as Period);
              setParams(next ? { period: value, from: "", to: "" } : { period: value, from: range.from, to: range.to });
            }}
            ariaLabel={t("paymentRecords.filters.period")}
          />
          {period === "custom" ? (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label={t("paymentRecords.filters.from")}>
                <TextInput type="date" value={params.from} onChange={(event) => setParams({ from: event.target.value })} />
              </Field>
              <Field label={t("paymentRecords.filters.to")} error={backwards ? t("paymentRecords.filters.backwards") : undefined}>
                <TextInput type="date" value={params.to} onChange={(event) => setParams({ to: event.target.value })} invalid={backwards} />
              </Field>
            </div>
          ) : null}
        </div>
        <div className="grid gap-2">
          <span className="text-meta font-medium text-muted">{t("paymentRecords.filters.direction")}</span>
          <SegmentedControl
            options={[
              { value: "", label: t("paymentRecords.filters.all") },
              { value: "INCOME", label: t("paymentRecords.filters.income") },
              { value: "EXPENSE", label: t("paymentRecords.filters.expense") },
            ]}
            value={direction}
            onChange={(value) => setParams({ direction: value, kind: "" })}
            ariaLabel={t("paymentRecords.filters.direction")}
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t("paymentRecords.filters.kind")}>
            <Select value={kind} onChange={(event) => setParams({ kind: event.target.value })}>
              <option value="">{t("paymentRecords.filters.allSources")}</option>
              {offered.some((value) => INCOME_KINDS.includes(value)) ? (
                <optgroup label={t("paymentRecords.filters.income")}>
                  {offered.filter((value) => INCOME_KINDS.includes(value)).map((value) => (
                    <option key={value} value={value}>
                      {t(`paymentRecords.kind.${value}`)}
                    </option>
                  ))}
                </optgroup>
              ) : null}
              {offered.some((value) => EXPENSE_KINDS.includes(value)) ? (
                <optgroup label={t("paymentRecords.filters.expense")}>
                  {offered.filter((value) => EXPENSE_KINDS.includes(value)).map((value) => (
                    <option key={value} value={value}>
                      {t(`paymentRecords.kind.${value}`)}
                    </option>
                  ))}
                </optgroup>
              ) : null}
            </Select>
          </Field>
          {showsItem ? (
            <Field label={t("paymentRecords.filters.item")}>
              <Select value={item} onChange={(event) => setParams({ item: event.target.value })}>
                <option value="">{t("paymentRecords.filters.allItems")}</option>
                <option value="PACKAGE">{t("paymentRecords.filters.packages")}</option>
                <option value="EXAM">{t("paymentRecords.filters.exams")}</option>
              </Select>
            </Field>
          ) : null}
          {showsItem ? (
            <Field label={t("paymentRecords.filters.category")}>
              <Select value={params.categoryId} onChange={(event) => setParams({ categoryId: event.target.value })}>
                <option value="">{t("paymentRecords.filters.allCategories")}</option>
                {flattenCategories(categories.data ?? []).map(({ node, depth }) => (
                  <option key={node.id} value={node.id}>
                    {`${"— ".repeat(depth)}${locale === "bn" ? node.titleBn || node.titleEn : node.titleEn}`}
                  </option>
                ))}
              </Select>
            </Field>
          ) : null}
          {showsBatch ? (
            <Field label={t("paymentRecords.filters.batch")}>
              <Select value={params.batchId} onChange={(event) => setParams({ batchId: event.target.value })}>
                <option value="">{t("paymentRecords.filters.allBatches")}</option>
                {(batches.data ?? []).map((batch) => (
                  <option key={batch.id} value={batch.id}>
                    {locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn}
                  </option>
                ))}
              </Select>
            </Field>
          ) : null}
          <Field label={t("paymentRecords.filters.method")}>
            <Select value={method} onChange={(event) => setParams({ method: event.target.value })}>
              <option value="">{t("paymentRecords.filters.allMethods")}</option>
              {FILTER_METHODS.map((value) => (
                <option key={value} value={value}>
                  {methodLabel(value, t)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("paymentRecords.filters.status")}>
            <Select value={status} onChange={(event) => setParams({ status: event.target.value })}>
              <option value="">{t("paymentRecords.filters.allStatuses")}</option>
              {FILTER_STATUSES.map((value) => (
                <option key={value} value={value}>
                  {statusLabel(value, t)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("paymentRecords.filters.search")}>
            <SearchInput value={params.q} onChange={(value) => setParams({ q: value })} placeholder={t("paymentRecords.filters.search")} />
          </Field>
        </div>
      </section>

      {records.isError ? (
        <ErrorState onRetry={() => records.refetch()} error={records.error} />
      ) : (
        <>
          {records.data ? (
            <p className="text-meta text-muted" role="status">
              {t("paymentRecords.summary", {
                count: rows.length,
                moneyIn: formatMoney(moneyIn) ?? "",
                moneyOut: formatMoney(moneyOut) ?? "",
                net: formatMoney(moneyIn - moneyOut) ?? "",
              })}
              {rows.length >= 500 ? ` ${t("paymentRecords.limit")}` : ""}
            </p>
          ) : null}
          <Table
            columns={columns}
            rows={rows}
            rowKey={(row) => `${row.kind}-${row.id}`}
            loading={records.isPending}
            empty={<EmptyState title={t("paymentRecords.empty")} body={t("paymentRecords.emptyBody")} />}
          />
        </>
      )}
      {editing ? <EditDialog row={editing} isMaster={isMaster} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

/** The amount as typed, from the API's number or string ("400.00" shows as "400"). */
function amountText(value: number | string): string {
  return String(toNumber(value));
}

function EditDialog({ row, isMaster, onClose }: { row: PaymentRow; isMaster: boolean; onClose: () => void }) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();
  const { toast } = useToast();
  const propose = useProposePaymentEdit();
  const [amount, setAmount] = useState(amountText(row.amount));
  const [paidOn, setPaidOn] = useState(row.paidOn);
  const [method, setMethod] = useState<PaymentMethod>(row.method === "ONLINE" ? "CASH" : row.method);
  const [reference, setReference] = useState(row.reference ?? "");
  const [note, setNote] = useState(row.note ?? "");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  const changed =
    toNumber(amount) !== toNumber(row.amount) ||
    paidOn !== row.paidOn ||
    method !== row.method ||
    reference.trim() !== (row.reference ?? "") ||
    note.trim() !== (row.note ?? "");

  function validate(): Record<string, string> {
    const problems: Record<string, string> = {};
    if (!AMOUNT.test(amount.trim()) || Number(amount) <= 0) problems.amount = t("paymentRecords.dialog.errors.amount");
    if (!paidOn || paidOn > dhakaToday()) problems.paidOn = t("paymentRecords.dialog.errors.paidOn");
    if (!REFERENCE.test(reference.trim())) problems.reference = t("paymentRecords.dialog.errors.reference");
    if (!reason.trim()) problems.reason = t("paymentRecords.dialog.errors.reason");
    if (Object.keys(problems).length === 0 && !changed) problems.form = t("paymentRecords.dialog.errors.nothing");
    return problems;
  }

  async function submit() {
    const problems = validate();
    setErrors(problems);
    if (Object.keys(problems).length > 0) return;
    try {
      const edit = await propose.mutateAsync({
        // Only the payments the office records reach this dialog (row.editable).
        kind: row.kind as PaymentKind,
        id: row.id,
        body: { amount: amount.trim(), paidOn, method, reference: reference.trim(), note: note.trim(), reason: reason.trim() },
      });
      toast({ title: t(edit.status === "FINALIZED" ? "paymentRecords.dialog.saved" : "paymentRecords.dialog.sent"), tone: "ok" });
      onClose();
    } catch (error) {
      setErrors(editErrors(error, t, formatMoney));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => (open ? undefined : onClose())}
      title={t("paymentRecords.dialog.title")}
      description={t("paymentRecords.dialog.who", { kind: t(`paymentRecords.kind.${row.kind}`), person: row.personName || "—" })}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={propose.isPending}>
            {t(isMaster ? "paymentRecords.dialog.submitMaster" : "paymentRecords.dialog.submitStaff")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="rounded-control bg-bg px-3 py-2 text-meta text-muted">
          {t(isMaster ? "paymentRecords.dialog.masterNote" : "paymentRecords.dialog.staffNote")}
          {row.kind === "DIRECT" ? ` ${t("paymentRecords.dialog.directNote")}` : ""}
        </p>
        {errors.form ? (
          <p role="alert" className="text-meta text-danger">
            {errors.form}
          </p>
        ) : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("paymentRecords.dialog.amount")} error={errors.amount} required>
            <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} invalid={Boolean(errors.amount)} />
          </Field>
          <Field label={t("paymentRecords.dialog.paidOn")} error={errors.paidOn} required>
            <TextInput type="date" value={paidOn} max={dhakaToday()} onChange={(event) => setPaidOn(event.target.value)} invalid={Boolean(errors.paidOn)} />
          </Field>
          <Field label={t("paymentRecords.dialog.method")} required>
            <Select value={method} onChange={(event) => setMethod(event.target.value as PaymentMethod)}>
              {METHODS.map((value) => (
                <option key={value} value={value}>
                  {t(methodLabelKey(value))}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("paymentRecords.dialog.reference")} error={errors.reference} required>
            <TextInput value={reference} onChange={(event) => setReference(event.target.value)} invalid={Boolean(errors.reference)} />
          </Field>
        </div>
        <Field label={t("paymentRecords.dialog.note")} error={errors.note}>
          <Textarea rows={2} value={note} onChange={(event) => setNote(event.target.value)} invalid={Boolean(errors.note)} />
        </Field>
        <Field label={t("paymentRecords.dialog.reason")} hint={t("paymentRecords.dialog.reasonHint")} error={errors.reason} required>
          <Textarea rows={2} value={reason} onChange={(event) => setReason(event.target.value)} invalid={Boolean(errors.reason)} />
        </Field>
      </div>
    </Dialog>
  );
}

/** The API's refusal in this page's words: the fields it names, and the conflicts it can answer. */
export function editErrors(
  error: unknown,
  t: (key: string, params?: Record<string, string | number>) => string,
  formatMoney: (value: number | string | null | undefined) => string | null,
): Record<string, string> {
  if (!(error instanceof ApiError)) return { form: offlineErrorText(error, t) };
  const codes: Record<string, string> = {
    INSTALLMENT_VOID_NOT_LATEST: "paymentRecords.dialog.errors.notLatest",
    INSTALLMENT_OVERPAYMENT: "paymentRecords.dialog.errors.overpay",
    INSTALLMENT_DUPLICATE_REFERENCE: "paymentRecords.dialog.errors.duplicateReference",
    ADMISSION_STATE: "paymentRecords.dialog.errors.state",
  };
  if (codes[error.code]) {
    return error.code === "INSTALLMENT_DUPLICATE_REFERENCE"
      ? { reference: t(codes[error.code]) }
      : { form: t(codes[error.code]) };
  }
  const fields: Record<string, string> = {};
  for (const problem of error.fieldErrors ?? []) {
    if (problem.field === "amount") {
      const most = /BDT (\d+(?:\.\d+)?)/.exec(problem.message ?? "");
      fields.amount = most ? t("paymentRecords.dialog.errors.amountMost", { amount: formatMoney(most[1]) ?? most[1] }) : t("paymentRecords.dialog.errors.amount");
    } else if (problem.field === "paidOn") {
      fields.paidOn = t("paymentRecords.dialog.errors.paidOn");
    } else if (problem.field === "reference") {
      fields.reference = t("paymentRecords.dialog.errors.reference");
    } else if (problem.field === "reason") {
      fields.reason = t("paymentRecords.dialog.errors.reason");
    } else {
      fields[problem.field] = problem.message;
    }
  }
  if (Object.keys(fields).length === 0) fields.form = offlineErrorText(error, t);
  return fields;
}

/** What a correction changes, field by field, old then new. */
function Changes({ before, after }: { before: PaymentValues; after: PaymentValues }) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const shown = (field: keyof PaymentValues, values: PaymentValues): string => {
    const value = values[field];
    if (value === null || value === undefined || value === "") return "—";
    if (field === "amount") return formatMoney(toNumber(value)) ?? "—";
    if (field === "paidOn") return formatDate(String(value));
    if (field === "method") return t(methodLabelKey(value as PaymentMethod));
    return String(value);
  };
  const fields = (["amount", "paidOn", "method", "reference", "note"] as const).filter((field) => shown(field, before) !== shown(field, after));
  return (
    <dl className="grid gap-1 text-meta">
      {fields.map((field) => (
        <div key={field} className="flex flex-wrap gap-x-2">
          <dt className="text-muted">{t(`paymentRecords.field.${field}`)}:</dt>
          <dd className="text-text">
            <span className="line-through decoration-muted">{shown(field, before)}</span> → <span className="font-medium">{shown(field, after)}</span>
          </dd>
        </div>
      ))}
    </dl>
  );
}

function EditsList({ status, isMaster }: { status: "PENDING" | "DECIDED"; isMaster: boolean }) {
  const { t } = useLocale();
  const { formatDateTime } = useFormat();
  const { toast } = useToast();
  const edits = usePaymentEdits(status);
  const finalize = useFinalizePaymentEdit();
  const [rejecting, setRejecting] = useState<PaymentEdit | null>(null);
  const rows = edits.data ?? [];

  const columns: Column<PaymentEdit>[] = [
    {
      key: "who",
      header: t("paymentRecords.columns.who"),
      cell: (edit) => (
        <div className="grid gap-0.5">
          <span className="font-medium text-text">{edit.personName || "—"}</span>
          <span className="text-meta text-muted">{t(`paymentRecords.kind.${edit.kind}`)}</span>
        </div>
      ),
    },
    { key: "changes", header: `${t("paymentRecords.waiting.before")} → ${t("paymentRecords.waiting.after")}`, cell: (edit) => <Changes before={edit.before} after={edit.after} /> },
    {
      key: "asked",
      header: t("paymentRecords.columns.status"),
      cell: (edit) => (
        <div className="grid gap-1 text-meta">
          <Pill tone={EDIT_TONE[edit.status]}>{t(`paymentRecords.status.${edit.status}`)}</Pill>
          <span className="text-muted">{t("paymentRecords.waiting.asked", { name: edit.requestedByName || "—", time: formatDateTime(edit.requestedAt) })}</span>
          <span className="text-text">{t("paymentRecords.waiting.reason", { reason: edit.reason })}</span>
          {edit.decidedAt ? (
            <span className="text-muted">{t("paymentRecords.waiting.decided", { name: edit.decidedByName || "—", time: formatDateTime(edit.decidedAt) })}</span>
          ) : null}
          {edit.status === "REJECTED" && edit.decisionReason ? (
            <span className="text-danger">{t("paymentRecords.waiting.decisionReason", { reason: edit.decisionReason })}</span>
          ) : null}
        </div>
      ),
    },
    {
      key: "actions",
      header: "",
      mobileLabel: t("paymentRecords.columns.actions"),
      align: "end",
      cell: (edit) =>
        isMaster && edit.status === "PENDING" ? (
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              size="sm"
              loading={finalize.isPending && finalize.variables === edit.id}
              onClick={() =>
                finalize.mutate(edit.id, {
                  onSuccess: () => toast({ title: t("paymentRecords.waiting.finalized"), tone: "ok" }),
                  onError: (error) => toast({ title: editErrors(error, t, () => null).form ?? offlineErrorText(error, t), tone: "danger" }),
                })
              }
            >
              {t("paymentRecords.waiting.finalize")}
            </Button>
            <Button size="sm" variant="secondary" onClick={() => setRejecting(edit)}>
              {t("paymentRecords.waiting.reject")}
            </Button>
          </div>
        ) : null,
    },
  ];

  if (edits.isError) return <ErrorState onRetry={() => edits.refetch()} error={edits.error} />;
  const emptyTitle = status === "PENDING" ? (isMaster ? "paymentRecords.waiting.empty" : "paymentRecords.waiting.emptyStaff") : "paymentRecords.history.empty";
  const emptyBody = status === "PENDING" ? "paymentRecords.waiting.emptyBody" : "paymentRecords.history.emptyBody";
  return (
    <div className="grid gap-4">
      <Table
        columns={columns}
        rows={rows}
        rowKey={(edit) => edit.id}
        loading={edits.isPending}
        empty={<EmptyState title={t(emptyTitle)} body={status === "PENDING" && !isMaster ? undefined : t(emptyBody)} />}
      />
      {rejecting ? <RejectEditDialog edit={rejecting} onClose={() => setRejecting(null)} /> : null}
    </div>
  );
}

function RejectEditDialog({ edit, onClose }: { edit: PaymentEdit; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const reject = useRejectPaymentEdit();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!reason.trim()) {
      setError(t("paymentRecords.waiting.rejectRequired"));
      return;
    }
    try {
      await reject.mutateAsync({ id: edit.id, reason: reason.trim() });
      toast({ title: t("paymentRecords.waiting.rejected"), tone: "ok" });
      onClose();
    } catch (caught) {
      setError(editErrors(caught, t, () => null).form ?? offlineErrorText(caught, t));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => (open ? undefined : onClose())}
      title={t("paymentRecords.waiting.rejectTitle")}
      description={t("paymentRecords.waiting.rejectBody")}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant="danger" onClick={() => void submit()} loading={reject.isPending}>
            {t("paymentRecords.waiting.rejectSubmit")}
          </Button>
        </>
      }
    >
      <Field label={t("paymentRecords.waiting.rejectReason")} error={error ?? undefined} required>
        <Textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} invalid={Boolean(error)} />
      </Field>
    </Dialog>
  );
}
