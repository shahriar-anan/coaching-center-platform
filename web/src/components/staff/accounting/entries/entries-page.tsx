"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Table, type Column } from "@/components/ui/table";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useAccounts } from "@/lib/api/accounting/accounts";
import { useGroupTypes } from "@/lib/api/accounting/account-types";
import { downloadAccountingEntriesCsv, useEntries, type EntryFilters } from "@/lib/api/accounting/entries";
import { useGroups } from "@/lib/api/accounting/groups";
import { useGroupPayees } from "@/lib/api/accounting/payees";
import type { AccountKind, Entry } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { useBatches } from "@/lib/api/exam/offline-admin";
import { ACCOUNTING_HOME, AccountingFrame } from "../accounting-frame";
import { accountLabel, money, pickName, writeErrorText } from "../accounting-model";

/** The filter names, as the URL holds them (shareable). Each one is optional. */
type FilterKey = "from" | "to" | "groupId" | "payeeId" | "typeId" | "kind" | "accountId" | "batchId" | "status" | "q";

/** The entries of every group, with the filters of plan 4.6 in the URL. Starts with this month. */
export function EntriesPage() {
  const { t } = useLocale();
  return (
    <AccountingFrame title={t("accounting.entries.title")}>
      <EntriesList />
    </AccountingFrame>
  );
}

function EntriesList() {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const { toast } = useToast();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const today = dhakaToday();
  const [csvError, setCsvError] = useState<string | null>(null);

  const read = (key: FilterKey, fallback = "") => params.get(key) ?? fallback;
  const filters: EntryFilters = {
    from: read("from", `${today.slice(0, 7)}-01`),
    to: read("to", today),
    groupId: read("groupId"),
    payeeId: read("payeeId"),
    typeId: read("typeId"),
    kind: read("kind"),
    accountId: read("accountId"),
    batchId: read("batchId"),
    status: read("status"),
    q: read("q"),
  };
  const page = Number(params.get("page") ?? "0") || 0;

  function update(changes: Partial<Record<FilterKey | "page", string>>) {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value) {
        next.set(key, value);
      } else {
        next.delete(key);
      }
    }
    // A new filter starts again from the first page; a page change keeps the filters.
    if (!("page" in changes)) {
      next.delete("page");
    }
    if (filters.groupId && changes.groupId !== undefined && changes.groupId !== filters.groupId) {
      next.delete("payeeId");
      next.delete("typeId");
    }
    const query = next.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false });
  }

  const groups = useGroups(true);
  const accounts = useAccounts(true);
  const batches = useBatches("");
  const types = useGroupTypes(read("groupId"), true);
  const payees = useGroupPayees(read("groupId"), { archived: true });
  const entries = useEntries({ ...filters, page, size: 50 });
  const accountKinds = (id: string | null): AccountKind | null => (accounts.data ?? []).find((account) => account.id === id)?.kind ?? null;

  const columns: Column<Entry>[] = [
    {
      key: "no",
      header: t("accounting.entries.columns.no"),
      mobileLabel: t("accounting.entries.columns.no"),
      cell: (row) => (
        <Link href={`${ACCOUNTING_HOME}/entries/${row.id}`} className="font-medium text-text underline-offset-4 hover:underline">
          #{row.no}
        </Link>
      ),
    },
    { key: "date", header: t("accounting.entries.columns.date"), mobileLabel: t("accounting.entries.columns.date"), cell: (row) => formatDate(row.date) },
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
      key: "group",
      header: t("accounting.entries.columns.group"),
      mobileLabel: t("accounting.entries.columns.group"),
      cell: (row) => pickName({ nameEn: row.groupNameEn, nameBn: row.groupNameBn }, locale),
    },
    {
      key: "who",
      header: t("accounting.entries.columns.who"),
      mobileLabel: t("accounting.entries.columns.who"),
      cell: (row) => row.payeeName ?? row.counterparty ?? "—",
    },
    {
      key: "what",
      header: t("accounting.entries.columns.what"),
      mobileLabel: t("accounting.entries.columns.what"),
      cell: (row) => (row.typeNameEn ? pickName({ nameEn: row.typeNameEn, nameBn: row.typeNameBn }, locale) : row.description ?? "—"),
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
      cell: (row) => (row.accountName ? accountLabel({ name: row.accountName, kind: accountKinds(row.accountId) }, locale, t) : "—"),
    },
  ];

  async function download() {
    setCsvError(null);
    try {
      await downloadAccountingEntriesCsv(filters);
      toast({ title: t("accounting.entries.csvReady"), tone: "ok" });
    } catch (error) {
      setCsvError(writeErrorText(error, t));
    }
  }

  const data = entries.data;
  return (
    <div className="grid gap-5">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Field label={t("accounting.entries.filters.from")}>
          <TextInput type="date" value={filters.from} onChange={(event) => update({ from: event.target.value })} />
        </Field>
        <Field label={t("accounting.entries.filters.to")}>
          <TextInput type="date" value={filters.to} onChange={(event) => update({ to: event.target.value })} />
        </Field>
        <Field label={t("accounting.entries.filters.group")}>
          <Select value={filters.groupId} onChange={(event) => update({ groupId: event.target.value })}>
            <option value="">{t("accounting.entries.filters.allGroups")}</option>
            {(groups.data ?? []).map((group) => (
              <option key={group.id} value={group.id}>
                {pickName(group, locale)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("accounting.entries.filters.kind")}>
          <Select value={filters.kind} onChange={(event) => update({ kind: event.target.value })}>
            <option value="">{t("accounting.entries.filters.allKinds")}</option>
            {(["CHARGE", "PAYMENT", "SPEND", "INCOME"] as const).map((kind) => (
              <option key={kind} value={kind}>
                {t(`accounting.kind.${kind}`)}
              </option>
            ))}
          </Select>
        </Field>
        {/* The payee filter: only a Payees group has payees (a Log group's entries have none). */}
        {filters.groupId && (groups.data ?? []).find((group) => group.id === filters.groupId)?.mode === "PAYEES" ? (
          <Field label={t("accounting.entries.filters.payee")}>
            <Select value={filters.payeeId} onChange={(event) => update({ payeeId: event.target.value })}>
              <option value="">{t("accounting.entries.filters.allPayees")}</option>
              {(payees.data ?? []).map((payee) => (
                <option key={payee.id} value={payee.id}>
                  {payee.name}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}
        <Field label={t("accounting.entries.filters.type")}>
          <Select value={filters.typeId} onChange={(event) => update({ typeId: event.target.value })}>
            <option value="">{t("accounting.entries.filters.allTypes")}</option>
            {(types.data ?? []).map((type) => (
              <option key={type.id} value={type.id}>
                {pickName(type, locale)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("accounting.entries.filters.account")}>
          <Select value={filters.accountId} onChange={(event) => update({ accountId: event.target.value })}>
            <option value="">{t("accounting.entries.filters.allAccounts")}</option>
            {(accounts.data ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {accountLabel(account, locale, t)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("accounting.entries.filters.batch")}>
          <Select value={filters.batchId} onChange={(event) => update({ batchId: event.target.value })}>
            <option value="">{t("accounting.entries.filters.allBatches")}</option>
            {(batches.data ?? []).map((batch) => (
              <option key={batch.id} value={batch.id}>
                {pickName(batch, locale)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("accounting.entries.filters.status")}>
          <Select value={filters.status} onChange={(event) => update({ status: event.target.value })}>
            <option value="">{t("accounting.entries.filters.statusAll")}</option>
            <option value="ACTIVE">{t("accounting.entries.filters.statusActive")}</option>
            <option value="VOIDED">{t("accounting.entries.filters.statusVoided")}</option>
          </Select>
        </Field>
        <Field label={t("accounting.entries.filters.search")} hint={t("accounting.entries.filters.searchHint")}>
          <TextInput type="search" value={filters.q} onChange={(event) => update({ q: event.target.value })} maxLength={200} />
        </Field>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        {data ? (
          <dl className="flex flex-wrap gap-4 text-body">
            <Total label={t("accounting.entries.totals.charged")} value={money(data.totals.charged, locale)} />
            <Total label={t("accounting.entries.totals.paid")} value={money(data.totals.paid, locale)} />
            <Total label={t("accounting.entries.totals.spent")} value={money(data.totals.spent, locale)} />
            <Total label={t("accounting.entries.totals.income")} value={money(data.totals.income, locale)} />
          </dl>
        ) : (
          <span />
        )}
        <Button variant="secondary" onClick={() => void download()}>
          {t("accounting.entries.csv")}
        </Button>
      </div>
      {csvError ? <p role="alert" className="text-meta text-danger">{csvError}</p> : null}

      {entries.isError ? (
        <ErrorState onRetry={() => void entries.refetch()} error={entries.error} />
      ) : (
        <>
          <Table columns={columns} rows={data?.items ?? []} rowKey={(row) => row.id} loading={entries.isPending} empty={<EmptyState title={t("accounting.entries.empty")} />} />
          {data ? <Pagination page={data.page} totalPages={Math.max(1, Math.ceil(data.total / data.size))} onPage={(next) => update({ page: String(next) })} /> : null}
        </>
      )}
    </div>
  );
}

function Total({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-1.5">
      <dt className="text-muted">{label}</dt>
      <dd className="font-medium text-text">{value}</dd>
    </div>
  );
}
