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
  ADMISSION_PHOTO_PATH,
  useSaveAdmissionForm,
  useSubmitAdmission,
  useUploadAdmissionPhoto,
  type AdmissionForm,
  type AdmissionFormField,
  type AdmissionView,
} from "@/lib/api/exam/offline-admission";
import { ApiError } from "@/lib/api/errors";
import { PhotoField } from "./photo-field";
import { ADDRESS_FIELDS, NUMERIC_FIELDS, OPTIONAL_FIELDS, PERSONAL_FIELDS } from "./fields";
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
 * Where the form sends its changes. A signed-in student uses their own admission (`useAccountFormApi`). A visitor who applies
 * without signing in uses the public application routes (`usePublicFormApi`), so the same form serves both (plan 8, A and B).
 */
export type AdmissionFormApi = {
  /** Saves the whole form as a draft. */
  save: (form: AdmissionForm) => Promise<unknown>;
  /** Sends the picked photo. The form is given too, so an application not yet started can be started from its email. */
  uploadPhoto: (file: File, form: AdmissionForm) => Promise<unknown>;
  /** Sends the form to the office. */
  submit: (form: AdmissionForm) => Promise<AdmissionView>;
  /** The authorised path the saved photo is read from, or null when it cannot be read. */
  photoPath: string | null;
};

/** The signed-in student's own admission: the `/api/v1/me/admission/...` routes, which answer the newest admission. */
export function useAccountFormApi(): AdmissionFormApi {
  const saveForm = useSaveAdmissionForm();
  const uploadPhoto = useUploadAdmissionPhoto();
  const submitForm = useSubmitAdmission();
  return {
    save: (form) => saveForm.mutateAsync(form),
    uploadPhoto: (file) => uploadPhoto.mutateAsync(file),
    submit: () => submitForm.mutateAsync(),
    photoPath: ADMISSION_PHOTO_PATH,
  };
}

/**
 * The registration form for a student whose admission is open (plan 6.2). It saves a draft as the student types, shows the
 * office's reason when the form was sent back, and asks before it submits. Field problems from the API are shown under
 * each field they belong to. `api` defaults to the signed-in student's own admission; `onSubmitted` runs once the office has the form.
 */
export function AdmissionFormView({
  admission,
  api,
  onSubmitted,
}: {
  admission: AdmissionView;
  api?: AdmissionFormApi;
  onSubmitted?: (view: AdmissionView) => void;
}) {
  const { t } = useLocale();
  const account = useAccountFormApi();
  const formApi = api ?? account;
  const [form, setForm] = useState<AdmissionForm>(() => localNumbers(admission.form));
  // "Same as present address" (16 Nov): ticked when the form already has both addresses the same.
  const [sameAddress, setSameAddress] = useState(() => {
    const present = typeof admission.form.presentAddress === "string" ? admission.form.presentAddress.trim() : "";
    const permanent = typeof admission.form.permanentAddress === "string" ? admission.form.permanentAddress.trim() : "";
    return present !== "" && present === permanent;
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [summary, setSummary] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const draft = useDraftSaver((next) => formApi.save(next));
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
    // While "Same as present address" is ticked, the permanent address follows the present one; typing another one unticks it.
    if (key === "permanentAddress" && sameAddress && value !== textValue("presentAddress")) {
      setSameAddress(false);
    }
    setForm((previous) =>
      key === "presentAddress" && sameAddress ? { ...previous, presentAddress: value, permanentAddress: value } : { ...previous, [key]: value },
    );
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
      <Field key={key} label={t(`admission.field.${key}`)} required={!OPTIONAL_FIELDS.includes(key)} error={error} hint={hint}>
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
    let view: AdmissionView;
    try {
      view = await formApi.submit(form);
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
    onSubmitted?.(view);
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

        <PhotoField
          hasPhoto={admission.hasPhoto}
          error={errors.photo}
          photoPath={formApi.photoPath}
          onSend={(file) => formApi.uploadPhoto(file, form)}
        />

        <section aria-labelledby="admission-address-heading" className="grid gap-4 rounded-card border border-border bg-card p-4">
          <h2 id="admission-address-heading" className="text-label font-semibold text-text">
            {t("admission.section.address")}
          </h2>
          {textField("presentAddress")}
          <label className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-body text-text">
            <input
              type="checkbox"
              className="size-5 accent-accent"
              checked={sameAddress}
              onChange={(event) => {
                const checked = event.target.checked;
                setSameAddress(checked);
                if (checked) update("permanentAddress", textValue("presentAddress"));
              }}
            />
            {t("admission.sameAddress")}
          </label>
          {textField("permanentAddress")}
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

/** The API keeps a sent form's numbers as +8801...; the form shows them as the student types them, 01... (the hint asks for that). */
function localNumbers(form: AdmissionForm): AdmissionForm {
  const local = (value: string | null | undefined) => (value && /^\+8801\d{9}$/.test(value) ? value.slice(3) : value);
  return { ...form, mobile: local(form.mobile), emergencyMobile: local(form.emergencyMobile) };
}
