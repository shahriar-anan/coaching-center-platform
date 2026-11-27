"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Sheet } from "@/components/ui/sheet";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useGroupTypes } from "@/lib/api/accounting/account-types";
import { useGroupPayees, useLastPrices } from "@/lib/api/accounting/payees";
import { useCreateEntries } from "@/lib/api/accounting/entries";
import type { AccType, EntryInput, Group } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import { ApiError } from "@/lib/api/errors";
import { countedAmount, errorLine, money, monthOf, parseAmount, parseQuantity, pickName, writeErrorText } from "../accounting-model";

/** The lines of one several-save are at most this many (plan 4.4). */
const MAX_LINES = 200;

type Line = { quantity: string; unitPrice: string; amount: string };

/**
 * Add for several payees (B6): one charge type, date, month, description and note once; then a line for each ticked person, with their
 * own quantity and price (or amount), starting from their last price. Saved together or not at all.
 */
export function SeveralSheet({ group, onClose }: { group: Group; onClose: () => void }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const types = useGroupTypes(group.id, false);
  const payees = useGroupPayees(group.id, { archived: false });
  const create = useCreateEntries();
  const today = dhakaToday();

  const [typeId, setTypeId] = useState("");
  const [date, setDate] = useState(today);
  const [period, setPeriod] = useState("");
  const [description, setDescription] = useState("");
  const [note, setNote] = useState("");
  const [lines, setLines] = useState<Record<string, Line>>({});
  const [problem, setProblem] = useState<string | null>(null);

  const typeList = (types.data ?? []).filter((item) => !item.archived);
  const type: AccType | null = typeList.find((item) => item.id === typeId) ?? null;
  const counted = type?.kind === "COUNTED";
  const ticked = (payees.data ?? []).filter((payee) => lines[payee.id]);

  function toggle(payeeId: string) {
    setLines((current) => {
      const next = { ...current };
      if (next[payeeId]) {
        delete next[payeeId];
      } else {
        next[payeeId] = { quantity: "", unitPrice: "", amount: "" };
      }
      return next;
    });
  }

  function lineAmount(line: Line): string | null {
    if (counted) {
      const quantity = parseQuantity(line.quantity);
      const price = parseAmount(line.unitPrice);
      return quantity && price ? countedAmount(quantity, price) : null;
    }
    return parseAmount(line.amount);
  }

  const total = ticked.reduce((sum, payee) => {
    const amount = lineAmount(lines[payee.id]);
    return amount ? sum + Number(amount) : sum;
  }, 0);

  async function save() {
    setProblem(null);
    if (!type) {
      setProblem(t("accounting.form.typeRequired"));
      return;
    }
    if (ticked.length === 0) {
      setProblem(t("accounting.several.noneTicked"));
      return;
    }
    if (ticked.length > MAX_LINES) {
      setProblem(t("accounting.errors.too_many_lines"));
      return;
    }
    if (date > today) {
      setProblem(t("accounting.errors.future_date"));
      return;
    }
    if (type.needsPeriod && !/^\d{4}-(0[1-9]|1[0-2])$/.test(period || monthOf(date))) {
      setProblem(t("accounting.errors.period_required"));
      return;
    }
    if (type.needsDescription && !description.trim()) {
      setProblem(t("accounting.errors.description_required"));
      return;
    }
    const entries: EntryInput[] = [];
    for (const payee of ticked) {
      const line = lines[payee.id];
      if (!lineAmount(line)) {
        setProblem(`${payee.name}: ${t(counted ? "accounting.errors.quantity_required" : "accounting.form.amountInvalid")}`);
        return;
      }
      entries.push({
        kind: "CHARGE",
        groupId: group.id,
        payeeId: payee.id,
        typeId: type.id,
        date,
        period: type.needsPeriod ? period || monthOf(date) : null,
        quantity: counted ? parseQuantity(line.quantity) : null,
        unitPrice: counted ? parseAmount(line.unitPrice) : null,
        amount: counted ? null : parseAmount(line.amount),
        description: description.trim() || null,
        note: note.trim() || null,
      });
    }
    try {
      await create.mutateAsync(entries);
      toast({ title: t("accounting.common.saved"), tone: "ok" });
      onClose();
    } catch (error) {
      // The API names the line by its place among the ticked payees; show the payee's name instead.
      const line = error instanceof ApiError ? errorLine(error) : null;
      const text = writeErrorText(error, t, { withLine: false });
      setProblem(line !== null && ticked[line] ? `${ticked[line].name}: ${text}` : text);
    }
  }

  return (
    <Sheet open onOpenChange={(open) => !open && onClose()} title={t("accounting.several.title", { group: pickName(group, locale) })}>
      <div className="grid gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("accounting.form.type")} required>
            <Select
              value={typeId}
              onChange={(event) => {
                // A new type starts each ticked line empty, so each person's last price for that type can fill it.
                setTypeId(event.target.value);
                setLines((current) =>
                  Object.fromEntries(Object.keys(current).map((id) => [id, { quantity: "", unitPrice: "", amount: "" }])),
                );
              }}
            >
              <option value="">{t("accounting.form.chooseType")}</option>
              {typeList.map((item) => (
                <option key={item.id} value={item.id}>
                  {pickName(item, locale)}
                </option>
              ))}
            </Select>
          </Field>
          <Field label={t("accounting.form.date")} required>
            <TextInput type="date" value={date} max={today} onChange={(event) => setDate(event.target.value)} />
          </Field>
          {type?.needsPeriod ? (
            <Field label={t("accounting.form.period")} required hint={t("accounting.form.periodHint")}>
              <TextInput type="month" value={period || monthOf(date)} onChange={(event) => setPeriod(event.target.value)} />
            </Field>
          ) : null}
          <Field label={t("accounting.form.description")} required={Boolean(type?.needsDescription)}>
            <TextInput value={description} onChange={(event) => setDescription(event.target.value)} maxLength={500} />
          </Field>
        </div>
        <Field label={t("accounting.form.note")}>
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} maxLength={500} rows={2} />
        </Field>

        <h3 className="text-label font-semibold text-text">{t("accounting.several.people")}</h3>
        <ul className="grid gap-3">
          {(payees.data ?? []).map((payee) => {
            const line = lines[payee.id];
            return (
              <li key={payee.id} className="grid gap-3 rounded-card border border-border p-3">
                <label className="flex min-h-10 items-center gap-3">
                  <input type="checkbox" checked={Boolean(line)} onChange={() => toggle(payee.id)} className="size-5" />
                  <span className="font-medium text-text">{payee.name}</span>
                </label>
                {line && type ? (
                  <LineFields
                    payeeId={payee.id}
                    typeId={type.id}
                    counted={counted}
                    line={line}
                    amount={lineAmount(line)}
                    onChange={(next) => setLines((current) => ({ ...current, [payee.id]: next }))}
                  />
                ) : null}
              </li>
            );
          })}
        </ul>
        {payees.data && payees.data.length === 0 ? <p className="text-body text-muted">{t("accounting.payees.none")}</p> : null}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-3">
          <span className="text-label text-text">
            {t("accounting.several.total", { count: ticked.length })} · <strong>{money(total.toFixed(2), locale)}</strong>
          </span>
          {problem ? <Pill tone="danger">{problem}</Pill> : null}
        </div>
        <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-border bg-card px-4 py-3 sm:static sm:mx-0 sm:border-0 sm:p-0">
          <Button variant="ghost" onClick={onClose}>
            {t("accounting.common.cancel")}
          </Button>
          <Button loading={create.isPending} onClick={() => void save()}>
            {create.isPending ? t("accounting.common.saving") : t("accounting.common.save")}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

/** One person's line: quantity and price for a counted type, else the amount. It starts from their last price. */
function LineFields({
  payeeId,
  typeId,
  counted,
  line,
  amount,
  onChange,
}: {
  payeeId: string;
  typeId: string;
  counted: boolean;
  line: Line;
  amount: string | null;
  onChange: (line: Line) => void;
}) {
  const { t, locale } = useLocale();
  const lastPrices = useLastPrices(payeeId);
  const last = lastPrices.data?.find((row) => row.typeId === typeId) ?? null;
  // The price for a counted type; the amount (as a positive number) for a plain one, such as a monthly salary (plan A7).
  const suggestion = !last ? null : counted ? last.unitPrice : Math.abs(Number(last.amount)).toFixed(2);
  const prefilledFor = useRef<string | null>(null);
  useEffect(() => {
    // Each type's first suggestion fills an empty field once. Later edits belong to the person typing.
    if (!suggestion || prefilledFor.current === typeId) {
      return;
    }
    prefilledFor.current = typeId;
    if (counted && !line.unitPrice) {
      onChange({ ...line, unitPrice: suggestion });
    } else if (!counted && !line.amount) {
      onChange({ ...line, amount: suggestion });
    }
    // The line is read when the suggestion arrives; it is not a dependency on purpose.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [suggestion, counted, typeId]);

  if (!counted) {
    return (
      <Field label={t("accounting.form.amount")} required>
        <TextInput inputMode="decimal" value={line.amount} onChange={(event) => onChange({ ...line, amount: event.target.value })} />
      </Field>
    );
  }
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <Field label={t("accounting.form.quantity")} required>
        <TextInput inputMode="decimal" value={line.quantity} onChange={(event) => onChange({ ...line, quantity: event.target.value })} />
      </Field>
      <Field label={t("accounting.form.unitPrice")} required>
        <TextInput inputMode="decimal" value={line.unitPrice} onChange={(event) => onChange({ ...line, unitPrice: event.target.value })} />
      </Field>
      <p className="self-end text-body text-text" aria-live="polite">
        {amount ? money(amount, locale) : "—"}
      </p>
    </div>
  );
}
