"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useCreateBatch, useSaveBatch, type BatchBody, type BatchView } from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";

/**
 * Create a batch, or edit one (Master Admin only; the parent decides who sees the button). A batch's end date and fee are only the
 * defaults for each admission, so editing one never changes an admission that is already onboarded.
 */
export function BatchFormDialog({
  batch,
  open,
  onOpenChange,
  onSaved,
}: {
  batch: BatchView | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved?: (batch: BatchView) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const create = useCreateBatch();
  const save = useSaveBatch(batch?.id ?? "");
  const [nameEn, setNameEn] = useState(batch?.nameEn ?? "");
  const [nameBn, setNameBn] = useState(batch?.nameBn ?? "");
  const [startsOn, setStartsOn] = useState(batch?.startsOn ?? "");
  const [endsOn, setEndsOn] = useState(batch?.endsOn ?? "");
  const [fee, setFee] = useState(batch ? String(batch.defaultMonthlyFee) : "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const busy = create.isPending || save.isPending;

  function validate(): Record<string, string> {
    const problems: Record<string, string> = {};
    if (!nameEn.trim()) problems.nameEn = t("staffOffline.batches.errors.name");
    if (!nameBn.trim()) problems.nameBn = t("staffOffline.batches.errors.name");
    if (!endsOn) problems.endsOn = t("staffOffline.batches.errors.endsOn");
    const value = Number(fee);
    if (!fee.trim() || !Number.isFinite(value) || value <= 0) problems.defaultMonthlyFee = t("staffOffline.batches.errors.fee");
    if (startsOn && endsOn && startsOn > endsOn) problems.endsOn = t("staffOffline.batches.errors.order");
    return problems;
  }

  async function submit() {
    const problems = validate();
    setErrors(problems);
    if (Object.keys(problems).length > 0) {
      return;
    }
    const body: BatchBody = {
      nameEn: nameEn.trim(),
      nameBn: nameBn.trim(),
      startsOn: startsOn || null,
      endsOn,
      defaultMonthlyFee: fee.trim(),
    };
    try {
      const saved = batch ? await save.mutateAsync(body) : await create.mutateAsync(body);
      toast({ title: t(batch ? "staffOffline.batches.saved" : "staffOffline.batches.created"), tone: "ok" });
      onSaved?.(saved);
      onOpenChange(false);
    } catch (error) {
      const fields = error instanceof ApiError && error.fieldErrors ? Object.fromEntries(error.fieldErrors.map((p) => [p.field, p.message])) : {};
      setErrors({ form: offlineErrorText(error, t), ...fields });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t(batch ? "staffOffline.batches.editTitle" : "staffOffline.batches.createTitle")}
      description={t("staffOffline.batches.formNote")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void submit()} loading={busy}>
            {t(batch ? "staffOffline.batches.save" : "staffOffline.batches.create")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {errors.form ? <p role="alert" className="text-meta text-danger">{errors.form}</p> : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("staffOffline.batches.nameEn")} error={errors.nameEn} required>
            <TextInput value={nameEn} onChange={(event) => setNameEn(event.target.value)} invalid={Boolean(errors.nameEn)} />
          </Field>
          <Field label={t("staffOffline.batches.nameBn")} error={errors.nameBn} required>
            <TextInput value={nameBn} onChange={(event) => setNameBn(event.target.value)} invalid={Boolean(errors.nameBn)} />
          </Field>
          <Field label={t("staffOffline.batches.startsOn")}>
            <TextInput type="date" value={startsOn} onChange={(event) => setStartsOn(event.target.value)} />
          </Field>
          <Field label={t("staffOffline.batches.endsOn")} error={errors.endsOn} required>
            <TextInput type="date" value={endsOn} onChange={(event) => setEndsOn(event.target.value)} invalid={Boolean(errors.endsOn)} />
          </Field>
          <Field label={t("staffOffline.batches.fee")} error={errors.defaultMonthlyFee} required>
            <TextInput inputMode="decimal" value={fee} onChange={(event) => setFee(event.target.value)} invalid={Boolean(errors.defaultMonthlyFee)} />
          </Field>
        </div>
      </div>
    </Dialog>
  );
}
