"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { usePayDefaults, useSavePayDefaults, type PayDefaults } from "@/lib/api/exam/staff-pay";
import { parseAmount, payErrorKey } from "./pay-format";

/**
 * The Master Admin's pay settings: the default sheet rate and the two reminder days. A change never alters a line already
 * written; it applies to the sheets published after it.
 */
export function PayDefaultsPage() {
  const { t } = useLocale();
  const defaults = usePayDefaults();

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffPay.defaults.title")}
        actions={
          <Link href="/staff/pay" className="text-label text-accent underline-offset-4 hover:underline">
            {t("staffPay.person.back")}
          </Link>
        }
      />
      {defaults.isError ? (
        <ErrorState onRetry={() => void defaults.refetch()} error={defaults.error} />
      ) : !defaults.data ? (
        <Skeleton variant="rows" count={3} />
      ) : (
        <DefaultsForm key={defaults.data.updatedAt} initial={defaults.data} />
      )}
    </div>
  );
}

function DefaultsForm({ initial }: { initial: PayDefaults }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSavePayDefaults();
  const [sheetRate, setSheetRate] = useState(String(initial.sheetRate));
  const [unpaid, setUnpaid] = useState(String(initial.unpaidReminderDays));
  const [dueSoon, setDueSoon] = useState(String(initial.salaryDueSoonDays));
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const rate = parseAmount(sheetRate);
    const unpaidDays = Number(unpaid);
    const dueSoonDays = Number(dueSoon);
    if (rate === null) {
      setError(t("staffPay.errors.amount"));
      return;
    }
    if (!Number.isInteger(unpaidDays) || unpaidDays < 1 || unpaidDays > 365 || !Number.isInteger(dueSoonDays) || dueSoonDays < 0 || dueSoonDays > 30) {
      setError(t("staffPay.errors.days"));
      return;
    }
    save.mutate(
      { sheetRate: rate, unpaidReminderDays: unpaidDays, salaryDueSoonDays: dueSoonDays },
      {
        onSuccess: () => toast({ title: t("staffPay.defaults.saved"), tone: "ok" }),
        onError: (failure) => setError(t(payErrorKey(failure))),
      },
    );
  }

  return (
    <Card>
      <form onSubmit={submit} className="grid gap-4" aria-label={t("staffPay.defaults.title")} noValidate>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label={t("staffPay.defaults.sheetRate")} hint={t("staffPay.defaults.sheetRateHint")}>
            <TextInput inputMode="decimal" value={sheetRate} onChange={(event) => setSheetRate(event.target.value)} />
          </Field>
          <Field label={t("staffPay.defaults.unpaidDays")} hint={t("staffPay.defaults.unpaidDaysHint")}>
            <TextInput inputMode="numeric" value={unpaid} onChange={(event) => setUnpaid(event.target.value)} />
          </Field>
          <Field label={t("staffPay.defaults.dueSoonDays")} hint={t("staffPay.defaults.dueSoonDaysHint")}>
            <TextInput inputMode="numeric" value={dueSoon} onChange={(event) => setDueSoon(event.target.value)} />
          </Field>
        </div>
        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
        <div>
          <Button type="submit" variant="primary" loading={save.isPending}>
            {t("staffPay.defaults.save")}
          </Button>
        </div>
      </form>
    </Card>
  );
}
