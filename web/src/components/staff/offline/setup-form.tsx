"use client";

import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useBatches, type AdmissionSetup, type BatchView } from "@/lib/api/exam/offline-admin";
import { defaultFirstDue } from "./offline-model";
import { SetupCard } from "./setup-card";
import { setupTotals, text, type SetupDraft } from "./setup-draft";

/** How many payment dates the line lists before it says "+N more". */
const PAYMENT_DATES_SHOWN = 6;

/**
 * Card 1, "Batch and fees": the batch (its default fee, end date and routine are shown with it), the monthly fee, the admitted, first
 * due and course end dates, and the payments the dates create. The fields save through the setup draft; this card only shows and edits
 * them. Read-only unless the admission is under review.
 */
export function SetupForm({
  id,
  step,
  draft,
  onEdit,
  errors,
  editable,
  savedBatch,
}: {
  id: string;
  step?: number;
  draft: SetupDraft;
  onEdit: (next: Partial<SetupDraft>) => void;
  errors: Record<string, string>;
  editable: boolean;
  /** The batch the admission was saved with, shown when it is no longer among the active batches. */
  savedBatch: Pick<AdmissionSetup, "batchNameEn" | "batchNameBn">;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney, pickTitle } = useFormat();
  const role = useRole();
  const batches = useBatches("ACTIVE");
  const batchOptions = batches.data ?? [];
  const currentBatchMissing = draft.batchId !== "" && !batchOptions.some((batch) => batch.id === draft.batchId);
  const chosen = batchOptions.find((batch) => batch.id === draft.batchId) ?? null;
  const disabled = !editable;

  function batchDetails(batch: BatchView) {
    const fee = formatMoney(batch.defaultMonthlyFee) ?? "";
    const routine = t(batch.routineUploaded ? "staffOffline.setupScreen.batch.routineYes" : "staffOffline.setupScreen.batch.routineNo");
    return { fee, end: formatDate(batch.endsOn), routine };
  }

  function chooseBatch(batch: BatchView | undefined) {
    onEdit({
      batchId: batch?.id ?? "",
      monthlyFee: batch ? text(batch.defaultMonthlyFee) : draft.monthlyFee,
      courseEndsOn: batch ? text(batch.endsOn) : draft.courseEndsOn,
    });
  }

  // The first due date is a month after the admitted date, and moves with it only while it still holds that default (plan D12).
  function changeAdmittedOn(value: string) {
    const wasDefault =
      draft.firstDueOn === "" || (draft.admittedOn !== "" && draft.firstDueOn === defaultFirstDue(draft.admittedOn));
    onEdit({ admittedOn: value, firstDueOn: value && wasDefault ? defaultFirstDue(value) : draft.firstDueOn });
  }

  const totals = setupTotals(draft);
  const shownDates = totals.dueDates.slice(0, PAYMENT_DATES_SHOWN).map((date) => formatDate(date));
  const moreDates = totals.dueDates.length - shownDates.length;

  const hint = chosen
    ? t("staffOffline.setupScreen.batch.line", batchDetails(chosen))
    : t("staffOffline.setup.batchHint");

  return (
    <SetupCard id={id} step={step} title={t("staffOffline.setupScreen.cards.batchFees")}>
      <Field label={t("staffOffline.setup.batch")} hint={hint} error={errors.batchId} required>
        <Select
          className="h-11"
          value={draft.batchId}
          disabled={disabled}
          onChange={(event) => chooseBatch(batchOptions.find((batch) => batch.id === event.target.value))}
          invalid={Boolean(errors.batchId)}
        >
          <option value="">{t("staffOffline.setup.batchNone")}</option>
          {currentBatchMissing ? (
            <option value={draft.batchId}>{pickTitle({ titleEn: savedBatch.batchNameEn, titleBn: savedBatch.batchNameBn }) || ""}</option>
          ) : null}
          {batchOptions.map((batch) => {
            const details = batchDetails(batch);
            return (
              <option key={batch.id} value={batch.id}>
                {t("staffOffline.setupScreen.batch.option", { name: `${batch.nameEn} / ${batch.nameBn}`, ...details })}
              </option>
            );
          })}
        </Select>
      </Field>

      {!batches.isPending && batchOptions.length === 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-dashed border-border p-3">
          <p className="text-meta text-muted">
            {t(role.isMasterAdmin ? "staffOffline.setupScreen.batch.noneMaster" : "staffOffline.setupScreen.batch.noneStaff")}
          </p>
          {role.isMasterAdmin ? (
            <Button variant="secondary" size="sm" href="/staff/offline-batches">
              {t("staffOffline.setupScreen.batch.create")}
            </Button>
          ) : null}
        </div>
      ) : null}

      <div className="grid gap-x-4 gap-y-3 sm:grid-cols-2">
        <Field label={t("staffOffline.setup.monthlyFee")} error={errors.monthlyFee} reserveHint required>
          <TextInput
            className="h-11"
            inputMode="decimal"
            value={draft.monthlyFee}
            disabled={disabled}
            onChange={(event) => onEdit({ monthlyFee: event.target.value })}
            invalid={Boolean(errors.monthlyFee)}
          />
        </Field>
        <Field label={t("staffOffline.setup.admittedOn")} error={errors.admittedOn} reserveHint required>
          <TextInput
            className="h-11"
            type="date"
            value={draft.admittedOn}
            disabled={disabled}
            onChange={(event) => changeAdmittedOn(event.target.value)}
            invalid={Boolean(errors.admittedOn)}
          />
        </Field>
        <Field label={t("staffOffline.setup.firstDue")} hint={t("staffOffline.setup.firstDueHint")} error={errors.firstDueOn} reserveHint required>
          <TextInput
            className="h-11"
            type="date"
            value={draft.firstDueOn}
            disabled={disabled}
            onChange={(event) => onEdit({ firstDueOn: event.target.value })}
            invalid={Boolean(errors.firstDueOn)}
          />
        </Field>
        <Field label={t("staffOffline.setup.courseEnds")} error={errors.courseEndsOn} reserveHint required>
          <TextInput
            className="h-11"
            type="date"
            value={draft.courseEndsOn}
            disabled={disabled}
            onChange={(event) => onEdit({ courseEndsOn: event.target.value })}
            invalid={Boolean(errors.courseEndsOn)}
          />
        </Field>
      </div>

      <p className="text-meta text-muted">
        {totals.months === 0
          ? t("staffOffline.setup.duesEmpty")
          : t("staffOffline.setupScreen.payments.line", {
              dates: [...shownDates, ...(moreDates > 0 ? [t("staffOffline.setupScreen.payments.more", { count: moreDates })] : [])].join(" · "),
            })}
      </p>
    </SetupCard>
  );
}
