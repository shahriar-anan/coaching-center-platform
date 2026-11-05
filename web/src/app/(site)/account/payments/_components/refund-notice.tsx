"use client";

import { useLocale } from "@/i18n/locale-context";
import type { MyOrder } from "@/lib/api/exam/account";
import { formatDate, formatMoney } from "@/lib/format";

/**
 * The notice of a refunded order (plan 5.4): what came back and when, where the money goes, and whether access ended. The
 * reason and the staff name are never shown to the student. Renders nothing for an order without a refund.
 */
export function RefundNotice({ order }: { order: Pick<MyOrder, "status" | "refund" | "amount" | "paymentChannel"> }) {
  const { t, locale } = useLocale();
  const refund = order.refund;
  if (!refund) {
    return null;
  }
  const amount = formatMoney(refund.amount, locale) ?? "";
  const date = formatDate(refund.at, locale, { year: "auto" });
  const headline =
    order.status === "PARTLY_REFUNDED"
      ? t("account.payments.refund.partly", { amount, paid: formatMoney(order.amount, locale) ?? "", date })
      : t("account.payments.refund.full", { amount, date });
  const destination = order.paymentChannel
    ? t("account.payments.refund.backToChannel", { channel: order.paymentChannel })
    : t("account.payments.refund.backToAccount");
  return (
    <div className="grid gap-1 rounded-control border border-border bg-bg p-3 text-body">
      <p className="font-semibold text-text">{headline}</p>
      <p className="text-muted">{`${destination} ${t("account.payments.refund.arrival")}`}</p>
      {refund.accessRevoked ? <p className="text-muted">{t("account.payments.refund.accessEnded")}</p> : null}
    </div>
  );
}
