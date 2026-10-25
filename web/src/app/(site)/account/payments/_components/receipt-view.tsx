"use client";

import { Button } from "@/components/ui/button";
import { PrintArea } from "@/components/ui/print-area";
import { Card } from "@/components/ui/card";
import { Pill, type PillTone } from "@/components/ui/pill";
import { useLocale } from "@/i18n/locale-context";
import type { MyOrder, PaymentStatus } from "@/lib/api/exam/account";
import { formatDateTime, formatMoney, pickTitle } from "@/lib/format";

const STATUS_TONE: Record<PaymentStatus, PillTone> = {
  PENDING: "warn",
  PAID: "ok",
  FAILED: "danger",
  CANCELLED: "muted",
};

/** A printable receipt for one payment. Print styles hide the buttons; the page keeps only this card. */
export function ReceiptView({ order, studentName }: { order: MyOrder; studentName: string }) {
  const { t, locale } = useLocale();
  const item =
    order.scope === "MODULE"
      ? pickTitle({ titleEn: order.moduleTitleEn ?? "", titleBn: order.moduleTitleBn }, locale)
      : pickTitle({ titleEn: order.examTitleEn ?? "", titleBn: order.examTitleBn }, locale);
  const statusWord = {
    PENDING: t("account.payments.statusPending"),
    PAID: t("account.payments.statusPaid"),
    FAILED: t("account.payments.statusFailed"),
    CANCELLED: t("account.payments.statusCancelled"),
  }[order.status];

  const rows: [string, string][] = [
    [t("account.receipt.student"), studentName],
    [t("account.receipt.item"), item],
    [t("account.receipt.amount"), formatMoney(order.amount, locale) ?? ""],
    [t("account.receipt.method"), order.paymentChannel ?? "—"],
    [t("account.receipt.tran"), order.tranId ?? "—"],
    [t("account.receipt.date"), formatDateTime(order.createdAt, locale)],
  ];

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Button variant="secondary" href="/account/payments">{t("account.receipt.back")}</Button>
        <Button onClick={() => window.print()}>{t("account.receipt.print")}</Button>
      </div>
      <PrintArea>
        <Card padding="lg" className="grid gap-4">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <p className="text-title font-bold">{t("account.receipt.site")}</p>
              <p className="text-meta text-muted">{t("account.receipt.title")}</p>
            </div>
            <Pill tone={STATUS_TONE[order.status]}>{statusWord}</Pill>
          </div>
          <dl className="grid gap-3">
            {rows.map(([label, value]) => (
              <div key={label} className="flex flex-wrap items-baseline justify-between gap-2 border-b border-border pb-2">
                <dt className="text-meta text-muted">{label}</dt>
                <dd className="text-body font-semibold text-text tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
        </Card>
      </PrintArea>
    </div>
  );
}
