"use client";

import { useState, type FormEvent } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import {
  useAddAdjustment,
  useRecordPayment,
  useSavePaySettings,
  useVoidLine,
  type PayLedgerRow,
  type PayMethod,
  type PayPayoutAccount,
  type PaySettings,
  type PayRole,
} from "@/lib/api/exam/staff-pay";
import { PAY_METHODS, defaultPaymentAmount, isAdvance, parseAmount, parseSignedAmount, payErrorKey, todayInDhaka } from "./pay-format";
import { isMissing } from "@/lib/api/missing";

/** The last four digits of an account number or wallet. Shorter numbers show none. */
function lastFour(value: string | null): string {
  const text = value ?? "";
  return text.length >= 5 ? text.slice(-4) : "";
}

/**
 * Record a payment. The amount starts at what is owed. An amount above the balance needs its advance tick. An account, if
 * chosen, sets the method and its masked text is kept with the payment by the API.
 */
export function PaymentForm({
  userId,
  balance,
  owedNow,
  accounts,
  onDone,
}: {
  userId: string;
  balance: number;
  owedNow: number;
  accounts: PayPayoutAccount[];
  onDone: () => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const record = useRecordPayment(userId);
  const accountLabel = (account: PayPayoutAccount) => {
    const last = lastFour(account.accountNumber ?? account.walletNumber);
    const kind = t(`staffPay.method.${account.kind}`);
    return last ? `${kind} ••••${last}` : kind;
  };
  const [amount, setAmount] = useState(() => defaultPaymentAmount(owedNow));
  const [paidOn, setPaidOn] = useState(() => todayInDhaka());
  const [method, setMethod] = useState<PayMethod>("BANK");
  const [accountId, setAccountId] = useState("");
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [allowAdvance, setAllowAdvance] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const parsed = parseAmount(amount);
  const advance = parsed !== null && isAdvance(parsed, balance);

  function pickAccount(id: string) {
    setAccountId(id);
    const account = accounts.find((candidate) => candidate.id === id);
    if (account) {
      setMethod(account.kind);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (parsed === null) {
      setError(t("staffPay.errors.amount"));
      return;
    }
    if (advance && !allowAdvance) {
      setError(t("staffPay.errors.advanceTick"));
      return;
    }
    record.mutate(
      {
        amount: parsed,
        paidOn,
        method,
        accountId: accountId || undefined,
        reference: reference.trim() || undefined,
        note: note.trim() || undefined,
        allowAdvance,
      },
      {
        onSuccess: () => {
          toast({ title: t("staffPay.payment.recorded"), tone: "ok" });
          onDone();
        },
        onError: (failure) => setError(t(payErrorKey(failure))),
      },
    );
  }

  return (
    <form onSubmit={submit} className="grid gap-4" aria-label={t("staffPay.payment.title")} noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("staffPay.payment.amount")} hint={t("staffPay.payment.balanceHint", { amount: balance.toFixed(2) })}>
          <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
        </Field>
        <Field label={t("staffPay.payment.date")}>
          <TextInput type="date" value={paidOn} onChange={(event) => setPaidOn(event.target.value)} />
        </Field>
        <Field label={t("staffPay.payment.account")}>
          <Select value={accountId} onChange={(event) => pickAccount(event.target.value)}>
            <option value="">{t("staffPay.payment.noAccount")}</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {accountLabel(account)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffPay.payment.method")}>
          <Select value={method} onChange={(event) => setMethod(event.target.value as PayMethod)}>
            {PAY_METHODS.map((value) => (
              <option key={value} value={value}>
                {t(`staffPay.method.${value}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffPay.payment.reference")}>
          <TextInput value={reference} onChange={(event) => setReference(event.target.value)} maxLength={100} />
        </Field>
        <Field label={t("staffPay.payment.note")}>
          <TextInput value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} />
        </Field>
      </div>

      {advance ? (
        <label className="flex items-center gap-2 text-body text-text">
          <input
            type="checkbox"
            checked={allowAdvance}
            onChange={(event) => setAllowAdvance(event.target.checked)}
            className="size-4 accent-accent"
          />
          {t("staffPay.payment.advance")}
        </label>
      ) : null}

      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" variant="primary" loading={record.isPending}>
          {t("staffPay.payment.save")}
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          {t("staffPay.common.cancel")}
        </Button>
      </div>
    </form>
  );
}

/** A one-off bonus, advance, deduction or part month: plus or minus, with a required note. */
export function AdjustmentForm({ userId, onDone }: { userId: string; onDone: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const add = useAddAdjustment(userId);
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const parsed = parseSignedAmount(amount);
    if (parsed === null) {
      setError(t("staffPay.errors.amount"));
      return;
    }
    if (!note.trim()) {
      setError(t("staffPay.errors.noteRequired"));
      return;
    }
    add.mutate(
      { amount: parsed, note: note.trim() },
      {
        onSuccess: () => {
          toast({ title: t("staffPay.adjustment.saved"), tone: "ok" });
          onDone();
        },
        onError: (failure) => setError(t(payErrorKey(failure))),
      },
    );
  }

  return (
    <form onSubmit={submit} className="grid gap-4" aria-label={t("staffPay.adjustment.title")} noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("staffPay.adjustment.amount")} hint={t("staffPay.adjustment.amountHint")}>
          <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
        </Field>
        <Field label={t("staffPay.adjustment.note")} required>
          <TextInput value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} />
        </Field>
      </div>
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" variant="primary" loading={add.isPending}>
          {t("staffPay.adjustment.save")}
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          {t("staffPay.common.cancel")}
        </Button>
      </div>
    </form>
  );
}

/**
 * The pay settings. A System Admin gets the salary, pay day and dates; an instructor gets a rate override (blank uses the
 * default). The fields that belong to the other role are not shown.
 */
export function SettingsForm({
  userId,
  role,
  settings,
  defaultRate,
}: {
  userId: string;
  role: PayRole;
  settings: PaySettings;
  defaultRate: number | null;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const save = useSavePaySettings(userId);
  // The API leaves unset fields out of its JSON, so they arrive as undefined: isMissing, never `=== null`.
  const [salary, setSalary] = useState(isMissing(settings.monthlySalary) ? "" : String(settings.monthlySalary));
  const [payDay, setPayDay] = useState(isMissing(settings.payDay) ? "" : String(settings.payDay));
  const [start, setStart] = useState(settings.salaryStart ?? "");
  const [end, setEnd] = useState(settings.salaryEnd ?? "");
  const [rate, setRate] = useState(isMissing(settings.sheetRate) ? "" : String(settings.sheetRate));
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (role === "INSTRUCTOR") {
      const parsedRate = rate.trim() === "" ? null : parseAmount(rate);
      if (rate.trim() !== "" && parsedRate === null) {
        setError(t("staffPay.errors.amount"));
        return;
      }
      save.mutate({ sheetRate: parsedRate }, { onSuccess: () => saved(), onError: (failure) => setError(t(payErrorKey(failure))) });
      return;
    }
    const parsedSalary = salary.trim() === "" ? null : parseAmount(salary);
    const day = payDay.trim() === "" ? null : Number(payDay);
    if (salary.trim() !== "" && parsedSalary === null) {
      setError(t("staffPay.errors.amount"));
      return;
    }
    if (parsedSalary !== null && (day === null || !Number.isInteger(day) || day < 1 || day > 28)) {
      setError(t("staffPay.errors.payDay"));
      return;
    }
    if (parsedSalary !== null && !start) {
      setError(t("staffPay.errors.salaryStart"));
      return;
    }
    save.mutate(
      {
        monthlySalary: parsedSalary,
        payDay: parsedSalary === null ? null : day,
        salaryStart: parsedSalary === null ? null : start,
        salaryEnd: parsedSalary === null || !end ? null : end,
      },
      { onSuccess: () => saved(), onError: (failure) => setError(t(payErrorKey(failure))) },
    );
  }

  function saved() {
    toast({ title: t("staffPay.settings.saved"), tone: "ok" });
  }

  if (role === "INSTRUCTOR") {
    return (
      <form onSubmit={submit} className="grid gap-4" aria-label={t("staffPay.settings.title")} noValidate>
        <Field
          label={t("staffPay.settings.sheetRate")}
          hint={t("staffPay.settings.sheetRateHint", { rate: defaultRate === null ? "" : defaultRate.toFixed(2) })}
        >
          <TextInput inputMode="decimal" value={rate} onChange={(event) => setRate(event.target.value)} />
        </Field>
        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
        <div>
          <Button type="submit" variant="primary" loading={save.isPending}>
            {t("staffPay.settings.save")}
          </Button>
        </div>
      </form>
    );
  }

  return (
    <form onSubmit={submit} className="grid gap-4" aria-label={t("staffPay.settings.title")} noValidate>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("staffPay.settings.salary")} hint={t("staffPay.settings.salaryHint")}>
          <TextInput inputMode="decimal" value={salary} onChange={(event) => setSalary(event.target.value)} />
        </Field>
        <Field label={t("staffPay.settings.payDay")} hint={t("staffPay.settings.payDayHint")}>
          <TextInput inputMode="numeric" value={payDay} onChange={(event) => setPayDay(event.target.value)} />
        </Field>
        <Field label={t("staffPay.settings.start")}>
          <TextInput type="date" value={start} onChange={(event) => setStart(event.target.value)} />
        </Field>
        <Field label={t("staffPay.settings.end")} hint={t("staffPay.settings.endHint")}>
          <TextInput type="date" value={end} onChange={(event) => setEnd(event.target.value)} />
        </Field>
      </div>
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
      <div>
        <Button type="submit" variant="primary" loading={save.isPending}>
          {t("staffPay.settings.save")}
        </Button>
      </div>
    </form>
  );
}

/** Voids a payment or an adjustment. The reason is required and kept with the line. */
export function VoidDialog({ row, onClose }: { row: PayLedgerRow | null; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const voidLine = useVoidLine();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  function confirm() {
    if (!row) {
      return;
    }
    setError(null);
    if (!reason.trim()) {
      setError(t("staffPay.errors.reasonRequired"));
      return;
    }
    voidLine.mutate(
      { id: row.id, reason: reason.trim() },
      {
        onSuccess: () => {
          toast({ title: t("staffPay.void.done"), tone: "ok" });
          setReason("");
          onClose();
        },
        onError: (failure) => setError(t(payErrorKey(failure))),
      },
    );
  }

  return (
    <Dialog
      open={row !== null}
      onOpenChange={(open) => {
        if (!open) {
          setReason("");
          setError(null);
          onClose();
        }
      }}
      title={t("staffPay.void.title")}
      description={t("staffPay.void.body")}
      footer={
        <>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t("staffPay.common.cancel")}
          </Button>
          <Button type="button" variant="danger" loading={voidLine.isPending} onClick={confirm}>
            {t("staffPay.void.confirm")}
          </Button>
        </>
      }
    >
      <div className="grid gap-3">
        <Field label={t("staffPay.void.reason")} required>
          <TextInput value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} />
        </Field>
        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
      </div>
    </Dialog>
  );
}
