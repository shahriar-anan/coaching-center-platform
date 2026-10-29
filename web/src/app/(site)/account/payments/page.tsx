"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { Check, Copy } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { RequireStudent } from "@/lib/auth/guards";
import {
  downloadReceiptPdf,
  isNotDeployed,
  useMyOrder,
  useMyOrders,
  useMyOrderSummary,
  type MyOrder,
  type PaymentStatus,
} from "@/lib/api/exam/account";
import { useCancelOrder, useResumeOrder } from "@/lib/api/exam/purchase";
import { useSession } from "@/lib/auth/session-context";
import { useMyLiveExamAccess } from "@/lib/api/exam/catalog";
import { useListParams } from "@/lib/url/list-params";
import { formatDate, formatDateTime, formatMoney, formatNumber, pickTitle } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { IconButton } from "@/components/ui/icon-button";
import { DoorActions } from "@/components/student/catalog/door-actions";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { accessEnd } from "@/components/student/catalog/my-exams-rows";
import {
  actionsFor,
  hasDiscount,
  hasFilters,
  itemHref,
  parseScopeFilter,
  parseStatusFilter,
  rowDate,
  statusKey,
  toOrderFilter,
  type PaymentFilters,
} from "./_components/payment-model";

const STATUS_TONE: Record<PaymentStatus, PillTone> = {
  PENDING: "warn",
  PAID: "ok",
  FAILED: "danger",
  CANCELLED: "muted",
};

const STATUS_WORDS: PaymentStatus[] = ["PENDING", "PAID", "FAILED", "CANCELLED"];

/** The title of the module or exam the order is for. */
function productTitle(row: MyOrder, locale: "bn" | "en"): string {
  const title = row.scope === "EXAM"
    ? { titleEn: row.examTitleEn, titleBn: row.examTitleBn }
    : { titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn };
  return pickTitle(title, locale);
}

export default function PaymentsPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStudent>
        <PaymentsView />
      </RequireStudent>
    </Suspense>
  );
}

function CopyValue({ value, label }: { value: string | null; label: string }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const [copied, setCopied] = useState(false);
  if (!value) {
    return <span className="text-muted">—</span>;
  }
  async function copy() {
    try {
      await navigator.clipboard.writeText(value ?? "");
      setCopied(true);
      toast({ title: t("account.payments.copied"), tone: "info" });
    } catch {
      /* clipboard blocked: the value stays visible */
    }
  }
  return (
    <span className="inline-flex items-center gap-1">
      <code className="font-latin text-meta tabular-nums break-all">{value}</code>
      <IconButton
        label={`${t("common.actions.copy")}: ${label}`}
        className="size-9"
        onClick={copy}
        icon={copied ? <Check aria-hidden="true" className="size-4" /> : <Copy aria-hidden="true" className="size-4" />}
      />
    </span>
  );
}

/** The access the row's grant gives, read from the student's access list (a grant with an order id). */
function AccessLine({ orderId }: { orderId: string }) {
  const { t, locale } = useLocale();
  const access = useMyLiveExamAccess(true);
  const grant = [...(access.data?.exams ?? []), ...(access.data?.modules ?? [])].find((row) => row.orderId === orderId);
  if (!grant) {
    return <span className="text-muted">{t("account.payments.detail.accessNone")}</span>;
  }
  const end = accessEnd(grant);
  if (end.kind === "expired") {
    return <span>{t("account.payments.detail.accessExpired", { date: formatDate(grant.endsAt ?? "", locale, { year: "auto" }) })}</span>;
  }
  if (end.kind === "lifetime") {
    return <span>{t("account.payments.detail.accessLifetime")}</span>;
  }
  return <span>{t("account.payments.detail.accessUntil", { date: formatDate(end.endsAt, locale, { year: "auto" }) })}</span>;
}

/** The detail panel of one order, with the actions its status allows (Phase 4, S4.6). */
function PaymentDetail({ order, onClose }: { order: MyOrder; onClose: () => void }) {
  const { t, locale } = useLocale();
  const { session } = useSession();
  const verified = session.status === "authenticated" && Boolean(session.user.emailVerifiedAt);
  const { toast } = useToast();
  const resume = useResumeOrder();
  const cancel = useCancelOrder();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [busy, setBusy] = useState(false);
  const actions = actionsFor(order.status);
  const href = itemHref(order);

  async function downloadPdf() {
    setBusy(true);
    try {
      await downloadReceiptPdf(order.orderId, order.receiptNo ?? order.orderId);
    } catch {
      toast({ title: t("account.payments.detail.pdfFailed"), tone: "danger" });
    } finally {
      setBusy(false);
    }
  }

  // Continue payment posts the order's target again. The API reuses the open order, so no new order is made (plan 5, P-continue).
  async function continuePayment() {
    setBusy(true);
    try {
      await resume(order);
    } catch {
      setBusy(false);
      toast({ title: t("common.states.errorBody"), tone: "danger" });
    }
  }

  async function cancelOrder() {
    try {
      await cancel.mutateAsync(order.orderId);
      toast({ title: t("account.payments.detail.cancelled"), tone: "ok" });
    } catch {
      toast({ title: t("common.states.errorBody"), tone: "danger" });
    }
  }

  return (
    <Card padding="md" className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="payment-detail-title" className="text-section font-bold">{t("account.payments.detail.title")}</h2>
        <Button variant="ghost" onClick={onClose}>{t("account.payments.detail.close")}</Button>
      </div>
      <p className="flex flex-wrap items-center gap-2">
        <Pill tone={STATUS_TONE[order.status]}>{t(statusKey(order.status))}</Pill>
        <span className="font-semibold">{productTitle(order, locale)}</span>
        <span className="text-meta text-muted">
          {order.scope === "EXAM"
            ? t("account.payments.kindExam")
            : order.scope === "BUNDLE"
              ? t("account.payments.detail.bundle")
              : t("account.payments.kindModule")}
        </span>
      </p>
      <dl className="grid gap-2 text-body sm:grid-cols-2">
        <div><dt className="text-meta text-muted">{order.paidAt ? t("account.payments.detail.paidOn") : t("account.payments.detail.createdOn")}</dt><dd>{formatDateTime(rowDate(order), locale)}</dd></div>
        <div><dt className="text-meta text-muted">{t("account.payments.detail.receiptNo")}</dt><dd>{order.receiptNo ? <code className="font-latin">{order.receiptNo}</code> : "—"}</dd></div>
        {order.status === "PAID" ? (
          // The receipt line (Phase 4B): where the receipt went. A paid order with no address and no verified email got none.
          <div>
            <dt className="sr-only">{t("account.payments.detail.receiptTo")}</dt>
            <dd>
              {order.receiptEmail
                ? t("account.payments.detail.receiptSentTo", { email: order.receiptEmail })
                : verified
                  ? t("account.payments.detail.receiptAccount")
                  : t("account.payments.detail.receiptNone")}
            </dd>
          </div>
        ) : null}
        <div><dt className="text-meta text-muted">{t("account.payments.method")}</dt><dd>{order.paymentChannel ?? "—"}</dd></div>
        <div><dt className="text-meta text-muted">{t("account.payments.detail.orderId")}</dt><dd><CopyValue value={order.orderId} label={t("account.payments.detail.orderId")} /></dd></div>
        <div><dt className="text-meta text-muted">{t("account.payments.tranId")}</dt><dd><CopyValue value={order.tranId} label={t("account.payments.tranId")} /></dd></div>
        <div>
          <dt className="text-meta text-muted">{t("account.payments.column.amount")}</dt>
          <dd className="tabular-nums">
            {hasDiscount(order) ? (
              <span className="me-2 text-muted line-through">{formatMoney(order.listPrice ?? 0, locale)}</span>
            ) : null}
            {formatMoney(order.amount, locale)}
          </dd>
        </div>
        {order.status === "PAID" ? (
          <div><dt className="text-meta text-muted">{t("account.payments.detail.access")}</dt><dd><AccessLine orderId={order.orderId} /></dd></div>
        ) : null}
      </dl>

      <div className="flex flex-wrap gap-2">
        {actions.includes("downloadPdf") ? (
          <Button variant="secondary" loading={busy} onClick={downloadPdf}>{t("account.payments.detail.downloadPdf")}</Button>
        ) : null}
        {actions.includes("printReceipt") ? (
          <Button variant="secondary" href={`/account/payments/receipt?order=${encodeURIComponent(order.orderId)}`}>
            {t("account.payments.detail.printReceipt")}
          </Button>
        ) : null}
        {actions.includes("open") && href ? (
          <Button variant="secondary" href={href}>{t("account.payments.detail.open")}</Button>
        ) : null}
        {actions.includes("continue") ? (
          <Button loading={busy} onClick={continuePayment}>{t("account.payments.detail.continue")}</Button>
        ) : null}
        {actions.includes("cancel") ? (
          <Button variant="secondary" onClick={() => setConfirmCancel(true)}>{t("account.payments.detail.cancel")}</Button>
        ) : null}
        {actions.includes("buyAgain") && href ? (
          <Button href={href}>{t("account.payments.detail.buyAgain")}</Button>
        ) : null}
      </div>
      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title={t("account.payments.detail.cancelTitle")}
        body={t("account.payments.detail.cancelBody")}
        confirmLabel={t("account.payments.detail.cancelConfirm")}
        tone="danger"
        onConfirm={cancelOrder}
      />
    </Card>
  );
}

function PaymentsView() {
  const { t, locale } = useLocale();
  const [params, setParams] = useListParams({
    page: 0,
    order: "",
    status: "",
    scope: "",
    from: "",
    to: "",
    examId: "",
    moduleId: "",
  });
  const filters: PaymentFilters = {
    status: parseStatusFilter(params.status),
    scope: parseScopeFilter(params.scope),
    from: params.from,
    to: params.to,
    examId: params.examId,
    moduleId: params.moduleId,
  };
  const query = useMyOrders(params.page, toOrderFilter(filters));
  const summary = useMyOrderSummary();
  const detail = useMyOrder(params.order || null);
  const rows = query.data?.content ?? [];
  const selected = detail.data?.content?.[0] ?? null;
  const filtered = hasFilters(filters);

  const statusWord = (status: PaymentStatus) => t(statusKey(status));
  const scopeWord = (scope: PaymentFilters["scope"]) =>
    scope === "EXAM"
      ? t("account.payments.kindExam")
      : scope === "BUNDLE"
        ? t("account.payments.detail.bundle")
        : scope === "MODULE"
          ? t("account.payments.kindModule")
          : "";
  const showing = [
    filters.status ? statusWord(filters.status) : "",
    filters.scope ? scopeWord(filters.scope) : "",
    filters.from || filters.to ? `${filters.from || "…"} – ${filters.to || "…"}` : "",
  ].filter(Boolean).join(", ");

  const columns: Column<MyOrder>[] = [
    {
      key: "date",
      header: t("account.payments.column.date"),
      mobileLabel: t("account.payments.column.date"),
      cell: (row) => <span className="text-meta">{formatDateTime(rowDate(row), locale)}</span>,
    },
    {
      key: "product",
      header: t("account.payments.column.product"),
      mobileLabel: t("account.payments.column.product"),
      cell: (row) => (
        <span className="grid gap-0.5">
          <span className="font-semibold">{productTitle(row, locale)}</span>
          <span className="text-meta text-muted">{scopeWord(row.scope)}</span>
        </span>
      ),
    },
    {
      key: "status",
      header: t("account.payments.column.status"),
      mobileLabel: t("account.payments.column.status"),
      cell: (row) => <Pill tone={STATUS_TONE[row.status]}>{statusWord(row.status)}</Pill>,
    },
    {
      key: "amount",
      header: t("account.payments.column.amount"),
      mobileLabel: t("account.payments.column.amount"),
      align: "end",
      cell: (row) => (
        <span className="grid justify-items-end">
          {hasDiscount(row) ? <span className="text-meta text-muted line-through">{formatMoney(row.listPrice ?? 0, locale)}</span> : null}
          <span className="tabular-nums">{formatMoney(row.amount, locale)}</span>
          {hasDiscount(row) ? (
            <span className="text-meta text-ok-text">{t("account.payments.discountOff", { percent: formatNumber(row.discountPercent ?? 0, locale) })}</span>
          ) : null}
        </span>
      ),
    },
    {
      key: "method",
      header: t("account.payments.method"),
      mobileLabel: t("account.payments.method"),
      cell: (row) => <span className="text-meta">{row.paymentChannel ?? "—"}</span>,
    },
    {
      key: "receipt",
      header: t("account.payments.column.receipt"),
      mobileLabel: t("account.payments.column.receipt"),
      cell: (row) => (row.receiptNo ? <code className="font-latin text-meta">{row.receiptNo}</code> : <span className="text-muted">—</span>),
    },
  ];

  return (
    <>
      <PageHeader title={t("account.payments.title")} />

      {summary.data ? (
        <div className="mb-4 grid grid-cols-3 divide-x divide-border rounded-card border border-border bg-card" aria-label={t("account.payments.summary.label")}>
          <div className="grid gap-0.5 p-3 text-center">
            <span className="text-section font-bold tabular-nums">{formatMoney(summary.data.totalPaid, locale)}</span>
            <span className="text-meta text-muted">{t("account.payments.summary.totalPaid")}</span>
          </div>
          <div className="grid gap-0.5 p-3 text-center">
            <span className="text-section font-bold tabular-nums">{formatNumber(summary.data.paidCount, locale)}</span>
            <span className="text-meta text-muted">{t("account.payments.summary.paidCount")}</span>
          </div>
          <button
            type="button"
            onClick={() => setParams({ status: "PENDING", page: 0, order: "" })}
            className="grid gap-0.5 p-3 text-center hover:bg-bg"
          >
            <span className="text-section font-bold tabular-nums">{formatNumber(summary.data.pendingCount, locale)}</span>
            <span className="text-meta text-accent-text underline underline-offset-4">{t("account.payments.summary.pending")}</span>
          </button>
        </div>
      ) : null}

      <form className="mb-4 flex flex-wrap items-end gap-3 text-meta" aria-label={t("account.payments.filters.label")}>
        <label className="grid gap-1">
          <span className="text-muted">{t("account.payments.filters.status")}</span>
          <select
            value={filters.status}
            onChange={(event) => setParams({ status: event.target.value, page: 0, order: "" })}
            className="min-h-10 rounded-control border border-border-control bg-card px-2"
          >
            <option value="">{t("account.payments.filters.all")}</option>
            {STATUS_WORDS.map((status) => (
              <option key={status} value={status}>{statusWord(status)}</option>
            ))}
          </select>
        </label>
        <label className="grid gap-1">
          <span className="text-muted">{t("account.payments.filters.type")}</span>
          <select
            value={filters.scope}
            onChange={(event) => setParams({ scope: event.target.value, page: 0, order: "" })}
            className="min-h-10 rounded-control border border-border-control bg-card px-2"
          >
            <option value="">{t("account.payments.filters.all")}</option>
            <option value="MODULE">{t("account.payments.kindModule")}</option>
            <option value="EXAM">{t("account.payments.kindExam")}</option>
            <option value="BUNDLE">{t("account.payments.detail.bundle")}</option>
          </select>
        </label>
        <label className="grid gap-1">
          <span className="text-muted">{t("account.payments.filters.from")}</span>
          <input
            type="date"
            value={filters.from}
            onChange={(event) => setParams({ from: event.target.value, page: 0, order: "" })}
            className="min-h-10 rounded-control border border-border-control bg-card px-2"
          />
        </label>
        <label className="grid gap-1">
          <span className="text-muted">{t("account.payments.filters.to")}</span>
          <input
            type="date"
            value={filters.to}
            onChange={(event) => setParams({ to: event.target.value, page: 0, order: "" })}
            className="min-h-10 rounded-control border border-border-control bg-card px-2"
          />
        </label>
        {filtered ? (
          <Button variant="ghost" onClick={() => setParams({ status: "", scope: "", from: "", to: "", examId: "", moduleId: "", page: 0, order: "" })}>
            {t("account.payments.filters.clear")}
          </Button>
        ) : null}
      </form>
      {filtered ? (
        <p className="mb-3 text-meta text-muted" role="status">
          {t("account.payments.filters.showing", { what: showing })}
        </p>
      ) : null}

      {selected ? (
        <div className="mb-4">
          <PaymentDetail order={selected} onClose={() => setParams({ order: "" })} />
        </div>
      ) : null}

      {query.isPending ? (
        <Skeleton variant="rows" count={4} />
      ) : query.isError && isNotDeployed(query.error) ? (
        <EmptyState title={t("account.payments.notYet")} />
      ) : query.isError ? (
        <ErrorState onRetry={() => void query.refetch()} />
      ) : (
        <div className="grid gap-4">
          <Table
            columns={columns}
            rows={rows}
            rowKey={(row) => row.orderId}
            onRowClick={(row) => setParams({ order: row.orderId })}
            empty={
              filtered ? (
                <EmptyState title={t("account.payments.filters.none")} />
              ) : (
                <EmptyState title={t("account.payments.empty")} body={t("account.payments.emptyBody")} action={<DoorActions />} />
              )
            }
          />
          <Pagination page={params.page} totalPages={query.data?.totalPages ?? 0} onPage={(page) => setParams({ page })} />
        </div>
      )}

      <p className="mt-6 text-body text-muted">
        {t("account.payments.help.problem")}{" "}
        <Link href={`/contact${selected ? `?order=${encodeURIComponent(selected.orderId)}` : ""}`} className="font-semibold text-accent underline underline-offset-4">
          {t("account.payments.help.contact")}
        </Link>
        {selected ? <> {t("account.payments.help.quote", { orderId: selected.orderId })}</> : null}
      </p>
    </>
  );
}

