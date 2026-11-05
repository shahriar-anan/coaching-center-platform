"use client";

import { useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { staffPayTermsKeys } from "@/lib/api/exam/staff-pay-terms";
import {
  useAssignExamInstructor,
  useCategoryTree,
  useInstructorDirectory,
  useSetExamPopular,
  useUnassignExamInstructor,
  type CategoryTreeNode,
  type StaffExam,
} from "@/lib/api/exam/staff-catalog";
import type { PaperLanguage } from "@/lib/api/exam/types";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { SearchInput } from "@/components/ui/search-input";
import { cn } from "@/components/ui/cn";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { SettingRow } from "@/components/staff/catalog/setting-row";
import { ExamPayTerms } from "@/components/staff/catalog/exam-pay-terms";
import type { SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

/** The Master Admin's own controls: the popular flag and the exam's instructor. They act at once, not on Save. */
function MasterControls({ exam }: { exam: StaffExam }) {
  const { t } = useLocale();
  const role = useRole();
  const { toast } = useToast();
  const popular = useSetExamPopular(exam.id);
  const [failure, setFailure] = useState<string | null>(null);

  async function togglePopular(next: boolean) {
    setFailure(null);
    try {
      await popular.mutateAsync(next);
      toast({ title: t("staffCatalog.examMeta.popularSaved"), tone: "ok" });
    } catch (e) {
      setFailure(t(catalogErrorKey(e)));
    }
  }

  return (
    <>
      <SettingRow
        id="exam-popular"
        label={t("staffCatalog.examMeta.popular")}
        description={role.canEditPrice ? t("staffCatalog.examMeta.popularHint") : t("staffCatalog.examMeta.masterOnlyReason")}
        checked={exam.popular ?? false}
        onCheckedChange={(value) => togglePopular(value)}
        disabled={!role.canEditPrice || popular.isPending}
      />
      {failure ? (
        <p role="alert" className="text-meta text-danger">
          {failure}
        </p>
      ) : null}
    </>
  );
}

/**
 * The exam's teacher, as its own section (`#exam-instructor`). It is set right away, so it is not a staged change and
 * the section says so. An MCQ exam has no teacher and one line says so. A written exam also shows its pay per sheet and
 * the change history (P18), inside this same section.
 */
export function ExamInstructorSection({ exam, badge }: { exam: StaffExam; badge?: ReactNode }) {
  const { t } = useLocale();
  const role = useRole();
  const queryClient = useQueryClient();
  const written = exam.examType === "WRITTEN";
  // Assigning or removing the marker adds a line to the change history, so the pay terms are read again.
  const onMarkerChanged = () => {
    void queryClient.invalidateQueries({ queryKey: staffPayTermsKeys.exam(exam.id) });
  };
  return (
    <section id="exam-instructor" className="scroll-mt-24 grid gap-4 rounded-card border border-border bg-card p-5">
      <div className="grid gap-1">
        <h2 className="text-label font-semibold text-text">{t("staffCatalog.examMeta.instructor")}</h2>
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.instructorHint")}</p>
        {badge}
      </div>
      {written && !exam.instructor ? (
        <div role="status" className="grid gap-0.5 rounded-control border border-warn/40 bg-warn/10 px-4 py-3">
          <p className="text-body font-semibold text-warn">{t("staffCatalog.examMeta.noInstructorWarning")}</p>
          <p className="text-meta text-warn">
            {t(role.canAssignInstructor ? "staffCatalog.examMeta.noInstructorWarningHint" : "staffCatalog.examMeta.noInstructorWarningInfo")}
          </p>
        </div>
      ) : null}
      {exam.examType === "MCQ" ? (
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.mcqNoInstructor")}</p>
      ) : role.canAssignInstructor ? (
        <InstructorPicker exam={exam} onChanged={onMarkerChanged} />
      ) : (
        <div className="grid gap-1.5">
          {exam.instructor ? <p className="text-body">{exam.instructor.name}</p> : null}
          <p className="text-meta text-muted">{t("staffCatalog.examMeta.masterOnlyReason")}</p>
        </div>
      )}
      {written ? <ExamPayTerms examId={exam.id} /> : null}
    </section>
  );
}

/**
 * Chooses or clears the exam's instructor. Search by name, email or phone; pick one in the list, then Assign.
 */
function InstructorPicker({ exam, onChanged }: { exam: StaffExam; onChanged: () => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const [query, setQuery] = useState("");
  const [picked, setPicked] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const assigned = Boolean(exam.instructor);
  const directory = useInstructorDirectory({ status: "ACTIVE", q: query.trim() });
  const assign = useAssignExamInstructor(exam.id);
  const unassign = useUnassignExamInstructor(exam.id);
  const rows = directory.data?.content ?? [];

  async function run(action: () => Promise<unknown>, done: string) {
    setFailure(null);
    try {
      await action();
      onChanged();
      toast({ title: t(done), tone: "ok" });
    } catch (e) {
      setFailure(t(catalogErrorKey(e)));
    }
  }

  if (assigned && exam.instructor) {
    return (
      <div className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-ok/50 bg-ok/10 px-4 py-3">
          <div className="grid gap-0.5">
            <span className="text-meta font-semibold uppercase tracking-wide text-ok-text">{t("staffCatalog.examMeta.currentInstructorLabel")}</span>
            <span className="text-body font-bold text-text">{exam.instructor.name}</span>
          </div>
          <Button variant="secondary" disabled={unassign.isPending} loading={unassign.isPending} onClick={() => run(() => unassign.mutateAsync(), "staffCatalog.examMeta.instructorRemoved")}>
            {t("staffCatalog.examMeta.instructorRemove")}
          </Button>
        </div>
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.changeInstructorHint")}</p>
        {failure ? (
          <p role="alert" className="text-meta text-danger">
            {failure}
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="grid gap-3">
      <SearchInput value={query} onChange={setQuery} placeholder={t("staffCatalog.examMeta.instructorSearch")} />
      {rows.length === 0 ? (
        <p className="text-meta text-muted">{directory.isPending ? t("catalog.loading") : t("staffCatalog.examMeta.instructorNone")}</p>
      ) : (
        <ul className="grid max-h-72 gap-2 overflow-y-auto">
          {rows.map((row) => {
            const active = picked === row.id;
            return (
              <li key={row.id}>
                <button
                  type="button"
                  aria-pressed={active}
                  onClick={() => setPicked(row.id)}
                  className={cn(
                    "grid w-full gap-0.5 rounded-control border px-3 py-2 text-start transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent",
                    active ? "border-action bg-action/10" : "border-border bg-card hover:border-accent",
                  )}
                >
                  <span className="font-semibold text-text">{row.fullName ?? row.email ?? row.phone ?? row.id}</span>
                  {row.email || row.phone ? (
                    <span className="text-meta text-muted">{[row.email, row.phone].filter(Boolean).join(" · ")}</span>
                  ) : null}
                </button>
              </li>
            );
          })}
        </ul>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button
          variant="secondary"
          disabled={!picked || assign.isPending}
          loading={assign.isPending}
          onClick={() => picked && run(() => assign.mutateAsync(picked), "staffCatalog.examMeta.instructorSaved")}
        >
          {t("staffCatalog.examMeta.instructorAssign")}
        </Button>
      </div>
      {failure ? (
        <p role="alert" className="text-meta text-danger">
          {failure}
        </p>
      ) : null}
    </div>
  );
}

/**
 * The categories as select options, subcategories indented under their parent. An archived category is left out, unless
 * the exam is already in it, so the saved choice still shows.
 */
function categoryOptions(
  nodes: CategoryTreeNode[] | undefined,
  current: string,
  depth = 0,
): { id: string; depth: number; titleEn: string; titleBn: string }[] {
  return (nodes ?? []).flatMap((node) => [
    ...(node.archived && node.id !== current ? [] : [{ id: node.id, depth, titleEn: node.titleEn, titleBn: node.titleBn }]),
    ...categoryOptions(node.children, current, depth + 1),
  ]);
}

/** Titles, the category, paper language, the preview setting, and the Master Admin controls. One column, except the titles. */
export function ExamDetailsFields({ exam, form, set, errors }: SectionFieldsProps) {
  const { t, locale } = useLocale();
  const tree = useCategoryTree();
  const options = categoryOptions(tree.data, form.categoryId);
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("staffCatalog.examMeta.titleEn")} required error={errors.titleEn}>
          <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
        </Field>
        <Field label={t("staffCatalog.examMeta.titleBn")} required error={errors.titleBn}>
          <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
        </Field>
      </div>
      <Field label={t("staffCatalog.examMeta.category")} required error={errors.categoryId} hint={t("staffCatalog.examMeta.categoryHint")}>
        <Select value={form.categoryId} onChange={(e) => set("categoryId", e.target.value)}>
          <option value="">{t("staffCatalog.examMeta.categoryPick")}</option>
          {options.map((item) => (
            <option key={item.id} value={item.id}>
              {`${"— ".repeat(item.depth)}${locale === "bn" ? item.titleBn : item.titleEn}`}
            </option>
          ))}
        </Select>
      </Field>
      <Field label={t("staffCatalog.examMeta.paperLanguage")} hint={t("staffCatalog.examMeta.paperLanguageHint")} error={errors.paperLanguage}>
        <Select value={form.paperLanguage} onChange={(e) => set("paperLanguage", e.target.value as PaperLanguage | "")} className="sm:w-80">
          <option value="">{t("staffCatalog.moduleExams.paperLanguageNotSet")}</option>
          <option value="BN">{t("staffCatalog.paperLanguage.BN")}</option>
          <option value="EN">{t("staffCatalog.paperLanguage.EN")}</option>
          <option value="BOTH">{t("staffCatalog.paperLanguage.BOTH")}</option>
        </Select>
      </Field>
      <SettingRow
        id="exam-preview-enabled"
        label={t("staffCatalog.examMeta.previewEnabled")}
        description={t("staffCatalog.examMeta.previewHint")}
        checked={form.previewEnabled}
        onCheckedChange={(value) => set("previewEnabled", value)}
      />
      <MasterControls exam={exam} />
    </>
  );
}
