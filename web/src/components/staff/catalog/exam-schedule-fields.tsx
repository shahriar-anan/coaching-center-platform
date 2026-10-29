"use client";

import { useMemo } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { cn } from "@/components/ui/cn";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Switch } from "@/components/ui/switch";
import type { SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

/** Mode, the window, duration and marks. A practice exam has no window; the negative mark field shows only when it is on. */
export function ExamScheduleFields({ exam, form, set, errors, disabled, context }: SectionFieldsProps) {
  const { t } = useLocale();
  const { formatDateTime } = useFormat();
  // The mode is set once, on a draft that nobody has started (the API refuses a later change).
  const modeLocked = exam.status !== "DRAFT";
  const windowOn = form.mode === "LIVE";
  const endsAt = useMemo(() => {
    if (!windowOn || !form.dated || !form.liveAt) {
      return null;
    }
    const minutes = Number(form.durationMinutes);
    if (!Number.isInteger(minutes) || minutes < 2) {
      return null;
    }
    return formatDateTime(new Date(new Date(form.liveAt).getTime() + minutes * 60_000));
  }, [windowOn, form.dated, form.liveAt, form.durationMinutes, formatDateTime]);

  return (
    <>
      <fieldset className="grid gap-2">
        <legend className="mb-1 text-label font-medium">{t("staffCatalog.examMeta.modeLegend")}</legend>
        <div role="radiogroup" aria-label={t("staffCatalog.examMeta.modeLegend")} className="grid gap-2 sm:grid-cols-2">
          {(["LIVE", "PRACTICE"] as const).map((mode) => {
            const selected = form.mode === mode;
            return (
              <button
                key={mode}
                type="button"
                role="radio"
                aria-checked={selected}
                disabled={disabled || modeLocked}
                onClick={() => set("mode", mode)}
                className={cn(
                  "min-h-11 rounded-control border px-4 text-start text-body disabled:cursor-not-allowed disabled:opacity-60",
                  selected ? "border-action font-medium ring-1 ring-action" : "border-border-control bg-field hover:border-muted",
                )}
              >
                <span className="block">{t(`staffCatalog.examMeta.mode.${mode}`)}</span>
                <span className="block text-meta text-muted">{t(`staffCatalog.examMeta.modeHint.${mode}`)}</span>
              </button>
            );
          })}
        </div>
        {modeLocked ? <p className="text-meta text-muted">{t("staffCatalog.examMeta.modeLockedHint")}</p> : null}
      </fieldset>

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label={t("staffCatalog.examMeta.duration")} required hint={t("staffCatalog.examMeta.durationHint")} error={errors.durationMinutes}>
          <NumberInput inputMode="numeric" min={2} step={1} value={form.durationMinutes} onChange={(e) => set("durationMinutes", e.target.value)} />
        </Field>
        <Field label={t("staffCatalog.examMeta.passPercent")} hint={t("staffCatalog.examMeta.passHint")} error={errors.passPercent}>
          <NumberInput inputMode="numeric" min={0} max={100} step={1} value={form.passPercent} onChange={(e) => set("passPercent", e.target.value)} />
        </Field>
        {context.written ? (
          <Field label={t("staffCatalog.examMeta.maximum")} required hint={t("staffCatalog.examMeta.maximumHint")} error={errors.maximum}>
            <NumberInput inputMode="numeric" min={1} step={1} value={form.maximum} onChange={(e) => set("maximum", e.target.value)} />
          </Field>
        ) : null}
      </div>

      {windowOn ? (
        <div id="exam-window" className="grid gap-3">
          <fieldset className="grid gap-2">
            <legend className="mb-1 text-label font-medium">{t("staffCatalog.examMeta.startLegend")}</legend>
            <div role="radiogroup" aria-label={t("staffCatalog.examMeta.startLegend")} className="grid gap-2 sm:grid-cols-2">
              {[
                { dated: true, label: t("staffCatalog.examMeta.dateSet") },
                { dated: false, label: t("staffCatalog.examMeta.dateLater") },
              ].map((option) => {
                const selected = form.dated === option.dated;
                return (
                  <button
                    key={String(option.dated)}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    disabled={disabled}
                    onClick={() => {
                      set("dated", option.dated);
                      if (!option.dated) {
                        set("liveAt", null);
                        set("endAt", null);
                      }
                    }}
                    className={cn(
                      "min-h-11 rounded-control border px-4 text-start text-body disabled:cursor-not-allowed disabled:opacity-60",
                      selected ? "border-action font-medium ring-1 ring-action" : "border-border-control bg-field hover:border-muted",
                    )}
                  >
                    {option.label}
                  </button>
                );
              })}
            </div>
          </fieldset>
          {form.dated ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label={t("staffCatalog.examMeta.start")} hint={t("staffCatalog.examMeta.startHint")} error={errors.liveAt}>
                <DateTimeInput value={form.liveAt} onChange={(iso) => set("liveAt", iso)} invalid={Boolean(errors.liveAt)} disabled={disabled} />
              </Field>
              <Field label={t("staffCatalog.examMeta.end")} hint={t("staffCatalog.examMeta.endHint")} error={errors.window}>
                <DateTimeInput value={form.endAt} onChange={(iso) => set("endAt", iso)} invalid={Boolean(errors.window)} disabled={disabled} />
              </Field>
            </div>
          ) : (
            <p className="text-meta text-muted">{t("staffCatalog.dateNotSet")}</p>
          )}
          {endsAt && !form.endAt ? <p className="text-meta text-muted">{t("staffCatalog.examMeta.endsAt", { time: endsAt })}</p> : null}
        </div>
      ) : (
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.practiceNoWindow")}</p>
      )}

      {context.written ? null : (
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="grid gap-2">
            <Switch
              id="exam-negative-on"
              checked={form.negativeOn}
              onCheckedChange={(value) => set("negativeOn", value)}
              disabled={disabled}
              label={t("staffCatalog.examMeta.negativeOn")}
            />
            {form.negativeOn ? (
              <Field label={t("staffCatalog.examMeta.negativeMarks")} hint={t("staffCatalog.examMeta.negativeHint")} error={errors.negativeMarks}>
                <NumberInput inputMode="decimal" min={0} step="0.25" value={form.negativeMarks} onChange={(e) => set("negativeMarks", e.target.value)} className="max-w-32" />
              </Field>
            ) : null}
          </div>
          <div className="grid gap-1 self-start">
            <Switch
              id="exam-answers-changeable"
              checked={form.answersChangeable}
              onCheckedChange={(value) => set("answersChangeable", value)}
              disabled={disabled}
              label={t("staffCatalog.examMeta.answersChangeable")}
            />
            <p className="text-meta text-muted">{t("staffCatalog.examMeta.answersChangeableHint")}</p>
          </div>
        </div>
      )}
    </>
  );
}
