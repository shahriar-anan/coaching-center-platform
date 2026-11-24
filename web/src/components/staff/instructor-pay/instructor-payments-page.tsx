"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Select } from "@/components/ui/select";
import { SummaryStrip, type SummaryItem } from "@/components/ui/summary-strip";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useBatches } from "@/lib/api/exam/offline-admin";
import { useAllInstructorPayments, useOfflineInstructors, usePayCards, type PayCard } from "@/lib/api/exam/instructor-pay";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import { dhakaToday, toNumber } from "@/components/staff/offline/offline-model";
import { InstructorFormDialog } from "./instructor-form-dialog";
import { PaymentTable } from "./payment-history";
import { PAY_KINDS, kindKey, monthRange } from "./pay-model";

/**
 * Instructor payments (24 Nov, instructor-pay-simple-plan.md, D13): what was paid this month, last month and this year, and every
 * payment to an offline instructor, filtered by date (this month to start with), batch, instructor and type. Each row links to its
 * instructor, where payments are recorded, corrected and voided. The Master Admin only.
 */
export function InstructorPayments() {
  const { t, locale } = useLocale();
  const { formatMoney, formatNumber } = useFormat();
  const router = useRouter();
  const month = monthRange(dhakaToday());
  const [params, setParams] = useListParams({ from: month.from, to: month.to, batch: "", instructor: "", kind: "" });
  const cards = usePayCards();
  const instructors = useOfflineInstructors(true);
  const batches = useBatches("");
  const payments = useAllInstructorPayments({
    from: params.from || undefined,
    to: params.to || undefined,
    batchId: params.batch || undefined,
    instructorId: params.instructor || undefined,
    kind: params.kind || undefined,
  });
  const [adding, setAdding] = useState(false);

  const card = (label: string, value: PayCard | undefined): SummaryItem => ({
    label: value
      ? `${label} · ${value.payments === 1 ? t("instructorPay.cards.countOne") : t("instructorPay.cards.count", { count: formatNumber(value.payments) })}`
      : label,
    value: value ? (formatMoney(toNumber(value.amount)) ?? "") : "—",
    tone: "ok",
  });
  const items: SummaryItem[] = [
    card(t("instructorPay.cards.thisMonth"), cards.data?.thisMonth),
    card(t("instructorPay.cards.lastMonth"), cards.data?.lastMonth),
    card(t("instructorPay.cards.thisYear"), cards.data?.thisYear),
  ];
  const moved = params.from !== month.from || params.to !== month.to || Boolean(params.batch || params.instructor || params.kind);

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("instructorPay.payments.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("instructorPay.payments.title") }]}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" href="/staff/offline-instructors">
              {t("instructorPay.payments.instructorsLink")}
            </Button>
            <Button onClick={() => setAdding(true)}>{t("instructorPay.payments.addInstructor")}</Button>
          </div>
        }
      />
      <p className="text-body text-muted">{t("instructorPay.payments.note")}</p>

      <InstructorFormDialog
        key={adding ? "open" : "closed"}
        instructor={null}
        open={adding}
        onOpenChange={setAdding}
        onSaved={(saved) => router.push(`/staff/offline-instructors/${saved.id}`)}
      />

      {cards.isError ? <ErrorState onRetry={() => void cards.refetch()} error={cards.error} /> : <SummaryStrip items={items} loading={cards.isPending} />}

      <div className="grid gap-3 rounded-card border border-border bg-card p-4 sm:grid-cols-2 lg:grid-cols-5">
        <Field label={t("instructorPay.filters.from")}>
          <TextInput type="date" value={params.from} onChange={(event) => setParams({ from: event.target.value })} />
        </Field>
        <Field label={t("instructorPay.filters.to")}>
          <TextInput type="date" value={params.to} onChange={(event) => setParams({ to: event.target.value })} />
        </Field>
        <Field label={t("instructorPay.filters.batch")}>
          <Select value={params.batch} onChange={(event) => setParams({ batch: event.target.value })}>
            <option value="">{t("instructorPay.filters.allBatches")}</option>
            {(batches.data ?? []).map((batch) => (
              <option key={batch.id} value={batch.id}>
                {locale === "bn" ? batch.nameBn || batch.nameEn : batch.nameEn || batch.nameBn}
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
        {moved ? (
          <div className="sm:col-span-2 lg:col-span-5">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setParams({ from: month.from, to: month.to, batch: "", instructor: "", kind: "" })}
            >
              {t("instructorPay.filters.clear")}
            </Button>
          </div>
        ) : null}
      </div>

      {payments.isError ? (
        <ErrorState onRetry={() => void payments.refetch()} error={payments.error} />
      ) : (
        <PaymentTable list={payments.data} loading={payments.isPending} filtered showInstructor />
      )}
    </div>
  );
}
