"use client";

import Image from "next/image";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PrintArea } from "@/components/ui/print-area";
import { RequireStaff } from "@/lib/auth/guards";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useEntry } from "@/lib/api/accounting/entries";
import { useGroups } from "@/lib/api/accounting/groups";
import type { EntryDetail } from "@/lib/api/accounting/types";
import { ACCOUNTING_HOME } from "../accounting-frame";
import { accountLabel, money, pickName, unitWithCount } from "../accounting-model";
import { amountInWords } from "../amount-words";

/** A5 portrait for one voucher; the page prints on A4 with the margins (plan 4.6). The app's chrome is hidden by the print rules. */
const PRINT_CSS = `@media print { @page { size: A5 portrait; margin: 10mm; } }`;

const TITLES: Record<"PAYMENT" | "SPEND" | "INCOME", "voucherPayment" | "voucherSpend" | "voucherIncome"> = {
  PAYMENT: "voucherPayment",
  SPEND: "voucherSpend",
  INCOME: "voucherIncome",
};

/** The voucher of one payment, spending or income (accounting-plan 4.6, B10). Charges have none. */
export function VoucherPage({ entryId }: { entryId: string }) {
  const { t } = useLocale();
  return (
    <RequireStaff
      roles={["MASTER_ADMIN"]}
      notFound={<EmptyState title={t("accounting.forMaster.title")} body={t("accounting.forMaster.body")} />}
    >
      <VoucherBody entryId={entryId} />
    </RequireStaff>
  );
}

function VoucherBody({ entryId }: { entryId: string }) {
  const { t, locale } = useLocale();
  const entry = useEntry(entryId);
  const groups = useGroups(true);
  if (entry.isError) {
    return <ErrorState onRetry={() => void entry.refetch()} error={entry.error} />;
  }
  if (entry.isPending) {
    return <Skeleton variant="card" count={1} />;
  }
  const data = entry.data;
  if (!data || data.kind === "CHARGE") {
    return <EmptyState title={t("accounting.common.notFound")} body={t("accounting.voucher.noVoucher")} />;
  }
  const group = (groups.data ?? []).find((item) => item.id === data.groupId) ?? null;
  return (
    <div className="grid gap-4">
      <style>{PRINT_CSS}</style>
      <div className="flex flex-wrap items-center justify-between gap-2 print:hidden">
        <Link href={`${ACCOUNTING_HOME}/entries/${data.id}`} className="text-label text-accent-text underline-offset-4 hover:underline">
          {t("accounting.voucher.back")}
        </Link>
        <Button onClick={() => window.print()}>{t("accounting.reports.print")}</Button>
      </div>
      <PrintArea>
        <VoucherSheet entry={data} groupName={group ? pickName(group, locale) : ""} />
      </PrintArea>
    </div>
  );
}

/** The sheet itself: the centre and logo, the title and number, the fields, the amount in figures and in words, and the signatures. */
export function VoucherSheet({ entry, groupName }: { entry: EntryDetail; groupName: string }) {
  const { t, locale } = useLocale();
  const { formatDate, formatDateTime } = useFormat();
  const kind = entry.kind === "CHARGE" ? "SPEND" : entry.kind;
  const title = t(`accounting.voucher.${TITLES[kind as "PAYMENT" | "SPEND" | "INCOME"]}`);
  const counted = entry.quantity != null && entry.unitPrice != null;
  const unit = pickName({ nameEn: entry.unitEn ?? "", nameBn: entry.unitBn }, locale);
  const voided = entry.status === "VOIDED";
  const amount = entry.amount.replace("-", "");
  const who = entry.payeeName ?? entry.counterparty ?? "—";
  const accountName = entry.accountName ? accountLabel({ name: entry.accountName, kind: null }, locale, t) : "—";
  // The type and the quantity line are printed only where the entry has them: an amount-only payment shows its amount and nothing else.
  const rows: [string, string][] = [
    [t("accounting.voucher.paidTo"), who],
    [t("accounting.voucher.group"), groupName],
    ...(entry.typeNameEn ? [[t("accounting.voucher.type"), pickName({ nameEn: entry.typeNameEn, nameBn: entry.typeNameBn }, locale)] as [string, string]] : []),
    ...(counted ? [[t("accounting.voucher.quantityPrice"), `${entry.quantity} ${unitWithCount(unit, entry.quantity ?? "0", locale)} × ${money(entry.unitPrice, locale)}`] as [string, string]] : []),
    [t("accounting.voucher.account"), entry.reference ? `${accountName} · ${entry.reference}` : accountName],
    [t("accounting.voucher.description"), entry.description ?? "—"],
  ];

  return (
    <article className="relative mx-auto grid w-full max-w-[148mm] gap-4 overflow-hidden rounded-card border border-border bg-card p-5 text-text print:max-w-none print:border-0 print:bg-white print:text-black">
      <header className="grid justify-items-center gap-1 border-b border-border pb-3 text-center">
        {/* The logo as the fee receipt shows it; the name is the centre's. */}
        <Image src="/brand/logo-light.png" alt="" width={48} height={54} className="h-12 w-auto dark:hidden print:block" />
        <p className="text-label font-semibold">{t("common.brand.name")}</p>
        <p className="text-section font-semibold">{title}</p>
      </header>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-body">
        <dt className="text-muted">{t("accounting.voucher.number")}</dt>
        <dd className="font-medium">#{entry.no}</dd>
        <dt className="text-muted">{t("accounting.voucher.date")}</dt>
        <dd>{formatDate(entry.date)}</dd>
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-muted">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
        <dt className="text-muted">{t("accounting.voucher.amount")}</dt>
        <dd className="text-section font-semibold">{money(amount, locale)}</dd>
        <dt className="text-muted">{t("accounting.voucher.inWords")}</dt>
        <dd className="text-body">{amountInWords(amount, locale)}</dd>
        {entry.correctedAt ? (
          <>
            <dt className="text-muted">{t("accounting.voucher.corrected")}</dt>
            <dd>{formatDateTime(entry.correctedAt)}</dd>
          </>
        ) : null}
      </dl>

      <footer className="mt-6 grid grid-cols-2 gap-8 pt-6 text-meta">
        <p className="border-t border-current pt-1">{t("accounting.voucher.receivedBy")}</p>
        <p className="border-t border-current pt-1 text-end">{t("accounting.voucher.paidBy")}</p>
      </footer>

      {voided ? (
        <div aria-label={t("accounting.entries.voided")} className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <span className="rotate-[-20deg] border-4 border-danger px-6 py-2 text-[72px] font-bold tracking-widest text-danger opacity-70">VOID</span>
        </div>
      ) : null}
    </article>
  );
}
