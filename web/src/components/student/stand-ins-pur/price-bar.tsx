// Stand-in for PUR-FND's PriceBar. Delete at the switch to @/components/ui/purchase.
import type { ReactNode } from "react";
import { formatMoney } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";

export function PriceBar({
  price,
  priceNote,
  status,
  action,
  secondary,
}: {
  price: number | null;
  priceNote?: string;
  status?: ReactNode;
  action?: ReactNode;
  secondary?: ReactNode;
}) {
  const { t, locale } = useLocale();
  return (
    <div className="grid gap-2">
      <p className="text-card font-bold">
        {price === null ? t("purchase.shared.priceNotSet") : formatMoney(price, locale)}
      </p>
      {priceNote ? <p className="text-meta text-muted">{priceNote}</p> : null}
      {status}
      {action}
      {secondary}
    </div>
  );
}
