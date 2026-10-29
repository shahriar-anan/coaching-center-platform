"use client";

import { useLocale } from "@/i18n/locale-context";
import { RichTextEditor } from "@/components/ui/rich-text-editor";
import { plainExcerpt } from "@/lib/format/rich-text";
import { richTextValue, type SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

/** The four rich text fields: the description and the rules, in both languages, one under the other (plan 7.2, section 2). */
export function ExamTextFields({ form, set, errors }: SectionFieldsProps) {
  const { t } = useLocale();
  const fields = [
    { key: "descriptionEn", label: t("staffCatalog.examMeta.descriptionEn") },
    { key: "descriptionBn", label: t("staffCatalog.examMeta.descriptionBn") },
    { key: "rulesEn", label: t("staffCatalog.examMeta.rulesEn") },
    { key: "rulesBn", label: t("staffCatalog.examMeta.rulesBn") },
  ] as const;
  return (
    <>
      {fields.map((field) => (
        <div key={field.key} className="grid gap-1.5">
          <RichTextEditor
            label={field.label}
            value={form[field.key]}
            onChange={(html) => set(field.key, richTextValue(html, form[field.key]))}
            invalid={Boolean(errors[field.key])}
          />
          {errors[field.key] ? (
            <p role="alert" className="text-meta text-danger">
              {errors[field.key]}
            </p>
          ) : null}
          <p className="text-meta text-muted">
            <span className="font-medium">{t("staffCatalog.examMeta.plainPreview")}: </span>
            {plainExcerpt(form[field.key], 240) || t("staffCatalog.examMeta.plainPreviewEmpty")}
          </p>
        </div>
      ))}
    </>
  );
}
