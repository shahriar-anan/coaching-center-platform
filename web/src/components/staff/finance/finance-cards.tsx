"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { ArrowDown, ArrowRight, ArrowUp } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { Card } from "@/components/ui/card";
import { formatMoney, formatNumber } from "@/lib/format";
import type { FinanceChange, FinanceSummary } from "@/lib/api/exam/finance";
import { changeParts } from "@/components/staff/finance/finance-model";
import { cn } from "@/components/ui/cn";

// The top row (plan 8.4): income, expenditure and net for the period, each with its change against the period before. The figures
// link to the entries behind them. Net is green when positive and red when negative, and says which with a word as well.

type Tone = "neutral" | "ok" | "danger";

function netTone(net: number): Tone {
  if (net > 0) {
    return "ok";
  }
  return net < 0 ? "danger" : "neutral";
}

function ChangeLine({ percent }: { percent: number | null }) {
  const { t, locale } = useLocale();
  const change = changeParts(percent);
  if (change.direction === "none") {
    return <p className="text-meta text-muted">{t("finance.cards.noChange")}</p>;
  }
  const Icon = change.direction === "up" ? ArrowUp : change.direction === "down" ? ArrowDown : ArrowRight;
  return (
    <p className="flex items-center gap-1.5 text-meta text-muted tabular-nums">
      <Icon aria-hidden="true" className="size-4 shrink-0" />
      <span>
        {t("finance.cards.change", { percent: formatNumber(change.size, locale, { decimals: 1 }) })}
      </span>
    </p>
  );
}

function FigureCard({
  label,
  value,
  tone,
  previous,
  percent,
  href,
  children,
}: {
  label: string;
  value: number;
  tone: Tone;
  previous: number;
  percent: number | null;
  href: string | null;
  children?: ReactNode;
}) {
  const { t, locale } = useLocale();
  const colour = tone === "ok" ? "text-ok-text" : tone === "danger" ? "text-danger" : "text-text";
  return (
    <Card padding="lg" className="grid content-start gap-2">
      <h2 className="text-meta font-medium text-muted">{label}</h2>
      <p className={cn("text-title font-bold tabular-nums tracking-tight", colour)}>{formatMoney(value, locale)}</p>
      {children}
      <ChangeLine percent={percent} />
      <p className="text-meta tabular-nums text-muted">{t("finance.cards.before", { amount: formatMoney(previous, locale) ?? "" })}</p>
      {href ? (
        <Link href={href} className="text-meta font-medium text-text-accent underline-offset-2 hover:underline">
          {t("finance.cards.seeEntries")}
        </Link>
      ) : null}
    </Card>
  );
}

export function FinanceCards({
  summary,
  hrefs,
}: {
  summary: FinanceSummary;
  /** Where each figure's entries are; null when no source of that side is on. */
  hrefs: { income: string | null; expense: string | null; net: string | null };
}) {
  const { t } = useLocale();
  const change: FinanceChange = summary.change;
  const netPart = summary.net;
  return (
    <div className="grid gap-4 md:grid-cols-3">
      <FigureCard
        label={t("finance.cards.income")}
        value={summary.income}
        tone="neutral"
        previous={summary.previous.income}
        percent={change.income}
        href={hrefs.income}
      />
      <FigureCard
        label={t("finance.cards.expense")}
        value={summary.expense}
        tone="neutral"
        previous={summary.previous.expense}
        percent={change.expense}
        href={hrefs.expense}
      />
      <FigureCard
        label={t("finance.cards.net")}
        value={netPart}
        tone={netTone(netPart)}
        previous={summary.previous.net}
        percent={change.net}
        href={hrefs.net}
      >
        <p className="text-meta text-muted">
          {netPart > 0 ? t("finance.cards.netPositive") : netPart < 0 ? t("finance.cards.netNegative") : null}
        </p>
      </FigureCard>
    </div>
  );
}
