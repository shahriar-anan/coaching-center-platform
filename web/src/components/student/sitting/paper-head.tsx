"use client";

import { RichTextView } from "@/components/ui/rich-text-view";
import type { StudentExam } from "@/lib/api/exam/sitting";
import { formatNumber, pickText } from "@/lib/format";
import type { Locale } from "@/i18n/locale-context";
import { paperClass } from "./paper-theme";

type Props = {
  exam: StudentExam;
  /** "bn" for a Bangla paper, "en" otherwise: the digits and the language of the rules. */
  paperLocale: Locale;
  /** The student's name and code, already joined the way the written sitting shows them. */
  studentLine?: string;
  t: (key: string, params?: Record<string, string | number>) => string;
};

/**
 * The top of the printed paper: the title, the time and full marks, the wrong-answer note when the exam has
 * negative marks, the student's line, and the exam's rules as "Instructions" (open at first).
 */
export function PaperHead({ exam, paperLocale, studentLine, t }: Props) {
  const title = exam.titleBn ?? exam.titleEn;
  const rules = pickText({ rulesEn: exam.rulesEn, rulesBn: exam.rulesBn }, "rules", paperLocale);
  const negative = exam.negativeMarks ?? 0;

  return (
    <div className={`flex flex-col items-center gap-2 border-b pb-4 text-center ${paperClass.line} ${paperClass.ink}`}>
      <h1 className={`font-bold leading-snug ${paperClass.title}`}>{title}</h1>
      <p className={`tabular-nums ${paperClass.meta}`}>
        {t("sitting.paper.time", { minutes: formatNumber(exam.durationMinutes, paperLocale) })}
        {" · "}
        {t("sitting.paper.fullMarks", { marks: formatNumber(exam.maximum, paperLocale) })}
      </p>
      {negative > 0 ? (
        <p className={`tabular-nums ${paperClass.meta}`}>
          {t("sitting.paper.negative", { marks: formatNumber(negative, paperLocale) })}
        </p>
      ) : null}
      {studentLine ? <p className={paperClass.meta}>{studentLine}</p> : null}
      {rules ? (
        <details open className="mt-2 w-full text-start">
          <summary className={`cursor-pointer font-semibold ${paperClass.body}`}>{t("sitting.paper.instructions")}</summary>
          {/* The rules' own colours are the app's, so the paper sets its ink over them. */}
          <div className={`mt-2 [&_*]:text-[color:var(--paper-ink)]! ${paperClass.body}`}>
            <RichTextView value={rules} />
          </div>
        </details>
      ) : null}
    </div>
  );
}
