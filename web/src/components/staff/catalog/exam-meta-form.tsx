"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useSaveExam, type ExamInput, type StaffExam } from "@/lib/api/exam/staff-catalog";
import type { PaperLanguage } from "@/lib/api/exam/types";
import { useUnsavedChangesGuard } from "@/lib/hooks/use-unsaved-changes-guard";
import { Button } from "@/components/ui/button";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Select } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { parsePrice } from "@/components/staff/catalog/module-details";
import { catalogErrorKey, clashExamFor, fieldErrorFor, type ClashExam } from "@/components/staff/catalog/errors";

/** The three editable parts of an exam. Each saves on its own button and sends only its own fields. */
export type ExamSection = "details" | "schedule" | "price";

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
  price: string;
  dated: boolean;
  liveAt: string | null;
};

type Errors = Partial<Record<keyof FormState, string>>;

const SECTION_FIELDS: Record<ExamSection, (keyof FormState)[]> = {
  details: ["titleEn", "titleBn", "descriptionEn", "descriptionBn", "rulesEn", "rulesBn", "paperLanguage"],
  schedule: ["durationMinutes", "passPercent", "maximum", "dated", "liveAt"],
  price: ["price"],
};

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
    price: exam.price == null ? "" : String(exam.price),
    dated: exam.liveAt !== null,
    liveAt: exam.liveAt,
  };
}

function pick(state: FormState, keys: (keyof FormState)[]): Partial<FormState> {
  return Object.fromEntries(keys.map((key) => [key, state[key]])) as Partial<FormState>;
}

/**
 * One section of the exam editor. Times are typed in Asia/Dhaka; the end is computed
 * from the duration and shown, never asked. Once the start has passed every section
 * is read-only and says so.
 */
export function ExamMetaForm({ exam, locked, section }: { exam: StaffExam; locked: boolean; section: ExamSection }) {
  const { t } = useLocale();
  const { formatDateTime, formatMoney } = useFormat();
  const { toast } = useToast();
  const role = useRole();
  const save = useSaveExam(exam.id);
  // Only Master Admin sets the price. System Admin sees it as text and never sends the field (the API would refuse it).
  const canSetPrice = role.canEditPrice;
  const [form, setForm] = useState<FormState>(() => initialState(exam));
  const [baseline, setBaseline] = useState<FormState>(() => initialState(exam));
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [clash, setClash] = useState<ClashExam | null>(null);
  const dirty = JSON.stringify(pick(form, SECTION_FIELDS[section])) !== JSON.stringify(pick(baseline, SECTION_FIELDS[section]));
  useUnsavedChangesGuard(dirty);
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
    if (section === "details") {
      if (!form.titleEn.trim()) {
        next.titleEn = t("staffCatalog.examMeta.titleEnRequired");
      }
      if (!form.titleBn.trim()) {
        next.titleBn = t("staffCatalog.examMeta.titleBnRequired");
      }
    }
    if (section === "schedule") {
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
    }
    const price = section === "price" && canSetPrice ? parsePrice(form.price) : null;
    if (price && !price.ok) {
      next.price = t("staffCatalog.examMeta.priceInvalid");
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return null;
    }
    if (section === "details") {
      return {
        titleEn: form.titleEn.trim(),
        titleBn: form.titleBn.trim(),
        descriptionEn: form.descriptionEn.trim() || null,
        descriptionBn: form.descriptionBn.trim() || null,
        rulesEn: form.rulesEn.trim() || null,
        rulesBn: form.rulesBn.trim() || null,
        paperLanguage: form.paperLanguage,
      };
    }
    if (section === "schedule") {
      const body: ExamInput = {
        durationMinutes: Number(form.durationMinutes),
        passPercent: form.passPercent === "" ? null : Number(form.passPercent),
        liveAt: form.dated ? form.liveAt : null,
      };
      if (isWritten) {
        body.maximum = Number(form.maximum);
      }
      return body;
    }
    return price?.ok ? { price: price.value } : null;
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    setClash(null);
    const body = validate();
    if (!body) {
      return;
    }
    try {
      const saved = await save.mutateAsync(body);
      const next = initialState(saved);
      setForm((current) => ({ ...current, ...pick(next, SECTION_FIELDS[section]) }));
      setBaseline((current) => ({ ...current, ...pick(next, SECTION_FIELDS[section]) }));
      toast({
        title:
          saved.status === "DRAFT" && exam.status === "PUBLISHED"
            ? t("staffCatalog.examMeta.savedReturnedToDraft")
            : t("staffCatalog.examMeta.saved"),
        tone: "ok",
      });
    } catch (error) {
      const clashing = clashExamFor(error);
      setClash(clashing);
      setFormError(clashing ? null : t(catalogErrorKey(error)));
      setErrors({
        titleEn: fieldErrorFor(error, "titleEn") ?? undefined,
        titleBn: fieldErrorFor(error, "titleBn") ?? undefined,
        liveAt: fieldErrorFor(error, "liveAt") ?? undefined,
        durationMinutes: fieldErrorFor(error, "durationMinutes") ?? undefined,
        passPercent: fieldErrorFor(error, "passPercent") ?? undefined,
        maximum: fieldErrorFor(error, "maximum") ?? undefined,
        price: fieldErrorFor(error, "price") ?? undefined,
      });
    }
  }

  const saveRow = !locked && (section !== "price" || canSetPrice) ? (
    <div className="flex flex-wrap items-center gap-3">
      <Button type="submit" loading={save.isPending} disabled={save.isPending || !dirty}>
        {t("staffCatalog.examMeta.save")}
      </Button>
      {dirty ? (
        <span role="status" className="text-meta text-warn">
          {t("staffCatalog.examMeta.dirty")}
        </span>
      ) : null}
      <p className="text-meta text-muted">{t("staffCatalog.examMeta.returnsToDraftHelp")}</p>
    </div>
  ) : null;

  return (
    <form id={`exam-${section}-form`} onSubmit={onSubmit} noValidate className="grid gap-5">
      {locked ? (
        <p role="status" className="rounded-control border border-warn/40 bg-warn/10 px-4 py-3 text-meta text-warn">
          {t("staffCatalog.examMeta.lockedNotice")}
        </p>
      ) : null}
      <fieldset disabled={locked} className="grid gap-5">
        {section === "details" ? (
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
            <Field label={t("staffCatalog.examMeta.paperLanguage")} hint={t("staffCatalog.examMeta.paperLanguageHint")}>
              <Select value={form.paperLanguage} onChange={(e) => set("paperLanguage", e.target.value as PaperLanguage)}>
                <option value="BN">{t("staffCatalog.paperLanguage.BN")}</option>
                <option value="EN">{t("staffCatalog.paperLanguage.EN")}</option>
              </Select>
            </Field>
          </>
        ) : null}

        {section === "schedule" ? (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label={t("staffCatalog.examMeta.duration")} required error={errors.durationMinutes}>
                <NumberInput inputMode="numeric" min={1} step={1} value={form.durationMinutes} onChange={(e) => set("durationMinutes", e.target.value)} />
              </Field>
              <Field label={t("staffCatalog.examMeta.passPercent")} hint={t("staffCatalog.examMeta.passHint")} error={errors.passPercent}>
                <NumberInput inputMode="numeric" min={0} max={100} step={1} value={form.passPercent} onChange={(e) => set("passPercent", e.target.value)} />
              </Field>
              {isWritten ? (
                <Field label={t("staffCatalog.examMeta.maximum")} required hint={t("staffCatalog.examMeta.maximumHint")} error={errors.maximum}>
                  <NumberInput inputMode="numeric" min={1} step={1} value={form.maximum} onChange={(e) => set("maximum", e.target.value)} />
                </Field>
              ) : null}
            </div>
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
                <p className="text-meta text-muted">{t("staffCatalog.dateNotSet")}</p>
              )}
              {endLabel ? <p className="text-meta text-muted">{t("staffCatalog.examMeta.endsAt", { time: endLabel })}</p> : null}
            </div>
          </>
        ) : null}

        {section === "price" ? (
          canSetPrice ? (
            <Field label={t("staffCatalog.examMeta.price")} hint={t("staffCatalog.examMeta.priceHint")} error={errors.price}>
              <NumberInput inputMode="decimal" min={0} step="0.01" value={form.price} onChange={(e) => set("price", e.target.value)} className="max-w-48" />
            </Field>
          ) : (
            <div className="grid gap-1">
              <span className="text-meta font-medium text-muted">{t("staffCatalog.examMeta.price")}</span>
              <p className="text-body">{formatMoney(exam.price) ?? t("staffCatalog.examMeta.noPrice")}</p>
              <p className="text-meta text-muted">{t("staffCatalog.examMeta.priceReadOnly")}</p>
            </div>
          )
        ) : null}
      </fieldset>

      {clash ? <ClashAlert clash={clash} /> : null}
      {formError ? (
        <p role="alert" className="text-meta text-danger">
          {formError}
        </p>
      ) : null}
      {saveRow}
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
