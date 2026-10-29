"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { RichTextEditor } from "@/components/ui/rich-text-editor";
import { RichTextView } from "@/components/ui/rich-text-view";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { plainExcerpt } from "@/lib/format/rich-text";

type Language = "en" | "bn";

const isEmpty = (text: string) => plainExcerpt(text, 1).trim() === "";

/**
 * One rich text box for a topic written in both languages: a bold title, an English / বাংলা switch, and the
 * editor of the chosen language. Both values are kept; the switch only changes which one the box edits.
 * A language with nothing written shows a dot on its switch, and an error in the other language moves the box there.
 *
 * With `onSave`, the box also tracks what is saved. Once there is saved text it opens as a preview, the way students
 * read it, with an Edit button; a save that clears every change returns to the preview.
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
  /** The last saved texts. With `onSave`, the box shows whether each language is saved and offers a Save button. */
  savedEn?: string;
  savedBn?: string;
  onSave?: () => void;
  saving?: boolean;
}) {
  const { t } = useLocale();
  const dirtyEn = savedEn !== undefined && valueEn !== savedEn;
  const dirtyBn = savedBn !== undefined && valueBn !== savedBn;
  const anyDirty = dirtyEn || dirtyBn;
  const hasSaved = onSave !== undefined && !(isEmpty(savedEn ?? "") && isEmpty(savedBn ?? ""));

  const [language, setLanguage] = useState<Language>(errorBn && !errorEn ? "bn" : "en");
  const [editing, setEditing] = useState(!hasSaved || anyDirty);
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

  // After a save, or a cancel, that leaves nothing unsaved, the box goes back to the preview.
  const [wasDirty, setWasDirty] = useState(anyDirty);
  if (anyDirty !== wasDirty) {
    setWasDirty(anyDirty);
    if (wasDirty && !anyDirty && hasSaved && !saving && !errorEn && !errorBn) {
      setEditing(false);
    }
  }

  const languageName = t(language === "en" ? "staffCatalog.richText.english" : "staffCatalog.richText.bangla");
  const otherName = t(language === "en" ? "staffCatalog.richText.bangla" : "staffCatalog.richText.english");
  const activeDirty = language === "en" ? dirtyEn : dirtyBn;
  const otherDirty = language === "en" ? dirtyBn : dirtyEn;
  const dot = (text: string) => (isEmpty(text) ? " •" : "");

  const switcher = (
    <SegmentedControl
      ariaLabel={t("staffCatalog.richText.languageLabel")}
      value={language}
      onChange={(next) => setLanguage(next as Language)}
      options={[
        { value: "en", label: `${t("staffCatalog.richText.english")}${dot(valueEn)}` },
        { value: "bn", label: `${t("staffCatalog.richText.bangla")}${dot(valueBn)}` },
      ]}
    />
  );

  if (!editing && onSave) {
    return (
      <div className="grid gap-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-label font-semibold text-text">{title}</h3>
          <div className="flex flex-wrap items-center gap-2">
            {switcher}
            <Button variant="secondary" onClick={() => setEditing(true)}>
              {t("staffCatalog.richText.edit")}
            </Button>
          </div>
        </div>
        <div
          role="region"
          aria-label={t("staffCatalog.richText.editorLabel", { title, language: languageName })}
          className="rounded-control border border-border bg-bg p-4"
        >
          {isEmpty(value) ? (
            <p className="text-body text-muted">{t("staffCatalog.richText.empty")}</p>
          ) : (
            <RichTextView value={value} />
          )}
        </div>
      </div>
    );
  }

  function cancel() {
    onChange("en", savedEn ?? "");
    onChange("bn", savedBn ?? "");
    setEditing(false);
  }

  return (
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-label font-semibold text-text">{title}</h3>
        {switcher}
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
          <div className="flex flex-wrap gap-2">
            {hasSaved ? (
              <Button variant="ghost" onClick={cancel} disabled={saving}>
                {t("common.actions.cancel")}
              </Button>
            ) : null}
            <Button variant="secondary" onClick={onSave} loading={saving} disabled={saving || !anyDirty}>
              {t("staffCatalog.richText.save")}
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
