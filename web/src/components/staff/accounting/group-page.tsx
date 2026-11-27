"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Pagination } from "@/components/ui/pagination";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useGroupTypes } from "@/lib/api/accounting/account-types";
import { useAccounts } from "@/lib/api/accounting/accounts";
import { useGroups } from "@/lib/api/accounting/groups";
import { useEntries } from "@/lib/api/accounting/entries";
import { useGroupPayees } from "@/lib/api/accounting/payees";
import type { AccountKind, Entry, EntryKind, Group, PayeeRow } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { AccountingFrame } from "./accounting-frame";
import { QuickAddSheet } from "./entries/quick-add";
import { CopyMonthSheet } from "./entries/copy-month";
import { SeveralSheet } from "./entries/several";
import { PayeeForm } from "./payees/payee-form";
import { BalanceText } from "./balance-text";
import { accountLabel, money, pickName } from "./accounting-model";

/** The group page (accounting-plan 4.6): people with balances for a Payees group, the entries with filters for a Log group. */
export function GroupPage({ groupId }: { groupId: string }) {
  const { t, locale } = useLocale();
  const groups = useGroups(true);
  const group = (groups.data ?? []).find((item) => item.id === groupId) ?? null;

  return (
    <AccountingFrame title={group ? pickName(group, locale) : t("accounting.title")} breadcrumbs={group ? [{ label: pickName(group, locale) }] : []}>
      {groups.isError ? (
        <ErrorState onRetry={() => void groups.refetch()} error={groups.error} />
      ) : groups.isPending ? (
        <Skeleton variant="rows" count={4} />
      ) : !group ? (
        <EmptyState title={t("accounting.common.notFound")} />
      ) : group.mode === "PAYEES" ? (
        <PayeesPanel group={group} />
      ) : (
        <LogPanel group={group} />
      )}
    </AccountingFrame>
  );
}

function Totals({ items }: { items: { label: string; value: string; tone?: "owed" | "advance" | "muted" }[] }) {
  return (
    <dl className="grid gap-3 sm:grid-cols-3">
      {items.map((item) => (
        <div key={item.label} className="rounded-card border border-border bg-card p-4">
          <dt className="text-meta text-muted">{item.label}</dt>
          <dd
            className={
              item.tone === "owed" ? "text-title text-warn" : item.tone === "advance" ? "text-title text-accent-text" : "text-title text-text"
            }
          >
            {item.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/** A Payees group: the people with their balances, search, and the bulk actions (plan 4.6, B6, B7). */
function PayeesPanel({ group }: { group: Group }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const [query, setQuery] = useState("");
  const [showArchived, setShowArchived] = useState(false);
  const payees = useGroupPayees(group.id, { q: query, archived: showArchived });
  const [adding, setAdding] = useState(false);
  const [several, setSeveral] = useState(false);
  const [copying, setCopying] = useState(false);
  const [quickAdd, setQuickAdd] = useState(false);

  const columns: Column<PayeeRow>[] = [
    {
      key: "name",
      header: t("accounting.payees.columns.name"),
      mobileLabel: t("accounting.payees.columns.name"),
      cell: (row) => (
        <Link href={`/staff/accounting/payees/${row.id}`} prefetch className="font-medium text-text underline-offset-4 hover:underline">
          {row.name}
          {row.archived ? <span className="ms-2 text-meta text-muted">{t("accounting.common.archived")}</span> : null}
        </Link>
      ),
    },
    {
      key: "balance",
      header: t("accounting.payees.columns.balance"),
      mobileLabel: t("accounting.payees.columns.balance"),
      align: "end",
      nowrap: true,
      cell: (row) => <BalanceText balance={row.balance} />,
    },
    {
      key: "last",
      header: t("accounting.payees.columns.lastPayment"),
      mobileLabel: t("accounting.payees.columns.lastPayment"),
      cell: (row) =>
        row.lastPaymentOn ? (
          <span className="text-body">
            {formatDate(row.lastPaymentOn)} · {money(row.lastPaymentAmount, locale)}
          </span>
        ) : (
          <span className="text-muted">{t("accounting.payees.noPaymentYet")}</span>
        ),
    },
    {
      key: "month",
      header: t("accounting.payees.columns.paidThisMonth"),
      mobileLabel: t("accounting.payees.columns.paidThisMonth"),
      align: "end",
      cell: (row) => money(row.paidThisMonth, locale),
    },
  ];

  return (
    <div className="grid gap-5">
      <Totals
        items={[
          { label: t("accounting.balance.owedAll"), value: money(group.owed, locale), tone: "owed" },
          { label: t("accounting.balance.advanceAll"), value: money(group.advance, locale), tone: "advance" },
          { label: t("accounting.payees.thisMonth"), value: money(group.thisMonth, locale), tone: "muted" },
        ]}
      />
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => setAdding(true)}>{t("accounting.payees.add")}</Button>
        <Button variant="secondary" onClick={() => setSeveral(true)}>
          {t("accounting.several.button")}
        </Button>
        <Button variant="secondary" onClick={() => setCopying(true)}>
          {t("accounting.copy.button")}
        </Button>
        <Button variant="secondary" onClick={() => setQuickAdd(true)}>
          {t("accounting.quick.addEntry")}
        </Button>
      </div>
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <TextInput value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t("accounting.payees.search")} aria-label={t("accounting.payees.search")} />
        <Switch showLabel checked={showArchived} onCheckedChange={setShowArchived} label={t("accounting.common.showArchived")} />
      </div>
      {payees.isError ? (
        <ErrorState onRetry={() => void payees.refetch()} error={payees.error} />
      ) : (
        <Table
          columns={columns}
          rows={payees.data ?? []}
          rowKey={(row) => row.id}
          loading={payees.isPending}
          empty={<EmptyState title={t("accounting.payees.none")} body={t("accounting.payees.noneBody")} />}
        />
      )}
      {adding ? <PayeeForm group={group} payee={null} onClose={() => setAdding(false)} /> : null}
      {several ? <SeveralSheet group={group} onClose={() => setSeveral(false)} /> : null}
      {copying ? <CopyMonthSheet group={group} onClose={() => setCopying(false)} /> : null}
      {quickAdd ? <QuickAddSheet group={group} onClose={() => setQuickAdd(false)} /> : null}
    </div>
  );
}

/** A Log group: its entries with filters, the total, and Add spending or Add income (plan 4.6). */
function LogPanel({ group }: { group: Group }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const today = dhakaToday();
  const monthStart = `${today.slice(0, 7)}-01`;
  const [from, setFrom] = useState(monthStart);
  const [to, setTo] = useState(today);
  const [typeId, setTypeId] = useState("");
  const [accountId, setAccountId] = useState("");
  const [page, setPage] = useState(0);
  // Archived types stay in the filter, since old entries still carry them.
  const types = useGroupTypes(group.id, true);
  const accounts = useAccounts(false);
  const entries = useEntries({ groupId: group.id, from, to, typeId, accountId, page, size: 50 });
  const [adding, setAdding] = useState<EntryKind | null>(null);
  const [copying, setCopying] = useState(false);
  const isExpense = group.direction === "EXPENSE";

  const columns: Column<Entry>[] = [
    {
      key: "date",
      header: t("accounting.entries.columns.date"),
      mobileLabel: t("accounting.entries.columns.date"),
      cell: (row) => <Link href={`/staff/accounting/entries/${row.id}`} className="text-body text-text underline-offset-4 hover:underline">{formatDate(row.date)}</Link>,
    },
    {
      key: "type",
      header: t("accounting.entries.columns.type"),
      mobileLabel: t("accounting.entries.columns.type"),
      cell: (row) => (row.typeNameEn ? pickName({ nameEn: row.typeNameEn, nameBn: row.typeNameBn }, locale) : "—"),
    },
    {
      key: "who",
      header: t(isExpense ? "accounting.entries.columns.paidTo" : "accounting.entries.columns.receivedFrom"),
      mobileLabel: t(isExpense ? "accounting.entries.columns.paidTo" : "accounting.entries.columns.receivedFrom"),
      cell: (row) => row.counterparty ?? row.description ?? "—",
    },
    {
      key: "amount",
      header: t("accounting.entries.columns.amount"),
      mobileLabel: t("accounting.entries.columns.amount"),
      align: "end",
      nowrap: true,
      cell: (row) => money(row.amount, locale),
    },
    {
      key: "account",
      header: t("accounting.entries.columns.account"),
      mobileLabel: t("accounting.entries.columns.account"),
      cell: (row) => (row.accountName ? accountLabel({ name: row.accountName, kind: accountKindOf(accounts.data, row.accountId) }, locale, t) : "—"),
    },
  ];
  const data = entries.data;
  const total = isExpense ? data?.totals.spent : data?.totals.income;

  return (
    <div className="grid gap-5">
      <Totals items={[{ label: t("accounting.log.thisMonth"), value: money(group.thisMonth, locale), tone: "muted" }]} />
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => setAdding(isExpense ? "SPEND" : "INCOME")}>
          {t(isExpense ? "accounting.log.addSpending" : "accounting.log.addIncome")}
        </Button>
        <Button variant="secondary" onClick={() => setCopying(true)}>
          {t("accounting.copy.button")}
        </Button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label={t("accounting.entries.filters.from")}>
          <TextInput type="date" value={from} onChange={(event) => { setFrom(event.target.value); setPage(0); }} />
        </Field>
        <Field label={t("accounting.entries.filters.to")}>
          <TextInput type="date" value={to} onChange={(event) => { setTo(event.target.value); setPage(0); }} />
        </Field>
        <Field label={t("accounting.entries.filters.type")}>
          <Select value={typeId} onChange={(event) => { setTypeId(event.target.value); setPage(0); }}>
            <option value="">{t("accounting.entries.filters.allTypes")}</option>
            {(types.data ?? []).map((item) => (
              <option key={item.id} value={item.id}>
                {pickName(item, locale)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("accounting.entries.filters.account")}>
          <Select value={accountId} onChange={(event) => { setAccountId(event.target.value); setPage(0); }}>
            <option value="">{t("accounting.entries.filters.allAccounts")}</option>
            {(accounts.data ?? []).filter((account) => !account.archived).map((account) => (
              <option key={account.id} value={account.id}>
                {accountLabel(account, locale, t)}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      {entries.isError ? (
        <ErrorState onRetry={() => void entries.refetch()} error={entries.error} />
      ) : (
        <>
          <p className="text-label text-text">
            {t("accounting.entries.total")}: <strong>{money(total ?? "0.00", locale)}</strong>
          </p>
          <Table columns={columns} rows={data?.items ?? []} rowKey={(row) => row.id} loading={entries.isPending} empty={<EmptyState title={t("accounting.entries.empty")} />} />
          {data ? <Pagination page={data.page} totalPages={Math.max(1, Math.ceil(data.total / data.size))} onPage={setPage} /> : null}
        </>
      )}

      {adding ? (
        <QuickAddSheet kind={adding} group={group} onClose={() => setAdding(null)} />
      ) : null}
      {copying ? <CopyMonthSheet group={group} onClose={() => setCopying(false)} /> : null}
    </div>
  );
}

function accountKindOf(accounts: { id: string; kind: AccountKind }[] | undefined, id: string | null): AccountKind | null {
  return (accounts ?? []).find((account) => account.id === id)?.kind ?? null;
}
