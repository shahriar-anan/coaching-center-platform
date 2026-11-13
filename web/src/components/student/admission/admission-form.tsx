"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useLocale } from "@/i18n/locale-context";
import {
  useSaveAdmissionForm,
  useSubmitAdmission,
  type AdmissionForm,
  type AdmissionFormField,
  type AdmissionView,
} from "@/lib/api/exam/offline-admission";
import { ApiError } from "@/lib/api/errors";
import { PhotoField } from "./photo-field";
import { ADDRESS_FIELDS, NUMERIC_FIELDS, PERSONAL_FIELDS } from "./fields";
import { fieldErrorsOf } from "./field-errors";
import { useDraftSaver } from "./use-draft-saver";

/** The date input gives yyyy-MM-dd, the format the API takes. */
const DATE_FIELDS: AdmissionFormField[] = ["dateOfBirth"];
const EMAIL_FIELDS: AdmissionFormField[] = ["email"];
const AUTOCOMPLETE: Partial<Record<AdmissionFormField, string>> = {
  fullName: "name",
  email: "email",
  dateOfBirth: "bday",
};

/**
 * The registration form for a student whose admission is open (plan 6.2). It saves a draft as the student types, shows the
 * office's reason when the form was sent back, and asks before it submits. Field problems from the API are shown under
 * each field they belong to.
 */
export function AdmissionFormView({ admission }: { admission: AdmissionView }) {
  const { t } = useLocale();
  const [form, setForm] = useState<AdmissionForm>(() => ({ ...admission.form }));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [summary, setSummary] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const saveForm = useSaveAdmissionForm();
  const submitForm = useSubmitAdmission();
  const draft = useDraftSaver((next) => saveForm.mutateAsync(next));
  const changesRequested = admission.state === "CHANGES_REQUESTED";
  const submitLabel = changesRequested ? t("admission.submit.again") : t("admission.submit.button");

  // The first render is the saved form, so it is not a change to save.
  const { schedule } = draft;
  const seen = useRef(false);
  useEffect(() => {
    if (!seen.current) {
      seen.current = true;
      return;
    }
    schedule(form);
  }, [form, schedule]);

  // After a failed check, the first wrong box takes the focus, so the student sees where to look.
  useEffect(() => {
    if (Object.keys(errors).length > 0) {
      formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
    }
  }, [errors]);

  function update(key: AdmissionFormField, value: string | null) {
    setForm((previous) => ({ ...previous, [key]: value }));
    setErrors((previous) => {
      if (!(key in previous)) {
        return previous;
      }
      const rest = { ...previous };
      delete rest[key];
      return rest;
    });
  }

  function textValue(key: AdmissionFormField): string {
    const value = form[key];
    return typeof value === "string" ? value : "";
  }

  function textField(key: AdmissionFormField) {
    const numeric = NUMERIC_FIELDS.includes(key);
    const address = ADDRESS_FIELDS.includes(key);
    const error = errors[key];
    const control = address ? (
      <Textarea
        name={key}
        autoComplete="street-address"
        value={textValue(key)}
        invalid={Boolean(error)}
        onChange={(event) => update(key, event.target.value)}
        rows={3}
      />
    ) : (
      <TextInput
        name={key}
        type={DATE_FIELDS.includes(key) ? "date" : EMAIL_FIELDS.includes(key) ? "email" : "text"}
        inputMode={numeric ? "numeric" : undefined}
        autoComplete={AUTOCOMPLETE[key] ?? "off"}
        value={textValue(key)}
        invalid={Boolean(error)}
        onChange={(event) => update(key, event.target.value)}
        className="min-h-12"
      />
    );
    const hint =
      key === "nid" ? t("admission.hint.nid") : key === "mobile" || key === "emergencyMobile" ? t("admission.hint.mobile") : undefined;
    return (
      <Field key={key} label={t(`admission.field.${key}`)} required error={error} hint={hint}>
        {control}
      </Field>
    );
  }

  function genderField() {
    const error = errors.gender;
    const options: { value: "MALE" | "FEMALE"; label: string }[] = [
      { value: "MALE", label: t("admission.gender.male") },
      { value: "FEMALE", label: t("admission.gender.female") },
    ];
    return (
      <fieldset className="grid gap-1.5">
        <legend className="text-label font-medium text-text">
          {t("admission.field.gender")}
          <span className="ms-1 text-muted" aria-hidden="true">
            *
          </span>
          <span className="sr-only"> (required)</span>
        </legend>
        <div className="grid grid-cols-2 gap-3">
          {options.map((option) => {
            const selected = form.gender === option.value;
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={selected}
                onClick={() => update("gender", option.value)}
                className={cn(
                  "min-h-14 rounded-control border px-4 text-body font-medium transition-colors",
                  selected ? "border-accent bg-accent/10 text-text" : "border-border-control bg-field text-text hover:border-muted",
                  error && !selected && "border-danger",
                )}
              >
                {option.label}
              </button>
            );
          })}
        </div>
        {error ? (
          <p className="text-meta text-danger" role="alert">
            {error}
          </p>
        ) : null}
      </fieldset>
    );
  }

  async function submitNow() {
    setSummary(null);
    const saved = await draft.flush();
    if (!saved) {
      throw new Error(t("admission.draft.failed"));
    }
    try {
      await submitForm.mutateAsync();
    } catch (error) {
      if (error instanceof ApiError && error.code === "VALIDATION_FAILED" && error.fieldErrors?.length) {
        setErrors(fieldErrorsOf(error, t));
        setSummary(t("admission.errors.generic"));
        return;
      }
      if (error instanceof ApiError && error.code === "ADMISSION_STATE") {
        setSummary(t("admission.errors.conflict"));
        return;
      }
      throw new Error(t("admission.errors.submitFailed"));
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setConfirmOpen(true);
  }

  return (
    <div className="grid gap-6">
      {changesRequested ? (
        <section
          aria-labelledby="admission-changes-heading"
          className="grid gap-2 rounded-card border border-warn/40 bg-warn/10 p-4"
        >
          <h2 id="admission-changes-heading" className="text-label font-semibold text-warn">
            {t("admission.changes.title")}
          </h2>
          <p className="text-meta text-text">{t("admission.changes.reason")}</p>
          <p className="whitespace-pre-line text-body text-text">{admission.changesReason}</p>
          <p className="text-meta text-muted">{t("admission.changes.lead")}</p>
        </section>
      ) : null}

      <header className="grid gap-2">
        <h1 className="text-title tracking-tight text-text">{t("admission.title")}</h1>
        {!changesRequested ? <p className="text-body text-muted">{t("admission.lead")}</p> : null}
      </header>

      <form ref={formRef} onSubmit={onSubmit} noValidate className="grid gap-6">
        <section aria-labelledby="admission-personal-heading" className="grid gap-4 rounded-card border border-border bg-card p-4">
          <h2 id="admission-personal-heading" className="text-label font-semibold text-text">
            {t("admission.section.personal")}
          </h2>
          {PERSONAL_FIELDS.map((key) => (key === "gender" ? <div key={key}>{genderField()}</div> : textField(key)))}
        </section>

        <PhotoField hasPhoto={admission.hasPhoto} error={errors.photo} />

        <section aria-labelledby="admission-address-heading" className="grid gap-4 rounded-card border border-border bg-card p-4">
          <h2 id="admission-address-heading" className="text-label font-semibold text-text">
            {t("admission.section.address")}
          </h2>
          {ADDRESS_FIELDS.map((key) => textField(key))}
        </section>

        <div className="grid gap-3" aria-live="polite">
          <DraftNote status={draft.status} onRetry={() => void draft.flush()} />
          {summary ? (
            <p role="alert" className="text-body text-danger">
              {summary}
            </p>
          ) : null}
          <Button type="submit" size="lg" fullWidth>
            {submitLabel}
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={changesRequested ? t("admission.submit.confirmAgain") : t("admission.submit.confirmTitle")}
        body={t("admission.submit.confirmBody")}
        confirmLabel={changesRequested ? t("admission.submit.confirmAgain") : t("admission.submit.confirmButton")}
        onConfirm={submitNow}
      />
    </div>
  );
}

function DraftNote({ status, onRetry }: { status: "idle" | "saving" | "saved" | "failed"; onRetry: () => void }) {
  const { t } = useLocale();
  if (status === "saving") {
    return <p className="text-meta text-muted">{t("admission.draft.saving")}</p>;
  }
  if (status === "saved") {
    return <p className="text-meta text-muted">{t("admission.draft.saved")}</p>;
  }
  if (status === "failed") {
    return (
      <p className="flex flex-wrap items-center gap-2 text-meta text-danger">
        {t("admission.draft.failed")}
        <Button type="button" variant="ghost" size="sm" onClick={onRetry}>
          {t("admission.draft.retry")}
        </Button>
      </p>
    );
  }
  return null;
}
