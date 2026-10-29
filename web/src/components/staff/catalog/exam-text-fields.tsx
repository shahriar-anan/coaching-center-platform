"use client";

import { useLocale } from "@/i18n/locale-context";
import { LocalizedRichText } from "@/components/staff/catalog/localized-rich-text";
import { richTextValue, type SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

/** The description and the rules: one rich text box each, with an English / বাংলা switch (plan 7.2, section 2). */
export function ExamTextFields({ form, set, errors }: SectionFieldsProps) {
  const { t } = useLocale();
  return (
    <>
      <LocalizedRichText
        title={t("staffCatalog.richText.descriptionTitle")}
        valueEn={form.descriptionEn}
        valueBn={form.descriptionBn}
        onChange={(language, html) =>
          language === "en"
            ? set("descriptionEn", richTextValue(html, form.descriptionEn))
            : set("descriptionBn", richTextValue(html, form.descriptionBn))
        }
        errorEn={errors.descriptionEn}
        errorBn={errors.descriptionBn}
      />
      <LocalizedRichText
        title={t("staffCatalog.richText.rulesTitle")}
        valueEn={form.rulesEn}
        valueBn={form.rulesBn}
        onChange={(language, html) =>
          language === "en"
            ? set("rulesEn", richTextValue(html, form.rulesEn))
            : set("rulesBn", richTextValue(html, form.rulesBn))
        }
        errorEn={errors.rulesEn}
        errorBn={errors.rulesBn}
      />
    </>
  );
}
