"use client";

import { useMemo } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { cn } from "@/components/ui/cn";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { SettingRow } from "@/components/staff/catalog/setting-row";
import type { SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

/**
 * Mode, the window, duration and marks, in one column. The mode is a two-option control with its description under it.
 * A practice exam has no window; the negative mark field shows only when its setting is on.
 */
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
        <legend className="mb-1 text-label font-medium text-text">{t("staffCatalog.examMeta.modeLegend")}</legend>
        <div role="radiogroup" aria-label={t("staffCatalog.examMeta.modeLegend")} className="grid grid-cols-2 gap-1 rounded-control border border-border-control bg-field p-1 sm:w-80">
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
                  "min-h-11 rounded-control px-4 text-label font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60",
                  selected ? "bg-action text-on-action" : "text-text hover:bg-card",
                )}
              >
                {t(`staffCatalog.examMeta.mode.${mode}`)}
              </button>
            );
          })}
        </div>
        <p className="text-meta text-muted">{t(`staffCatalog.examMeta.modeHint.${form.mode}`)}</p>
        {modeLocked ? <p className="text-meta text-muted">{t("staffCatalog.examMeta.modeLockedHint")}</p> : null}
      </fieldset>

      {windowOn ? (
        <div id="exam-window" className="grid gap-4">
          <SettingRow
            id="exam-dated"
            label={t("staffCatalog.examMeta.dateSet")}
            description={t("staffCatalog.examMeta.dateLaterHint")}
            checked={form.dated}
            disabled={disabled}
            onCheckedChange={(value) => {
              set("dated", value);
              if (!value) {
                set("liveAt", null);
                set("endAt", null);
              }
            }}
          />
          {form.dated ? (
            <div className="grid gap-4">
              <Field label={t("staffCatalog.examMeta.start")} hint={t("staffCatalog.examMeta.startHint")} error={errors.liveAt}>
                <DateTimeInput value={form.liveAt} onChange={(iso) => set("liveAt", iso)} invalid={Boolean(errors.liveAt)} disabled={disabled} />
              </Field>
              <Field label={t("staffCatalog.examMeta.end")} hint={t("staffCatalog.examMeta.endHint")} error={errors.window}>
                <DateTimeInput value={form.endAt} onChange={(iso) => set("endAt", iso)} invalid={Boolean(errors.window)} disabled={disabled} />
              </Field>
              {endsAt && !form.endAt ? <p className="text-meta text-muted">{t("staffCatalog.examMeta.endsAt", { time: endsAt })}</p> : null}
            </div>
          ) : (
            <p className="text-body">{t("staffCatalog.examMeta.dateLater")}</p>
          )}
        </div>
      ) : (
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.practiceNoWindow")}</p>
      )}

      <div className={cn("grid gap-4", context.written ? "sm:grid-cols-3" : "sm:grid-cols-2")}>
        <Field label={t("staffCatalog.examMeta.duration")} required hint={t("staffCatalog.examMeta.durationHint")} error={errors.durationMinutes}>
          <NumberInput inputMode="numeric" min={2} step={1} value={form.durationMinutes} onChange={(e) => set("durationMinutes", e.target.value)} className="max-w-48" />
        </Field>
        <Field label={t("staffCatalog.examMeta.passPercent")} hint={t("staffCatalog.examMeta.passHint")} error={errors.passPercent}>
          <NumberInput inputMode="numeric" min={0} max={100} step={1} value={form.passPercent} onChange={(e) => set("passPercent", e.target.value)} className="max-w-48" />
        </Field>
        {context.written ? (
          <Field label={t("staffCatalog.examMeta.maximum")} required hint={t("staffCatalog.examMeta.maximumHint")} error={errors.maximum}>
            <NumberInput inputMode="numeric" min={1} step={1} value={form.maximum} onChange={(e) => set("maximum", e.target.value)} className="max-w-48" />
          </Field>
        ) : null}
      </div>

      {context.written ? null : (
        <>
          <SettingRow
            id="exam-negative-on"
            label={t("staffCatalog.examMeta.negativeOn")}
            description={t("staffCatalog.examMeta.negativeOnHint")}
            checked={form.negativeOn}
            onCheckedChange={(value) => set("negativeOn", value)}
            disabled={disabled}
          >
            {form.negativeOn ? (
              <Field label={t("staffCatalog.examMeta.negativeMarks")} hint={t("staffCatalog.examMeta.negativeHint")} error={errors.negativeMarks}>
                <NumberInput inputMode="decimal" min={0} step="0.25" value={form.negativeMarks} onChange={(e) => set("negativeMarks", e.target.value)} className="max-w-32" />
              </Field>
            ) : null}
          </SettingRow>
          <SettingRow
            id="exam-answers-changeable"
            label={t("staffCatalog.examMeta.answersChangeable")}
            description={t("staffCatalog.examMeta.answersChangeableHint")}
            checked={form.answersChangeable}
            onCheckedChange={(value) => set("answersChangeable", value)}
            disabled={disabled}
          />
        </>
      )}
    </>
  );
}
