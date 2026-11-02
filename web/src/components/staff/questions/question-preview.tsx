"use client";

import { useLocale } from "@/i18n/locale-context";
import type { QuestionDraft } from "@/components/staff/catalog/question-draft";
import { optionLetter } from "@/components/staff/questions/question-form";

/**
 * The question as a student meets it in the sitting: the stem, then the options with their letters. It reads the draft,
 * so it moves as the editor is typed in. Nothing is saved from here.
 */
export function QuestionPreview({ draft }: { draft: QuestionDraft }) {
  const { t, locale } = useLocale();
  // Keep each option's own index, so a blank option in the middle does not shift the letters after it.
  const filled = draft.options.map((option, index) => ({ text: option.text, index })).filter((option) => option.text.trim() !== "");
  return (
    <div className="grid content-start gap-4 rounded-card border border-border bg-card p-4 shadow-[var(--card-shadow)]">
      <p className="text-meta font-semibold uppercase tracking-wide text-muted">{t("staffCatalog.questionPage.previewHeading")}</p>
      {draft.stem.trim() ? (
        <p className="whitespace-pre-line font-medium">{draft.stem}</p>
      ) : (
        <p className="text-meta text-muted">{t("staffCatalog.questionPage.previewHint")}</p>
      )}
      <ul className="grid gap-2">
        {filled.map((option) => (
          <li key={option.index} className="flex items-start gap-3 rounded-control border border-border px-3 py-2 text-body">
            <span aria-hidden="true" className="w-5 shrink-0 font-semibold text-muted">
              {optionLetter(option.index, locale)}
            </span>
            <span>{option.text}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
