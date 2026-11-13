"use client";

import { useLocale } from "@/i18n/locale-context";
import { formatDate } from "@/lib/format";
import type { AdmissionForm } from "@/lib/api/exam/offline-admission";
import { FORM_GROUPS } from "./offline-model";

/** One form field's value as the office reads it: a date in the day order, a gender in words, an empty field as a dash. */
function valueText(form: AdmissionForm, key: keyof AdmissionForm, t: (key: string) => string, locale: "bn" | "en"): string {
  const value = form[key];
  if (value === null || value === undefined || value === "") {
    return "—";
  }
  if (key === "dateOfBirth") {
    return formatDate(String(value), locale);
  }
  if (key === "gender") {
    return value === "MALE" ? t("admission.gender.male") : value === "FEMALE" ? t("admission.gender.female") : String(value);
  }
  return String(value);
}

/** The student's form, read-only, in its groups. Nothing here can be edited: staff send the form back instead (plan C14). */
export function FormReadOnly({ form }: { form: AdmissionForm }) {
  const { t, locale } = useLocale();
  return (
    <div className="grid gap-4">
      {FORM_GROUPS.map((group) => (
        <section key={group.key} aria-labelledby={`form-group-${group.key}`} className="grid gap-3 rounded-card border border-border-control bg-card p-4">
          <h3 id={`form-group-${group.key}`} className="text-card-title text-text">
            {t(`staffOffline.form.groups.${group.key}`)}
          </h3>
          <dl className="grid gap-x-5 gap-y-3 sm:grid-cols-2">
            {group.fields.map((key) => (
              <div key={key} className="grid min-w-0 gap-0.5 border-b border-border-control/60 pb-2">
                <dt className="text-meta text-muted">{t(`admission.field.${key}`)}</dt>
                <dd className="whitespace-pre-line break-words text-body text-text">{valueText(form, key, t, locale)}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
    </div>
  );
}
