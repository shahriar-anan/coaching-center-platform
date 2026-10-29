"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { RichTextEditor } from "@/components/ui/rich-text-editor";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { plainExcerpt } from "@/lib/format/rich-text";

type Language = "en" | "bn";

export const isRichTextEmpty = (text: string) => plainExcerpt(text, 1).trim() === "";

/**
 * One rich text box for a topic written in both languages: a bold title, an English / বাংলা switch, and the
 * editor of the chosen language. Both values are kept; the switch only changes which one the box edits.
 * A language with nothing written shows a dot on its switch, and an error in the other language moves the box there.
 *
 * With `onSave`, the box also tracks what is saved: it says whether the open language is saved and offers a Save button.
 */
export function LocalizedRichText({
  title,
  valueEn,
  valueBn,
  onChange,
  errorEn,
  errorBn,
  savedEn,
  savedBn,
  onSave,
  saving,
}: {
  title: string;
  valueEn: string;
  valueBn: string;
  onChange: (language: Language, html: string) => void;
  errorEn?: string;
  errorBn?: string;
  /** The last saved texts, to tell what is unsaved. */
  savedEn?: string;
  savedBn?: string;
  onSave?: () => void;
  saving?: boolean;
}) {
  const { t } = useLocale();
  const [language, setLanguage] = useState<Language>(errorBn && !errorEn ? "bn" : "en");
  const value = language === "en" ? valueEn : valueBn;
  const error = language === "en" ? errorEn : errorBn;
  const otherError = language === "en" ? errorBn : errorEn;

  // A save that fails on the other language would otherwise leave the error out of sight: when the errors change, the box
  // moves to the language that has one. (State adjusted while rendering, not in an effect.)
  const signature = `${errorEn ?? ""}|${errorBn ?? ""}`;
  const [seen, setSeen] = useState(signature);
  if (signature !== seen) {
    setSeen(signature);
    if (!error && otherError) {
      setLanguage(language === "en" ? "bn" : "en");
    }
  }

  const languageName = t(language === "en" ? "staffCatalog.richText.english" : "staffCatalog.richText.bangla");
  const otherName = t(language === "en" ? "staffCatalog.richText.bangla" : "staffCatalog.richText.english");
  const dirtyEn = savedEn !== undefined && valueEn !== savedEn;
  const dirtyBn = savedBn !== undefined && valueBn !== savedBn;
  const anyDirty = dirtyEn || dirtyBn;
  const activeDirty = language === "en" ? dirtyEn : dirtyBn;
  const otherDirty = language === "en" ? dirtyBn : dirtyEn;
  const dot = (text: string) => (isRichTextEmpty(text) ? " •" : "");

  return (
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-label font-semibold text-text">{title}</h3>
        <SegmentedControl
          ariaLabel={t("staffCatalog.richText.languageLabel")}
          value={language}
          onChange={(next) => setLanguage(next as Language)}
          options={[
            { value: "en", label: `${t("staffCatalog.richText.english")}${dot(valueEn)}` },
            { value: "bn", label: `${t("staffCatalog.richText.bangla")}${dot(valueBn)}` },
          ]}
        />
      </div>
      <RichTextEditor
        key={language}
        hideLabel
        label={t("staffCatalog.richText.editorLabel", { title, language: languageName })}
        value={value}
        onChange={(html) => onChange(language, html)}
        invalid={Boolean(error)}
      />
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
      <p className="text-meta text-muted">
        <span className="font-medium">{t("staffCatalog.examMeta.plainPreview")}: </span>
        {plainExcerpt(value, 240) || t("staffCatalog.richText.empty")}
      </p>
      {onSave ? (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p role="status" className={anyDirty ? "text-meta font-medium text-warn" : "text-meta font-medium text-ok-text"}>
            {activeDirty
              ? t("staffCatalog.richText.dirty", { language: languageName })
              : otherDirty
                ? t("staffCatalog.richText.otherDirty", { language: languageName, other: otherName })
                : t("staffCatalog.richText.saved", { language: languageName })}
          </p>
          <Button variant="secondary" onClick={onSave} loading={saving} disabled={saving || !anyDirty}>
            {t("staffCatalog.richText.save")}
          </Button>
        </div>
      ) : null}
    </div>
  );
}
