"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import { useFormat } from "@/lib/format/use-format";
import { useAddQuestion, type QuestionInput, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { catalogErrorKey, isApiError } from "@/components/staff/catalog/errors";

/**
 * The paste format (F7). Questions are separated by a blank line. Each block:
 *
 *   প্রশ্ন: বাংলাদেশের রাজধানী কোথায়?
 *   ক) ঢাকা *
 *   খ) চট্টগ্রাম
 *   গ) সিলেট
 *   নম্বর: 2
 *   ব্যাখ্যা: ঢাকা রাজধানী। (optional)
 *
 * `*` marks a correct option. One `*` makes a single-answer question; several make a multiple-answer one
 * (NR-01). Options may use ক–ঘ or a–d. Marks default to 1.
 */
export const PASTE_FORMAT_EXAMPLE = [
  ["প্রশ্ন: বাংলাদেশের রাজধানী কোথায়?", "ক) ঢাকা *", "খ) চট্টগ্রাম", "গ) সিলেট", "নম্বর: 2"].join("\n"),
  ["প্রশ্ন: নিচের কোনগুলো বিভাগীয় শহর?", "ক) ঢাকা *", "খ) রাজশাহী *", "গ) সিলেট", "নম্বর: 2"].join("\n"),
].join("\n\n");

export type ParsedBlock = { index: number; question: QuestionInput } | { index: number; error: ParseError };
export type ParseError = "noStem" | "fewOptions" | "noCorrect" | "badMarks";

const OPTION_LABEL = /^([কখগঘa-dA-D])\)\s*(.+)$/;
const STEM = /^প্রশ্ন:\s*(.+)$/;
const MARKS = /^নম্বর:\s*([0-9০-৯]+)\s*$/;
const EXPLANATION = /^ব্যাখ্যা:\s*(.+)$/;

/** Bengali digits to Latin, so marks written either way read the same. */
function latinDigits(text: string): string {
  return text.replace(/[০-৯]/g, (digit) => String("০১২৩৪৫৬৭৮৯".indexOf(digit)));
}

/** Parses the pasted text into blocks. A bad block gives its error and does not stop the others. */
export function parsePastedQuestions(text: string): ParsedBlock[] {
  const blocks = text
    .replace(/\r\n/g, "\n")
    .split(/\n\s*\n/)
    .map((block) => block.trim())
    .filter((block) => block.length > 0);

  return blocks.map((block, position) => {
    const index = position + 1;
    const lines = block.split("\n").map((line) => line.trim());
    const stemLine = lines.find((line) => STEM.test(line));
    if (!stemLine) {
      return { index, error: "noStem" };
    }
    const stem = STEM.exec(stemLine)?.[1].trim() ?? "";

    const options: { text: string; correct: boolean }[] = [];
    let marks = 1;
    let explanation: string | null = null;
    for (const line of lines) {
      const option = OPTION_LABEL.exec(line);
      if (option) {
        const raw = option[2].trim();
        const correct = raw.endsWith("*");
        options.push({ text: (correct ? raw.slice(0, -1) : raw).trim(), correct });
        continue;
      }
      const markLine = MARKS.exec(line);
      if (markLine) {
        marks = Number(latinDigits(markLine[1]));
        continue;
      }
      const explanationLine = EXPLANATION.exec(line);
      if (explanationLine) {
        explanation = explanationLine[1].trim();
      }
    }

    if (options.length < 2) {
      return { index, error: "fewOptions" };
    }
    const correctCount = options.filter((option) => option.correct).length;
    if (correctCount === 0) {
      return { index, error: "noCorrect" };
    }
    if (!Number.isInteger(marks) || marks < 1) {
      return { index, error: "badMarks" };
    }
    // Several correct marks make a multiple-answer question; the server checks the same rule.
    return { index, question: { stem, marks, explanation, multipleAnswer: correctCount > 1, options } };
  });
}

/**
 * Paste many MCQ questions at once. The preview lists each block or its error. Creation runs one
 * question after another, shows progress, and stops at the first failed create with its error.
 */
export function PasteQuestions({
  exam,
  nextPosition,
  open,
  onOpenChange,
  onStructureLocked,
}: {
  exam: StaffExam;
  nextPosition: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onStructureLocked?: () => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const { formatNumber } = useFormat();
  const add = useAddQuestion(exam.id);
  const [text, setText] = useState("");
  const [blocks, setBlocks] = useState<ParsedBlock[] | null>(null);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const ready = blocks?.filter((block): block is { index: number; question: QuestionInput } => "question" in block) ?? [];
  const problems = blocks?.filter((block): block is { index: number; error: ParseError } => "error" in block) ?? [];

  async function create() {
    setError(null);
    setProgress({ done: 0, total: ready.length });
    for (const [offset, block] of ready.entries()) {
      try {
        await add.mutateAsync({ ...block.question, position: nextPosition + offset });
        setProgress({ done: offset + 1, total: ready.length });
      } catch (e) {
        setProgress(null);
        if (isApiError(e, "QUESTION_STRUCTURE_LOCKED")) {
          onStructureLocked?.();
        }
        setError(t("staffCatalog.paste.stopped", { n: formatNumber(block.index), reason: t(catalogErrorKey(e)) }));
        return;
      }
    }
    setProgress(null);
    toast({ title: t("staffCatalog.paste.added", { n: formatNumber(ready.length) }), tone: "ok" });
    setText("");
    setBlocks(null);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("staffCatalog.paste.title")} description={t("staffCatalog.paste.body")}>
      <div className="grid gap-4">
        <p className="rounded-control border border-border bg-bg px-3 py-2 text-meta whitespace-pre-line" dir="ltr" lang="bn">
          {PASTE_FORMAT_EXAMPLE}
        </p>
        <p className="text-meta text-muted">{t("staffCatalog.paste.multiHint")}</p>
        <Textarea
          rows={8}
          aria-label={t("staffCatalog.paste.label")}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setBlocks(null);
          }}
        />
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" disabled={text.trim() === "" || progress !== null} onClick={() => setBlocks(parsePastedQuestions(text))}>
            {t("staffCatalog.paste.preview")}
          </Button>
        </div>

        {blocks ? (
          <div className="grid gap-2">
            <p className="text-label font-medium">{t("staffCatalog.paste.summary", { ready: formatNumber(ready.length), problems: formatNumber(problems.length) })}</p>
            <ul className="grid gap-1 text-meta">
              {problems.map((problem) => (
                <li key={problem.index} className="text-danger">
                  {t("staffCatalog.paste.blockError", { n: formatNumber(problem.index), reason: t(`staffCatalog.paste.errors.${problem.error}`) })}
                </li>
              ))}
            </ul>
            <div className="flex flex-wrap items-center gap-3">
              <Button loading={progress !== null} disabled={ready.length === 0 || progress !== null} onClick={create}>
                {t("staffCatalog.paste.create", { n: formatNumber(ready.length) })}
              </Button>
              {progress ? (
                <span role="status" className="text-meta tabular-nums">
                  {t("staffCatalog.paste.progress", { done: formatNumber(progress.done), total: formatNumber(progress.total) })}
                </span>
              ) : null}
            </div>
          </div>
        ) : null}

        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
      </div>
    </Dialog>
  );
}
