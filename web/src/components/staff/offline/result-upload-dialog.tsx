"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useRole } from "@/lib/auth/guards";
import {
  RESULT_MAX_BYTES,
  isXlsxName,
  useCheckResult,
  usePublishResult,
  useSaveStagedResult,
  useSheetView,
  type ResultExamType,
  type ResultMessageParams,
  type ResultPreview,
  type ResultProblem,
  type ResultUploadInput,
  type ResultWarning,
} from "@/lib/api/exam/offline-results";
import { useFormat } from "@/lib/format/use-format";
import { offlineErrorText } from "./offline-error";
import { ResultTable } from "./result-table";

const EXAM_TYPES: ResultExamType[] = ["MCQ", "MCQ_WRITTEN", "WRITTEN"];

/** The codes with a sentence in staffOfflineResults.problems and .warnings. Any other code shows the API's English message. */
const PROBLEM_CODES = [
  "ROLL_NOT_IN_BATCH",
  "ROLL_IN_OTHER_BATCH",
  "ROW_DUPLICATE_ROLL",
  "TOTAL_ABOVE_FULL",
  "FULL_MARKS_DIFFER",
  "NEGATIVE_DIFFER",
  "NOT_A_NUMBER",
  "NOT_WHOLE",
  "NOT_TAKEN",
  "CANCELLED",
  "NOT_EXAM",
  "FUTURE_DATE",
  "PASS_MARK_ABOVE_FULL",
  "NO_ROWS",
  "NO_FULL_MARKS",
  "NO_HEADER",
  "MISSING_COLUMN",
  "FULL_MARKS_MISSING",
  "NEGATIVE_MISSING",
  "BELOW_ZERO",
  "COUNT_TOO_LARGE",
];
const WARNING_CODES = ["ROLL_MATCHED_ZERO_PADDED", "ABSENT", "DIFFERS"];

type Step = "choose" | "preview";
type Translate = (key: string, params?: Record<string, string | number>) => string;
type FieldError = { field: string; message: string };

/** The API's field errors of a failed call, or null when it gave none. */
function fieldErrorsOf(error: unknown): FieldError[] | null {
  if (error instanceof ApiError && error.fieldErrors && error.fieldErrors.length > 0) {
    return error.fieldErrors;
  }
  return null;
}

/** The sentence of a problem or a warning for its code, filled from its params. An unknown code shows the API's English message. */
function codeText(
  kind: "problems" | "warnings",
  known: readonly string[],
  item: { code: string; params?: ResultMessageParams | null; message: string },
  t: Translate,
  formatNumber: (value: number) => string,
): string {
  if (!known.includes(item.code)) {
    return item.message;
  }
  const values: Record<string, string> = {};
  for (const [name, value] of Object.entries(item.params ?? {})) {
    values[name] = typeof value === "number" ? formatNumber(value) : (value ?? "");
  }
  return t(`staffOfflineResults.${kind}.${item.code}`, values);
}

/** A reason without its closing full stop (English or Bangla), so that a sentence can end with its own stop. */
function withoutStop(text: string): string {
  return text.replace(/[.।]\s*$/, "");
}

/**
 * Uploads one exam's result sheet (plan 7.6). Step 1 takes the exam type, the pass mark and the .xlsx file, and checks the file. Step 2
 * shows what the check found: problems block the save, warnings and the table show the rest. Nothing is saved until "Save as staged"
 * (or "Save and publish" for the Master Admin, after a confirm). Going back to choose another file keeps the type and the pass mark.
 * A save that the API refuses with field errors (the data changed since the check) goes back to the preview, with those problems.
 */
export function ResultUploadDialog({
  classId,
  examLabel,
  open,
  onOpenChange,
}: {
  classId: string;
  /** The exam's name and date, shown under the title. */
  examLabel: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { formatDate, formatNumber } = useFormat();
  const { toast } = useToast();
  const role = useRole();
  const view = useSheetView(classId);
  const check = useCheckResult(classId);
  const save = useSaveStagedResult(classId);
  const publish = usePublishResult(classId);
  // The staged version that a new upload replaces, if any (rule R10).
  const staged = view.data?.staged ?? null;

  const [step, setStep] = useState<Step>("choose");
  const [examType, setExamType] = useState<ResultExamType | null>(null);
  const [passMark, setPassMark] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [fileKey, setFileKey] = useState(0);
  // Field errors, by field: "type" (the exam type cards), "file", "passMark".
  const [errors, setErrors] = useState<Record<string, string>>({});
  // Errors that belong to no field, in the form alert.
  const [formErrors, setFormErrors] = useState<string[]>([]);
  // The input the last check ran on: the save sends exactly this.
  const [checked, setChecked] = useState<ResultUploadInput | null>(null);
  const [preview, setPreview] = useState<ResultPreview | null>(null);
  // The problems a save answered with, when the data changed since the check. They replace the preview's problems until the file is checked again.
  const [saveProblems, setSaveProblems] = useState<string[] | null>(null);
  const [confirmPublish, setConfirmPublish] = useState(false);

  function reset() {
    setStep("choose");
    setExamType(null);
    setPassMark("");
    setFile(null);
    setErrors({});
    setFormErrors([]);
    setChecked(null);
    setPreview(null);
    setSaveProblems(null);
    setConfirmPublish(false);
  }

  function close(next: boolean) {
    if (!next) {
      reset();
    }
    onOpenChange(next);
  }

  /** A failed check: its field errors go under their fields, the rest into the form alert, and any other error becomes one sentence. */
  function showCheckFailure(error: unknown) {
    setStep("choose");
    const fields = fieldErrorsOf(error);
    if (!fields) {
      setErrors({});
      setFormErrors([offlineErrorText(error, t)]);
      return;
    }
    const byField: Record<string, string> = {};
    const others: string[] = [];
    for (const { field, message } of fields) {
      const slot = field === "file" ? "file" : field === "examType" ? "type" : field === "passMark" ? "passMark" : null;
      if (slot) {
        byField[slot] = byField[slot] ? `${byField[slot]} ${message}` : message;
      } else {
        others.push(message);
      }
    }
    setErrors(byField);
    setFormErrors(others);
  }

  async function runCheck(input: ResultUploadInput) {
    try {
      const result = await check.mutateAsync(input);
      setChecked(input);
      setPreview(result);
      setSaveProblems(null);
      setErrors({});
      setFormErrors([]);
      setStep("preview");
    } catch (error) {
      showCheckFailure(error);
    }
  }

  async function checkFile() {
    const problems: Record<string, string> = {};
    if (!examType) {
      problems.type = t("staffOfflineResults.upload.errors.type");
    }
    if (!file) {
      problems.file = t("staffOfflineResults.upload.errors.file");
    } else if (!isXlsxName(file.name)) {
      problems.file = t("staffOfflineResults.upload.errors.notXlsx");
    } else if (file.size > RESULT_MAX_BYTES) {
      problems.file = t("staffOfflineResults.upload.errors.tooLarge");
    }
    const mark = passMark.trim();
    if (mark !== "" && !(Number(mark) >= 0)) {
      problems.passMark = t("staffOfflineResults.upload.errors.passMark");
    }
    setErrors(problems);
    setFormErrors([]);
    if (Object.keys(problems).length > 0 || !examType || !file) {
      return;
    }
    await runCheck({ file, examType, passMark });
  }

  /** Checks the same input again: the save refused it, so the data behind it has changed. */
  async function checkAgain() {
    if (checked) {
      await runCheck(checked);
    }
  }

  function chooseAnother() {
    setStep("choose");
    setPreview(null);
    setChecked(null);
    setSaveProblems(null);
    setFile(null);
    setFileKey((key) => key + 1);
    setErrors({});
    setFormErrors([]);
  }

  /** A save the API refused with field errors, or with the data-changed answer: the dialog goes back to the preview, with the problems. */
  function saveFailed(error: unknown): boolean {
    const fields = fieldErrorsOf(error);
    const changed = error instanceof ApiError && error.code === "RESULT_CHANGED";
    if (!fields && !changed) {
      return false;
    }
    setSaveProblems(fields ? fields.map((field) => field.message) : [t("staffOfflineResults.upload.errors.changed")]);
    setErrors({});
    setFormErrors([]);
    setStep("preview");
    return true;
  }

  async function saveAsStaged() {
    if (!checked) {
      return;
    }
    try {
      await save.mutateAsync(checked);
      toast({ title: t("staffOfflineResults.upload.saved"), tone: "ok" });
      close(false);
    } catch (error) {
      if (!saveFailed(error)) {
        setFormErrors([offlineErrorText(error, t)]);
      }
    }
  }

  /**
   * Saves, then publishes. A save the API refuses goes back to the preview (the confirm closes). A publish that fails after a good save
   * closes the dialog too: the sheet is staged, and the toast says where to publish it.
   */
  async function saveAndPublish() {
    if (!checked) {
      return;
    }
    try {
      await save.mutateAsync(checked);
    } catch (error) {
      if (saveFailed(error)) {
        return;
      }
      throw new Error(offlineErrorText(error, t));
    }
    try {
      await publish.mutateAsync();
    } catch (error) {
      const reason = withoutStop(offlineErrorText(error, t));
      toast({ title: t("staffOfflineResults.upload.savedNotPublished", { reason }), tone: "danger" });
      close(false);
      return;
    }
    toast({ title: t("staffOfflineResults.upload.publishedToast"), tone: "ok" });
    close(false);
  }

  // The students the publish tells: the rows with an admission. The staged sheet does not exist until the save, so the count comes from the check.
  const notifyCount = preview ? preview.rows.filter((row) => row.admissionId !== null).length : 0;
  const canPublish = role.canPublishOfflineResult;
  const blocked = saveProblems !== null || (preview?.problems.length ?? 0) > 0;

  const footer =
    step === "choose" ? (
      <>
        <Button variant="ghost" onClick={() => close(false)}>
          {t("common.actions.cancel")}
        </Button>
        <Button onClick={() => void checkFile()} loading={check.isPending}>
          {t("staffOfflineResults.upload.check")}
        </Button>
      </>
    ) : saveProblems !== null ? (
      <>
        <Button variant="ghost" onClick={chooseAnother} disabled={check.isPending}>
          {t("staffOfflineResults.upload.chooseAnother")}
        </Button>
        <Button onClick={() => void checkAgain()} loading={check.isPending}>
          {t("staffOfflineResults.upload.checkAgain")}
        </Button>
      </>
    ) : (
      <>
        <Button variant="ghost" onClick={chooseAnother} disabled={save.isPending}>
          {t("staffOfflineResults.upload.chooseAnother")}
        </Button>
        <Button variant="secondary" onClick={() => void saveAsStaged()} disabled={blocked} loading={save.isPending && !confirmPublish}>
          {t("staffOfflineResults.upload.save")}
        </Button>
        {canPublish ? (
          <Button onClick={() => setConfirmPublish(true)} disabled={blocked || save.isPending}>
            {t("staffOfflineResults.upload.saveAndPublish")}
          </Button>
        ) : null}
      </>
    );

  return (
    <>
      <Dialog
        open={open}
        onOpenChange={close}
        title={t("staffOfflineResults.upload.title")}
        description={examLabel}
        className={step === "preview" ? "sm:max-w-3xl" : undefined}
        footer={footer}
      >
        <div className="grid min-w-0 gap-4">
          {formErrors.length > 0 ? (
            <div role="alert" className="grid gap-1 text-meta text-danger">
              {formErrors.map((message, index) => (
                <p key={index}>{message}</p>
              ))}
            </div>
          ) : null}
          {step === "preview" && preview ? previewContent(preview) : chooseContent()}
        </div>
      </Dialog>

      {canPublish ? (
        <ConfirmDialog
          open={confirmPublish}
          onOpenChange={setConfirmPublish}
          title={t("staffOfflineResults.upload.saveAndPublishTitle")}
          body={
            notifyCount === 1
              ? t("staffOfflineResults.upload.saveAndPublishBodyOne")
              : t("staffOfflineResults.upload.saveAndPublishBody", { count: notifyCount })
          }
          confirmLabel={t("staffOfflineResults.upload.saveAndPublish")}
          onConfirm={saveAndPublish}
        />
      ) : null}
    </>
  );

  // Plain functions, not components: a component declared here would be remounted on every render and lose focus from its inputs.
  /** Rule R10, review F6: whose staged version a save replaces. Shown when choosing the file and again beside Save. */
  function stagedNote() {
    if (!staged) return null;
    return (
      <p className="text-body text-warn">
        {staged.uploadedBy
          ? t("staffOfflineResults.upload.stagedReplaced", { name: staged.uploadedBy, date: formatDate(staged.uploadedAt) })
          : t("staffOfflineResults.upload.stagedReplacedNoName", { date: formatDate(staged.uploadedAt) })}
      </p>
    );
  }

  function chooseContent() {
    return (
      <>
        {stagedNote()}

        <fieldset className="grid min-w-0 gap-2">
          <legend className="mb-1 text-label font-medium text-text">{t("staffOfflineResults.upload.examType")}</legend>
          <div className="grid gap-2 sm:grid-cols-3">
            {EXAM_TYPES.map((type) => {
              const selected = examType === type;
              return (
                <label
                  key={type}
                  className={cn(
                    "flex min-w-0 cursor-pointer items-start gap-3 rounded-control border p-3",
                    selected ? "border-accent bg-accent/10" : "border-border-control bg-card",
                  )}
                >
                  <input
                    type="radio"
                    name="result-exam-type"
                    value={type}
                    checked={selected}
                    onChange={() => setExamType(type)}
                    aria-labelledby={`result-type-${type}-name`}
                    aria-describedby={`result-type-${type}-columns`}
                    className="mt-1 size-4 shrink-0 accent-accent"
                  />
                  <span className="grid min-w-0 gap-0.5">
                    <span id={`result-type-${type}-name`} className="font-semibold text-text">
                      {t(`staffOfflineResults.upload.types.${type}.name`)}
                    </span>
                    <span id={`result-type-${type}-columns`} className="text-meta text-muted">
                      {t(`staffOfflineResults.upload.types.${type}.columns`)}
                    </span>
                  </span>
                </label>
              );
            })}
          </div>
          {errors.type ? <p className="text-meta text-danger">{errors.type}</p> : null}
        </fieldset>

        <Field label={t("staffOfflineResults.upload.passMark")} hint={t("staffOfflineResults.upload.passMarkHelp")} error={errors.passMark}>
          <NumberInput
            min={0}
            step="any"
            inputMode="decimal"
            value={passMark}
            onChange={(event) => setPassMark(event.target.value)}
            invalid={Boolean(errors.passMark)}
            className="max-w-40"
          />
        </Field>

        <Field label={t("staffOfflineResults.upload.file")} hint={t("staffOfflineResults.upload.fileHint")} error={errors.file} required>
          <input
            key={fileKey}
            type="file"
            accept=".xlsx"
            className="block w-full min-w-0 text-body text-text file:mr-3 file:rounded-control file:border file:border-border-control file:bg-card file:px-3 file:py-2 file:text-label"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </Field>
      </>
    );
  }

  /** One line of the problems box: the row and roll, then the sentence for the problem's code. */
  function problemLine(problem: ResultProblem): string {
    const text = codeText("problems", PROBLEM_CODES, problem, t, formatNumber);
    if (problem.rowNumber === null) {
      return t("staffOfflineResults.upload.problemFile", { message: text });
    }
    return problem.roll
      ? t("staffOfflineResults.upload.problemRow", { row: problem.rowNumber, roll: problem.roll, message: text })
      : t("staffOfflineResults.upload.problemRowOnly", { row: problem.rowNumber, message: text });
  }

  function problemsBox(lines: string[]) {
    return (
      <div className="grid gap-2 rounded-control border border-danger/50 bg-danger/10 p-3">
        <p className="font-semibold text-danger">{t("staffOfflineResults.upload.problemsTitle")}</p>
        <ul className="grid gap-1 text-body text-text">
          {lines.map((line, index) => (
            <li key={index} className="break-words">
              {line}
            </li>
          ))}
        </ul>
      </div>
    );
  }

  function previewContent(result: ResultPreview) {
    // The problems of a refused save replace the check's: the file is checked again before anything is saved.
    if (saveProblems !== null) {
      return problemsBox(saveProblems);
    }

    const c = result.counts;
    const absent = result.rows.filter((row) => row.absent);
    // A row that differs from the file: its total when the totals differ, otherwise its position (a rank-only mismatch). Once each.
    const differing = result.rows.filter((row) => row.mismatch && !row.absent);
    const differLines = differing.flatMap((row) => {
      if (row.total !== null && row.fileTotal !== null && row.total !== row.fileTotal) {
        return [t("staffOfflineResults.upload.differLine", { roll: row.roll, file: formatNumber(row.fileTotal), calc: formatNumber(row.total) })];
      }
      if (row.rank !== null && row.fileRank !== null && row.rank !== row.fileRank) {
        return [t("staffOfflineResults.upload.rankLine", { roll: row.roll, file: formatNumber(row.fileRank), calc: formatNumber(row.rank) })];
      }
      return [];
    });
    const absentLines = absent.map((row) =>
      row.studentName
        ? t("staffOfflineResults.upload.absentLine", { roll: row.roll, name: row.studentName })
        : t("staffOfflineResults.upload.absentNoName", { roll: row.roll }),
    );
    const warningLines = result.warnings.map((warning: ResultWarning) => codeText("warnings", WARNING_CODES, warning, t, formatNumber));
    const warningsAll = [...differLines, ...absentLines, ...warningLines];

    return (
      <>
        <p className="text-body text-text">
          {t("staffOfflineResults.upload.summary", {
            fullMarks: formatNumber(result.fullMarks),
            negative: formatNumber(result.negative),
            rows: c.rows,
            matched: c.matched,
            absent: c.absent,
            mismatched: c.mismatched,
          })}
        </p>

        {stagedNote()}

        {result.problems.length > 0 ? problemsBox(result.problems.map(problemLine)) : null}

        {warningsAll.length > 0 ? (
          <div className="grid gap-2 rounded-control border border-warn/50 bg-warn/10 p-3">
            <p className="font-semibold text-warn">{t("staffOfflineResults.upload.warningsTitle")}</p>
            <ul className="grid gap-1 text-body text-text">
              {warningsAll.map((line, index) => (
                <li key={index} className="break-words">
                  {line}
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        <ResultTable rows={result.rows} examType={result.examType} passMark={result.passMark} label={t("staffOfflineResults.upload.table.label")} />
      </>
    );
  }
}
