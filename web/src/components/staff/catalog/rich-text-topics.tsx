"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Pill } from "@/components/ui/pill";
import { RichTextView } from "@/components/ui/rich-text-view";
import { LocalizedRichText, isRichTextEmpty } from "@/components/staff/catalog/localized-rich-text";

type Language = "en" | "bn";

/** One topic written in both languages (a description, or the rules), with the text last saved. */
export type RichTextTopic = {
  title: string;
  valueEn: string;
  valueBn: string;
  savedEn: string;
  savedBn: string;
  errorEn?: string;
  errorBn?: string;
  onChange: (language: Language, html: string) => void;
};

/**
 * One topic as students read it: a white card on the grey page, with the English text and the Bangla text in their own
 * panels, each named by a pill, so the two languages are clearly separate.
 */
function TopicPreview({ topic }: { topic: RichTextTopic }) {
  const { t } = useLocale();
  return (
    <section className="grid gap-4 rounded-card border border-border bg-card p-5 shadow-sm">
      <h3 className="text-label font-semibold text-text">{topic.title}</h3>
      {[
        { label: t("staffCatalog.richText.english"), value: topic.valueEn },
        { label: t("staffCatalog.richText.bangla"), value: topic.valueBn },
      ].map((part) => (
        <div key={part.label} className="grid justify-items-start gap-3 rounded-control border border-border bg-bg p-4">
          <Pill tone="accent">{part.label}</Pill>
          <RichTextView value={part.value} />
        </div>
      ))}
    </section>
  );
}

/**
 * A group of rich text topics, each in English and Bangla. Until some text is saved, or while there are unsaved changes,
 * it shows one editor box per topic, each with its own Save. Once any text is saved it turns into a preview of how
 * students will read them, with an Edit button at the top. Cancel, when some text is saved, discards the changes.
 * The texts are optional (D18), so a group with nothing saved is valid.
 */
export function RichTextTopics({
  topics,
  onSave,
  saving,
  previewNote,
}: {
  topics: RichTextTopic[];
  onSave: () => void;
  saving?: boolean;
  previewNote: string;
}) {
  const { t } = useLocale();
  const hasSaved = topics.some((topic) => !isRichTextEmpty(topic.savedEn) || !isRichTextEmpty(topic.savedBn));
  const dirty = topics.some((topic) => topic.valueEn !== topic.savedEn || topic.valueBn !== topic.savedBn);
  const failed = topics.some((topic) => Boolean(topic.errorEn || topic.errorBn));
  const [editing, setEditing] = useState(!hasSaved || dirty);

  // A save, or a cancel, that leaves nothing unsaved in a group with saved text returns to the preview. (State adjusted
  // while rendering, not in an effect.)
  const [wasDirty, setWasDirty] = useState(dirty);
  if (dirty !== wasDirty) {
    setWasDirty(dirty);
    if (wasDirty && !dirty && hasSaved && !saving && !failed) {
      setEditing(false);
    }
  }

  if (!editing) {
    return (
      <div className="grid gap-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-meta text-muted">{previewNote}</p>
          <Button variant="secondary" onClick={() => setEditing(true)}>
            {t("staffCatalog.richText.edit")}
          </Button>
        </div>
        {topics.map((topic) => (
          <TopicPreview key={topic.title} topic={topic} />
        ))}
      </div>
    );
  }

  function cancel() {
    for (const topic of topics) {
      topic.onChange("en", topic.savedEn);
      topic.onChange("bn", topic.savedBn);
    }
    setEditing(false);
  }

  return (
    <>
      {hasSaved ? (
        <div className="flex justify-end">
          <Button variant="ghost" onClick={cancel} disabled={saving}>
            {t("common.actions.cancel")}
          </Button>
        </div>
      ) : null}
      {topics.map((topic) => (
        <LocalizedRichText
          key={topic.title}
          title={topic.title}
          valueEn={topic.valueEn}
          valueBn={topic.valueBn}
          onChange={topic.onChange}
          errorEn={topic.errorEn}
          errorBn={topic.errorBn}
          savedEn={topic.savedEn}
          savedBn={topic.savedBn}
          onSave={onSave}
          saving={saving}
        />
      ))}
    </>
  );
}
