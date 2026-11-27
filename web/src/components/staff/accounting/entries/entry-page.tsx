"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useAccounts } from "@/lib/api/accounting/accounts";
import { useGroupTypes } from "@/lib/api/accounting/account-types";
import { useEditEntry, useEntry, useVoidEntry, type EntryEditBody } from "@/lib/api/accounting/entries";
import { useGroups } from "@/lib/api/accounting/groups";
import type { AccType, EntryDetail, Group } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { ACCOUNTING_HOME, AccountingFrame } from "../accounting-frame";
import { accountLabel, countedAmount, money, parseAmount, parseQuantity, pickName, unitWithCount, writeErrorText } from "../accounting-model";

/** The fields a correction can change, in the order the corrections list shows them. */
const EDIT_FIELDS = ["date", "typeId", "period", "quantity", "unitPrice", "amount", "accountId", "reference", "counterparty", "batchId", "description", "note"] as const;

/** One entry: all its fields, its paired entry, its corrections and, when voided, why (plan 4.6). Edit and Void need a reason. */
export function EntryPage({ entryId }: { entryId: string }) {
  const { t, locale } = useLocale();
  const entry = useEntry(entryId);
  const groups = useGroups(true);
  const group = (groups.data ?? []).find((item) => item.id === entry.data?.groupId) ?? null;
  const crumbs = entry.data ? [{ label: `#${entry.data.no}` }] : [];
  return (
    <AccountingFrame title={entry.data ? `#${entry.data.no} · ${t(`accounting.kind.${entry.data.kind}`)}` : t("accounting.entries.title")} breadcrumbs={[{ label: t("accounting.entries.title"), href: `${ACCOUNTING_HOME}/entries` }, ...crumbs]}>
      {entry.isError ? (
        <ErrorState onRetry={() => void entry.refetch()} error={entry.error} />
      ) : entry.isPending ? (
        <Skeleton variant="card" count={2} />
      ) : !entry.data ? (
        <EmptyState title={t("accounting.common.notFound")} />
      ) : (
        <EntryBody entry={entry.data} group={group} locale={locale} />
      )}
    </AccountingFrame>
  );
}

function EntryBody({ entry, group, locale }: { entry: EntryDetail; group: Group | null; locale: "en" | "bn" }) {
  const { t } = useLocale();
  const { formatDate, formatDateTime } = useFormat();
  const accounts = useAccounts(true);
  const [editing, setEditing] = useState(false);
  const [voiding, setVoiding] = useState(false);
  const active = entry.status === "ACTIVE";
  const accountName = entry.accountName ? accountLabel({ name: entry.accountName, kind: (accounts.data ?? []).find((a) => a.id === entry.accountId)?.kind ?? null }, locale, t) : "—";
  const counted = entry.quantity != null && entry.unitPrice != null;
  const unit = pickName({ nameEn: entry.unitEn ?? "", nameBn: entry.unitBn }, locale);

  const fields: [string, string][] = [
    [t("accounting.entry.fields.group"), pickName({ nameEn: entry.groupNameEn, nameBn: entry.groupNameBn }, locale)],
    [t("accounting.entry.fields.payee"), entry.payeeName ?? "—"],
    [t("accounting.entries.columns.type"), entry.typeNameEn ? pickName({ nameEn: entry.typeNameEn, nameBn: entry.typeNameBn }, locale) : "—"],
    [t("accounting.entry.fields.date"), formatDate(entry.date)],
    [t("accounting.entry.fields.period"), entry.period ?? "—"],
    [
      t("accounting.entry.fields.counted"),
      counted ? `${entry.quantity} ${unitWithCount(unit, entry.quantity ?? "0", locale)} × ${money(entry.unitPrice, locale)}` : "—",
    ],
    [t("accounting.entries.columns.amount"), entry.kind === "PAYMENT" ? `−${money(entry.amount, locale)}` : money(entry.amount, locale)],
    [t("accounting.entries.columns.account"), accountName],
    [t("accounting.form.reference"), entry.reference ?? "—"],
    [t(entry.kind === "INCOME" ? "accounting.form.receivedFrom" : "accounting.form.paidTo"), entry.counterparty ?? "—"],
    [t("accounting.form.batch"), entry.batchNameEn ? pickName({ nameEn: entry.batchNameEn, nameBn: entry.batchNameBn }, locale) : "—"],
    [t("accounting.form.exam"), entry.liveExamTitleEn ? pickName({ nameEn: entry.liveExamTitleEn, nameBn: entry.liveExamTitleBn }, locale) : "—"],
    [t("accounting.form.description"), entry.description ?? "—"],
    [t("accounting.form.note"), entry.note ?? "—"],
    [t("accounting.entry.fields.recorded"), `${entry.recordedByName} · ${formatDateTime(entry.recordedAt)}`],
  ];

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center gap-2">
        {active ? <Pill tone="ok">{t("accounting.entry.active")}</Pill> : <Pill tone="danger">{t("accounting.entries.voided")}</Pill>}
        {entry.correctedAt ? <Pill tone="muted">{t("accounting.entry.corrected", { date: formatDate(entry.correctedAt) })}</Pill> : null}
      </div>

      <div className="flex flex-wrap gap-2">
        {active && group ? (
          <Button variant="secondary" onClick={() => setEditing(true)}>
            {t("accounting.common.edit")}
          </Button>
        ) : null}
        {active ? (
          <Button variant="ghost" onClick={() => setVoiding(true)}>
            {t("accounting.entry.void")}
          </Button>
        ) : null}
        {entry.kind !== "CHARGE" ? (
          <Button variant="secondary" href={`${ACCOUNTING_HOME}/entries/${entry.id}/voucher`}>
            {t("accounting.entry.voucher")}
          </Button>
        ) : null}
      </div>

      <dl className="grid gap-3 rounded-card border border-border bg-card p-4 sm:grid-cols-2">
        {fields.filter(([label]) => label).map(([label, value]) => (
          <div key={label} className="grid gap-0.5">
            <dt className="text-meta text-muted">{label}</dt>
            <dd className="text-body text-text">{value}</dd>
          </div>
        ))}
      </dl>

      {entry.paired ? (
        <p className="text-body text-text">
          {t("accounting.entry.paired")}:{" "}
          <Link href={`${ACCOUNTING_HOME}/entries/${entry.paired.id}`} className="text-accent-text underline-offset-4 hover:underline">
            #{entry.paired.no} · {t(`accounting.kind.${entry.paired.kind}`)} · {money(entry.paired.amount, locale)}
          </Link>
        </p>
      ) : null}

      {!active ? (
        <section className="grid gap-1 rounded-card border border-danger/40 bg-danger/5 p-4">
          <p className="text-label font-semibold text-danger">{t("accounting.entry.voidedTitle")}</p>
          <p className="text-body text-text">{entry.voidReason ?? "—"}</p>
          <p className="text-meta text-muted">
            {entry.voidedByName ?? "—"}
            {entry.voidedAt ? ` · ${formatDateTime(entry.voidedAt)}` : ""}
          </p>
        </section>
      ) : null}

      <section className="grid gap-3" aria-labelledby="acc-entry-edits">
        <h2 id="acc-entry-edits" className="text-section font-semibold text-text">
          {t("accounting.entry.corrections")}
        </h2>
        {entry.edits.length === 0 ? (
          <p className="text-body text-muted">{t("accounting.entry.noCorrections")}</p>
        ) : (
          <ul className="grid gap-3">
            {entry.edits.map((edit, index) => (
              <li key={`${edit.at}-${index}`} className="grid gap-2 rounded-card border border-border bg-card p-4">
                <p className="text-meta text-muted">
                  {formatDateTime(edit.at)} · {edit.byName}
                </p>
                <p className="text-body text-text">{edit.reason}</p>
                <ul className="grid gap-1 text-meta text-muted">
                  {EDIT_FIELDS.filter((key) => key in edit.after || key in edit.before)
                    .filter((key) => String(edit.before[key] ?? "") !== String(edit.after[key] ?? ""))
                    .map((key) => (
                      <li key={key}>
                        {t(`accounting.edit.fields.${key}`)}: {String(edit.before[key] ?? "—")} → {String(edit.after[key] ?? "—")}
                      </li>
                    ))}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </section>

      {editing && group ? <EntryEditForm entry={entry} group={group} onClose={() => setEditing(false)} /> : null}
      {voiding ? <VoidForm entry={entry} onClose={() => setVoiding(false)} /> : null}
    </div>
  );
}

/**
 * Edit (accounting-plan 4.5): the same fields as the form, filled in, and a reason. A moved class payment can change only its date,
 * amount, account, reference and note. Kind, group and payee never change.
 */
function EntryEditForm({ entry, group, onClose }: { entry: EntryDetail; group: Group; onClose: () => void }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const edit = useEditEntry();
  const types = useGroupTypes(group.id, true);
  const accounts = useAccounts(false);
  const restricted = entry.legacy === "CLASS";
  const [date, setDate] = useState(entry.date);
  const [typeId, setTypeId] = useState(entry.typeId ?? "");
  const [period, setPeriod] = useState(entry.period ?? "");
  const [quantity, setQuantity] = useState(entry.quantity ?? "");
  const [unitPrice, setUnitPrice] = useState(entry.unitPrice ?? "");
  const [amount, setAmount] = useState(String(Math.abs(Number(entry.amount)).toFixed(2)));
  const [accountId, setAccountId] = useState(entry.accountId ?? "");
  const [reference, setReference] = useState(entry.reference ?? "");
  const [counterparty, setCounterparty] = useState(entry.counterparty ?? "");
  const [description, setDescription] = useState(entry.description ?? "");
  const [note, setNote] = useState(entry.note ?? "");
  const [reason, setReason] = useState("");
  const [problem, setProblem] = useState<{ reason?: string; amount?: string; description?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);

  const type: AccType | null = (types.data ?? []).find((item) => item.id === typeId) ?? null;
  const typeCounted = type ? type.kind === "COUNTED" : entry.unitPrice != null;
  // A moved-over charge of a counted type has no price: its amount was typed in the old pages, and stays typed while its type is kept
  // and no price is given (plan 4.4). Its quantity is kept as it is.
  const legacyAmount = entry.legacy != null && entry.unitPrice == null && typeId === entry.typeId && !unitPrice.trim();
  const counted = typeCounted && !legacyAmount;
  // A deduction stays a deduction: only the deduction types are offered for one (plan 4.5).
  const isDeduction = entry.typeKind === "DEDUCTION";
  const offered = (types.data ?? []).filter((item) => (!item.archived && (item.kind === "DEDUCTION") === isDeduction) || item.id === entry.typeId);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found: typeof problem = {};
    if (!reason.trim()) {
      found.reason = t("accounting.common.reasonRequired");
    }
    // A counted type's amount is quantity times price, worked out by the server; every other type takes the typed amount.
    if (!(counted && !restricted) && !parseAmount(amount)) {
      found.amount = t("accounting.form.amountInvalid");
    }
    if (type?.needsDescription && !description.trim()) {
      found.description = t("accounting.errors.description_required");
    }
    setProblem(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    // The API takes the whole form: a field left out is cleared. So the fields this form does not show (batch, exam) are sent as they
    // are, and a moved class entry sends its own values for everything but its money fields (plan 4.5).
    const body: EntryEditBody = {
      date,
      amount: counted && !restricted ? null : parseAmount(amount),
      accountId: entry.kind === "CHARGE" ? undefined : accountId || null,
      reference: reference.trim() || null,
      note: note.trim() || null,
      reason: reason.trim(),
      version: entry.version,
      batchId: entry.batchId ?? null,
      liveExamId: entry.kind === "CHARGE" ? (entry.liveExamId ?? null) : null,
      ...(restricted
        ? {
            typeId: entry.typeId ?? null,
            period: entry.period ?? null,
            quantity: entry.quantity ?? null,
            unitPrice: entry.unitPrice ?? null,
            counterparty: entry.counterparty ?? null,
            description: entry.description ?? null,
          }
        : {
            typeId: typeId || null,
            period: type?.needsPeriod ? period || null : null,
            quantity: counted || legacyAmount ? parseQuantity(quantity) : null,
            unitPrice: counted ? parseAmount(unitPrice) : null,
            counterparty: counterparty.trim() || null,
            description: description.trim() || null,
          }),
    };
    setFormError(null);
    try {
      await edit.mutateAsync({ id: entry.id, body });
      toast({ title: t("accounting.entry.saved"), tone: "ok" });
      onClose();
    } catch (error) {
      setFormError(writeErrorText(error, t));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t("accounting.entry.editTitle", { no: entry.no })}>
      <form className="grid gap-4" onSubmit={(event) => void submit(event)} noValidate>
        <Field label={t("accounting.form.date")} required>
          <TextInput type="date" value={date} max={dhakaToday()} onChange={(event) => setDate(event.target.value)} />
        </Field>
        {!restricted ? (
          <Field label={t("accounting.form.type")}>
            <Select value={typeId} onChange={(event) => setTypeId(event.target.value)}>
              {offered.map((item) => (
                <option key={item.id} value={item.id}>
                  {pickName(item, locale)}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}
        {!restricted && type?.needsPeriod ? (
          <Field label={t("accounting.form.period")}>
            <TextInput type="month" value={period} onChange={(event) => setPeriod(event.target.value)} />
          </Field>
        ) : null}
        {!restricted && counted ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("accounting.form.quantity")}>
              <TextInput inputMode="decimal" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
            </Field>
            <Field label={t("accounting.form.unitPrice")}>
              <TextInput inputMode="decimal" value={unitPrice} onChange={(event) => setUnitPrice(event.target.value)} />
            </Field>
            {quantity && unitPrice ? (
              <p className="text-body text-text sm:col-span-2">{money(countedAmount(quantity, unitPrice), locale)}</p>
            ) : null}
          </div>
        ) : !restricted && legacyAmount ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label={t("accounting.form.quantity")}>
              <TextInput inputMode="decimal" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
            </Field>
            <Field label={t("accounting.form.unitPrice")} hint={t("accounting.entry.legacyPriceHint")}>
              <TextInput inputMode="decimal" value={unitPrice} onChange={(event) => setUnitPrice(event.target.value)} />
            </Field>
            <div className="sm:col-span-2">
              <Field label={t("accounting.form.amount")} required error={problem.amount}>
                <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} invalid={Boolean(problem.amount)} />
              </Field>
            </div>
          </div>
        ) : (
          <Field label={t("accounting.form.amount")} required error={problem.amount}>
            <TextInput inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} invalid={Boolean(problem.amount)} />
          </Field>
        )}
        {entry.kind !== "CHARGE" ? (
          <Field label={t("accounting.form.account")}>
            <Select value={accountId} onChange={(event) => setAccountId(event.target.value)}>
              {(accounts.data ?? []).filter((account) => !account.archived || account.id === entry.accountId).map((account) => (
                <option key={account.id} value={account.id}>
                  {accountLabel(account, locale, t)}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}
        <Field label={t("accounting.form.reference")}>
          <TextInput value={reference} onChange={(event) => setReference(event.target.value)} maxLength={100} />
        </Field>
        {!restricted && (entry.kind === "SPEND" || entry.kind === "INCOME") ? (
          <Field label={t(entry.kind === "INCOME" ? "accounting.form.receivedFrom" : "accounting.form.paidTo")}>
            <TextInput value={counterparty} onChange={(event) => setCounterparty(event.target.value)} maxLength={200} />
          </Field>
        ) : null}
        {!restricted ? (
          <Field label={t("accounting.form.description")} required={Boolean(type?.needsDescription)} error={problem.description}>
            <TextInput value={description} onChange={(event) => setDescription(event.target.value)} maxLength={500} invalid={Boolean(problem.description)} />
          </Field>
        ) : null}
        <Field label={t("accounting.form.note")}>
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} rows={2} />
        </Field>
        <Field label={t("accounting.common.reason")} required hint={t("accounting.common.reasonHint")} error={problem.reason}>
          <Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={2} invalid={Boolean(problem.reason)} />
        </Field>
        {formError ? <p role="alert" className="text-meta text-danger">{formError}</p> : null}
        <div className="sticky bottom-0 flex justify-end gap-2 bg-card pt-2">
          <Button variant="ghost" onClick={onClose}>
            {t("accounting.common.cancel")}
          </Button>
          <Button type="submit" loading={edit.isPending}>
            {edit.isPending ? t("accounting.common.saving") : t("accounting.common.save")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Void (accounting-plan 4.5): a reason is needed. A charge saved with a payment asks whether the payment goes too, ticked by default. */
function VoidForm({ entry, onClose }: { entry: EntryDetail; onClose: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const voidEntry = useVoidEntry();
  const [reason, setReason] = useState("");
  const [alsoPaired, setAlsoPaired] = useState(true);
  const [problem, setProblem] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const paired = entry.paired && entry.paired.status === "ACTIVE" ? entry.paired : null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reason.trim()) {
      setProblem(t("accounting.common.reasonRequired"));
      return;
    }
    setProblem(null);
    setFormError(null);
    try {
      await voidEntry.mutateAsync({ id: entry.id, reason: reason.trim(), alsoVoidPaired: Boolean(paired) && alsoPaired });
      toast({ title: t("accounting.entry.voidedToast"), tone: "ok" });
      onClose();
    } catch (error) {
      setFormError(writeErrorText(error, t));
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()} title={t("accounting.entry.voidTitle", { no: entry.no })}>
      <form className="grid gap-4" onSubmit={(event) => void submit(event)} noValidate>
        <p className="text-body text-muted">{t("accounting.entry.voidBody")}</p>
        <Field label={t("accounting.common.reason")} required hint={t("accounting.common.reasonHint")} error={problem ?? undefined}>
          <Textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={2} invalid={Boolean(problem)} />
        </Field>
        {paired ? (
          <div className="grid gap-1">
            <Switch checked={alsoPaired} onCheckedChange={setAlsoPaired} label={t("accounting.entry.alsoVoidPaired")} />
            <p className="text-meta text-muted">{t("accounting.entry.alsoVoidPairedHint", { no: paired.no })}</p>
          </div>
        ) : null}
        {formError ? <p role="alert" className="text-meta text-danger">{formError}</p> : null}
        <div className="sticky bottom-0 flex justify-end gap-2 bg-card pt-2">
          <Button variant="ghost" onClick={onClose}>
            {t("accounting.common.cancel")}
          </Button>
          <Button type="submit" variant="danger" loading={voidEntry.isPending}>
            {t("accounting.entry.void")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

