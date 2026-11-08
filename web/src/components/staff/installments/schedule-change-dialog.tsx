"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { adminErrorText } from "@/components/staff/access/admin-error";
import { parseMoney, todayInDhaka } from "@/components/staff/enrol/payment-model";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import {
  useChangeInstallmentSchedule,
  type InstallmentPlan,
  type InstallmentRow,
} from "@/lib/api/exam/staff-installments";
import {
  MAX_PLAN_ROWS,
  checkScheduleChange,
  editableRowsFrom,
  frozenCount,
  frozenLinesFrom,
  nextRowDate,
  toPaisa,
  type EditableRow,
  type ScheduleError,
} from "./installment-model";

/**
 * Changes the unpaid rows of a plan. Paid rows are shown and cannot change. The unpaid rows start as they are now, and the whole
 * schedule must add up to the agreed total (plan section 4). Mounted only while open, so each opening starts from the plan as it
 * is now.
 */
export function ScheduleChangeDialog({
  plan,
  schedule,
  onClose,
}: {
  plan: InstallmentPlan;
  schedule: InstallmentRow[];
  onClose: () => void;
}) {
  const { t } = useLocale();
  const { formatMoney, formatDate } = useFormat();
  const { toast } = useToast();
  const change = useChangeInstallmentSchedule(plan.planId);
  const today = todayInDhaka();
  const frozenRows = schedule.slice(0, frozenCount(schedule));
  const frozen = frozenLinesFrom(schedule);
  const totalPaisa = toPaisa(plan.agreedTotal);

  const [rows, setRows] = useState<EditableRow[]>(() => editableRowsFrom(schedule));
  const [tried, setTried] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const check = checkScheduleChange({ totalPaisa, frozen, rows, today });

  // What the rows add up to as typed, counting only the amounts that read as money.
  const typedPaisa = rows.reduce((sum, row) => {
    const amount = parseMoney(row.amountText);
    return amount.ok ? sum + amount.paisa : sum;
  }, frozen.reduce((sum, line) => sum + line.paisa, 0));

  function updateRow(index: number, patch: Partial<EditableRow>) {
    setRows((current) => current.map((row, position) => (position === index ? { ...row, ...patch } : row)));
  }

  function removeRow(index: number) {
    setRows((current) => current.filter((_, position) => position !== index));
  }

  function addRow() {
    setRows((current) => [...current, { amountText: "", dueOn: nextRowDate(current, today) }]);
  }

  /** The message for one row's field, once the admin has tried to save. */
  function rowMessage(index: number, field: "amount" | "date"): string | undefined {
    if (!tried || check.ok) {
      return undefined;
    }
    const found: ScheduleError | undefined = check.errors.find((error) => error.row === index && (field === "date" ? error.code.startsWith("date") : !error.code.startsWith("date")));
    return found ? t(`staffAdmin.installments.schedule.error.${found.code}`) : undefined;
  }

  /** The message for the whole schedule, such as the count or the total, once the admin has tried to save. */
  function scheduleMessage(): string | undefined {
    if (!tried || check.ok) {
      return undefined;
    }
    const found = check.errors.find((error) => error.row === undefined);
    return found ? t(`staffAdmin.installments.schedule.error.${found.code}`) : undefined;
  }

  async function save() {
    setTried(true);
    setFailure(null);
    if (!check.ok) {
      return;
    }
    try {
      await change.mutateAsync(check.schedule);
      toast({ title: t("staffAdmin.installments.schedule.done"), tone: "ok" });
      onClose();
    } catch (caught) {
      setFailure(adminErrorText(caught, t));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !change.isPending) {
          onClose();
        }
      }}
      title={t("staffAdmin.installments.schedule.title")}
      description={t("staffAdmin.installments.schedule.body")}
      className="max-w-2xl"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={change.isPending}>
            {t("common.actions.cancel")}
          </Button>
          <Button loading={change.isPending} disabled={change.isPending} onClick={() => void save()}>
            {t("staffAdmin.installments.schedule.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {frozenRows.length > 0 ? (
          <ol className="grid gap-2" aria-label={t("staffAdmin.installments.schedule.fixedHeading")}>
            {frozenRows.map((row) => (
              <li
                key={row.seq}
                className="flex flex-wrap items-center justify-between gap-2 rounded-control border border-border bg-bg px-3.5 py-2.5 text-meta"
              >
                <span className="font-medium text-text">{t("staffAdmin.installments.schedule.installment", { n: row.seq })}</span>
                <span className="tabular-nums text-muted">{formatDate(row.dueOn)}</span>
                <span className="tabular-nums text-text">{formatMoney(row.amount) ?? ""}</span>
                <span className="text-muted">{t("staffAdmin.installments.schedule.fixed")}</span>
              </li>
            ))}
          </ol>
        ) : null}

        <ol className="grid gap-3">
          {rows.map((row, index) => (
            <li key={index} className="grid gap-3 rounded-control border border-border-control bg-card p-3.5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-label font-medium text-text">
                  {t("staffAdmin.installments.schedule.installment", { n: frozenRows.length + index + 1 })}
                </span>
                {rows.length > 1 ? (
                  <Button variant="ghost" size="sm" onClick={() => removeRow(index)}>
                    {t("staffAdmin.installments.schedule.remove")}
                  </Button>
                ) : null}
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label={t("staffAdmin.installments.schedule.amount")} error={rowMessage(index, "amount")} required>
                  <TextInput
                    inputMode="decimal"
                    value={row.amountText}
                    invalid={Boolean(rowMessage(index, "amount"))}
                    onChange={(event) => updateRow(index, { amountText: event.target.value })}
                  />
                </Field>
                <Field label={t("staffAdmin.installments.schedule.date")} error={rowMessage(index, "date")} required>
                  <TextInput
                    type="date"
                    min={today}
                    value={row.dueOn}
                    invalid={Boolean(rowMessage(index, "date"))}
                    onChange={(event) => updateRow(index, { dueOn: event.target.value })}
                  />
                </Field>
              </div>
            </li>
          ))}
        </ol>

        {frozenRows.length + rows.length < MAX_PLAN_ROWS ? (
          <Button variant="secondary" className="justify-self-start" onClick={addRow}>
            {t("staffAdmin.installments.schedule.add")}
          </Button>
        ) : null}

        <div className="grid gap-1 rounded-control border border-border bg-bg px-3.5 py-2.5 text-meta">
          <span className="tabular-nums text-text">
            {t("staffAdmin.installments.schedule.total", { amount: formatMoney(typedPaisa / 100) ?? "" })}
          </span>
          <span className="tabular-nums text-muted">
            {t("staffAdmin.installments.schedule.target", { amount: formatMoney(plan.agreedTotal) ?? "" })}
          </span>
        </div>

        {scheduleMessage() ? (
          <p role="alert" className="text-meta text-danger">
            {scheduleMessage()}
          </p>
        ) : null}
        {failure ? (
          <p role="alert" className="text-meta text-danger">
            {failure}
          </p>
        ) : null}
      </div>
    </Dialog>
  );
}
