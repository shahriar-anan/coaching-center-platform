"use client";

import Image from "next/image";
import Link from "next/link";
import { useSearchParams, usePathname, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PrintArea } from "@/components/ui/print-area";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { RequireStaff } from "@/lib/auth/guards";
import { usePayee, usePayeeStatement } from "@/lib/api/accounting/payees";
import type { Statement } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { ACCOUNTING_HOME } from "../accounting-frame";
import { money, monthOf, pickName } from "../accounting-model";

/** A4 portrait for the statement (plan 4.6). The app's chrome is hidden by the print rules; the range fields are not printed. */
const PRINT_CSS = `@media print { @page { size: A4 portrait; margin: 14mm; } }`;

/**
 * One payee's statement (accounting-plan 4.6, B10): the payee, the range (this month by default), the opening balance, every active entry
 * with a running balance, the totals, the closing balance and the day it was printed.
 */
export function StatementPage({ payeeId }: { payeeId: string }) {
  const { t } = useLocale();
  return (
    <RequireStaff
      roles={["MASTER_ADMIN"]}
      notFound={<EmptyState title={t("accounting.forMaster.title")} body={t("accounting.forMaster.body")} />}
    >
      <StatementBody payeeId={payeeId} />
    </RequireStaff>
  );
}

function StatementBody({ payeeId }: { payeeId: string }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const today = dhakaToday();
  const from = params.get("from") ?? `${monthOf(today)}-01`;
  const to = params.get("to") ?? today;
  const payee = usePayee(payeeId);
  const statement = usePayeeStatement(payeeId, from, to);

  function setRange(next: { from?: string; to?: string }) {
    const query = new URLSearchParams({ from: next.from ?? from, to: next.to ?? to });
    router.replace(`${pathname}?${query}`, { scroll: false });
  }

  if (payee.isError) {
    return <ErrorState onRetry={() => void payee.refetch()} error={payee.error} />;
  }
  if (!payee.data) {
    return <Skeleton variant="card" count={1} />;
  }
  const data = statement.data;
  return (
    <div className="grid gap-4">
      <style>{PRINT_CSS}</style>
      <div className="grid gap-3 print:hidden sm:grid-cols-[1fr_1fr_auto] sm:items-end">
        <Field label={t("accounting.statement.from")}>
          <TextInput type="date" value={from} max={to} onChange={(event) => setRange({ from: event.target.value })} />
        </Field>
        <Field label={t("accounting.statement.to")}>
          <TextInput type="date" value={to} max={today} onChange={(event) => setRange({ to: event.target.value })} />
        </Field>
        <div className="flex gap-2">
          <Link href={`${ACCOUNTING_HOME}/payees/${payeeId}`} className="inline-flex min-h-11 items-center text-label text-accent-text underline-offset-4 hover:underline">
            {t("accounting.statement.back")}
          </Link>
          <Button onClick={() => window.print()} disabled={!data}>
            {t("accounting.reports.print")}
          </Button>
        </div>
      </div>
      {statement.isError ? (
        <ErrorState onRetry={() => void statement.refetch()} error={statement.error} />
      ) : statement.isPending || !data ? (
        <Skeleton variant="rows" count={4} />
      ) : (
        <PrintArea>
          <StatementSheet statement={data} printedOn={formatDate(today)} groupName={pickName({ nameEn: payee.data.groupNameEn, nameBn: payee.data.groupNameBn }, locale)} />
        </PrintArea>
      )}
    </div>
  );
}

/** The statement sheet: the payee, the range, the lines with the running balance, and the totals. */
export function StatementSheet({ statement, printedOn, groupName }: { statement: Statement; printedOn: string; groupName: string }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  return (
    <article className="mx-auto grid w-full max-w-[190mm] gap-4 rounded-card border border-border bg-card p-6 text-text print:max-w-none print:border-0 print:bg-white print:p-0 print:text-black">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
        <div className="flex items-center gap-3">
          {/* The logo as the fee receipt shows it; the name is the centre's. */}
          <Image src="/brand/logo-light.png" alt="" width={44} height={50} className="h-11 w-auto dark:hidden print:block" />
          <div>
            <p className="text-label font-semibold">{t("common.brand.name")}</p>
            <p className="text-section font-semibold">{t("accounting.statement.title")}</p>
          </div>
        </div>
        <div className="text-end text-body">
          <p className="font-semibold">{statement.payee.name}</p>
          <p className="text-muted">{groupName}</p>
          <p className="text-meta text-muted">
            {t("accounting.statement.range", { from: formatDate(statement.from), to: formatDate(statement.to) })}
          </p>
        </div>
      </header>

      <table className="w-full text-start text-body">
        <thead>
          <tr className="border-b border-border text-meta text-muted">
            <th className="py-2 text-start font-medium">{t("accounting.entries.columns.date")}</th>
            <th className="py-2 text-start font-medium">{t("accounting.entries.columns.no")}</th>
            <th className="py-2 text-start font-medium">{t("accounting.entries.columns.what")}</th>
            <th className="py-2 text-end font-medium">{t("accounting.statement.charged")}</th>
            <th className="py-2 text-end font-medium">{t("accounting.statement.paid")}</th>
            <th className="py-2 text-end font-medium">{t("accounting.payee.balanceAfter")}</th>
          </tr>
        </thead>
        <tbody>
          <tr className="border-b border-border">
            <td className="py-2" colSpan={5}>
              {t("accounting.statement.opening")}
            </td>
            <td className="py-2 text-end">{money(statement.opening, locale)}</td>
          </tr>
          {statement.lines.map((line) => (
            <tr key={line.id} className="border-b border-border align-top">
              <td className="py-2">{formatDate(line.date)}</td>
              <td className="py-2">#{line.no}</td>
              <td className="py-2">
                {t(`accounting.kind.${line.kind}`)}
                {line.typeNameEn ? ` · ${pickName({ nameEn: line.typeNameEn, nameBn: line.typeNameBn }, locale)}` : ""}
                {line.description ? ` · ${line.description}` : ""}
              </td>
              <td className="py-2 text-end">{line.kind === "CHARGE" ? money(line.amount, locale) : ""}</td>
              <td className="py-2 text-end">{line.kind === "PAYMENT" ? money(line.amount, locale) : ""}</td>
              <td className="py-2 text-end">{money(line.balanceAfter, locale)}</td>
            </tr>
          ))}
          {statement.lines.length === 0 ? (
            <tr>
              <td className="py-3 text-muted" colSpan={6}>
                {t("accounting.statement.noLines")}
              </td>
            </tr>
          ) : null}
        </tbody>
        <tfoot>
          <tr className="font-medium">
            <td className="py-2" colSpan={3}>
              {t("accounting.statement.totals")}
            </td>
            <td className="py-2 text-end">{money(statement.charged, locale)}</td>
            <td className="py-2 text-end">{money(statement.paid, locale)}</td>
            <td className="py-2 text-end">{money(statement.closing, locale)}</td>
          </tr>
        </tfoot>
      </table>

      <footer className="flex flex-wrap justify-between gap-2 pt-2 text-meta text-muted">
        <span>{t("accounting.statement.closing", { amount: money(statement.closing, locale) })}</span>
        <span>{t("accounting.statement.printedOn", { date: printedOn })}</span>
      </footer>
    </article>
  );
}
