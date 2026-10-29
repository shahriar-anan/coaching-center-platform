"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { RichTextView } from "@/components/ui/rich-text-view";
import { LocalizedRichText, isRichTextEmpty } from "@/components/staff/catalog/localized-rich-text";
import { richTextValue, type SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

const TEXT_KEYS = ["descriptionEn", "descriptionBn", "rulesEn", "rulesBn"] as const;

/** One topic as students read it: the English text, then the Bangla text. */
function TopicPreview({ title, en, bn }: { title: string; en: string; bn: string }) {
  const { t } = useLocale();
  return (
    <section className="grid gap-3 rounded-control border border-border bg-bg p-4">
      <h3 className="text-label font-semibold text-text">{title}</h3>
      {[
        { label: t("staffCatalog.richText.english"), value: en },
        { label: t("staffCatalog.richText.bangla"), value: bn },
      ].map((part) => (
        <div key={part.label} className="grid gap-1 border-t border-border pt-3 first-of-type:border-t-0 first-of-type:pt-0">
          <p className="text-meta font-semibold text-muted">{part.label}</p>
          <RichTextView value={part.value} />
        </div>
      ))}
    </section>
  );
}

/**
 * The description and the rules, each in English and Bangla (plan 7.2, section 2). While any of the four is missing, or
 * there are unsaved changes, the section shows two editor boxes, one per topic. Once all four are written and saved it
 * turns into a preview of how students will read them, with an Edit button at the top.
 */
export function ExamTextFields({ form, set, errors, saved, saving, onSave }: SectionFieldsProps) {
  const { t } = useLocale();
  const complete = TEXT_KEYS.every((key) => !isRichTextEmpty(saved[key]));
  const dirty = TEXT_KEYS.some((key) => form[key] !== saved[key]);
  const failed = TEXT_KEYS.some((key) => Boolean(errors[key]));
  const [editing, setEditing] = useState(!complete || dirty);

  // A save, or a cancel, that leaves nothing unsaved in a complete section returns to the preview. (State adjusted while
  // rendering, not in an effect.)
  const [wasDirty, setWasDirty] = useState(dirty);
  if (dirty !== wasDirty) {
    setWasDirty(dirty);
    if (wasDirty && !dirty && complete && !saving && !failed) {
      setEditing(false);
    }
  }

  if (!editing) {
    return (
      <div className="grid gap-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-meta text-muted">{t("staffCatalog.richText.previewNote")}</p>
          <Button variant="secondary" onClick={() => setEditing(true)}>
            {t("staffCatalog.richText.edit")}
          </Button>
        </div>
        <TopicPreview title={t("staffCatalog.richText.descriptionTitle")} en={form.descriptionEn} bn={form.descriptionBn} />
        <TopicPreview title={t("staffCatalog.richText.rulesTitle")} en={form.rulesEn} bn={form.rulesBn} />
      </div>
    );
  }

  function cancel() {
    set("descriptionEn", saved.descriptionEn);
    set("descriptionBn", saved.descriptionBn);
    set("rulesEn", saved.rulesEn);
    set("rulesBn", saved.rulesBn);
    setEditing(false);
  }

  return (
    <>
      {complete ? (
        <div className="flex justify-end">
          <Button variant="ghost" onClick={cancel} disabled={saving}>
            {t("common.actions.cancel")}
          </Button>
        </div>
      ) : null}
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
        savedEn={saved.descriptionEn}
        savedBn={saved.descriptionBn}
        onSave={onSave}
        saving={saving}
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
        savedEn={saved.rulesEn}
        savedBn={saved.rulesBn}
        onSave={onSave}
        saving={saving}
      />
    </>
  );
}
