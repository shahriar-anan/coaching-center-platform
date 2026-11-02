"use client";

import type { Dispatch, SetStateAction } from "react";
import { Plus, Trash2 } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { IconButton } from "@/components/ui/icon-button";
import { NumberInput } from "@/components/ui/number-input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import {
  MAX_OPTIONS,
  MIN_OPTIONS,
  removeOptionAt,
  toggleCorrect,
  type QuestionDraft,
} from "@/components/staff/catalog/question-draft";

/** The option letters a student sees: A, B, C… or the Bangla letters on a Bangla screen. */
export function optionLetter(index: number, locale: string) {
  return locale === "bn" ? ["ক", "খ", "গ", "ঘ", "ঙ", "চ"][index] : String.fromCharCode(65 + index);
}

/**
 * The fields of one question: wording, marks, the answer kind, the options (each letter ticks the correct answer), and the
 * explanation. The page owns the draft; this form only edits it. A locked question keeps its wording and key editable, but
 * options cannot be added or removed.
 */
export function QuestionForm({
  draft,
  setDraft,
  errors,
  structureLocked,
  keepChoice,
  onKindChange,
  onKeep,
}: {
  draft: QuestionDraft;
  setDraft: Dispatch<SetStateAction<QuestionDraft>>;
  errors: Record<string, string>;
  structureLocked: boolean;
  keepChoice: boolean;
  onKindChange: (on: boolean) => void;
  onKeep: (index: number) => void;
}) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  const isCorrect = (index: number) => draft.correct.includes(index);

  return (
    <div className="grid gap-5">
      <Field label={t("staffCatalog.question.stem")} required error={errors.stem}>
        <Textarea rows={3} value={draft.stem} onChange={(e) => setDraft((c) => ({ ...c, stem: e.target.value }))} />
      </Field>

      <div className="grid gap-4 sm:grid-cols-[10rem_1fr]">
        <Field label={t("staffCatalog.question.marks")} required error={errors.marks}>
          <NumberInput inputMode="numeric" min={1} step={1} value={draft.marks} onChange={(e) => setDraft((c) => ({ ...c, marks: e.target.value }))} />
        </Field>
        <div className="grid gap-1 self-end">
          <Switch
            id="question-multiple"
            checked={draft.multipleAnswer}
            onCheckedChange={(on) => onKindChange(on)}
            label={t("staffCatalog.question.multiple")}
          />
          <p className="text-meta text-muted">{t("staffCatalog.question.multipleHint")}</p>
        </div>
      </div>

      {keepChoice ? (
        <div role="group" aria-label={t("staffCatalog.question.keepPrompt")} className="grid gap-2 rounded-control border border-warn/40 bg-warn/10 p-3">
          <p className="text-meta font-medium">{t("staffCatalog.question.keepPrompt")}</p>
          <div className="flex flex-wrap gap-2">
            {draft.correct.map((index) => (
              <Button key={index} variant="secondary" onClick={() => onKeep(index)}>
                {t("staffCatalog.question.keepOne", { n: formatNumber(index + 1) })}
              </Button>
            ))}
          </div>
        </div>
      ) : null}

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-label font-medium">
          {t("staffCatalog.question.options")} <span className="text-muted">{t("staffCatalog.question.optionsHint")}</span>
        </legend>
        {errors.options ? (
          <p role="alert" className="text-meta text-danger">
            {errors.options}
          </p>
        ) : null}
        {errors.correct ? (
          <p role="alert" className="text-meta text-danger">
            {errors.correct}
          </p>
        ) : null}
        {draft.options.map((option, index) => (
          <div key={index} className="flex items-center gap-2">
            <label
              className={`flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-control border text-label font-semibold ${
                isCorrect(index) ? "border-ok bg-ok/10 text-ok" : "border-border text-muted"
              }`}
            >
              {draft.multipleAnswer ? (
                <input
                  type="checkbox"
                  className="sr-only"
                  checked={isCorrect(index)}
                  onChange={() => setDraft((c) => toggleCorrect(c, index))}
                />
              ) : (
                <input
                  type="radio"
                  name="correct"
                  className="sr-only"
                  checked={isCorrect(index)}
                  onChange={() => setDraft((c) => toggleCorrect(c, index))}
                />
              )}
              <span aria-hidden="true">{optionLetter(index, locale)}</span>
              <span className="sr-only">{t("staffCatalog.question.correctFor", { n: formatNumber(index + 1) })}</span>
            </label>
            <TextInput
              aria-label={t("staffCatalog.question.optionLabel", { n: formatNumber(index + 1) })}
              value={option.text}
              onChange={(e) => {
                const text = e.target.value;
                setDraft((c) => ({ ...c, options: c.options.map((o, i) => (i === index ? { text } : o)) }));
              }}
            />
            {draft.options.length > MIN_OPTIONS && !structureLocked ? (
              <IconButton
                label={t("staffCatalog.question.removeOption", { n: formatNumber(index + 1) })}
                icon={<Trash2 className="size-4" />}
                onClick={() => setDraft((c) => removeOptionAt(c, index))}
              />
            ) : null}
          </div>
        ))}
        {draft.options.length < MAX_OPTIONS && !structureLocked ? (
          <div>
            <Button variant="ghost" onClick={() => setDraft((c) => ({ ...c, options: [...c.options, { text: "" }] }))}>
              <Plus aria-hidden="true" className="size-4" />
              {t("staffCatalog.question.addOption")}
            </Button>
          </div>
        ) : null}
        {structureLocked ? <p className="text-meta text-muted">{t("staffCatalog.question.lockedOptions")}</p> : null}
      </fieldset>

      <Field label={t("staffCatalog.question.explanation")} hint={t("staffCatalog.question.explanationHint")}>
        <Textarea rows={2} value={draft.explanation} onChange={(e) => setDraft((c) => ({ ...c, explanation: e.target.value }))} />
      </Field>
    </div>
  );
}
