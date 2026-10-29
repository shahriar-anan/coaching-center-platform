"use client";

import { useLocale } from "@/i18n/locale-context";
import { RichTextTopics } from "@/components/staff/catalog/rich-text-topics";
import { richTextValue, type SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

/** The description and the rules, each in English and Bangla (plan 7.2, section 2): editors until all four are saved, then a preview. */
export function ExamTextFields({ form, set, errors, saved, saving, onSave }: SectionFieldsProps) {
  const { t } = useLocale();
  return (
    <RichTextTopics
      onSave={onSave}
      saving={saving}
      previewNote={t("staffCatalog.richText.previewNote")}
      topics={[
        {
          title: t("staffCatalog.richText.descriptionTitle"),
          valueEn: form.descriptionEn,
          valueBn: form.descriptionBn,
          savedEn: saved.descriptionEn,
          savedBn: saved.descriptionBn,
          errorEn: errors.descriptionEn,
          errorBn: errors.descriptionBn,
          onChange: (language, html) =>
            language === "en"
              ? set("descriptionEn", richTextValue(html, form.descriptionEn))
              : set("descriptionBn", richTextValue(html, form.descriptionBn)),
        },
        {
          title: t("staffCatalog.richText.rulesTitle"),
          valueEn: form.rulesEn,
          valueBn: form.rulesBn,
          savedEn: saved.rulesEn,
          savedBn: saved.rulesBn,
          errorEn: errors.rulesEn,
          errorBn: errors.rulesBn,
          onChange: (language, html) =>
            language === "en"
              ? set("rulesEn", richTextValue(html, form.rulesEn))
              : set("rulesBn", richTextValue(html, form.rulesBn)),
        },
      ]}
    />
  );
}
