"use client";

import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useBatches, type AdmissionSetup, type BatchView } from "@/lib/api/exam/offline-admin";
import { defaultFirstDue, fittingInstallments, MAX_INSTALLMENTS } from "./offline-model";
import { cn } from "@/components/ui/cn";
import { SetupCard } from "./setup-card";
import { rebalanceRows, rowsTotal, setupTotals, text, type PlanRow, type RowIssue, type SetupDraft } from "./setup-draft";
import { addMonths } from "./offline-model";

/**
 * Card 1, "Batch and fees": the batch (its course fee, instalments, end date and routine are shown with it), this student's course fee
 * and number of instalments, the admitted, first due and course end dates, and the instalments they create (course-fee-plan.md). The fields save through the setup draft; this card only shows and edits
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
  // A draft batch is not offered (V103); the batch already chosen stays, so the select can show it.
  const batchOptions = (batches.data ?? []).filter((batch) => batch.published !== false || batch.id === draft.batchId);
  const currentBatchMissing = draft.batchId !== "" && !batchOptions.some((batch) => batch.id === draft.batchId);
  const chosen = batchOptions.find((batch) => batch.id === draft.batchId) ?? null;
  const disabled = !editable;

  /** The routine as the details block shows it: the upload date when there is one, else the plain word. */
  function routineText(batch: BatchView): string {
    if (!batch.routineUploaded) {
      return t("staffOffline.batches.noRoutine");
    }
    return batch.routineUploadedAt
      ? t("staffOffline.batches.routineOn", { date: formatDate(batch.routineUploadedAt) })
      : t("staffOffline.setupScreen.batch.routineUploaded");
  }

  // A batch brings its course fee and end date, and its number of instalments cut down to what fits before that end.
  function chooseBatch(batch: BatchView | undefined) {
    const courseEndsOn = batch ? text(batch.endsOn) : draft.courseEndsOn;
    const fitting = draft.firstDueOn && courseEndsOn ? fittingInstallments(draft.firstDueOn, courseEndsOn) : null;
    onEdit({
      batchId: batch?.id ?? "",
      courseFee: batch ? text(batch.courseFee) : draft.courseFee,
      // A batch brings its even split again; the admin can then edit any row.
      custom: batch ? false : draft.custom,
      // Without a first due date yet, the count is left to the API, which cuts the batch's number to what fits.
      installments: batch ? (fitting == null ? "" : String(Math.min(batch.defaultInstallments, fitting))) : draft.installments,
      courseEndsOn,
    });
  }

  // 17 Nov: an application arrives with its batch already chosen, so the batch's fee, end date and instalments were never filled in and
  // the fields sat empty. When the setup opens with a batch but no course fee, it is filled from that batch once, as choosing it does.
  // 18 Nov: the admitted date is the admin's to enter (it starts the instalments), so it is never guessed; the number of instalments
  // follows once the first due date is known. The admin can change any of it.
  const filledFrom = useRef<string | null>(null);
  useEffect(() => {
    if (!editable || !chosen || draft.courseFee !== "" || filledFrom.current === chosen.id) {
      return;
    }
    filledFrom.current = chosen.id;
    const courseEndsOn = draft.courseEndsOn || text(chosen.endsOn);
    onEdit({
      courseFee: text(chosen.courseFee),
      custom: false,
      courseEndsOn,
      installments: draft.firstDueOn
        ? String(Math.min(chosen.defaultInstallments, fittingInstallments(draft.firstDueOn, courseEndsOn)))
        : draft.installments,
    });
  }, [editable, chosen, draft.courseFee, draft.firstDueOn, draft.courseEndsOn, draft.installments, onEdit]);

  // The first due date is a month after the admitted date, and moves with it only while it still holds that default (plan D12).
  function changeAdmittedOn(value: string) {
    const wasDefault =
      draft.firstDueOn === "" || (draft.admittedOn !== "" && draft.firstDueOn === defaultFirstDue(draft.admittedOn));
    const firstDueOn = value && wasDefault ? defaultFirstDue(value) : draft.firstDueOn;
    // With a batch chosen and no count yet, the count is the batch's, cut to what fits before the course ends.
    const installments =
      draft.installments === "" && chosen && firstDueOn && draft.courseEndsOn
        ? String(Math.min(chosen.defaultInstallments, fittingInstallments(firstDueOn, draft.courseEndsOn)))
        : draft.installments;
    onEdit({ admittedOn: value, firstDueOn, installments });
  }

  const totals = setupTotals(draft);

  /** The rows as the table holds them: the admin's own, or the even split written out, so any row can be edited (E1). */
  const tableRows: PlanRow[] = draft.custom
    ? draft.rows
    : totals.rows.map((row) => ({ dueOn: row.dueOn, amount: String(row.amount) }));

  /**
   * Editing a row makes the instalments the admin's own (E1). The course fee the admin agreed is kept in `courseFee`: the saved fee is
   * always the sum of the rows (E2), and the other rows are rebalanced so that sum stays the agreed fee (16 Nov).
   */
  function editRows(next: PlanRow[], courseFee?: string) {
    onEdit(courseFee === undefined ? { custom: true, rows: next } : { custom: true, rows: next, courseFee });
  }

  /** The fee the rows should add up to: the one the admin set, or what the rows add up to when there is none. */
  const agreedFee = Number(draft.courseFee) > 0 ? Number(draft.courseFee) : totals.fee;

  function changeRow(index: number, change: Partial<PlanRow>) {
    if (change.amount === undefined) {
      editRows(tableRows.map((row, at) => (at === index ? { ...row, ...change } : row)));
      return;
    }
    // 16 Nov: an amount typed into one row moves the others, so the course fee stays what the admin set.
    const result = rebalanceRows(tableRows, index, change.amount, agreedFee);
    const finished = /^\d+(\.\d{1,2})?$/.test(change.amount.trim());
    // When the fee cannot be kept (too big an amount, a single row), the fee follows the rows, as before.
    editRows(result.rows, result.balanced || !finished ? undefined : rowsTotal(result.rows));
  }

  function addRow() {
    const last = tableRows[tableRows.length - 1];
    const dueOn = last?.dueOn ? addMonths(last.dueOn, 1) : draft.firstDueOn || "";
    editRows([...tableRows, { dueOn, amount: "" }], rowsTotal(tableRows));
  }

  function removeRow(index: number) {
    // Adding or removing a row changes the plan itself, so the fee follows the rows that are left.
    const left = tableRows.filter((_, at) => at !== index);
    editRows(left, rowsTotal(left));
  }

  // Typing a total or a number of instalments spreads it evenly again, from the first row's date.
  function spread(change: Partial<SetupDraft>) {
    const firstDueOn = draft.custom && draft.rows[0]?.dueOn ? draft.rows[0].dueOn : draft.firstDueOn;
    onEdit({
      custom: false,
      courseFee: draft.custom ? String(totals.fee) : draft.courseFee,
      installments: draft.custom ? String(draft.rows.length) : draft.installments,
      firstDueOn,
      ...change,
    });
  }

  const issueText: Record<RowIssue, string> = {
    amount: t("staffOffline.setup.rowIssues.amount"),
    date: t("staffOffline.setup.rowIssues.date"),
    order: t("staffOffline.setup.rowIssues.order"),
    beforeAdmitted: t("staffOffline.setup.rowIssues.beforeAdmitted"),
    afterEnd: t("staffOffline.setup.rowIssues.afterEnd"),
  };

  return (
    <SetupCard id={id} step={step} title={t("staffOffline.setupScreen.cards.batchFees")}>
      <Field label={t("staffOffline.setup.batch")} error={errors.batchId} required>
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
          {batchOptions.map((batch) => (
            <option key={batch.id} value={batch.id}>
              {pickTitle({ titleEn: batch.nameEn, titleBn: batch.nameBn })}
            </option>
          ))}
        </Select>
      </Field>

      <div className="rounded-control border border-border-control bg-bg p-3">
        {chosen ? (
          <dl className="grid gap-x-4 gap-y-3 text-body sm:grid-cols-2">
            <BatchFact label={t("staffOffline.setup.courseFee")} value={formatMoney(chosen.courseFee) ?? "—"} />
            <BatchFact label={t("staffOffline.setup.batchInstallments")} value={String(chosen.defaultInstallments)} />
            <BatchFact label={t("staffOffline.setupScreen.batch.endsOn")} value={formatDate(chosen.endsOn)} />
            <BatchFact label={t("staffOffline.batches.routine")} value={routineText(chosen)} />
            <BatchFact label={t("staffOffline.setupScreen.batch.students")} value={String(chosen.studentCount)} />
          </dl>
        ) : (
          <p className="text-meta text-muted">{t("staffOffline.setupScreen.batch.choose")}</p>
        )}
      </div>

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

      <div className="grid items-start gap-x-4 gap-y-3 sm:grid-cols-2">
        <Field
          label={t("staffOffline.setup.admittedOn")}
          error={errors.admittedOn}
          hint={editable && draft.admittedOn === "" ? t("staffOffline.setup.admittedOnHint") : undefined}
          reserveHint
          required
        >
          <TextInput
            // 18 Nov: the date is never filled in for the admin, so an empty one stands out until it is entered.
            className={cn("h-11", editable && draft.admittedOn === "" && "border-warn ring-2 ring-warn/30")}
            type="date"
            value={draft.admittedOn}
            disabled={disabled}
            onChange={(event) => changeAdmittedOn(event.target.value)}
            invalid={Boolean(errors.admittedOn)}
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

      <section aria-labelledby="setup-instalments" className="grid gap-3 rounded-control border border-border-control p-3">
        <div className="grid gap-1">
          <h3 id="setup-instalments" className="text-label font-bold text-text">
            {t("staffOffline.setup.instalmentsTitle")}
          </h3>
          <p className="text-meta text-muted">{t("staffOffline.setup.instalmentsHint")}</p>
        </div>

        {/* Spread evenly: a total and a number of instalments, one a month from the first due date. */}
        <div className="grid gap-x-4 gap-y-3 sm:grid-cols-3">
          <Field label={t("staffOffline.setup.studentCourseFee")} error={errors.courseFee} reserveHint required>
            <TextInput
              className="h-11"
              inputMode="decimal"
              value={draft.custom ? String(totals.fee) : draft.courseFee}
              disabled={disabled}
              onChange={(event) => spread({ courseFee: event.target.value })}
              invalid={Boolean(errors.courseFee)}
            />
          </Field>
          <Field label={t("staffOffline.setup.installments")} error={errors.installments} reserveHint required>
            <TextInput
              className="h-11"
              type="number"
              inputMode="numeric"
              min={1}
              max={MAX_INSTALLMENTS}
              step={1}
              value={draft.custom ? String(draft.rows.length) : draft.installments}
              disabled={disabled}
              onChange={(event) => spread({ installments: event.target.value })}
              invalid={Boolean(errors.installments)}
            />
          </Field>
          <Field label={t("staffOffline.setup.firstDue")} error={errors.firstDueOn} reserveHint required>
            <TextInput
              className="h-11"
              type="date"
              value={draft.custom ? (draft.rows[0]?.dueOn ?? "") : draft.firstDueOn}
              disabled={disabled}
              onChange={(event) => spread({ firstDueOn: event.target.value })}
              invalid={Boolean(errors.firstDueOn)}
            />
          </Field>
        </div>
        <p className="text-meta text-muted">{t("staffOffline.setup.spreadHint")}</p>

        {tableRows.length === 0 ? (
          <p className="text-meta text-muted">{t("staffOffline.setup.duesEmpty")}</p>
        ) : (
          <ol className="grid gap-2">
            {tableRows.map((row, index) => {
              const issues = totals.rowIssues[index] ?? [];
              return (
                <li key={index} className="grid gap-1">
                  {/* On a phone the date and Remove share a line and the amount sits under the date, so the full date shows (17 Nov). */}
                  <div className="grid grid-cols-[2rem_minmax(0,1fr)_auto] items-center gap-2 sm:grid-cols-[2rem_minmax(0,1fr)_minmax(0,1fr)_auto]">
                    <span className="text-meta font-semibold text-muted">{index + 1}</span>
                    <TextInput
                      type="date"
                      aria-label={t("staffOffline.setup.rowDate", { no: index + 1 })}
                      value={row.dueOn}
                      disabled={disabled}
                      onChange={(event) => changeRow(index, { dueOn: event.target.value })}
                      invalid={issues.some((issue) => issue !== "amount")}
                    />
                    <TextInput
                      inputMode="decimal"
                      aria-label={t("staffOffline.setup.rowAmount", { no: index + 1 })}
                      className="max-sm:col-start-2 max-sm:row-start-2"
                      value={row.amount}
                      disabled={disabled}
                      onChange={(event) => changeRow(index, { amount: event.target.value })}
                      invalid={issues.includes("amount")}
                    />
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={disabled || tableRows.length <= 1}
                      onClick={() => removeRow(index)}
                      aria-label={t("staffOffline.setup.rowRemove", { no: index + 1 })}
                      className="max-sm:col-start-3 max-sm:row-start-1"
                    >
                      {t("staffOffline.setup.remove")}
                    </Button>
                  </div>
                  {issues.length > 0 ? (
                    <p className="ps-10 text-meta text-danger">{issues.map((issue) => issueText[issue]).join(" ")}</p>
                  ) : null}
                </li>
              );
            })}
          </ol>
        )}
        {errors.installmentPlan ? <p role="alert" className="text-meta text-danger">{errors.installmentPlan}</p> : null}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Button variant="secondary" size="sm" disabled={disabled || tableRows.length >= MAX_INSTALLMENTS} onClick={addRow}>
            {t("staffOffline.setup.addRow")}
          </Button>
          <p className="text-body font-semibold text-text">
            {t("staffOffline.setup.planTotal", { total: formatMoney(totals.fee) ?? "", count: tableRows.length })}
          </p>
        </div>
      </section>
    </SetupCard>
  );
}

/** One fact of the chosen batch: a label above its value, one column on a phone. */
function BatchFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid min-w-0 gap-0.5">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="min-w-0 break-words text-text">{value}</dd>
    </div>
  );
}
