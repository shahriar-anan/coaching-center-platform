"use client";

import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { DateTimeInput } from "@/components/ui/date-time-input";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { SettingRow } from "@/components/staff/catalog/setting-row";
import type { SectionFieldsProps } from "@/components/staff/catalog/exam-form-state";

/**
 * Price, free, discount and access. Only the Master Admin edits these (the API answers a System Admin with 403). A
 * System Admin sees the saved values and the reason.
 */
export function ExamPriceFields({ exam, form, set, errors, disabled, context }: SectionFieldsProps) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();

  if (!context.canSetPrice) {
    return (
      <div className="grid gap-4">
        <div className="grid gap-1.5">
          <span className="text-label font-medium text-text">{t("staffCatalog.examMeta.price")}</span>
          <p className="text-body">{formatMoney(exam.price) ?? t("staffCatalog.examMeta.noPrice")}</p>
        </div>
        <p className="text-meta text-muted">{t("staffCatalog.examMeta.priceReadOnly")}</p>
      </div>
    );
  }

  const discounted = form.discountPercent.trim() !== "";
  return (
    <>
      <SettingRow
        id="exam-free"
        label={t("staffCatalog.examMeta.free")}
        description={t("staffCatalog.examMeta.freeHint")}
        checked={form.free}
        onCheckedChange={(value) => set("free", value)}
        disabled={disabled}
      />
      <Field label={t("staffCatalog.examMeta.price")} hint={t("staffCatalog.examMeta.priceHint")} error={errors.price}>
        <NumberInput
          inputMode="decimal"
          min={0}
          step="0.01"
          value={form.price}
          disabled={disabled || form.free}
          onChange={(e) => set("price", e.target.value)}
          className="max-w-48"
        />
      </Field>
      <Field label={t("staffCatalog.examMeta.discountPercent")} hint={t("staffCatalog.examMeta.discountHint")} error={errors.discountPercent}>
        <NumberInput
          inputMode="numeric"
          min={1}
          max={99}
          step={1}
          value={form.discountPercent}
          disabled={disabled || form.free}
          onChange={(e) => set("discountPercent", e.target.value)}
          className="max-w-32"
        />
      </Field>
      <Field label={t("staffCatalog.examMeta.discountEndsAt")} error={errors.discountEndsAt}>
        <DateTimeInput
          value={form.discountEndsAt}
          onChange={(iso) => set("discountEndsAt", iso)}
          invalid={Boolean(errors.discountEndsAt)}
          disabled={disabled || !discounted || form.free}
          dateOnly
          futureOnly
        />
      </Field>
      <Field label={t("staffCatalog.examMeta.accessDays")} hint={t("staffCatalog.examMeta.accessDaysHint")} error={errors.accessDays}>
        <NumberInput inputMode="numeric" min={1} step={1} value={form.accessDays} disabled={disabled} onChange={(e) => set("accessDays", e.target.value)} className="max-w-32" />
      </Field>
    </>
  );
}
