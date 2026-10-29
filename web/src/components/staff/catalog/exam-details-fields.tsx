"use client";

import { useId, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
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
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { SettingRow } from "@/components/staff/catalog/setting-row";
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
      {role.canAssignInstructor ? (
        <InstructorPicker exam={exam} />
      ) : (
        <div className="grid gap-1.5">
          <span className="text-label font-medium text-text">{t("staffCatalog.examMeta.instructor")}</span>
          <p className="text-body">{exam.instructor?.name ?? t("staffCatalog.examMeta.noInstructor")}</p>
          <p className="text-meta text-muted">{t("staffCatalog.examMeta.masterOnlyReason")}</p>
        </div>
      )}
    </>
  );
}

/** Chooses or clears the exam's instructor. The list is the active instructor directory. */
function InstructorPicker({ exam }: { exam: StaffExam }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const directory = useInstructorDirectory({ status: "ACTIVE" });
  const assign = useAssignExamInstructor(exam.id);
  const unassign = useUnassignExamInstructor(exam.id);
  const [picked, setPicked] = useState("");
  const [failure, setFailure] = useState<string | null>(null);
  const selectId = useId();

  async function run(action: () => Promise<unknown>, done: string) {
    setFailure(null);
    try {
      await action();
      toast({ title: t(done), tone: "ok" });
    } catch (e) {
      setFailure(t(catalogErrorKey(e)));
    }
  }

  return (
    <div className="grid gap-1.5">
      <label htmlFor={selectId} className="text-label font-medium text-text">
        {t("staffCatalog.examMeta.instructor")}
      </label>
      {/* The select and its buttons share one row, centred on the 44 px control height. */}
      <div className="flex flex-wrap items-center gap-3">
        <Select id={selectId} className="sm:w-80" value={picked} onChange={(e) => setPicked(e.target.value)}>
          <option value="">{t("staffCatalog.examMeta.instructorPick")}</option>
          {(directory.data?.content ?? []).map((row) => (
            <option key={row.id} value={row.id}>
              {row.fullName ?? row.email ?? row.phone ?? row.id}
            </option>
          ))}
        </Select>
        <Button
          variant="secondary"
          disabled={!picked || assign.isPending}
          loading={assign.isPending}
          onClick={() => run(() => assign.mutateAsync(picked), "staffCatalog.examMeta.instructorSaved")}
        >
          {t("staffCatalog.examMeta.instructorAssign")}
        </Button>
        {exam.instructor ? (
          <Button variant="secondary" disabled={unassign.isPending} onClick={() => run(() => unassign.mutateAsync(), "staffCatalog.examMeta.instructorRemoved")}>
            {t("staffCatalog.examMeta.instructorRemove")}
          </Button>
        ) : null}
      </div>
      <p className="text-meta text-muted">
        {exam.instructor ? t("staffCatalog.examMeta.currentInstructor", { name: exam.instructor.name }) : t("staffCatalog.examMeta.noInstructor")}
      </p>
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
