"use client";

import { useMemo, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useSaveExam, type ExamInput, type StaffExam } from "@/lib/api/exam/staff-catalog";
import type { PaperLanguage } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";
import { useUnsavedWarning } from "@/components/staff/catalog/use-unsaved-warning";

type FormState = {
  titleEn: string;
  titleBn: string;
  descriptionEn: string;
  descriptionBn: string;
  rulesEn: string;
  rulesBn: string;
  paperLanguage: PaperLanguage;
  durationMinutes: string;
  passPercent: string;
  maximum: string;
  dated: boolean;
  liveAt: string | null;
};

type Errors = Partial<Record<keyof FormState | "liveAt", string>>;

function initialState(exam: StaffExam): FormState {
  return {
    titleEn: exam.titleEn,
    titleBn: exam.titleBn,
    descriptionEn: exam.descriptionEn ?? "",
    descriptionBn: exam.descriptionBn ?? "",
    rulesEn: exam.rulesEn ?? "",
    rulesBn: exam.rulesBn ?? "",
    paperLanguage: exam.paperLanguage,
    durationMinutes: String(exam.durationMinutes),
    passPercent: exam.passPercent == null ? "" : String(exam.passPercent),
    maximum: String(exam.maximum),
    dated: exam.liveAt !== null,
    liveAt: exam.liveAt,
  };
}

/**
 * The meta fields of an MCQ or written exam. Times are typed in Asia/Dhaka; the
 * end is computed from the duration and shown, never asked. Once the start has
 * passed the whole form is read-only and says so.
 */
export function ExamMetaForm({ exam, locked }: { exam: StaffExam; locked: boolean }) {
  const { t } = useLocale();
  const { formatDateTime } = useFormat();
  const { toast } = useToast();
  const save = useSaveExam(exam.id);
  const [form, setForm] = useState<FormState>(() => initialState(exam));
  const [baseline, setBaseline] = useState<FormState>(() => initialState(exam));
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useUnsavedWarning(dirty);
  const isWritten = exam.examType === "WRITTEN";

  const endLabel = useMemo(() => {
    if (!form.dated || !form.liveAt) {
      return null;
    }
    const minutes = Number(form.durationMinutes);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      return null;
    }
    return formatDateTime(new Date(new Date(form.liveAt).getTime() + minutes * 60_000));
  }, [form.dated, form.liveAt, form.durationMinutes, formatDateTime]);

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function validate(): ExamInput | null {
    const next: Errors = {};
    if (!form.titleEn.trim()) {
      next.titleEn = t("staffCatalog.examMeta.titleEnRequired");
    }
    if (!form.titleBn.trim()) {
      next.titleBn = t("staffCatalog.examMeta.titleBnRequired");
    }
    const duration = Number(form.durationMinutes);
    if (!Number.isInteger(duration) || duration <= 0) {
      next.durationMinutes = t("staffCatalog.examMeta.durationInvalid");
    }
    if (form.passPercent !== "") {
      const pass = Number(form.passPercent);
      if (!Number.isInteger(pass) || pass < 0 || pass > 100) {
        next.passPercent = t("staffCatalog.examMeta.passInvalid");
      }
    }
    if (isWritten) {
      const max = Number(form.maximum);
      if (!Number.isInteger(max) || max < 1) {
        next.maximum = t("staffCatalog.examMeta.maximumInvalid");
      }
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return null;
    }
    const body: ExamInput = {
      titleEn: form.titleEn.trim(),
      titleBn: form.titleBn.trim(),
      descriptionEn: form.descriptionEn.trim() || null,
      descriptionBn: form.descriptionBn.trim() || null,
      rulesEn: form.rulesEn.trim() || null,
      rulesBn: form.rulesBn.trim() || null,
      paperLanguage: form.paperLanguage,
      durationMinutes: duration,
      passPercent: form.passPercent === "" ? null : Number(form.passPercent),
      liveAt: form.dated ? form.liveAt : null,
    };
    if (isWritten) {
      body.maximum = Number(form.maximum);
    }
    return body;
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    const body = validate();
    if (!body) {
      return;
    }
    try {
      const saved = await save.mutateAsync(body);
      const next = initialState(saved);
      setForm(next);
      setBaseline(next);
      toast({
        title: saved.status === "DRAFT" && exam.status === "PUBLISHED" ? t("staffCatalog.examMeta.savedReturnedToDraft") : t("staffCatalog.examMeta.saved"),
        tone: "ok",
      });
    } catch (error) {
      setFormError(t(catalogErrorKey(error)));
      setErrors({
        titleEn: fieldErrorFor(error, "titleEn") ?? undefined,
        titleBn: fieldErrorFor(error, "titleBn") ?? undefined,
        liveAt: fieldErrorFor(error, "liveAt") ?? undefined,
        durationMinutes: fieldErrorFor(error, "durationMinutes") ?? undefined,
        passPercent: fieldErrorFor(error, "passPercent") ?? undefined,
        maximum: fieldErrorFor(error, "maximum") ?? undefined,
      });
    }
  }

  return (
    <form id="exam-meta" onSubmit={onSubmit} noValidate className="grid max-w-3xl gap-5">
      {locked ? (
        <p role="status" className="rounded-control border border-warn/40 bg-warn/10 px-4 py-3 text-sm text-warn">
          {t("staffCatalog.examMeta.lockedNotice")}
        </p>
      ) : null}
      <fieldset disabled={locked} className="grid gap-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffCatalog.examMeta.titleEn")} required error={errors.titleEn}>
            <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
          </Field>
          <Field label={t("staffCatalog.examMeta.titleBn")} required error={errors.titleBn}>
            <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffCatalog.examMeta.descriptionEn")}>
            <Textarea rows={3} value={form.descriptionEn} onChange={(e) => set("descriptionEn", e.target.value)} dir="ltr" />
          </Field>
          <Field label={t("staffCatalog.examMeta.descriptionBn")}>
            <Textarea rows={3} value={form.descriptionBn} onChange={(e) => set("descriptionBn", e.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffCatalog.examMeta.rulesEn")}>
            <Textarea rows={3} value={form.rulesEn} onChange={(e) => set("rulesEn", e.target.value)} dir="ltr" />
          </Field>
          <Field label={t("staffCatalog.examMeta.rulesBn")}>
            <Textarea rows={3} value={form.rulesBn} onChange={(e) => set("rulesBn", e.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label={t("staffCatalog.examMeta.paperLanguage")} hint={t("staffCatalog.examMeta.paperLanguageHint")}>
            <Select value={form.paperLanguage} onChange={(e) => set("paperLanguage", e.target.value as PaperLanguage)}>
              <option value="BN">{t("staffCatalog.paperLanguage.BN")}</option>
              <option value="EN">{t("staffCatalog.paperLanguage.EN")}</option>
            </Select>
          </Field>
          <Field label={t("staffCatalog.examMeta.duration")} required error={errors.durationMinutes}>
            <NumberInput inputMode="numeric" min={1} step={1} value={form.durationMinutes} onChange={(e) => set("durationMinutes", e.target.value)} />
          </Field>
          <Field label={t("staffCatalog.examMeta.passPercent")} hint={t("staffCatalog.examMeta.passHint")} error={errors.passPercent}>
            <NumberInput inputMode="numeric" min={0} max={100} step={1} value={form.passPercent} onChange={(e) => set("passPercent", e.target.value)} />
          </Field>
        </div>
        {isWritten ? (
          <Field label={t("staffCatalog.examMeta.maximum")} required hint={t("staffCatalog.examMeta.maximumHint")} error={errors.maximum}>
            <NumberInput inputMode="numeric" min={1} step={1} value={form.maximum} onChange={(e) => set("maximum", e.target.value)} className="max-w-40" />
          </Field>
        ) : null}
        <div id="exam-start" className="grid gap-3">
          <Switch
            id="exam-undated"
            label={t("staffCatalog.examMeta.undated")}
            checked={!form.dated}
            onCheckedChange={(undated) => {
              set("dated", !undated);
              if (undated) {
                set("liveAt", null);
              }
            }}
          />
          {form.dated ? (
            <Field label={t("staffCatalog.examMeta.start")} hint={t("staffCatalog.examMeta.startHint")} error={errors.liveAt}>
              <DateTimeInput value={form.liveAt} onChange={(iso) => set("liveAt", iso)} invalid={Boolean(errors.liveAt)} />
            </Field>
          ) : (
            <p className="text-sm text-muted">{t("staffCatalog.dateNotSet")}</p>
          )}
          {endLabel ? (
            <p className="text-sm text-muted">{t("staffCatalog.examMeta.endsAt", { time: endLabel })}</p>
          ) : null}
        </div>
      </fieldset>

      {formError ? (
        <p role="alert" className="text-sm text-danger">
          {formError}
        </p>
      ) : null}
      {!locked ? (
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" loading={save.isPending} disabled={save.isPending}>
            {t("staffCatalog.examMeta.save")}
          </Button>
          {dirty ? (
            <span role="status" className="text-sm text-warn">
              {t("staffCatalog.examMeta.dirty")}
            </span>
          ) : null}
          <p className="text-sm text-muted">{t("staffCatalog.examMeta.returnsToDraftHelp")}</p>
        </div>
      ) : null}
    </form>
  );
}
