"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import type { UseQueryResult } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useGroups } from "@/lib/api/accounting/groups";
import { useEntries } from "@/lib/api/accounting/entries";
import { usePayee, usePayeeAction } from "@/lib/api/accounting/payees";
import { ACC_GROUP_IDS } from "@/lib/api/accounting/ids";
import type { Entry, EntryPage, Group, Payee } from "@/lib/api/accounting/types";
import { ACCOUNTING_HOME, AccountingFrame } from "./accounting-frame";
import { BalanceText } from "./balance-text";
import { PayeeForm } from "./payees/payee-form";
import { QuickAddButton } from "./entries/quick-add";
import { accountLabel, money, pickName, unitWithCount, writeErrorText } from "./accounting-model";

/** The effect of an entry on the balance: a charge adds (a deduction is stored negative), a payment takes away, a voided entry does nothing. */
export function balanceEffect(entry: Pick<Entry, "kind" | "status" | "amount">): number {
  if (entry.status !== "ACTIVE") {
    return 0;
  }
  if (entry.kind === "CHARGE") {
    return Number(entry.amount);
  }
  return entry.kind === "PAYMENT" ? -Number(entry.amount) : 0;
}

/**
 * The balance after each entry, newest first, counted back from the payee's balance now. Only exact when every entry is loaded, so the
 * caller passes the entries of the whole ledger (the page reads up to 200).
 */
export function runningBalances(balance: string, entries: Entry[]): string[] {
  const out: string[] = [];
  let running = Number(balance);
  for (const entry of entries) {
    out.push(running.toFixed(2));
    running -= balanceEffect(entry);
  }
  return out;
}

const TAB_VALUES = ["entries", "details"] as const;
type PayeeTab = (typeof TAB_VALUES)[number];

/** One payee (accounting-plan 4.6): the balance and totals, the buttons, the Entries tab with a running balance, and the details. */
export function PayeePage({ payeeId }: { payeeId: string }) {
  const { t, locale } = useLocale();
  const payee = usePayee(payeeId);
  const groups = useGroups(true);
  const group = (groups.data ?? []).find((item) => item.id === payee.data?.groupId) ?? null;
  const title = payee.data?.name ?? t("accounting.title");

  return (
    <AccountingFrame
      title={title}
      breadcrumbs={
        payee.data && group ? [{ label: pickName(group, locale), href: `${ACCOUNTING_HOME}/groups/${group.id}` }, { label: payee.data.name }] : []
      }
    >
      {payee.isError ? (
        <ErrorState onRetry={() => void payee.refetch()} error={payee.error} />
      ) : payee.isPending || groups.isPending ? (
        <Skeleton variant="card" count={2} />
      ) : !payee.data ? (
        <EmptyState title={t("accounting.common.notFound")} />
      ) : (
        <PayeeBody payee={payee.data} group={group} />
      )}
    </AccountingFrame>
  );
}

function PayeeBody({ payee, group }: { payee: Payee; group: Group | null }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const { toast } = useToast();
  const [tab, setTab] = useState<PayeeTab>("entries");
  const [editing, setEditing] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const action = usePayeeAction();
  const entries = useEntries({ payeeId: payee.id, size: 200 }, tab === "entries");
  const isInstructors = payee.groupId === ACC_GROUP_IDS.OFFLINE_INSTRUCTORS;
  const groupName = group ? pickName(group, locale) : "";

  async function run(verb: "archive" | "restore") {
    setActionError(null);
    try {
      await action.mutateAsync({ id: payee.id, action: verb });
      setArchiving(false);
      toast({ title: t(verb === "archive" ? "accounting.common.archived" : "accounting.payees.restored"), tone: "ok" });
    } catch (error) {
      setArchiving(false);
      setActionError(writeErrorText(error, t));
    }
  }

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-body text-muted">{groupName}</span>
        {payee.archived ? <Pill tone="warn">{t("accounting.common.archived")}</Pill> : null}
        {payee.userId ? <Pill tone="accent">{t("accounting.payees.linked")}</Pill> : null}
      </div>

      <section className="grid gap-3 rounded-card border border-border bg-card p-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="grid gap-1 sm:col-span-2 lg:col-span-1">
          <span className="text-meta text-muted">{t("accounting.payee.balance")}</span>
          <BalanceText balance={payee.balance} large />
        </div>
        <Figure label={t("accounting.payee.totalCharged")} value={money(payee.totalCharged, locale)} />
        <Figure label={t("accounting.payee.totalPaid")} value={money(payee.totalPaid, locale)} />
        <Figure label={t("accounting.payee.paidThisMonth")} value={money(payee.paidThisMonth, locale)} />
        <Figure
          label={t("accounting.payee.lastPayment")}
          value={payee.lastPaymentOn ? `${formatDate(payee.lastPaymentOn)} · ${money(payee.lastPaymentAmount, locale)}` : t("accounting.payees.noPaymentYet")}
        />
        <Figure label={t("accounting.payee.payments")} value={String(payee.paymentsCount)} />
        {isInstructors && payee.batches.length > 0 ? (
          <div className="grid gap-1 sm:col-span-2 lg:col-span-2">
            <span className="text-meta text-muted">{t("accounting.payee.batches")}</span>
            <div className="flex flex-wrap gap-1">
              {payee.batches.map((batch) => (
                <Pill key={batch.id} tone="muted">
                  {pickName(batch, locale)}
                </Pill>
              ))}
            </div>
          </div>
        ) : null}
      </section>

      <div className="flex flex-wrap gap-2">
        {group ? <QuickAddButton kind="CHARGE" group={group} payee={payee} label={t("accounting.payee.addCharge")} /> : null}
        {group ? <QuickAddButton kind="PAYMENT" group={group} payee={payee} label={t("accounting.payee.recordPayment")} /> : null}
        <Button variant="secondary" href={`/staff/accounting/payees/${payee.id}/statement`}>
          {t("accounting.payee.statement")}
        </Button>
        <Button variant="secondary" onClick={() => setEditing(true)}>
          {t("accounting.common.edit")}
        </Button>
        {payee.archived ? (
          <Button variant="secondary" onClick={() => void run("restore")}>
            {t("accounting.common.restore")}
          </Button>
        ) : (
          <Button variant="ghost" onClick={() => setArchiving(true)}>
            {t("accounting.common.archive")}
          </Button>
        )}
      </div>
      {actionError ? <p role="alert" className="text-meta text-danger">{actionError}</p> : null}

      <Tabs
        ariaLabel={t("accounting.payee.tabsLabel")}
        tabs={[
          { value: "entries", label: t("accounting.payee.tabs.entries") },
          { value: "details", label: t("accounting.payee.tabs.details") },
        ]}
        value={tab}
        onChange={(value) => setTab(value as PayeeTab)}
      />

      {tab === "entries" ? (
        <EntriesTab payee={payee} entries={entries} />
      ) : (
        <section className="grid gap-4 rounded-card border border-border bg-card p-4">
          <dl className="grid gap-3 sm:grid-cols-2">
            <Detail label={t("accounting.payee.details.phone")} value={payee.phone ?? t("accounting.payee.details.none")} />
            <Detail label={t("accounting.payee.details.note")} value={payee.note ?? t("accounting.payee.details.none")} />
            {isInstructors ? (
              <Detail label={t("accounting.payee.details.aliases")} value={payee.aliases.length ? payee.aliases.join(", ") : t("accounting.payee.details.none")} />
            ) : null}
            <Detail
              label={t("accounting.payee.details.linked")}
              value={payee.userName ?? t("accounting.payee.details.notLinked")}
            />
          </dl>
          {payee.userId ? (
            <Link href={`/staff/pay/${payee.userId}`} className="text-label text-accent-text underline-offset-4 hover:underline">
              {t("accounting.payee.alsoOnStaffPay", { amount: money(payee.staffPayBalance ?? "0.00", locale) })}
            </Link>
          ) : null}
        </section>
      )}

      {editing && group ? <PayeeForm group={group} payee={payee} onClose={() => setEditing(false)} /> : null}

      <ConfirmDialog
        open={archiving}
        onOpenChange={(open) => !open && setArchiving(false)}
        title={t("accounting.payee.archiveTitle", { name: payee.name })}
        body={archiveBody(payee, t, locale)}
        confirmLabel={t("accounting.common.archive")}
        tone="danger"
        onConfirm={async () => {
          await run("archive");
        }}
      />
    </div>
  );
}

/** The archive warning (accounting-plan 4.3): a balance that is not zero is said out loud before the person archives. */
function archiveBody(payee: Payee, t: (key: string, params?: Record<string, string | number>) => string, locale: "en" | "bn"): string {
  const side = Number(payee.balance);
  if (side > 0) {
    return t("accounting.payee.archiveOwed", { name: payee.name, amount: money(payee.balance, locale) });
  }
  if (side < 0) {
    return t("accounting.payee.archiveAdvance", { name: payee.name, amount: money(String(Math.abs(side).toFixed(2)), locale) });
  }
  return t("accounting.payee.archiveBody");
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1">
      <span className="text-meta text-muted">{label}</span>
      <span className="text-body text-text">{value}</span>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-1">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="text-body text-text">{value}</dd>
    </div>
  );
}

/** The payee's entries, newest first, with the balance after each. The balance column is shown only when every entry is loaded. */
function EntriesTab({
  payee,
  entries,
}: {
  payee: Payee;
  entries: UseQueryResult<EntryPage>;
}) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const items = entries.data?.items ?? [];
  const complete = (entries.data?.total ?? 0) <= items.length;
  const balances = runningBalances(payee.balance, items);

  if (entries.isError) {
    return <ErrorState onRetry={() => void entries.refetch()} error={entries.error} />;
  }
  const columns: Column<Entry>[] = [
    { key: "date", header: t("accounting.entries.columns.date"), mobileLabel: t("accounting.entries.columns.date"), cell: (row) => formatDate(row.date) },
    { key: "no", header: t("accounting.entries.columns.no"), mobileLabel: t("accounting.entries.columns.no"), cell: (row) => `#${row.no}` },
    {
      key: "kind",
      header: t("accounting.entries.columns.kind"),
      mobileLabel: t("accounting.entries.columns.kind"),
      cell: (row) => (
        <span>
          {t(`accounting.kind.${row.kind}`)}
          {row.status === "VOIDED" ? <Pill tone="danger">{t("accounting.entries.voided")}</Pill> : null}
        </span>
      ),
    },
    {
      key: "what",
      header: t("accounting.entries.columns.what"),
      mobileLabel: t("accounting.entries.columns.what"),
      cell: (row) => <EntryWhat entry={row} />,
    },
    {
      key: "amount",
      header: t("accounting.entries.columns.amount"),
      mobileLabel: t("accounting.entries.columns.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => (row.kind === "PAYMENT" ? `−${money(row.amount, locale)}` : money(row.amount, locale)),
    },
    {
      key: "account",
      header: t("accounting.entries.columns.account"),
      mobileLabel: t("accounting.entries.columns.account"),
      cell: (row) => (row.accountName ? accountLabel({ name: row.accountName, kind: null }, locale, t) : "—"),
    },
    {
      key: "balance",
      header: t("accounting.payee.balanceAfter"),
      mobileLabel: t("accounting.payee.balanceAfter"),
      align: "end",
      nowrap: true,
      cell: (row) => {
        const index = items.indexOf(row);
        return complete && index >= 0 ? money(balances[index], locale) : "—";
      },
    },
  ];
  return (
    <Table
      columns={columns}
      rows={items}
      rowKey={(row) => row.id}
      loading={entries.isPending}
      empty={<EmptyState title={t("accounting.payee.noEntries")} />}
    />
  );
}

/** What an entry is for: a counted line reads "40 answer sheets × ৳50", otherwise its type and description. */
function EntryWhat({ entry }: { entry: Entry }) {
  const { t, locale } = useLocale();
  const type = entry.typeNameEn ? pickName({ nameEn: entry.typeNameEn, nameBn: entry.typeNameBn }, locale) : null;
  const unit = entry.unitEn || entry.unitBn ? pickName({ nameEn: entry.unitEn ?? "", nameBn: entry.unitBn }, locale) : "";
  if (entry.kind === "PAYMENT") {
    return <span>{entry.reference ? `${t("accounting.entries.reference")}: ${entry.reference}` : t("accounting.kind.PAYMENT")}</span>;
  }
  return (
    <span className="grid gap-0.5">
      <span>{type ?? t(`accounting.kind.${entry.kind}`)}</span>
      {entry.quantity && entry.unitPrice ? (
        <span className="text-meta text-muted">
          {`${entry.quantity} ${unitWithCount(unit, entry.quantity, locale)} × ${money(entry.unitPrice, locale)}`}
        </span>
      ) : null}
      {entry.description ? <span className="text-meta text-muted">{entry.description}</span> : null}
    </span>
  );
}

