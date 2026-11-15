"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useChangeRoll, type AdmissionDetail } from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";

/** A roll number: 4 to 12 digits, as the API checks it. */
const ROLL = /^\d{4,12}$/;

/**
 * Changes an onboarded student's roll number (plan 7.3). The Master Admin only. It says that published results keep the old roll. The
 * input is checked here before Save; the API's field error for the roll shows under the input.
 */
export function RollChangeDialog({
  admission,
  open,
  onOpenChange,
}: {
  admission: AdmissionDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const change = useChangeRoll(admission.id);
  const [roll, setRoll] = useState(admission.rollNo ?? "");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const value = roll.trim();
  const valid = ROLL.test(value);
  const unchanged = value === (admission.rollNo ?? "");
  const error = fieldError ?? (value !== "" && !valid ? t("staffOffline.onboard.rollInvalid") : undefined);

  async function save() {
    if (!valid || unchanged) {
      return;
    }
    setFieldError(null);
    setFormError(null);
    try {
      await change.mutateAsync({ rollNo: value });
      toast({ title: t("staffOffline.rollChange.saved"), tone: "ok" });
      onOpenChange(false);
    } catch (failure) {
      // A roll the batch already uses is named under its field; anything else is a sentence above the buttons.
      const field = failure instanceof ApiError ? failure.fieldErrors?.find((problem) => problem.field === "rollNo") : undefined;
      if (field) {
        setFieldError(field.message);
      } else {
        setFormError(offlineErrorText(failure, t));
      }
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffOffline.rollChange.title")}
      description={t("staffOffline.rollChange.body")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void save()} disabled={!valid || unchanged} loading={change.isPending}>
            {t("staffOffline.rollChange.save")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}
        <Field label={t("staffOffline.rollChange.label")} error={error} required>
          <TextInput
            inputMode="numeric"
            value={roll}
            invalid={Boolean(error)}
            onChange={(event) => {
              setRoll(event.target.value);
              setFieldError(null);
            }}
          />
        </Field>
      </div>
    </Dialog>
  );
}
