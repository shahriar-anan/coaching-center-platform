"use client";

import { Field } from "@/components/ui/field";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { PageHeader } from "@/components/ui/page-header";
import { SearchInput } from "@/components/ui/search-input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { SummaryStrip } from "@/components/ui/summary-strip";
import { PaymentsTable } from "@/components/staff/ops/payments-table";
import { PAYMENT_STATUS_FILTERS } from "@/components/staff/ops/ops-copy";
import { usePaymentsStrip, useStaffPayments } from "@/lib/api/exam/staff-ops";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

/**
 * The payments list: the summary strip, the status and search filters, and the table with its pages. Read only.
 * The payments page shows it with its own header; the admin dashboard shows it under a tab, without the header (`embedded`).
 */
export function PaymentsList({ embedded = false }: { embedded?: boolean }) {
  const { t } = useLocale();
  const format = useFormat();
  const [params, setParams] = useListParams({ page: 0, size: 20, q: "", tranId: "", status: "" });
  const payments = useStaffPayments(params);
  const strip = usePaymentsStrip();

  return (
    <div className="grid gap-5">
      {embedded ? null : (
        <PageHeader
          title={t("staffOps.payments.title")}
          breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("staffOps.payments.title") }]}
        />
      )}
      {/* Under the title. The header's bottom padding already leaves room, so the grid gap is pulled back. */}
      <p className={embedded ? "text-meta text-muted" : "-mt-5 text-meta text-muted"}>{t("staffOps.payments.readOnly")}</p>

      {strip.isError ? (
        <p className="text-meta text-muted">{t("staffOps.payments.strip.error")}</p>
      ) : (
        <SummaryStrip
          loading={strip.isPending}
          items={[
            {
              label: t("staffOps.payments.strip.today"),
              value: format.formatNumber(strip.data?.todayCount ?? 0),
              tone: "ok",
            },
            {
              label: t("staffOps.payments.strip.todayTotal"),
              value: format.formatMoney(strip.data?.todayTotal ?? 0) ?? t("staffResults.result.none"),
            },
            {
              label: t("staffOps.payments.strip.pending"),
              value: format.formatNumber(strip.data?.pendingCount ?? 0),
              tone: "warn",
            },
          ]}
        />
      )}

      <div className="grid gap-4">
        <SegmentedControl
          ariaLabel={t("staffOps.payments.statusLabel")}
          options={PAYMENT_STATUS_FILTERS.map((option) => ({
            value: option.value,
            label: t(`staffOps.payments.status.${option.key}`),
          }))}
          value={params.status}
          onChange={(status) => setParams({ status })}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffOps.payments.searchLabel")}>
            <SearchInput
              id="payments-q"
              value={params.q}
              onChange={(q) => setParams({ q })}
              placeholder={t("staffOps.payments.searchPlaceholder")}
            />
          </Field>
          <Field label={t("staffOps.payments.tranIdLabel")}>
            <SearchInput
              id="payments-tran"
              value={params.tranId}
              onChange={(tranId) => setParams({ tranId })}
              placeholder={t("staffOps.payments.tranIdPlaceholder")}
            />
          </Field>
        </div>
      </div>

      {payments.isError ? (
        <ErrorState onRetry={() => payments.refetch()} />
      ) : (
        <>
          <PaymentsTable
            rows={payments.data?.content ?? []}
            loading={payments.isPending}
            empty={
              <EmptyState title={t("staffOps.payments.empty")} body={t("staffOps.payments.emptyBody")} />
            }
          />
          {payments.data ? (
            <Pagination
              page={payments.data.page}
              totalPages={payments.data.totalPages}
              onPage={(page) => setParams({ page })}
            />
          ) : null}
        </>
      )}
    </div>
  );
}
