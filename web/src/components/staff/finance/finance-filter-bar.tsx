"use client";

import { useLocale } from "@/i18n/locale-context";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Select } from "@/components/ui/select";
import { controlBase, controlBorder } from "@/components/ui/control";
import { EXPENSE_SOURCES, INCOME_SOURCES, type FinanceSource } from "@/lib/api/exam/finance";
import type { BatchView } from "@/lib/api/exam/offline-admin";
import {
  presetRange,
  rangeProblem,
  RANGE_PRESETS,
  toggleSource,
  type FinanceUrlState,
  type Grouping,
  type RangePreset,
} from "@/components/staff/finance/finance-model";

// The filter bar (plan 8.4). Every control writes the URL; the page reads the URL and asks the API. A source the API does not offer
// for this range is not shown at all.

export function FinanceFilterBar({
  state,
  selected,
  available,
  batches,
  offlineAvailable,
  onChange,
}: {
  state: FinanceUrlState;
  /** The sources the filter selects now, among the ones the API offers. */
  selected: FinanceSource[];
  available: FinanceSource[];
  batches: BatchView[];
  offlineAvailable: boolean;
  /** Writes a change to the URL. Any change goes back to the first page of entries. */
  onChange: (patch: Partial<FinanceUrlState>) => void;
}) {
  const { t, locale } = useLocale();
  const problem = rangeProblem(state.from, state.to);
  const presetOptions = RANGE_PRESETS.map((preset) => ({ value: preset, label: t(`finance.filters.presets.${preset}`) }));
  const groupOptions = (["month", "week"] as Grouping[]).map((group) => ({
    value: group,
    label: t(group === "month" ? "finance.filters.groupMonth" : "finance.filters.groupWeek"),
  }));
  const incomeOffered = INCOME_SOURCES.filter((source) => available.includes(source));
  const expenseOffered = EXPENSE_SOURCES.filter((source) => available.includes(source));

  function choosePreset(value: string) {
    const preset = value as RangePreset;
    if (preset === "custom") {
      onChange({ range: "custom" });
      return;
    }
    onChange({ range: preset, ...presetRange(preset) });
  }

  function sourceBox(source: FinanceSource) {
    const on = selected.includes(source);
    const lastOn = on && selected.length === 1;
    return (
      <label key={source} className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-body text-text has-[:disabled]:cursor-not-allowed has-[:disabled]:text-muted">
        <input
          type="checkbox"
          className="size-4 accent-[var(--accent)]"
          checked={on}
          disabled={lastOn}
          onChange={() => onChange({ sources: toggleSource(selected, available, source) })}
        />
        {t(`finance.source.${source}`)}
      </label>
    );
  }

  return (
    <section aria-label={t("finance.filters.label")} className="grid gap-4 rounded-card border border-border bg-card p-4">
      <div className="grid gap-3">
        <span className="text-meta font-medium text-muted">{t("finance.filters.range")}</span>
        <SegmentedControl options={presetOptions} value={state.range} onChange={choosePreset} ariaLabel={t("finance.filters.range")} />
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="grid gap-1.5 text-meta text-muted">
            {t("finance.filters.from")}
            <input
              type="date"
              value={state.from}
              onChange={(event) => onChange({ range: "custom", from: event.target.value })}
              className={`${controlBase} ${controlBorder(problem !== null)}`}
            />
          </label>
          <label className="grid gap-1.5 text-meta text-muted">
            {t("finance.filters.to")}
            <input
              type="date"
              value={state.to}
              onChange={(event) => onChange({ range: "custom", to: event.target.value })}
              className={`${controlBase} ${controlBorder(problem !== null)}`}
            />
          </label>
        </div>
        {problem ? (
          <p role="alert" className="text-meta text-danger">
            {t(`finance.filters.problem.${problem}`)}
          </p>
        ) : null}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {incomeOffered.length > 0 ? (
          <fieldset className="grid gap-1">
            <legend className="text-meta font-medium text-muted">{t("finance.filters.income")}</legend>
            <div className="flex flex-wrap gap-x-5">{incomeOffered.map(sourceBox)}</div>
          </fieldset>
        ) : null}
        {expenseOffered.length > 0 ? (
          <fieldset className="grid gap-1">
            <legend className="text-meta font-medium text-muted">{t("finance.filters.expense")}</legend>
            <div className="flex flex-wrap gap-x-5">{expenseOffered.map(sourceBox)}</div>
          </fieldset>
        ) : null}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {offlineAvailable ? (
          <label className="grid gap-1.5 text-meta text-muted">
            {t("finance.filters.batch")}
            <Select value={state.batchId ?? ""} onChange={(event) => onChange({ batchId: event.target.value || null })}>
              <option value="">{t("finance.filters.allBatches")}</option>
              {batches.map((batch) => (
                <option key={batch.id} value={batch.id}>
                  {locale === "bn" ? batch.nameBn : batch.nameEn}
                </option>
              ))}
            </Select>
          </label>
        ) : null}
        <div className="grid gap-1.5">
          <span className="text-meta text-muted">{t("finance.filters.group")}</span>
          <SegmentedControl
            options={groupOptions}
            value={state.group}
            onChange={(value) => onChange({ group: value as Grouping })}
            ariaLabel={t("finance.filters.group")}
          />
        </div>
      </div>
    </section>
  );
}
