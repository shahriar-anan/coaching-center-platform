"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useDeskStart, openAdmissionRefusal, type AccessibleBatch, type DeskStart } from "@/lib/api/exam/offline-admin";
import { pickTitle } from "@/lib/format";
import { PHONE_PATTERN } from "@/components/staff/people/new-person-dialog";

/**
 * Starts a desk admission from the phone number alone (plan 6.3). The student is made, and the answer goes to `onCreated`, which shows
 * the code to read out. The rest of the form is the student's, after they activate.
 */
export function DeskStartDialog({
  open,
  onOpenChange,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (desk: DeskStart) => void;
}) {
  const { t, locale } = useLocale();
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);
  // Set when the phone already has an open offline admission (plan P13): the dialog then only closes, with the batch named.
  const [refusal, setRefusal] = useState<{ batch: AccessibleBatch | null } | null>(null);
  const start = useDeskStart();

  function close() {
    setPhone("");
    setError(null);
    setRefusal(null);
    onOpenChange(false);
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = phone.trim();
    if (!PHONE_PATTERN.test(value)) {
      setError(t("staffPeople.form.phoneHint"));
      return;
    }
    setError(null);
    try {
      const desk = await start.mutateAsync(value);
      setPhone("");
      onOpenChange(false);
      onCreated(desk);
    } catch (caught) {
      const open = openAdmissionRefusal(caught);
      if (open) {
        setRefusal(open);
        return;
      }
      const code = caught instanceof ApiError ? caught.code : null;
      setError(code === "DUPLICATE_PHONE" ? t("staffPeople.form.phoneInUse") : t("common.error.body"));
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : close())} title={t("staffOffline.desk.startTitle")} description={t("staffOffline.desk.startBody")}>
      {refusal ? (
        <div className="grid gap-4">
          <p role="alert" className="rounded-control border border-warn/40 bg-warn/10 px-3 py-2 text-body text-text">
            {refusal.batch
              ? t("staffOffline.desk.openElsewhere", { batch: pickTitle({ titleEn: refusal.batch.nameEn, titleBn: refusal.batch.nameBn }, locale) })
              : t("staffOffline.desk.openElsewhereAny")}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="ghost" onClick={close}>
              {t("common.actions.cancel")}
            </Button>
          </div>
        </div>
      ) : (
      <form className="grid gap-4" onSubmit={onSubmit} noValidate>
        <Field label={t("staffPeople.form.phone")} hint={t("staffPeople.form.phoneFormat")} error={error ?? undefined} required>
          <TextInput
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            value={phone}
            onChange={(event) => {
              setPhone(event.target.value);
              setError(null);
            }}
            invalid={Boolean(error)}
          />
        </Field>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" onClick={close}>
            {t("common.actions.cancel")}
          </Button>
          <Button type="submit" loading={start.isPending}>
            {t("staffOffline.desk.create")}
          </Button>
        </div>
      </form>
      )}
    </Dialog>
  );
}
