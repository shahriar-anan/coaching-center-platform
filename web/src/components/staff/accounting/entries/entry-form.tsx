"use client";

import { useMemo, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useAccounts } from "@/lib/api/accounting/accounts";
import { useGroupTypes } from "@/lib/api/accounting/account-types";
import { useLastPrices } from "@/lib/api/accounting/payees";
import { useCreateEntry } from "@/lib/api/accounting/entries";
import type { AccType, Entry, EntryInput, EntryKind, Group, PayeeRow } from "@/lib/api/accounting/types";
import { useBatches } from "@/lib/api/exam/offline-admin";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import {
  accountLabel,
  countedAmount,
  money,
  monthOf,
  parseAmount,
  parseQuantity,
  pickName,
  unitWithCount,
  writeErrorText,
} from "../accounting-model";
import { ExamPicker } from "./exam-picker";

/** The account a person last used, in this browser. Blocked storage means no default: the first live account is used. */
const LAST_ACCOUNT_KEY = "cc-acc-last-account";

export function readLastAccount(): string {
  try {
    return window.localStorage.getItem(LAST_ACCOUNT_KEY) ?? "";
  } catch {
    return "";
  }
}

function writeLastAccount(id: string) {
  try {
    window.localStorage.setItem(LAST_ACCOUNT_KEY, id);
  } catch {
    // Nothing to do: the default is simply not remembered.
  }
}

/** The values of the form before they are checked. The initial values come from the caller (a copy, or an edit later). */
export type EntryFormValues = {
  date: string;
  typeId: string;
  period: string;
  quantity: string;
  unitPrice: string;
  amount: string;
  accountId: string;
  reference: string;
  counterparty: string;
  batchId: string;
  liveExamId: string;
  description: string;
  note: string;
  paidNow: boolean;
  paidAccountId: string;
  paidAmount: string;
  paidReference: string;
};

type Problems = Partial<Record<keyof EntryFormValues, string>>;

const TITLES: Record<EntryKind, string> = {
  CHARGE: "accounting.form.titleCharge",
  PAYMENT: "accounting.form.titlePayment",
  SPEND: "accounting.form.titleSpend",
  INCOME: "accounting.form.titleIncome",
};

/**
 * The entry form, one component for the four kinds (accounting-plan 4.4). Counted types take a quantity and a unit price and show the
 * line live; the amount is then computed. The unit price starts from the payee's last price for that type. A charge can be paid now.
 * The server has the last word: its refusals are shown as they come.
 */
export function EntryForm({
  kind,
  group,
  payee = null,
  initial,
  onSaved,
  onCancel,
}: {
  kind: EntryKind;
  group: Group;
  payee?: PayeeRow | null;
  initial?: Partial<EntryFormValues>;
  onSaved: (entries: Entry[]) => void;
  onCancel: () => void;
}) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const create = useCreateEntry();
  const types = useGroupTypes(group.id, false);
  const accounts = useAccounts(false);
  const batches = useBatches("");
  const lastPrices = useLastPrices(kind === "CHARGE" ? payee?.id : null);
  const today = dhakaToday();

  const [values, setValues] = useState<EntryFormValues>({
    date: today,
    typeId: "",
    period: "",
    quantity: "",
    unitPrice: "",
    amount: "",
    accountId: "",
    reference: "",
    counterparty: "",
    batchId: "",
    liveExamId: "",
    description: "",
    note: "",
    paidNow: false,
    paidAccountId: "",
    paidAmount: "",
    paidReference: "",
    ...initial,
  });
  // The unit price the person typed, if they typed one. Until then the payee's last price for the type is shown (plan A7).
  const [typedPrice, setTypedPrice] = useState<string | null>(initial?.unitPrice ?? null);
  const [problems, setProblems] = useState<Problems>({});
  const [formError, setFormError] = useState<string | null>(null);

  const set = <K extends keyof EntryFormValues>(field: K, value: EntryFormValues[K]) =>
    setValues((current) => ({ ...current, [field]: value }));

  const typeList = useMemo<AccType[]>(() => {
    const live = (types.data ?? []).filter((item) => !item.archived);
    if (kind === "PAYMENT") {
      return [];
    }
    // A deduction lowers a balance, so it is a charge type of a Payees group only (plan 4.2).
    return live.filter((item) => item.kind !== "DEDUCTION" || (kind === "CHARGE" && group.mode === "PAYEES"));
  }, [types.data, kind, group.mode]);
  const type = typeList.find((item) => item.id === values.typeId) ?? null;
  const counted = type?.kind === "COUNTED";

  // The default account: the one used last, when it is still live; else the first live one.
  const liveAccounts = (accounts.data ?? []).filter((account) => !account.archived);
  const lastAccount = useMemo(() => readLastAccount(), []);
  const defaultAccount = liveAccounts.find((account) => account.id === lastAccount)?.id ?? liveAccounts[0]?.id ?? "";
  const accountId = values.accountId || defaultAccount;
  const paidAccountId = values.paidAccountId || defaultAccount;

  // The unit price starts from the payee's last price for this type (plan A7) until the person types one.
  const suggestion = lastPrices.data?.find((row) => row.typeId === values.typeId);
  const suggestedPrice = suggestion?.unitPrice ?? "";
  const unitPrice = typedPrice ?? suggestedPrice;

  // The charge amount: the counted product, or the typed amount. It is the default for "Paid now".
  let chargeAmount: string | null = null;
  if (kind === "CHARGE" && type) {
    if (counted) {
      const quantity = parseQuantity(values.quantity);
      const price = parseAmount(unitPrice);
      chargeAmount = quantity && price ? countedAmount(quantity, price) : null;
    } else {
      chargeAmount = parseAmount(values.amount);
    }
  }
  // The paid-now amount is set once, when the switch turns on, and is a plain field after that.
  const paidAmount = values.paidAmount;

  function pickType(typeId: string) {
    set("typeId", typeId);
    setTypedPrice(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = validate();
    setProblems(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    setFormError(null);
    const input = buildInput();
    try {
      const saved = await create.mutateAsync(input);
      if (input.accountId) {
        writeLastAccount(input.accountId);
      }
      toast({ title: t("accounting.common.saved"), tone: "ok" });
      onSaved(saved);
    } catch (error) {
      setFormError(writeErrorText(error, t));
    }
  }

  function validate(): Problems {
    const found: Problems = {};
    if (!values.date) {
      found.date = t("accounting.form.dateRequired");
    } else if (values.date > today) {
      found.date = t("accounting.errors.future_date");
    }
    if (kind !== "PAYMENT" && !type) {
      found.typeId = t("accounting.form.typeRequired");
    }
    if (kind === "PAYMENT" && !payee) {
      found.typeId = t("accounting.errors.payee_required");
    }
    if (type?.needsPeriod && !/^\d{4}-(0[1-9]|1[0-2])$/.test(values.period || monthOf(values.date))) {
      found.period = t("accounting.errors.period_required");
    }
    if (type && (kind === "CHARGE" || kind === "SPEND" || kind === "INCOME")) {
      if (counted) {
        if (!parseQuantity(values.quantity)) {
          found.quantity = t("accounting.errors.quantity_required");
        }
        if (!parseAmount(unitPrice)) {
          found.unitPrice = t("accounting.errors.price_required");
        }
      } else if (!parseAmount(values.amount)) {
        found.amount = t("accounting.form.amountInvalid");
      }
    }
    if (kind === "PAYMENT" && !parseAmount(values.amount)) {
      found.amount = t("accounting.form.amountInvalid");
    }
    if (type?.needsDescription && !values.description.trim()) {
      found.description = t("accounting.errors.description_required");
    }
    if (kind !== "CHARGE" && !accountId) {
      found.accountId = t("accounting.errors.account_required");
    }
    if (kind === "CHARGE" && values.paidNow) {
      if (!paidAccountId) {
        found.paidAccountId = t("accounting.errors.account_required");
      }
      const paid = parseAmount(paidAmount);
      if (!paid) {
        found.paidAmount = t("accounting.form.amountInvalid");
      } else if (chargeAmount && Number(paid) > Number(chargeAmount)) {
        found.paidAmount = t("accounting.errors.paid_now_too_much");
      }
    }
    return found;
  }

  function buildInput(): EntryInput {
    const period = type?.needsPeriod ? values.period || monthOf(values.date) : null;
    const isCharge = kind === "CHARGE";
    const input: EntryInput = {
      kind,
      groupId: group.id,
      payeeId: kind === "CHARGE" || kind === "PAYMENT" ? (payee?.id ?? null) : null,
      typeId: kind === "PAYMENT" ? null : (type?.id ?? null),
      date: values.date,
      period,
      quantity: type && counted ? parseQuantity(values.quantity) : null,
      unitPrice: type && counted ? parseAmount(unitPrice) : null,
      amount: type && counted ? null : parseAmount(values.amount),
      accountId: isCharge ? null : accountId,
      reference: values.reference.trim() || null,
      counterparty: kind === "SPEND" || kind === "INCOME" ? values.counterparty.trim() || null : null,
      batchId: values.batchId || null,
      liveExamId: isCharge ? values.liveExamId || null : null,
      description: values.description.trim() || null,
      note: values.note.trim() || null,
      paidNow:
        isCharge && values.paidNow && type?.kind !== "DEDUCTION"
          ? { accountId: paidAccountId, amount: parseAmount(paidAmount), reference: values.paidReference.trim() || null }
          : null,
    };
    return input;
  }

  const unitName = type && counted ? pickName({ nameEn: type.unitEn ?? "", nameBn: type.unitBn }, locale) : "";
  const quantityText = parseQuantity(values.quantity);
  const priceText = parseAmount(unitPrice);
  const liveLine =
    counted && quantityText && priceText
      ? `${quantityText} ${unitWithCount(unitName, quantityText, locale)} × ${money(priceText, locale)} = ${money(countedAmount(quantityText, priceText), locale)}`
      : null;

  const batchOptions = (batches.data ?? []).map((batch) => ({ id: batch.id, label: pickName(batch, locale) }));
  const needsPeriod = Boolean(type?.needsPeriod);
  // A payment has no type: it always takes a plain amount.
  const showAmount = kind === "PAYMENT" || (Boolean(type) && !counted);
  const showCounted = Boolean(type) && counted;

  return (
    <form className="grid gap-4" onSubmit={(event) => void submit(event)} noValidate>
      <h2 className="text-section font-semibold text-text">{t(TITLES[kind])}</h2>
      {payee ? (
        <p className="text-body text-text">
          {payee.name} <span className="text-meta text-muted">· {pickName(group, locale)}</span>
        </p>
      ) : null}

      <Field label={t("accounting.form.date")} required error={problems.date}>
        <TextInput type="date" value={values.date} max={today} onChange={(event) => set("date", event.target.value)} invalid={Boolean(problems.date)} />
      </Field>

      {kind !== "PAYMENT" ? (
        <Field label={t("accounting.form.type")} required error={problems.typeId}>
          <Select value={values.typeId} onChange={(event) => pickType(event.target.value)} invalid={Boolean(problems.typeId)}>
            <option value="">{t("accounting.form.chooseType")}</option>
            {typeList.map((item) => (
              <option key={item.id} value={item.id}>
                {pickName(item, locale)}
              </option>
            ))}
          </Select>
        </Field>
      ) : null}

      {needsPeriod ? (
        <Field label={t("accounting.form.period")} required hint={t("accounting.form.periodHint")} error={problems.period}>
          <TextInput type="month" value={values.period || monthOf(values.date)} onChange={(event) => set("period", event.target.value)} invalid={Boolean(problems.period)} />
        </Field>
      ) : null}

      {showCounted ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={`${t("accounting.form.quantity")} (${unitName})`} required error={problems.quantity}>
            <TextInput inputMode="decimal" value={values.quantity} onChange={(event) => set("quantity", event.target.value)} invalid={Boolean(problems.quantity)} />
          </Field>
          <Field label={t("accounting.form.unitPrice")} required error={problems.unitPrice} hint={suggestion ? t("accounting.form.priceFromLast") : undefined}>
            <TextInput
              inputMode="decimal"
              value={unitPrice}
              onChange={(event) => setTypedPrice(event.target.value)}
              invalid={Boolean(problems.unitPrice)}
            />
          </Field>
          {liveLine ? <p className="text-body text-text sm:col-span-2" aria-live="polite">{liveLine}</p> : null}
        </div>
      ) : null}

      {showAmount ? (
        <Field label={t("accounting.form.amount")} required error={problems.amount}>
          <TextInput inputMode="decimal" value={values.amount} onChange={(event) => set("amount", event.target.value)} invalid={Boolean(problems.amount)} />
        </Field>
      ) : null}

      {kind !== "CHARGE" ? (
        <>
          <Field label={t("accounting.form.account")} required error={problems.accountId}>
            <Select value={accountId} onChange={(event) => set("accountId", event.target.value)} invalid={Boolean(problems.accountId)}>
              {liveAccounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {accountLabel(account, locale, t)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("accounting.form.reference")} hint={t("accounting.form.referenceHint")}>
            <TextInput value={values.reference} onChange={(event) => set("reference", event.target.value)} maxLength={100} />
          </Field>
        </>
      ) : null}

      {kind === "SPEND" || kind === "INCOME" ? (
        <Field label={t(kind === "SPEND" ? "accounting.form.paidTo" : "accounting.form.receivedFrom")}>
          <TextInput value={values.counterparty} onChange={(event) => set("counterparty", event.target.value)} maxLength={200} />
        </Field>
      ) : null}

      <Field label={t("accounting.form.batch")}>
        <Select value={values.batchId} onChange={(event) => set("batchId", event.target.value)}>
          <option value="">{t("accounting.form.noBatch")}</option>
          {batchOptions.map((batch) => (
            <option key={batch.id} value={batch.id}>
              {batch.label}
            </option>
          ))}
        </Select>
      </Field>

      {kind === "CHARGE" ? (
        <ExamPicker value={values.liveExamId} onChange={(id) => set("liveExamId", id)} />
      ) : null}

      <Field
        label={t("accounting.form.description")}
        required={Boolean(type?.needsDescription)}
        hint={type?.needsDescription ? t("accounting.settings.types.needsDescriptionHint") : undefined}
        error={problems.description}
      >
        <TextInput value={values.description} onChange={(event) => set("description", event.target.value)} maxLength={500} invalid={Boolean(problems.description)} />
      </Field>

      <Field label={t("accounting.form.note")}>
        <Textarea value={values.note} onChange={(event) => set("note", event.target.value)} maxLength={500} rows={2} />
      </Field>

      {kind === "CHARGE" && type && type.kind !== "DEDUCTION" ? (
        <div className="grid gap-3 rounded-card border border-border p-4">
          <Switch
            checked={values.paidNow}
            onCheckedChange={(on) => {
              set("paidNow", on);
              if (on && !values.paidAmount) {
                set("paidAmount", chargeAmount ?? "");
              }
            }}
            label={t("accounting.form.paidNow")}
          />
          {values.paidNow ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label={t("accounting.form.account")} required error={problems.paidAccountId}>
                <Select value={paidAccountId} onChange={(event) => set("paidAccountId", event.target.value)}>
                  {liveAccounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {accountLabel(account, locale, t)}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label={t("accounting.form.paidAmount")} required error={problems.paidAmount} hint={t("accounting.form.paidAmountHint")}>
                <TextInput inputMode="decimal" value={paidAmount} onChange={(event) => set("paidAmount", event.target.value)} invalid={Boolean(problems.paidAmount)} />
              </Field>
              <Field label={t("accounting.form.reference")}>
                <TextInput value={values.paidReference} onChange={(event) => set("paidReference", event.target.value)} maxLength={100} />
              </Field>
            </div>
          ) : null}
        </div>
      ) : null}

      {formError ? <p role="alert" className="text-meta text-danger">{formError}</p> : null}
      <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-border bg-card px-4 py-3 sm:static sm:mx-0 sm:border-0 sm:p-0">
        <Button variant="ghost" onClick={onCancel}>
          {t("accounting.common.cancel")}
        </Button>
        <Button type="submit" loading={create.isPending}>
          {create.isPending ? t("accounting.common.saving") : t("accounting.common.save")}
        </Button>
      </div>
    </form>
  );
}

