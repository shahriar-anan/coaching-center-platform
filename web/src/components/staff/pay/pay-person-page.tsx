"use client";

import Link from "next/link";
import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { CopyField } from "@/components/ui/copy-field";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Stat, StatRow } from "@/components/ui/stat";
import { Table, type Column } from "@/components/ui/table";
import {
  usePayDefaults,
  usePayLedger,
  usePayPayoutAccounts,
  usePayPerson,
  type PayLedgerRow,
  type PayPayoutAccount,
  type PaySummary,
} from "@/lib/api/exam/staff-pay";
import { formatDate, formatMoney } from "@/lib/format";
import { AdjustmentForm, PaymentForm, SettingsForm, VoidDialog } from "./pay-forms";
import { PersonSalaryPanel } from "./salary-panel";
import { isVoidable, kindKey, methodKey } from "./pay-format";
import { PersonMarkingPay } from "./marking-section";
import { MyExamsSection } from "./my-exams-section";
import { useSession } from "@/lib/auth/session-context";

type OpenForm = "payment" | "adjustment" | null;

/**
 * One person's pay (Master Admin): summary, payout accounts (read only), record a payment or an adjustment, the settings, and
 * the ledger with void. Nothing here writes a payout account.
 */
export function PayPersonPage({ userId }: { userId: string }) {
  const { t, locale } = useLocale();
  const person = usePayPerson(userId);
  const accounts = usePayPayoutAccounts(userId);
  const defaults = usePayDefaults();
  const [page, setPage] = useState(0);
  const ledger = usePayLedger(userId, page);
  const [openForm, setOpenForm] = useState<OpenForm>(null);
  const [voiding, setVoiding] = useState<PayLedgerRow | null>(null);

  const money = (value: number | null | undefined) => (value === null || value === undefined ? "" : formatMoney(value, locale) ?? "");
  const signed = (value: number) => (value < 0 ? `−${money(-value)}` : money(value));

  if (person.isError) {
    return <ErrorState onRetry={() => void person.refetch()} error={person.error} />;
  }
  if (!person.data) {
    return <Skeleton variant="rows" count={4} />;
  }
  const detail = person.data;
  const isInstructor = detail.role === "INSTRUCTOR";
  const payoutAccounts = accounts.data ?? [];

  const columns: Column<PayLedgerRow>[] = [
    {
      key: "date",
      header: t("staffPay.ledger.date"),
      nowrap: true,
      cell: (row) => formatDate(row.paidOn ?? row.dueOn, locale),
    },
    {
      key: "kind",
      header: t("staffPay.ledger.kind"),
      cell: (row) => (
        <span className="grid gap-0.5">
          <span>{t(kindKey(row.kind))}</span>
          {row.voided ? <Pill tone="muted">{t("staffPay.ledger.voided")}</Pill> : null}
        </span>
      ),
    },
    {
      key: "amount",
      header: t("staffPay.ledger.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => <span className={row.voided ? "text-muted line-through" : "tabular-nums"}>{signed(row.amount)}</span>,
    },
    {
      key: "method",
      header: t("staffPay.ledger.method"),
      mobileHidden: true,
      cell: (row) => (
        <span className="grid gap-0.5">
          {row.method ? <span>{t(methodKey(row.method) ?? "")}</span> : null}
          {row.accountSnapshot ? <span className="text-meta text-muted">{row.accountSnapshot}</span> : null}
        </span>
      ),
    },
    {
      key: "detail",
      header: t("staffPay.ledger.detail"),
      mobileHidden: true,
      cell: (row) =>
        row.voided
          ? `${t("staffPay.ledger.voidedBecause")}: ${row.voidReason ?? ""}`
          : [row.reference, row.note].filter(Boolean).join(" · "),
    },
    {
      key: "action",
      header: "",
      cell: (row) =>
        isVoidable(row) ? (
          <Button type="button" size="sm" variant="ghost" onClick={() => setVoiding(row)}>
            {t("staffPay.ledger.void")}
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="grid gap-6">
      <PageHeader
        title={detail.fullName || t("staffPay.people.unnamed")}
        actions={
          <Link href="/staff/pay" className="text-label text-accent underline-offset-4 hover:underline">
            {t("staffPay.person.back")}
          </Link>
        }
      />
      <p className="-mt-4 flex flex-wrap items-center gap-2 text-meta text-muted">
        <span>{t(`staffPay.role.${detail.role}`)}</span>
        {detail.deleted ? <Pill tone="muted">{t("staffPay.person.removed")}</Pill> : null}
        {detail.payNotSetUp ? <Pill tone="warn">{t("staffPay.people.payNotSetUp")}</Pill> : null}
      </p>

      <Card>
        <StatRow>
          <Stat value={money(detail.summary.charged)} label={t("staffPay.summary.charged")} />
          <Stat value={money(detail.summary.paid)} label={t("staffPay.summary.paid")} />
          <Stat value={signed(detail.summary.balance)} label={t("staffPay.summary.balance")} />
          <Stat value={money(detail.summary.overdue)} label={t("staffPay.summary.overdue")} />
        </StatRow>
        <SummaryNotes summary={detail.summary} lastPaidLabel={t("staffPay.summary.lastPaid")} />
      </Card>

      {isInstructor ? <PersonMarkingPay userId={userId} /> : null}
      {isInstructor ? <PersonExams userId={userId} /> : null}
      {detail.role === "SYSTEM_ADMIN" ? <PersonSalaryPanel userId={userId} /> : null}

      <Card>
        <h2 className="text-section text-text">{t("staffPay.person.accounts")}</h2>
        <p className="mt-1 text-meta text-muted">{t("staffPay.person.accountsHint")}</p>
        {accounts.isError ? (
          <p className="mt-3 text-meta text-danger">{t("staffPay.errors.generic")}</p>
        ) : payoutAccounts.length === 0 ? (
          <p className="mt-3 text-body text-muted">{t("staffPay.person.noAccounts")}</p>
        ) : (
          <ul className="mt-4 grid gap-4">
            {payoutAccounts.map((account) => (
              <li key={account.id} className="grid gap-2 rounded-card border border-border p-4">
                <AccountDetails account={account} />
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-section text-text">{t("staffPay.person.actions")}</h2>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant={openForm === "payment" ? "secondary" : "primary"}
              onClick={() => setOpenForm(openForm === "payment" ? null : "payment")}
            >
              {t("staffPay.person.recordPayment")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => setOpenForm(openForm === "adjustment" ? null : "adjustment")}
            >
              {t("staffPay.person.addAdjustment")}
            </Button>
          </div>
        </div>
        {openForm === "payment" ? (
          <div className="mt-5">
            <PaymentForm
              userId={userId}
              balance={detail.summary.balance}
              owedNow={detail.summary.owedNow}
              accounts={payoutAccounts}
              onDone={() => setOpenForm(null)}
            />
          </div>
        ) : null}
        {openForm === "adjustment" ? (
          <div className="mt-5">
            <AdjustmentForm userId={userId} onDone={() => setOpenForm(null)} />
          </div>
        ) : null}
      </Card>

      <Card>
        <h2 className="text-section text-text">{t("staffPay.settings.heading")}</h2>
        <div className="mt-4">
          <SettingsForm
            key={`${detail.id}-${detail.settings.effectiveSheetRate ?? ""}-${detail.settings.monthlySalary ?? ""}`}
            userId={userId}
            role={detail.role}
            settings={detail.settings}
            defaultRate={defaults.data?.sheetRate ?? null}
          />
        </div>
        {isInstructor ? <p className="mt-3 text-meta text-muted">{t("staffPay.settings.rateNow", { rate: money(detail.settings.effectiveSheetRate) })}</p> : null}
      </Card>

      <section className="grid gap-3" aria-label={t("staffPay.ledger.title")}>
        <h2 className="text-section text-text">{t("staffPay.ledger.title")}</h2>
        {ledger.isError ? (
          <ErrorState onRetry={() => void ledger.refetch()} error={ledger.error} />
        ) : !ledger.data ? (
          <Skeleton variant="rows" count={4} />
        ) : (
          <>
            <Table<PayLedgerRow>
              columns={columns}
              rows={ledger.data.content}
              rowKey={(row) => row.id}
              empty={<EmptyState title={t("staffPay.ledger.empty")} body={t("staffPay.ledger.emptyBody")} />}
            />
            <Pagination page={ledger.data.page} totalPages={ledger.data.totalPages} onPage={setPage} />
          </>
        )}
      </section>

      <VoidDialog row={voiding} onClose={() => setVoiding(null)} />
    </div>
  );
}

/**
 * The instructor's "My exams" table. The list is read from the signed-in user's own route, so it shows only when that is
 * the person on this page. The Master Admin viewing someone else sees a note instead; a per-person read is not built yet.
 */
function PersonExams({ userId }: { userId: string }) {
  const { t } = useLocale();
  const { session } = useSession();
  if (session.status === "authenticated" && session.user.id === userId) {
    return <MyExamsSection />;
  }
  return (
    <Card>
      <h2 className="text-section text-text">{t("staffPay.myExams.title")}</h2>
      <p className="mt-3 text-body text-text">{t("staffPay.myExams.notHereTitle")}</p>
      <p className="mt-1 text-meta text-muted">{t("staffPay.myExams.notHereBody")}</p>
    </Card>
  );
}

/** Earned and paid dates and the advance or owed wording, under the summary figures. */
function SummaryNotes({ summary, lastPaidLabel }: { summary: PaySummary; lastPaidLabel: string }) {
  const { t, locale } = useLocale();
  return (
    <p className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-meta text-muted">
      <span>
        {summary.balance < 0
          ? t("staffPay.summary.advance", { amount: formatMoney(-summary.balance, locale) ?? "" })
          : t("staffPay.summary.owed", { amount: formatMoney(summary.owedNow, locale) ?? "" })}
      </span>
      <span>
        {lastPaidLabel}: {summary.lastPaymentOn ? formatDate(summary.lastPaymentOn, locale) : t("staffPay.people.neverPaid")}
      </span>
    </p>
  );
}

/** One payout account, read only. The number is shown in full with a copy button; the owner and the Master Admin can see it. */
function AccountDetails({ account }: { account: PayPayoutAccount }) {
  const { t } = useLocale();
  const isBank = account.kind === "BANK";
  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-text">{t(`staffPay.method.${account.kind}`)}</span>
        {account.preferred ? <Pill tone="accent">{t("staffPay.person.preferred")}</Pill> : null}
      </div>
      {isBank ? (
        <>
          <span className="text-body text-text">{[account.accountName, account.bankName, account.branch].filter(Boolean).join(" · ")}</span>
          {account.accountNumber ? (
            <CopyField label={t("staffPay.person.accountNumber")} value={account.accountNumber} />
          ) : null}
          {account.routingNumber ? (
            <CopyField label={t("staffPay.person.routingNumber")} value={account.routingNumber} />
          ) : null}
        </>
      ) : account.walletNumber ? (
        <CopyField label={t("staffPay.person.walletNumber")} value={account.walletNumber} />
      ) : null}
    </>
  );
}
