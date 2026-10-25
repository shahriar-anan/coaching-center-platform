// Stand-in for PUR-FND's ProductCard. Delete at the switch to @/components/ui/purchase.
import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { formatMoney, formatNumber } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";

export function ProductCard({
  href,
  title,
  subtitle,
  pills,
  price,
  examCount,
  footer,
}: {
  href: string;
  title: string;
  subtitle?: string;
  pills?: ReactNode;
  price: number | null;
  examCount: number;
  footer?: ReactNode;
}) {
  const { t, locale } = useLocale();
  return (
    <Card href={href}>
      <p className="text-card font-bold">{title}</p>
      {subtitle ? <p className="text-body text-muted">{subtitle}</p> : null}
      {pills}
      <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-body">
        <span className="text-muted">{t("purchase.shared.examCount", { n: formatNumber(examCount, locale) })}</span>
        <span className="font-semibold">
          {price === null ? t("purchase.shared.priceNotSet") : formatMoney(price, locale)}
        </span>
      </p>
      {footer}
    </Card>
  );
}
