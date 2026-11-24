"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { SummaryStrip, type SummaryItem } from "@/components/ui/summary-strip";
import { Tabs } from "@/components/ui/tabs";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useInstructorPayments, useOfflineInstructors, usePaySummary } from "@/lib/api/exam/instructor-pay";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { toNumber } from "@/components/staff/offline/offline-model";
import { InstructorFormDialog } from "./instructor-form-dialog";
import { PaymentTable } from "./payment-history";
import { PAY_KINDS, kindKey } from "./pay-model";
import { RecordPaymentForm } from "./record-payment-form";

const TABS = ["record", "history"] as const;

/**
 * One offline instructor (24 Nov, instructor-pay-simple-plan.md, D7 and D14): who they are, the summary of what they were paid, and two
 * tabs. Record payment records a payment by hand; Payment history lists every payment to them (old class payments too), with Edit and
 * Void. The Master Admin only. The tab and the history filters live in the URL.
 */
export function InstructorDetail({ id }: { id: string }) {
  const { t, locale } = useLocale();
  const { formatDate, formatMoney, formatNumber } = useFormat();
  const instructors = useOfflineInstructors(true);
  const summary = usePaySummary(id);
  const [params, setParams] = useListParams({ tab: "", from: "", to: "", kind: "" });
  const tab = (TABS as readonly string[]).includes(params.tab) ? params.tab : "record";
  const history = useInstructorPayments(id, { from: params.from || undefined, to: params.to || undefined, kind: params.kind || undefined });
  const [editing, setEditing] = useState(false);

  if (instructors.isError) {
    return <ErrorState onRetry={() => void instructors.refetch()} error={instructors.error} />;
  }
  if (instructors.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  const instructor = (instructors.data ?? []).find((candidate) => candidate.id === id);
  if (!instructor) {
    return <EmptyState title={t("instructorPay.detail.notFoundTitle")} body={t("instructorPay.detail.notFoundBody")} />;
  }

  const figures = summary.data;
  const items: SummaryItem[] = [
    { label: t("instructorPay.detail.totalPaid"), value: figures ? (formatMoney(toNumber(figures.totalPaid)) ?? "") : "—", tone: "ok" },
    { label: t("instructorPay.detail.paidMonth"), value: figures ? (formatMoney(toNumber(figures.paidThisMonth)) ?? "") : "—" },
    {
      label: t("instructorPay.detail.lastPayment"),
      value:
        figures?.lastPaidOn && figures.lastAmount != null
          ? t("instructorPay.detail.lastPaymentValue", {
              amount: formatMoney(toNumber(figures.lastAmount)) ?? "",
              date: formatDate(figures.lastPaidOn),
            })
          : t("instructorPay.detail.noPaymentYet"),
      tone: "muted",
    },
    { label: t("instructorPay.detail.payments"), value: figures ? formatNumber(figures.payments) : "—", tone: "muted" },
  ];
  const filtered = Boolean(params.from || params.to || params.kind);

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
          <Button variant="secondary" onClick={() => setEditing(true)}>
            {t("instructorPay.instructors.edit")}
          </Button>
        }
      />

      <Card>
        <dl className="grid gap-3 text-body sm:grid-cols-2">
          <div className="grid gap-0.5">
            <dt className="text-meta text-muted">{t("instructorPay.detail.phone")}</dt>
            <dd className="font-latin">{instructor.phone ?? t("instructorPay.detail.none")}</dd>
          </div>
          <div className="grid gap-0.5">
            <dt className="text-meta text-muted">{t("instructorPay.detail.note")}</dt>
            <dd>{instructor.note ?? t("instructorPay.detail.none")}</dd>
          </div>
          <div className="grid gap-1">
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
          <div className="grid gap-1">
            <dt className="text-meta text-muted">{t("instructorPay.detail.batches")}</dt>
            <dd>
              {figures && figures.batches.length > 0 ? (
                <ul className="flex flex-wrap gap-1.5">
                  {figures.batches.map((batch) => (
                    <li key={batch.id}>
                      <Pill tone="accent">{locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn}</Pill>
                    </li>
                  ))}
                </ul>
              ) : (
                <span className="text-muted">{t("instructorPay.detail.noBatches")}</span>
              )}
            </dd>
          </div>
          {instructor.archived ? (
            <div className="sm:col-span-2">
              <Pill tone="muted">{t("instructorPay.detail.archived")}</Pill>
            </div>
          ) : null}
        </dl>
      </Card>

      {summary.isError ? (
        <ErrorState onRetry={() => void summary.refetch()} error={summary.error} />
      ) : (
        <SummaryStrip items={items} loading={summary.isPending} />
      )}

      <Tabs
        tabs={[
          { value: "record", label: t("instructorPay.tabs.record") },
          { value: "history", label: t("instructorPay.tabs.history"), badge: figures && figures.payments > 0 ? figures.payments : undefined },
        ]}
        value={tab}
        onChange={(value) => setParams({ tab: value })}
        ariaLabel={t("instructorPay.tabs.label")}
      />

      {tab === "record" ? (
        <RecordPaymentForm instructorId={instructor.id} />
      ) : (
        <section aria-label={t("instructorPay.tabs.history")} className="grid gap-3">
          <div className="grid gap-3 rounded-card border border-border bg-card p-4 sm:grid-cols-3">
            <Field label={t("instructorPay.filters.from")}>
              <TextInput type="date" value={params.from} onChange={(event) => setParams({ from: event.target.value })} />
            </Field>
            <Field label={t("instructorPay.filters.to")}>
              <TextInput type="date" value={params.to} onChange={(event) => setParams({ to: event.target.value })} />
            </Field>
            <Field label={t("instructorPay.filters.kind")}>
              <Select value={params.kind} onChange={(event) => setParams({ kind: event.target.value })}>
                <option value="">{t("instructorPay.filters.allKinds")}</option>
                {PAY_KINDS.map((kind) => (
                  <option key={kind} value={kind}>
                    {t(kindKey(kind))}
                  </option>
                ))}
              </Select>
            </Field>
            {filtered ? (
              <div className="sm:col-span-3">
                <Button variant="ghost" size="sm" onClick={() => setParams({ from: "", to: "", kind: "" })}>
                  {t("instructorPay.filters.clear")}
                </Button>
              </div>
            ) : null}
          </div>
          {history.isError ? (
            <ErrorState onRetry={() => void history.refetch()} error={history.error} />
          ) : (
            <PaymentTable list={history.data} loading={history.isPending} filtered={filtered} actions />
          )}
        </section>
      )}

      <Card padding="sm">
        <p className="text-meta text-muted">{t("instructorPay.detail.mergeNote")}</p>
      </Card>

      <InstructorFormDialog key={editing ? instructor.version : "closed"} instructor={instructor} open={editing} onOpenChange={setEditing} />
    </div>
  );
}
