"use client";

import { useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Pill } from "@/components/ui/pill";
import { Sheet } from "@/components/ui/sheet";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useGroupTypes } from "@/lib/api/accounting/account-types";
import { useGroupPayees } from "@/lib/api/accounting/payees";
import { useCopySource, useCreateEntries } from "@/lib/api/accounting/entries";
import type { CopyDraft, EntryInput, Group } from "@/lib/api/accounting/types";
import { dhakaToday } from "@/components/staff/offline/offline-model";
import {
  countedAmount,
  money,
  monthLabel,
  monthOf,
  parseAmount,
  parseQuantity,
  pickName,
  shiftMonth,
  writeErrorText,
} from "../accounting-model";

type Edit = { included: boolean; amount: string; unitPrice: string };

/** A draft without the two fields only the copy needs (`sourceEntryId`, `alreadyThisMonth`): the shape the API takes. */
export function draftInput(draft: CopyDraft): EntryInput {
  return Object.fromEntries(Object.entries(draft).filter(([key]) => key !== "sourceEntryId" && key !== "alreadyThisMonth")) as EntryInput;
}

/**
 * Copy last month (B7): last month's charges (Payees) or spending (Log) of the group, ticked, each with its amount editable. A draft that
 * already looks added this month starts unticked, so pressing Copy twice does not double the month. Payments are never copied.
 */
export function CopyMonthSheet({ group, onClose }: { group: Group; onClose: () => void }) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const today = dhakaToday();
  const [month, setMonth] = useState(shiftMonth(monthOf(today), -1));
  const source = useCopySource(group.id, month);
  const types = useGroupTypes(group.id, true);
  const payees = useGroupPayees(group.id, { archived: true });
  const create = useCreateEntries();
  const [edits, setEdits] = useState<Record<number, Edit>>({});
  const [problem, setProblem] = useState<string | null>(null);

  const drafts = useMemo(() => source.data ?? [], [source.data]);
  const typeName = (id: string | null) => {
    const found = (types.data ?? []).find((item) => item.id === id);
    return found ? pickName(found, locale) : "";
  };
  const payeeName = (id: string | null) => (payees.data ?? []).find((payee) => payee.id === id)?.name ?? "";

  function editFor(index: number, draft: CopyDraft): Edit {
    return edits[index] ?? { included: !draft.alreadyThisMonth, amount: draft.amount, unitPrice: draft.unitPrice ?? "" };
  }

  function isCounted(draft: CopyDraft): boolean {
    return draft.quantity !== null && draft.quantity !== undefined;
  }

  function amountOf(draft: CopyDraft, edit: Edit): string | null {
    if (isCounted(draft)) {
      const quantity = parseQuantity(draft.quantity ?? "");
      const price = parseAmount(edit.unitPrice);
      return quantity && price ? countedAmount(quantity, price) : null;
    }
    return parseAmount(edit.amount);
  }

  function update(index: number, draft: CopyDraft, change: Partial<Edit>) {
    setEdits((current) => ({ ...current, [index]: { ...editFor(index, draft), ...change } }));
  }

  async function save() {
    setProblem(null);
    const chosen: EntryInput[] = [];
    for (let index = 0; index < drafts.length; index += 1) {
      const draft = drafts[index];
      const edit = editFor(index, draft);
      if (!edit.included) {
        continue;
      }
      if (!amountOf(draft, edit)) {
        setProblem(t("accounting.form.amountInvalid"));
        return;
      }
      const counted = isCounted(draft);
      chosen.push({
        ...draftInput(draft),
        unitPrice: counted ? parseAmount(edit.unitPrice) : null,
        amount: counted ? null : parseAmount(edit.amount),
      });
    }
    if (chosen.length === 0) {
      setProblem(t("accounting.copy.noneTicked"));
      return;
    }
    try {
      await create.mutateAsync(chosen);
      toast({ title: t("accounting.common.saved"), tone: "ok" });
      onClose();
    } catch (error) {
      setProblem(writeErrorText(error, t));
    }
  }

  return (
    <Sheet open onOpenChange={(open) => !open && onClose()} title={t("accounting.copy.title", { group: pickName(group, locale) })}>
      <div className="grid gap-4">
        <Field label={t("accounting.copy.from")}>
          <TextInput type="month" value={month} max={monthOf(today)} onChange={(event) => setMonth(event.target.value)} />
        </Field>
        <p className="text-meta text-muted">{t("accounting.copy.intro", { month: monthLabel(month, locale) })}</p>

        {source.isPending ? <p className="text-body text-muted">{t("accounting.common.loading")}</p> : null}
        {!source.isPending && drafts.length === 0 ? <p className="text-body text-muted">{t("accounting.copy.empty")}</p> : null}

        <ul className="grid gap-3">
          {drafts.map((draft, index) => {
            const edit = editFor(index, draft);
            const counted = isCounted(draft);
            const person = payeeName(draft.payeeId ?? null);
            const typeText = typeName(draft.typeId ?? null);
            return (
              <li key={`${draft.sourceEntryId}-${index}`} className="grid gap-3 rounded-card border border-border p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="grid gap-0.5">
                    <span className="font-medium text-text">{person || typeText}</span>
                    {person ? <span className="text-meta text-muted">{typeText}</span> : null}
                  </div>
                  {draft.alreadyThisMonth ? <Pill tone="warn">{t("accounting.copy.alreadyNote")}</Pill> : null}
                </div>
                <Switch checked={edit.included} onCheckedChange={(on) => update(index, draft, { included: on })} label={t("accounting.copy.include")} />
                <div className="grid gap-3 sm:grid-cols-2">
                  {counted ? (
                    <Field label={t("accounting.form.unitPrice")}>
                      <TextInput inputMode="decimal" value={edit.unitPrice} onChange={(event) => update(index, draft, { unitPrice: event.target.value })} />
                    </Field>
                  ) : (
                    <Field label={t("accounting.form.amount")}>
                      <TextInput inputMode="decimal" value={edit.amount} onChange={(event) => update(index, draft, { amount: event.target.value })} />
                    </Field>
                  )}
                  <p className="self-end text-body text-text">{money(amountOf(draft, edit), locale)}</p>
                </div>
              </li>
            );
          })}
        </ul>

        {problem ? <p role="alert" className="text-meta text-danger">{problem}</p> : null}
        <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-border bg-card px-4 py-3 sm:static sm:mx-0 sm:border-0 sm:p-0">
          <Button variant="ghost" onClick={onClose}>
            {t("accounting.common.cancel")}
          </Button>
          <Button loading={create.isPending} onClick={() => void save()} disabled={drafts.length === 0}>
            {create.isPending ? t("accounting.common.saving") : t("accounting.common.save")}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
