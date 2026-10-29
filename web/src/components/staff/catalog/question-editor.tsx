"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useAddQuestion, useUpdateQuestion, type StaffQuestion } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { IconButton } from "@/components/ui/icon-button";
import { NumberInput } from "@/components/ui/number-input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey, isApiError } from "@/components/staff/catalog/errors";
import {
  MAX_OPTIONS,
  MIN_OPTIONS,
  blankDraft,
  draftFromQuestion,
  keepOneCorrect,
  questionRequest,
  removeOptionAt,
  setMultipleAnswer,
  toggleCorrect,
  validateDraft,
  type DraftErrors,
  type QuestionDraft,
} from "@/components/staff/catalog/question-draft";

/**
 * Stem, marks, optional explanation and two to six options. A single-answer question has one correct option (radio
 * buttons); "Select all that apply" makes it multiple-answer (check boxes). Once a student has started the exam the
 * options cannot be added or removed (structureLocked); the wording, marks and explanation stay editable.
 */
export function QuestionEditor({
  examId,
  question,
  nextPosition,
  open,
  onOpenChange,
  structureLocked,
  onStructureLocked,
}: {
  examId: string;
  question?: StaffQuestion;
  nextPosition: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  structureLocked: boolean;
  onStructureLocked: () => void;
}) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const { toast } = useToast();
  const add = useAddQuestion(examId);
  const update = useUpdateQuestion(examId);
  const [draft, setDraft] = useState<QuestionDraft>(() => (question ? draftFromQuestion(question) : blankDraft()));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  // Set when a multiple-answer question with several ticked is switched to single: the teacher picks the one to keep.
  const [keepChoice, setKeepChoice] = useState(false);
  const busy = add.isPending || update.isPending;

  function setOption(index: number, text: string) {
    setDraft((current) => ({
      ...current,
      options: current.options.map((o, i) => (i === index ? { text } : o)),
    }));
  }

  function removeOption(index: number) {
    setDraft((current) => removeOptionAt(current, index));
  }

  function switchKind(on: boolean) {
    const next = setMultipleAnswer(draft, on);
    setDraft(next.draft);
    setKeepChoice(next.keepChoice);
  }

  function validate(): boolean {
    const found: DraftErrors = validateDraft(draft);
    const messages: Record<string, string> = {};
    for (const [field, key] of Object.entries(found)) {
      messages[field] = t(key as string);
    }
    setErrors(messages);
    return Object.keys(messages).length === 0;
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (keepChoice) {
      setFormError(t("staffCatalog.question.keepPrompt"));
      return;
    }
    if (!validate()) {
      return;
    }
    const body = questionRequest(draft, question?.position ?? nextPosition);
    try {
      if (question) {
        await update.mutateAsync({ questionId: question.id, body });
      } else {
        await add.mutateAsync(body);
      }
      toast({ title: t("staffCatalog.question.saved"), tone: "ok" });
      onOpenChange(false);
    } catch (e) {
      if (isApiError(e, "QUESTION_STRUCTURE_LOCKED")) {
        onStructureLocked();
      }
      setFormError(t(catalogErrorKey(e)));
    }
  }

  const isCorrect = (index: number) => draft.correct.includes(index);

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!busy) {
          onOpenChange(next);
        }
      }}
      title={question ? t("staffCatalog.question.editTitle") : t("staffCatalog.question.addTitle")}
      className="sm:max-w-2xl"
    >
      <form onSubmit={submit} noValidate className="grid gap-4">
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
              onCheckedChange={(on) => switchKind(on)}
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
                <Button key={index} variant="secondary" onClick={() => {
                  setDraft((current) => keepOneCorrect(current, index));
                  setKeepChoice(false);
                }}>
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
          {structureLocked ? <p className="text-meta text-muted">{t("staffCatalog.question.lockedOptions")}</p> : null}
          {draft.options.map((option, index) => (
            <div key={index} className="flex items-center gap-2">
              <label className="flex min-h-11 shrink-0 items-center gap-2 text-meta">
                {draft.multipleAnswer ? (
                  <input
                    type="checkbox"
                    checked={isCorrect(index)}
                    onChange={() => setDraft((c) => toggleCorrect(c, index))}
                    className="size-4"
                  />
                ) : (
                  <input
                    type="radio"
                    name="correct"
                    checked={isCorrect(index)}
                    onChange={() => setDraft((c) => toggleCorrect(c, index))}
                    className="size-4"
                  />
                )}
                <span className="sr-only">{t("staffCatalog.question.correctFor", { n: formatNumber(index + 1) })}</span>
                <span aria-hidden="true" className="w-6 text-muted">{index + 1}</span>
              </label>
              <TextInput
                aria-label={t("staffCatalog.question.optionLabel", { n: formatNumber(index + 1) })}
                value={option.text}
                onChange={(e) => setOption(index, e.target.value)}
              />
              {draft.options.length > MIN_OPTIONS && !structureLocked ? (
                <IconButton label={t("staffCatalog.question.removeOption", { n: formatNumber(index + 1) })} icon={<Trash2 className="size-4" />} onClick={() => removeOption(index)} />
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
        </fieldset>

        <Field label={t("staffCatalog.question.explanation")} hint={t("staffCatalog.question.explanationHint")}>
          <Textarea rows={2} value={draft.explanation} onChange={(e) => setDraft((c) => ({ ...c, explanation: e.target.value }))} />
        </Field>

        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={busy}>
            {t("common.actions.cancel")}
          </Button>
          <Button type="submit" loading={busy} disabled={busy}>
            {t("staffCatalog.question.save")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
