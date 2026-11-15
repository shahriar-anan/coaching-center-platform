"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import {
  useCreateBatch,
  useRemoveBatchPhoto,
  useSaveBatch,
  useUploadBatchPhoto,
  type BatchBody,
  type BatchView,
} from "@/lib/api/exam/offline-admin";
import { BatchPhotoField, type BatchPhotoChange } from "./batch-photo-field";
import { offlineErrorText } from "./offline-error";

/** The description limit of the API (plan 3.1). */
const DESCRIPTION_MAX = 2000;

/**
 * Create a batch, or edit one (Master Admin only; the parent decides who sees the button). A batch's end date and fee are only the
 * defaults for each admission, so editing one never changes an admission that is already onboarded. A batch is offline and carries no
 * exams or packages (plan P17): those are chosen per student in the admission setup. The photo is saved after the batch itself, so a
 * batch being created gets it once it has an id.
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
  const uploadPhoto = useUploadBatchPhoto();
  const removePhoto = useRemoveBatchPhoto();
  const [nameEn, setNameEn] = useState(batch?.nameEn ?? "");
  const [nameBn, setNameBn] = useState(batch?.nameBn ?? "");
  const [startsOn, setStartsOn] = useState(batch?.startsOn ?? "");
  const [endsOn, setEndsOn] = useState(batch?.endsOn ?? "");
  const [fee, setFee] = useState(batch ? String(batch.defaultMonthlyFee) : "");
  const [rollStart, setRollStart] = useState(batch?.rollStart ?? "");
  const [whatsappUrl, setWhatsappUrl] = useState(batch?.whatsappUrl ?? "");
  const [descriptionEn, setDescriptionEn] = useState(batch?.descriptionEn ?? "");
  const [descriptionBn, setDescriptionBn] = useState(batch?.descriptionBn ?? "");
  const [admissionsOpen, setAdmissionsOpen] = useState(batch?.admissionsOpen ?? true);
  const [displayOrder, setDisplayOrder] = useState(batch ? String(batch.displayOrder) : "0");
  const [photo, setPhoto] = useState<BatchPhotoChange>({ kind: "none" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const busy = create.isPending || save.isPending || uploadPhoto.isPending || removePhoto.isPending;

  function validate(): Record<string, string> {
    const problems: Record<string, string> = {};
    if (!nameEn.trim()) problems.nameEn = t("staffOffline.batches.errors.name");
    if (!nameBn.trim()) problems.nameBn = t("staffOffline.batches.errors.name");
    if (!endsOn) problems.endsOn = t("staffOffline.batches.errors.endsOn");
    const value = Number(fee);
    if (!fee.trim() || !Number.isFinite(value) || value <= 0) problems.defaultMonthlyFee = t("staffOffline.batches.errors.fee");
    if (startsOn && endsOn && startsOn > endsOn) problems.endsOn = t("staffOffline.batches.errors.order");
    if (descriptionEn.length > DESCRIPTION_MAX || descriptionBn.length > DESCRIPTION_MAX) {
      problems.description = t("staffOffline.batches.catalogue.errors.description");
    }
    if (!/^\d+$/.test(displayOrder.trim())) problems.displayOrder = t("staffOffline.batches.catalogue.errors.displayOrder");
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
      descriptionEn: descriptionEn.trim() || null,
      descriptionBn: descriptionBn.trim() || null,
      admissionsOpen,
      displayOrder: Number(displayOrder.trim()),
      rollStart: rollStart.trim() || null,
      whatsappUrl: whatsappUrl.trim() || null,
    };
    let saved: BatchView;
    try {
      saved = batch ? await save.mutateAsync(body) : await create.mutateAsync(body);
    } catch (error) {
      const fields = error instanceof ApiError && error.fieldErrors ? Object.fromEntries(error.fieldErrors.map((p) => [p.field, p.message])) : {};
      setErrors({ form: offlineErrorText(error, t), ...fields });
      return;
    }

    // The photo is a second request. The batch exists by now, so a failure here is reported, and the dialog closes: the Edit button
    // on the batch finishes the job.
    let photoFailed = false;
    try {
      if (photo.kind === "set") {
        await uploadPhoto.mutateAsync({ id: saved.id, file: photo.file });
      } else if (photo.kind === "remove") {
        await removePhoto.mutateAsync({ id: saved.id });
      }
    } catch {
      photoFailed = true;
    }
    if (photoFailed) {
      toast({ title: t("staffOffline.batches.catalogue.photoFailed"), tone: "danger" });
    } else {
      toast({ title: t(batch ? "staffOffline.batches.saved" : "staffOffline.batches.created"), tone: "ok" });
    }
    onSaved?.(saved);
    onOpenChange(false);
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t(batch ? "staffOffline.batches.editTitle" : "staffOffline.batches.createTitle")}
      description={t("staffOffline.batches.formNote")}
      className="sm:max-w-3xl!"
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
      <div className="grid gap-5">
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
          <Field label={t("staffOffline.batches.whatsapp")} hint={t("staffOffline.batches.whatsappHelp")} error={errors.whatsappUrl}>
            <TextInput type="url" inputMode="url" placeholder="https://chat.whatsapp.com/..." value={whatsappUrl} onChange={(event) => setWhatsappUrl(event.target.value)} invalid={Boolean(errors.whatsappUrl)} />
          </Field>
          <Field label={t("staffOffline.batches.rollStart")} hint={t("staffOffline.batches.rollStartHelp")} error={errors.rollStart}>
            <TextInput inputMode="numeric" value={rollStart} onChange={(event) => setRollStart(event.target.value)} invalid={Boolean(errors.rollStart)} />
          </Field>
          <Field label={t("staffOffline.batches.catalogue.displayOrder")} hint={t("staffOffline.batches.catalogue.displayOrderHelp")} error={errors.displayOrder}>
            <TextInput inputMode="numeric" value={displayOrder} onChange={(event) => setDisplayOrder(event.target.value)} invalid={Boolean(errors.displayOrder)} />
          </Field>
        </div>

        <div className="grid gap-1">
          <div className="flex items-center justify-between gap-3">
            <span className="text-label font-medium text-text">{t("staffOffline.batches.catalogue.admissionsOpen")}</span>
            <Switch
              checked={admissionsOpen}
              onCheckedChange={setAdmissionsOpen}
              label={t("staffOffline.batches.catalogue.admissionsOpen")}
            />
          </div>
          <p className="text-meta text-muted">{t("staffOffline.batches.catalogue.admissionsOpenHelp")}</p>
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <Field label={t("staffOffline.batches.catalogue.descriptionEn")} error={errors.description}>
            <Textarea rows={4} value={descriptionEn} onChange={(event) => setDescriptionEn(event.target.value)} invalid={Boolean(errors.description)} />
          </Field>
          <Field label={t("staffOffline.batches.catalogue.descriptionBn")} error={errors.description}>
            <Textarea rows={4} value={descriptionBn} onChange={(event) => setDescriptionBn(event.target.value)} invalid={Boolean(errors.description)} />
          </Field>
        </div>
        <p className="-mt-3 text-meta text-muted">{t("staffOffline.batches.catalogue.descriptionHelp")}</p>

        <BatchPhotoField
          storedPath={batch ? `/api/v1/offline-batches/${batch.id}/photo` : null}
          hasPhoto={batch?.photoUploaded ?? false}
          change={photo}
          onChange={setPhoto}
        />

      </div>
    </Dialog>
  );
}
