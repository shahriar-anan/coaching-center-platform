"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
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

/** One topic as students read it: the English text, then the Bangla text. */
function TopicPreview({ topic }: { topic: RichTextTopic }) {
  const { t } = useLocale();
  return (
    <section className="grid gap-3 rounded-control border border-border bg-bg p-4">
      <h3 className="text-label font-semibold text-text">{topic.title}</h3>
      {[
        { label: t("staffCatalog.richText.english"), value: topic.valueEn },
        { label: t("staffCatalog.richText.bangla"), value: topic.valueBn },
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
 * A group of rich text topics, each in English and Bangla. While any text is missing, or there are unsaved changes, it
 * shows one editor box per topic, each with its own Save. Once every text is written and saved it turns into a preview of
 * how students will read them, with an Edit button at the top. Cancel, when the group was complete, discards the changes.
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
  const complete = topics.every((topic) => !isRichTextEmpty(topic.savedEn) && !isRichTextEmpty(topic.savedBn));
  const dirty = topics.some((topic) => topic.valueEn !== topic.savedEn || topic.valueBn !== topic.savedBn);
  const failed = topics.some((topic) => Boolean(topic.errorEn || topic.errorBn));
  const [editing, setEditing] = useState(!complete || dirty);

  // A save, or a cancel, that leaves nothing unsaved in a complete group returns to the preview. (State adjusted while
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
      {complete ? (
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
