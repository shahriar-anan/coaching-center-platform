"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import type { ClassRow } from "@/lib/api/exam/instructor-pay";
import { useEditClass, useSetRate } from "@/lib/api/exam/offline-batch-pay";
import { offlineErrorText } from "@/components/staff/offline/offline-error";
import { useFormat } from "@/lib/format/use-format";
import { hasAmount, hasOwnRate } from "./pay-model";

/** A class whose amount can still change: not finished yet, or finished and not settled. */
function isOpen(row: ClassRow): boolean {
  return row.status === "SCHEDULED" || row.status === "HELD";
}

const MONEY = /^\d+(\.\d{1,2})?$/;

/**
 * Sets the price of a class from the instructor's page (17 Nov), so the admin need not open the batch's rates first. The amount goes
 * either to the instructor's rate in the batch, which every unsettled class of theirs there without its own rate takes, or to this
 * class only, as its rate override.
 */
export function SetRateDialog({
  row,
  classes,
  instructorName,
  batchName,
  onOpenChange,
}: {
  row: ClassRow;
  /** Every class of this instructor, to say what a batch rate changes. */
  classes: ClassRow[];
  instructorName: string;
  batchName: string;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const setRate = useSetRate(row.batchId);
  const editClass = useEditClass();
  // A class that already has its own rate is changed on that class; otherwise the batch rate is the usual choice.
  const [scope, setScope] = useState<"batch" | "class">(hasOwnRate(row) ? "class" : "batch");
  const [amount, setAmount] = useState(hasAmount(row) ? String(Number(row.amount)) : "");
  const [error, setError] = useState("");
  const busy = setRate.isPending || editClass.isPending;
  const ownRate = hasOwnRate(row);

  // What a batch rate changes (17 Nov): the unpaid classes in this batch without their own rate, plus this one, whose own rate is
  // removed. Classes paid or waiting keep their amount; other classes with their own rate keep it.
  const inBatch = classes.filter((item) => item.batchId === row.batchId && item.status !== "CANCELLED" && item.status !== "NOT_TAKEN");
  const changes = inBatch.filter((item) => isOpen(item) && (!hasOwnRate(item) || item.id === row.id)).length;
  const settled = inBatch.filter((item) => !isOpen(item)).length;
  const keepOwn = inBatch.filter((item) => isOpen(item) && hasOwnRate(item) && item.id !== row.id).length;

  async function submit() {
    const value = amount.trim();
    if (!MONEY.test(value) || Number(value) <= 0) {
      setError(t("instructorPay.rate.errors.amount"));
      return;
    }
    setError("");
    try {
      if (scope === "batch") {
        if (!row.instructorId) return;
        await setRate.mutateAsync({ instructorId: row.instructorId, rate: value });
        // This class had its own rate: remove it, so the class follows the batch rate like the others.
        if (ownRate) {
          await editClass.mutateAsync({ id: row.id, body: { version: row.version, rateOverride: null } });
        }
      } else {
        await editClass.mutateAsync({ id: row.id, body: { version: row.version, rateOverride: value } });
      }
      await queryClient.invalidateQueries();
      toast({ title: t(scope === "batch" ? "instructorPay.rate.savedBatch" : "instructorPay.rate.savedClass", { batch: batchName }), tone: "ok" });
      onOpenChange(false);
    } catch (problem) {
      setError(offlineErrorText(problem, t));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={onOpenChange}
      title={t("instructorPay.rate.title")}
      description={t("instructorPay.rate.body", { name: instructorName, batch: batchName })}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={busy}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={busy}>
            {t("instructorPay.rate.submit")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <Field label={t("instructorPay.rate.amount")} error={error || undefined} required>
          <TextInput
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            invalid={Boolean(error)}
            className="font-latin tabular-nums"
          />
        </Field>
        <fieldset className="grid gap-2 rounded-control border border-border-control p-3">
          <legend className="px-1 text-label font-medium text-text">{t("instructorPay.rate.scope")}</legend>
          <label className="flex min-h-11 cursor-pointer items-start gap-2 text-body text-text">
            <input
              type="radio"
              name="rate-scope"
              className="mt-1 size-4 accent-accent"
              checked={scope === "batch"}
              onChange={() => setScope("batch")}
              disabled={!row.instructorId}
            />
            <span className="grid gap-1">
              <span>{t("instructorPay.rate.scopeBatch", { name: instructorName, batch: batchName })}</span>
              {scope === "batch" ? (
                <span className="grid gap-0.5 text-meta text-muted">
                  <span className="font-medium text-text">{t("instructorPay.rate.changes", { count: formatNumber(changes) })}</span>
                  {settled > 0 ? <span>{t("instructorPay.rate.keepsSettled", { count: formatNumber(settled) })}</span> : null}
                  {keepOwn > 0 ? <span>{t("instructorPay.rate.keepsOwn", { count: formatNumber(keepOwn) })}</span> : null}
                  {ownRate ? <span>{t("instructorPay.rate.dropsOwn")}</span> : null}
                </span>
              ) : null}
            </span>
          </label>
          <label className="flex min-h-11 cursor-pointer items-start gap-2 text-body text-text">
            <input type="radio" name="rate-scope" className="mt-1 size-4 accent-accent" checked={scope === "class"} onChange={() => setScope("class")} />
            <span>{t("instructorPay.rate.scopeClass")}</span>
          </label>
        </fieldset>
      </div>
    </Dialog>
  );
}
