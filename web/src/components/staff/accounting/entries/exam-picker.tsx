"use client";

import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { useLocale } from "@/i18n/locale-context";
import { useExamList } from "@/lib/api/exam/staff-exams";
import { pickName } from "../accounting-model";

/** The online exam a charge was for (plan B4): optional, any live exam. Read from the exam list, page by page. */
export function ExamPicker({ value, onChange }: { value: string; onChange: (id: string) => void }) {
  const { t, locale } = useLocale();
  const exams = useExamList({});
  const rows = exams.data?.pages.flatMap((page) => page.content) ?? [];
  return (
    <Field label={t("accounting.form.exam")} hint={t("accounting.form.examHint")}>
      <Select value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">{t("accounting.form.noExam")}</option>
        {rows.map((exam) => (
          <option key={exam.id} value={exam.id}>
            {pickName({ nameEn: exam.titleEn, nameBn: exam.titleBn || null }, locale)}
          </option>
        ))}
      </Select>
    </Field>
  );
}
