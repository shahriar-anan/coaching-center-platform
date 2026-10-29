"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useSaveExam, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { useUnsavedChangesGuard } from "@/lib/hooks/use-unsaved-changes-guard";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey, clashExamFor, fieldErrorFor, isApiError, type ClashExam } from "@/components/staff/catalog/errors";
import {
  SECTION_KEYS,
  examBodyFor,
  examFormErrors,
  examFormState,
  sectionDirty,
  type ExamFormContext,
  type ExamFormErrors,
  type ExamFormState,
  type ExamSection,
  type SectionFieldsProps,
} from "@/components/staff/catalog/exam-form-state";
import { ExamDetailsFields } from "@/components/staff/catalog/exam-details-fields";
import { ExamTextFields } from "@/components/staff/catalog/exam-text-fields";
import { ExamScheduleFields } from "@/components/staff/catalog/exam-schedule-fields";
import { ExamPriceFields } from "@/components/staff/catalog/exam-price-fields";

export type { ExamSection } from "@/components/staff/catalog/exam-form-state";

/** Copies the fields of one section from the saved exam into a form state. The other sections are left as they are. */
function mergeSection(base: ExamFormState, from: ExamFormState, section: ExamSection): ExamFormState {
  return { ...base, ...Object.fromEntries(SECTION_KEYS[section].map((key) => [key, from[key]])) };
}

/**
 * One section of the exam page. It has its own form and its own Save button, and it sends only its own fields. The
 * client never locks a section by date: the API takes the edit and stages it when the exam is published. An archived
 * exam, or a role that cannot author, gets the section read-only.
 */
export function ExamMetaForm({
  exam,
  section,
  canEdit,
  onDirtyChange,
}: {
  exam: StaffExam;
  section: ExamSection;
  canEdit: boolean;
  /** Reports whether this section has unsaved changes. Optional: the form works without it. */
  onDirtyChange?: (section: ExamSection, dirty: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const role = useRole();
  const save = useSaveExam(exam.id);
  const context: ExamFormContext = { written: exam.examType === "WRITTEN", canSetPrice: role.canEditPrice };
  const formRef = useRef<HTMLFormElement>(null);
  const [form, setForm] = useState<ExamFormState>(() => examFormState(exam));
  const [saved, setSaved] = useState<ExamFormState>(() => examFormState(exam));
  const [errors, setErrors] = useState<ExamFormErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [clash, setClash] = useState<ClashExam | null>(null);
  const dirty = sectionDirty(form, saved, section);
  useUnsavedChangesGuard(dirty);
  useEffect(() => {
    onDirtyChange?.(section, dirty);
  }, [section, dirty, onDirtyChange]);

  const set = useCallback(<K extends keyof ExamFormState>(key: K, value: ExamFormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  }, []);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    // Save all submits every section; a section with nothing to save, or a price the role cannot set, sends nothing.
    if (!canEdit || !dirty || (section === "price" && !context.canSetPrice)) {
      return;
    }
    setFormError(null);
    setClash(null);
    const local = examFormErrors(form, section, context, saved);
    if (Object.keys(local).length > 0) {
      const messages: ExamFormErrors = {};
      for (const [key, value] of Object.entries(local)) {
        messages[key as keyof ExamFormErrors] = t(value as string);
      }
      setErrors(messages);
      return;
    }
    setErrors({});
    try {
      const next = examFormState(await save.mutateAsync(examBodyFor(form, section, context)));
      setSaved((current) => mergeSection(current, next, section));
      setForm((current) => mergeSection(current, next, section));
      toast({ title: t("staffCatalog.examMeta.saved"), tone: "ok" });
    } catch (error) {
      const clashing = clashExamFor(error);
      setClash(clashing);
      setFormError(clashing ? null : t(catalogErrorKey(error)));
      const messages: ExamFormErrors = {};
      for (const key of SECTION_KEYS[section]) {
        const message = fieldErrorFor(error, key);
        if (message) {
          messages[key] = message;
        }
      }
      if (isApiError(error, "EXAM_WINDOW_INVALID")) {
        messages.window = t(catalogErrorKey(error));
      }
      setErrors(messages);
    }
  }

  const fields: SectionFieldsProps = {
    exam,
    form,
    set,
    errors,
    disabled: !canEdit,
    context,
    saved,
    saving: save.isPending,
    onSave: () => formRef.current?.requestSubmit(),
  };
  const priceLocked = section === "price" && !context.canSetPrice;
  // The section's one footer row: its status on the left, its Save on the right.
  const footer = canEdit && !priceLocked ? (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
      <div className="grid gap-1">
        <p role="status" className={dirty ? "text-meta text-warn" : "text-meta text-muted"}>
          {dirty ? t("staffCatalog.examMeta.dirty") : t("staffCatalog.examMeta.savedStatus")}
        </p>
        {exam.status === "PUBLISHED" ? <p className="text-meta text-muted">{t("staffCatalog.examMeta.stagedHint")}</p> : null}
      </div>
      <Button type="submit" loading={save.isPending} disabled={save.isPending || !dirty}>
        {t("staffCatalog.examMeta.save")}
      </Button>
    </div>
  ) : null;

  return (
    <form ref={formRef} id={`exam-${section}-form`} onSubmit={onSubmit} noValidate className="grid gap-4">
      <fieldset disabled={!canEdit} className="grid gap-4">
        {section === "details" ? <ExamDetailsFields {...fields} /> : null}
        {section === "text" ? <ExamTextFields {...fields} /> : null}
        {section === "schedule" ? <ExamScheduleFields {...fields} /> : null}
        {section === "price" ? <ExamPriceFields {...fields} /> : null}
      </fieldset>

      {clash ? <ClashAlert clash={clash} /> : null}
      {formError ? (
        <p role="alert" className="text-meta text-danger">
          {formError}
        </p>
      ) : null}
      {footer}
    </form>
  );
}

/** The five-minute clash names the other exam and its time, with a link to it (G3). */
function ClashAlert({ clash }: { clash: ClashExam }) {
  const { t, locale } = useLocale();
  const { formatDate, formatTimeRange } = useFormat();
  const title = locale === "bn" ? clash.titleBn : clash.titleEn;
  const when = `${formatDate(clash.liveAt)}, ${formatTimeRange(clash.liveAt, clash.endAt)}`;
  // The title appears once in the sentence, so it can be split out and wrapped in the link.
  const [before, after] = t("staffCatalog.clash.message", { title, when }).split(title);
  return (
    <p role="alert" className="text-meta text-danger">
      {before}
      <Link href={`/staff/exams/${clash.id}`} className="underline hover:no-underline">
        {title}
      </Link>
      {after}
    </p>
  );
}
