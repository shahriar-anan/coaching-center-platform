"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useAddQuestion, useUpdateQuestion, type QuestionInput, type StaffQuestion } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { IconButton } from "@/components/ui/icon-button";
import { NumberInput } from "@/components/ui/number-input";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

export const MIN_OPTIONS = 2;
export const MAX_OPTIONS = 6;

type OptionDraft = { text: string };

type Draft = {
  stem: string;
  marks: string;
  explanation: string;
  options: OptionDraft[];
  /** The index of the one correct option, or `null` until one is chosen. */
  correct: number | null;
};

function draftFrom(question: StaffQuestion | undefined): Draft {
  if (!question) {
    return { stem: "", marks: "1", explanation: "", options: [{ text: "" }, { text: "" }, { text: "" }, { text: "" }], correct: null };
  }
  const options = [...question.options].sort((a, b) => a.position - b.position);
  return {
    stem: question.stem,
    marks: String(question.marks),
    explanation: question.explanation ?? "",
    options: options.map((o) => ({ text: o.text })),
    correct: options.findIndex((o) => o.correct) >= 0 ? options.findIndex((o) => o.correct) : null,
  };
}

/**
 * Stem, marks, optional explanation and two to six options. Choosing a correct
 * option clears the previous one, so there is always exactly one.
 */
export function QuestionEditor({
  examId,
  question,
  nextPosition,
  open,
  onOpenChange,
}: {
  examId: string;
  question?: StaffQuestion;
  nextPosition: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const add = useAddQuestion(examId);
  const update = useUpdateQuestion(examId);
  const [draft, setDraft] = useState<Draft>(() => draftFrom(question));
  const [errors, setErrors] = useState<{ stem?: string; marks?: string; options?: string; correct?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const busy = add.isPending || update.isPending;

  function setOption(index: number, text: string) {
    setDraft((current) => ({
      ...current,
      options: current.options.map((o, i) => (i === index ? { text } : o)),
    }));
  }

  function removeOption(index: number) {
    setDraft((current) => {
      const options = current.options.filter((_, i) => i !== index);
      let correct = current.correct;
      if (correct === index) {
        correct = null;
      } else if (correct !== null && correct > index) {
        correct -= 1;
      }
      return { ...current, options, correct };
    });
  }

  function validate(): QuestionInput | null {
    const next: typeof errors = {};
    if (!draft.stem.trim()) {
      next.stem = t("staffCatalog.question.stemRequired");
    }
    const marks = Number(draft.marks);
    if (!Number.isInteger(marks) || marks < 1) {
      next.marks = t("staffCatalog.question.marksInvalid");
    }
    const filled = draft.options.filter((o) => o.text.trim() !== "");
    if (filled.length < MIN_OPTIONS || filled.length !== draft.options.length) {
      next.options = t("staffCatalog.question.optionsRequired", { min: MIN_OPTIONS });
    }
    if (draft.correct === null || !draft.options[draft.correct]?.text.trim()) {
      next.correct = t("staffCatalog.question.correctRequired");
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return null;
    }
    return {
      stem: draft.stem.trim(),
      marks,
      explanation: draft.explanation.trim() || null,
      position: question?.position ?? nextPosition,
      options: draft.options.map((o, i) => ({ text: o.text.trim(), correct: i === draft.correct })),
    };
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    const body = validate();
    if (!body) {
      return;
    }
    try {
      if (question) {
        await update.mutateAsync({ questionId: question.id, body });
      } else {
        await add.mutateAsync(body);
      }
      toast({ title: t("staffCatalog.question.saved"), tone: "ok" });
      onOpenChange(false);
    } catch (e) {
      setFormError(t(catalogErrorKey(e)));
    }
  }

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
        </div>

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
              <label className="flex min-h-11 shrink-0 items-center gap-2 text-meta">
                <input
                  type="radio"
                  name="correct"
                  checked={draft.correct === index}
                  onChange={() => setDraft((c) => ({ ...c, correct: index }))}
                  className="size-4"
                />
                <span className="sr-only">{t("staffCatalog.question.correctFor", { n: index + 1 })}</span>
                <span aria-hidden="true" className="w-6 text-muted">{index + 1}</span>
              </label>
              <TextInput
                aria-label={t("staffCatalog.question.optionLabel", { n: index + 1 })}
                value={option.text}
                onChange={(e) => setOption(index, e.target.value)}
              />
              {draft.options.length > MIN_OPTIONS ? (
                <IconButton label={t("staffCatalog.question.removeOption", { n: index + 1 })} icon={<Trash2 className="size-4" />} onClick={() => removeOption(index)} />
              ) : null}
            </div>
          ))}
          {draft.options.length < MAX_OPTIONS ? (
            <div>
              <Button
                variant="ghost"
                onClick={() => setDraft((c) => ({ ...c, options: [...c.options, { text: "" }] }))}
              >
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
