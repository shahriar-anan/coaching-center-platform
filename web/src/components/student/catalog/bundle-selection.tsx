"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/errors";
import { useBundleOrder } from "@/lib/api/exam/purchase";
import { useLocale } from "@/i18n/locale-context";
import { formatMoney, formatNumber } from "@/lib/format";
import { SelectableExamList } from "@/components/ui/purchase/selectable-exam-list";
import { remember } from "@/components/student/purchase/purchase-sheet";
import { ReceiptEmailField } from "@/components/student/purchase/receipt-email-field";
import { isEmail } from "@/app/(auth)/_components/validate";
import { useSession } from "@/lib/auth/session-context";

export type BundleItem = {
  id: string;
  title: string;
  meta: string;
  price: number | null;
  owned: boolean;
};

/**
 * The total of the chosen exams, or null when any chosen exam has no price (plan decision D1:
 * the total is not shown then, rather than a wrong one).
 */
export function bundleTotal(prices: (number | null)[]): number | null {
  if (prices.length === 0) return 0;
  if (prices.some((price) => price === null)) return null;
  return prices.reduce<number>((sum, price) => sum + (price ?? 0), 0);
}

/** Several exams in one payment, behind the `bundleOrders` flag. Owned exams cannot be chosen. */
export function BundleSelection({ items, disabled }: { items: BundleItem[]; disabled: boolean }) {
  const { t, locale } = useLocale();
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const order = useBundleOrder();
  // The receipt address for this payment only (Phase 4B), asked the same way as on the purchase sheet.
  const { session } = useSession();
  const verified = session.status === "authenticated" && Boolean(session.user.emailVerifiedAt);
  const [receiptEmail, setReceiptEmail] = useState("");
  const [receiptError, setReceiptError] = useState<string | null>(null);

  const rows = items.map((item) => ({
    id: item.id,
    title: item.title,
    meta: <span className="text-meta text-muted">{item.meta}</span>,
    price: item.price,
    disabledReason: item.owned ? t("purchase.shared.owned") : undefined,
  }));

  const chosen = items.filter((item) => selected.includes(item.id) && !item.owned);
  const total = bundleTotal(chosen.map((item) => item.price));

  function summary(count: number): string {
    const countText = t("purchase.shared.summaryCount", { n: formatNumber(count, locale) });
    if (total === null) return `${countText} · ${t("purchase.shared.summaryNoTotal")}`;
    return `${countText} · ${t("purchase.shared.summaryTotal", { total: formatMoney(total, locale) ?? "" })}`;
  }

  async function buy() {
    setError(null);
    // The address is checked first, so an invalid one sends no request.
    const typed = receiptEmail.trim();
    if (typed && !isEmail(typed)) {
      setReceiptError(t("purchase.receipt.invalid"));
      document.getElementById("receipt-email")?.focus();
      return;
    }
    setReceiptError(null);
    try {
      const ids = chosen.map((item) => item.id);
      const result = await order.mutateAsync({ examIds: ids, receiptEmail: typed || undefined });
      if (!result.redirectUrl) {
        setError(t("purchase.errors.generic"));
        return;
      }
      // The result page reads these details to show the title, amount and the way back.
      remember(result.tranId, { scope: "BUNDLE", examId: null, moduleId: null, door: null, title: t("purchase.bundleTitle", { count: ids.length }), amount: result.amount, orderId: result.orderId });
      window.location.assign(result.redirectUrl);
    } catch (caught) {
      if (caught instanceof ApiError && caught.fieldErrors?.some((detail) => detail.field === "receiptEmail")) {
        setReceiptError(t("purchase.receipt.invalid"));
        document.getElementById("receipt-email")?.focus();
        return;
      }
      setError(
        caught instanceof ApiError && caught.code === "ALREADY_PURCHASED"
          ? t("purchase.shared.alreadyPurchasedBundle")
          : t("purchase.errors.generic"),
      );
    }
  }

  return (
    <div className="grid gap-3">
      <SelectableExamList
        items={rows}
        selected={selected}
        onSelectedChange={setSelected}
        summaryLabel={summary}
        action={
          <Button disabled={disabled || chosen.length === 0 || order.isPending} loading={order.isPending} onClick={() => void buy()}>
            {t("purchase.shared.buyChosen")}
          </Button>
        }
      />
      <ReceiptEmailField
        value={receiptEmail}
        error={receiptError ?? undefined}
        verified={verified}
        onChange={(value) => {
          setReceiptEmail(value);
          setReceiptError(null);
        }}
      />
      {error ? <p role="alert" className="text-body text-danger">{error}</p> : null}
    </div>
  );
}
