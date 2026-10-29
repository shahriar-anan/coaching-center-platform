"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import {
  useAssignExamInstructor,
  useInstructorDirectory,
  useSetExamPopular,
  useUnassignExamInstructor,
  type StaffExam,
} from "@/lib/api/exam/staff-catalog";
import type { PaperLanguage } from "@/lib/api/exam/types";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
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
    <div className="grid gap-4 sm:grid-cols-2">
      <div className="grid gap-1">
        <Switch
          id="exam-popular"
          checked={exam.popular ?? false}
          onCheckedChange={(value) => togglePopular(value)}
          disabled={!role.canEditPrice || popular.isPending}
          label={t("staffCatalog.examMeta.popular")}
        />
        <p className="text-meta text-muted">
          {role.canEditPrice ? t("staffCatalog.examMeta.popularHint") : t("staffCatalog.examMeta.masterOnlyReason")}
        </p>
      </div>
      <div className="grid gap-1">
        <span className="text-meta font-medium text-muted">{t("staffCatalog.examMeta.instructor")}</span>
        <p className="text-body">{exam.instructor?.name ?? t("staffCatalog.examMeta.noInstructor")}</p>
        {role.canAssignInstructor ? (
          <InstructorPicker exam={exam} />
        ) : (
          <p className="text-meta text-muted">{t("staffCatalog.examMeta.masterOnlyReason")}</p>
        )}
      </div>
      {failure ? (
        <p role="alert" className="text-meta text-danger sm:col-span-2">
          {failure}
        </p>
      ) : null}
    </div>
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
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <Select value={picked} onChange={(e) => setPicked(e.target.value)} aria-label={t("staffCatalog.examMeta.instructor")}>
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
      {failure ? (
        <p role="alert" className="text-meta text-danger">
          {failure}
        </p>
      ) : null}
    </div>
  );
}

/** Titles, paper language, the preview switch, and the Master Admin controls. */
export function ExamDetailsFields({ exam, form, set, errors }: SectionFieldsProps) {
  const { t } = useLocale();
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
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t("staffCatalog.examMeta.paperLanguage")} hint={t("staffCatalog.examMeta.paperLanguageHint")} error={errors.paperLanguage}>
          <Select value={form.paperLanguage} onChange={(e) => set("paperLanguage", e.target.value as PaperLanguage | "")}>
            <option value="">{t("staffCatalog.moduleExams.paperLanguageNotSet")}</option>
            <option value="BN">{t("staffCatalog.paperLanguage.BN")}</option>
            <option value="EN">{t("staffCatalog.paperLanguage.EN")}</option>
            <option value="BOTH">{t("staffCatalog.paperLanguage.BOTH")}</option>
          </Select>
        </Field>
        <div className="grid gap-1 self-start">
          <Switch
            id="exam-preview-enabled"
            checked={form.previewEnabled}
            onCheckedChange={(value) => set("previewEnabled", value)}
            label={t("staffCatalog.examMeta.previewEnabled")}
          />
          <p className="text-meta text-muted">{t("staffCatalog.examMeta.previewHint")}</p>
        </div>
      </div>
      <MasterControls exam={exam} />
    </>
  );
}
