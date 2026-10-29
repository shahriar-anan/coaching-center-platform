"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";
import { useCategoryTree, useCreateNewExam, useModules, type CategoryTreeNode } from "@/lib/api/exam/staff-catalog";
import type { ExamType, PaperLanguage } from "@/lib/api/exam/types";

/** The fields the dialog holds. Description and rules are written on the exam page (D2). */
export type NewExamForm = {
  titleEn: string;
  titleBn: string;
  paperLanguage: PaperLanguage | "";
  durationMinutes: string;
};

/** Where the exam goes: a package (its category comes with it) or a category only (R2). `id` is blank until chosen. */
export type NewExamPlace = { mode: "package" | "category"; id: string };

export type NewExamField = "titleEn" | "titleBn" | "durationMinutes" | "place";

export type NewExamRequest = { path: string; body: Record<string, unknown> };

export type NewExamBuild = { ok: true; request: NewExamRequest } | { ok: false; missing: NewExamField[] };

const blankForm: NewExamForm = { titleEn: "", titleBn: "", paperLanguage: "", durationMinutes: "" };

/**
 * The create request, or the fields still missing. Only the two titles, the duration and the place are needed here.
 * An unset paper language is left out of the body, so nothing is stored or shown for it (D5, C2). A package goes to
 * its own exam list; a category-only exam goes to the standalone create with its `categoryId` (R2).
 */
export function buildNewExamRequest(form: NewExamForm, examType: ExamType, place: NewExamPlace): NewExamBuild {
  const missing: NewExamField[] = [];
  if (!form.titleEn.trim()) {
    missing.push("titleEn");
  }
  if (!form.titleBn.trim()) {
    missing.push("titleBn");
  }
  const duration = Number(form.durationMinutes);
  if (!Number.isInteger(duration) || duration < 2) {
    missing.push("durationMinutes");
  }
  if (!place.id) {
    missing.push("place");
  }
  if (missing.length > 0) {
    return { ok: false, missing };
  }
  const body: Record<string, unknown> = {
    examType,
    titleEn: form.titleEn.trim(),
    titleBn: form.titleBn.trim(),
    durationMinutes: duration,
  };
  if (form.paperLanguage) {
    body.paperLanguage = form.paperLanguage;
  }
  if (place.mode === "package") {
    return { ok: true, request: { path: `/api/v1/live-exam-modules/${place.id}/exams`, body } };
  }
  return { ok: true, request: { path: "/api/v1/live-exams", body: { ...body, categoryId: place.id } } };
}

/** The categories as a flat list, indented by depth. Archived ones are left out. */
function categoryRows(nodes: CategoryTreeNode[], locale: string, depth = 0): { id: string; label: string }[] {
  return nodes.flatMap((node) =>
    node.archived
      ? []
      : [
          { id: node.id, label: `${"— ".repeat(depth)}${locale === "bn" ? node.titleBn : node.titleEn}` },
          ...categoryRows(node.children, locale, depth + 1),
        ],
  );
}

/** The package or category choice, with its list. Used only when the dialog is not opened from a package. */
function PlacePicker({
  value,
  onChange,
  error,
}: {
  value: NewExamPlace;
  onChange: (place: NewExamPlace) => void;
  error?: string;
}) {
  const { t, locale } = useLocale();
  const packages = useModules();
  const categories = useCategoryTree();
  const loading = value.mode === "package" ? packages.isLoading : categories.isLoading;
  const rows =
    value.mode === "package"
      ? (packages.data?.content ?? [])
          .filter((item) => item.status !== "ARCHIVED")
          .map((item) => ({ id: item.id, label: locale === "bn" ? item.titleBn : item.titleEn }))
      : categoryRows(categories.data ?? [], locale);
  const emptyKey = value.mode === "package" ? "staffCatalog.moduleExams.noPackages" : "staffCatalog.moduleExams.noCategories";
  return (
    <fieldset className="grid gap-2">
      <legend className="mb-1 text-label font-medium">{t("staffCatalog.moduleExams.placeLabel")}</legend>
      {(["package", "category"] as const).map((mode) => (
        <label key={mode} className="flex min-h-11 items-center gap-3 rounded-control border border-border px-3">
          <input
            type="radio"
            name="examPlace"
            value={mode}
            checked={value.mode === mode}
            onChange={() => onChange({ mode, id: "" })}
            className="size-4"
          />
          <span>{t(mode === "package" ? "staffCatalog.moduleExams.placePackage" : "staffCatalog.moduleExams.placeCategory")}</span>
        </label>
      ))}
      {loading ? (
        <Skeleton variant="text" />
      ) : (
        <Select value={value.id} onChange={(e) => onChange({ mode: value.mode, id: e.target.value })}>
          <option value="">
            {t(value.mode === "package" ? "staffCatalog.moduleExams.packagePick" : "staffCatalog.moduleExams.categoryPick")}
          </option>
          {rows.map((row) => (
            <option key={row.id} value={row.id}>
              {row.label}
            </option>
          ))}
        </Select>
      )}
      {!loading && rows.length === 0 ? <p className="text-meta text-muted">{t(emptyKey)}</p> : null}
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
    </fieldset>
  );
}

/**
 * The New exam dialog, shared by the package page and the Exams page. With `moduleId` the exam goes into that package;
 * without it, the dialog asks where it goes. Success opens the exam page with `?created=1`.
 */
export function NewExamDialog({
  open,
  onOpenChange,
  moduleId,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  moduleId?: string;
}) {
  const { t } = useLocale();
  const router = useRouter();
  const { toast } = useToast();
  const create = useCreateNewExam();
  const [examType, setExamType] = useState<ExamType>("MCQ");
  const [form, setForm] = useState<NewExamForm>(blankForm);
  const [picked, setPicked] = useState<NewExamPlace>({ mode: "package", id: "" });
  const [errors, setErrors] = useState<Partial<Record<NewExamField | "paperLanguage", string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const place: NewExamPlace = moduleId ? { mode: "package", id: moduleId } : picked;

  function set<K extends keyof NewExamForm>(key: K, value: NewExamForm[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function messageFor(field: NewExamField | "paperLanguage"): string {
    switch (field) {
      case "titleEn":
        return t("staffCatalog.examMeta.titleEnRequired");
      case "titleBn":
        return t("staffCatalog.examMeta.titleBnRequired");
      case "durationMinutes":
        return t("staffCatalog.examMeta.durationInvalid");
      case "place":
        return t("staffCatalog.moduleExams.placeRequired");
      default:
        return t("staffCatalog.errors.VALIDATION_FAILED");
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    const built = buildNewExamRequest(form, examType, place);
    if (!built.ok) {
      const next: Partial<Record<NewExamField, string>> = {};
      for (const field of built.missing) {
        next[field] = messageFor(field);
      }
      setErrors(next);
      return;
    }
    setErrors({});
    try {
      const exam = await create.mutateAsync(built.request);
      toast({ title: t("staffCatalog.moduleExams.created"), tone: "ok" });
      router.push(`/staff/exams/${exam.id}?created=1`);
    } catch (e) {
      const fromServer: Partial<Record<NewExamField | "paperLanguage", string>> = {};
      for (const field of ["titleEn", "titleBn", "durationMinutes", "paperLanguage"] as const) {
        const message = fieldErrorFor(e, field);
        if (message) {
          fromServer[field] = message;
        }
      }
      setErrors(fromServer);
      setFormError(t(catalogErrorKey(e)));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffCatalog.moduleExams.newTitle")}
      description={t("staffCatalog.moduleExams.newBody")}
      className="sm:max-w-2xl"
    >
      <form onSubmit={submit} noValidate className="grid gap-4">
        <fieldset className="grid gap-2">
          <legend className="mb-1 text-label font-medium">{t("staffCatalog.moduleExams.typeLabel")}</legend>
          {(["MCQ", "WRITTEN"] as ExamType[]).map((type) => (
            <label key={type} className="flex min-h-11 items-center gap-3 rounded-control border border-border px-3">
              <input type="radio" name="examType" value={type} checked={examType === type} onChange={() => setExamType(type)} className="size-4" />
              <span>
                <span className="block font-medium">{t(`staffCatalog.examType.${type}`)}</span>
                <span className="block text-meta text-muted">{t(`staffCatalog.moduleExams.typeHint.${type}`)}</span>
              </span>
            </label>
          ))}
        </fieldset>
        {moduleId ? null : <PlacePicker value={picked} onChange={setPicked} error={errors.place} />}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffCatalog.moduleForm.titleEn")} required error={errors.titleEn}>
            <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
          </Field>
          <Field label={t("staffCatalog.moduleForm.titleBn")} required error={errors.titleBn}>
            <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label={t("staffCatalog.examMeta.paperLanguage")}
            hint={t("staffCatalog.examMeta.paperLanguageHint")}
            error={errors.paperLanguage}
          >
            <Select value={form.paperLanguage} onChange={(e) => set("paperLanguage", e.target.value as PaperLanguage | "")}>
              <option value="">{t("staffCatalog.moduleExams.paperLanguageNotSet")}</option>
              <option value="BN">{t("staffCatalog.paperLanguage.BN")}</option>
              <option value="EN">{t("staffCatalog.paperLanguage.EN")}</option>
              <option value="BOTH">{t("staffCatalog.paperLanguage.BOTH")}</option>
            </Select>
          </Field>
          <Field label={t("staffCatalog.examMeta.duration")} required error={errors.durationMinutes}>
            <NumberInput
              inputMode="numeric"
              min={2}
              step={1}
              value={form.durationMinutes}
              onChange={(e) => set("durationMinutes", e.target.value)}
              className="max-w-32"
            />
          </Field>
        </div>
        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button type="submit" loading={create.isPending} disabled={create.isPending}>
            {t("staffCatalog.moduleExams.create")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
