"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { ListCard } from "@/components/ui/list-card";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useDashboard } from "@/lib/api/accounting/reports";
import type { Dashboard, Entry } from "@/lib/api/accounting/types";
import { ACCOUNTING_HOME, AccountingFrame } from "./accounting-frame";
import { BalanceText } from "./balance-text";
import { MonthChart } from "./month-chart";
import { money, pickName } from "./accounting-model";

/**
 * The accounting home (accounting-plan 4.6, B15): what is owed now, what went out and came in this month, a row per group, the payees
 * owed the most, the latest entries, and the twelve months. No budgets.
 */
export function AccountingHome() {
  const { t, locale } = useLocale();
  const dashboard = useDashboard();
  return (
    <AccountingFrame title={t("accounting.title")}>
      {dashboard.isError ? (
        <ErrorState onRetry={() => void dashboard.refetch()} error={dashboard.error} />
      ) : dashboard.isPending ? (
        <Skeleton variant="card" count={3} />
      ) : (
        <HomeBody data={dashboard.data} locale={locale} t={t} />
      )}
    </AccountingFrame>
  );
}

type Translate = (key: string, params?: Record<string, string | number>) => string;

function HomeBody({ data, locale, t }: { data: Dashboard; locale: "en" | "bn"; t: Translate }) {
  const { formatDate } = useFormat();
  return (
    <div className="grid gap-6">
      <section className="grid gap-3 sm:grid-cols-3" aria-label={t("accounting.home.cards")}>
        <Card label={t("accounting.home.owedNow")}>
          <BalanceText balance={data.owed} large />
          <span className="text-meta text-muted">{t("accounting.home.advanceUnder", { amount: money(data.advance, locale) })}</span>
        </Card>
        <Card label={t("accounting.home.spentThisMonth")}>
          <span className="text-title text-text">{money(data.spentThisMonth, locale)}</span>
          <span className="text-meta text-muted">{t("accounting.home.lastMonth", { amount: money(data.spentLastMonth, locale) })}</span>
        </Card>
        <Card label={t("accounting.home.incomeThisMonth")}>
          <span className="text-title text-text">{money(data.incomeThisMonth, locale)}</span>
        </Card>
      </section>

      <section className="grid gap-3" aria-labelledby="acc-home-groups">
        <h2 id="acc-home-groups" className="text-section font-semibold text-text">
          {t("accounting.home.groups")}
        </h2>
        {data.groups.length === 0 ? (
          <EmptyState title={t("accounting.settings.groups.empty")} />
        ) : (
          <ListCard>
            {data.groups.map((group) => (
              <li key={group.groupId}>
                <Link href={`${ACCOUNTING_HOME}/groups/${group.groupId}`} className="flex min-h-12 flex-wrap items-center justify-between gap-2 p-4 text-text hover:bg-bg">
                  <span className="font-medium">{pickName(group, locale)}</span>
                  <span className="text-body">
                    {group.mode === "PAYEES" ? (
                      <BalanceText balance={group.owed} />
                    ) : (
                      <span>{t("accounting.log.thisMonthShort", { amount: money(group.thisMonth, locale) })}</span>
                    )}
                  </span>
                </Link>
              </li>
            ))}
          </ListCard>
        )}
      </section>

      <section className="grid gap-3 lg:grid-cols-2">
        <div className="grid gap-3">
          <h2 className="text-section font-semibold text-text">{t("accounting.home.owedMost")}</h2>
          {data.topOwed.length === 0 ? (
            <EmptyState title={t("accounting.home.noOwed")} />
          ) : (
            <ListCard>
              {data.topOwed.map((row) => (
                <li key={row.id}>
                  <Link href={`/staff/accounting/payees/${row.id}`} className="flex min-h-12 flex-wrap items-center justify-between gap-2 p-4 text-text hover:bg-bg">
                    <span className="font-medium">{row.name}</span>
                    <BalanceText balance={row.balance} />
                  </Link>
                </li>
              ))}
            </ListCard>
          )}
        </div>
        <div className="grid gap-3">
          <h2 className="text-section font-semibold text-text">{t("accounting.home.latest")}</h2>
          {data.latest.length === 0 ? (
            <EmptyState title={t("accounting.entries.empty")} />
          ) : (
            <ListCard>
              {data.latest.map((entry) => (
                <li key={entry.id}>
                  <EntryRow entry={entry} formatDate={formatDate} />
                </li>
              ))}
            </ListCard>
          )}
        </div>
      </section>

      <section className="grid gap-3">
        <h2 className="text-section font-semibold text-text">{t("accounting.home.twelveMonths")}</h2>
        <MonthChart months={data.months} />
      </section>
    </div>
  );
}

function Card({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-2 rounded-card border border-border bg-card p-4 shadow-[var(--card-shadow)]">
      <span className="text-meta text-muted">{label}</span>
      {children}
    </div>
  );
}

/** One entry on the home page: its number, date, what it is for, and its amount. */
function EntryRow({ entry, formatDate }: { entry: Entry; formatDate: (value: string) => string }) {
  const { t, locale } = useLocale();
  const what = entry.payeeName ?? entry.counterparty ?? entry.typeNameEn ?? "";
  return (
    <Link href={`${ACCOUNTING_HOME}/entries/${entry.id}`} className="flex min-h-12 flex-wrap items-center justify-between gap-2 p-4 text-text hover:bg-bg">
      <span className="grid gap-0.5">
        <span className="font-medium">
          #{entry.no} · {t(`accounting.kind.${entry.kind}`)}
          {entry.status === "VOIDED" ? <Pill tone="danger"> {t("accounting.entries.voided")}</Pill> : null}
        </span>
        <span className="text-meta text-muted">
          {formatDate(entry.date)}
          {what ? ` · ${what}` : ""}
        </span>
      </span>
      <span className="text-body">{money(entry.amount, locale)}</span>
    </Link>
  );
}
