"use client";

import { Pill } from "@/components/ui/pill";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import type { ResultExamType, ResultRow } from "@/lib/api/exam/offline-results";

/**
 * One result sheet as a table: the preview and a saved sheet use it (plan 7.6, 7.7). The columns follow the exam type. A row whose total
 * differs from the file's is tinted, with the file's value beside the calculated one. A zero row is tagged "might be absent" (rule R2).
 * An absent student has no marks. At phone width each row becomes a compact card: roll and name, then the total and rank.
 */
export function ResultTable({
  rows,
  examType,
  passMark,
  label,
}: {
  rows: ResultRow[];
  examType: ResultExamType;
  passMark: number | null;
  label: string;
}) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const showRightWrong = examType !== "WRITTEN";
  const showMcq = examType === "MCQ_WRITTEN";
  const showWritten = examType !== "MCQ";
  const showResult = passMark !== null;

  const mark = (value: number | null) => (value === null ? "—" : formatNumber(value));
  const rankOf = (row: ResultRow) => (row.absent || row.rank === null ? "—" : String(row.rank));

  return (
    <>
      <div className="hidden max-h-[28rem] overflow-auto rounded-card border border-border-control bg-card sm:block">
        <table aria-label={label} className="w-full min-w-[36rem] border-collapse text-body">
          <thead className="sticky top-0 z-[1] bg-bg">
            <tr className="border-b border-border-control text-start text-text">
              <th scope="col" className="whitespace-nowrap px-3 py-2.5 text-start text-meta font-semibold">
                {t("staffOfflineResults.upload.table.rank")}
              </th>
              <th scope="col" className="whitespace-nowrap px-3 py-2.5 text-start text-meta font-semibold">
                {t("staffOfflineResults.upload.table.roll")}
              </th>
              <th scope="col" className="px-3 py-2.5 text-start text-meta font-semibold">
                {t("staffOfflineResults.upload.table.name")}
              </th>
              {showRightWrong ? (
                <>
                  <th scope="col" className="px-3 py-2.5 text-end text-meta font-semibold">
                    {t("staffOfflineResults.upload.table.right")}
                  </th>
                  <th scope="col" className="px-3 py-2.5 text-end text-meta font-semibold">
                    {t("staffOfflineResults.upload.table.wrong")}
                  </th>
                </>
              ) : null}
              {showMcq ? (
                <th scope="col" className="px-3 py-2.5 text-end text-meta font-semibold">
                  {t("staffOfflineResults.upload.table.mcq")}
                </th>
              ) : null}
              {showWritten ? (
                <th scope="col" className="px-3 py-2.5 text-end text-meta font-semibold">
                  {t("staffOfflineResults.upload.table.written")}
                </th>
              ) : null}
              <th scope="col" className="px-3 py-2.5 text-end text-meta font-semibold">
                {t("staffOfflineResults.upload.table.total")}
              </th>
              {showResult ? (
                <th scope="col" className="whitespace-nowrap px-3 py-2.5 text-start text-meta font-semibold">
                  {t("staffOfflineResults.upload.table.result")}
                </th>
              ) : null}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={`${row.roll}-${row.admissionId ?? row.rowNumber ?? ""}`}
                className={cn(
                  "border-b border-border-control/60 align-middle last:border-0",
                  row.mismatch && "bg-warn/10",
                  row.absent && "text-muted",
                )}
              >
                <td className="px-3 py-2.5 tabular-nums">{rankOf(row)}</td>
                <td className="whitespace-nowrap px-3 py-2.5 font-latin tabular-nums">{row.roll}</td>
                <td className="px-3 py-2.5">
                  <NameCell row={row} />
                </td>
                {showRightWrong ? (
                  <>
                    <td className="px-3 py-2.5 text-end tabular-nums">{row.absent ? "—" : mark(row.right)}</td>
                    <td className="px-3 py-2.5 text-end tabular-nums">{row.absent ? "—" : mark(row.wrong)}</td>
                  </>
                ) : null}
                {showMcq ? <td className="px-3 py-2.5 text-end tabular-nums">{row.absent ? "—" : mark(row.mcq)}</td> : null}
                {showWritten ? <td className="px-3 py-2.5 text-end tabular-nums">{row.absent ? "—" : mark(row.written)}</td> : null}
                <td className="px-3 py-2.5 text-end">
                  <TotalCell row={row} />
                </td>
                {showResult ? (
                  <td className="px-3 py-2.5">
                    <PassCell row={row} />
                  </td>
                ) : null}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ul aria-label={label} className="grid gap-2 sm:hidden">
        {rows.map((row) => (
          <li
            key={`${row.roll}-${row.admissionId ?? row.rowNumber ?? ""}`}
            className={cn(
              "grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1 rounded-control border border-border-control bg-card px-3 py-2.5",
              row.mismatch && "border-warn/50 bg-warn/10",
              row.absent && "text-muted",
            )}
          >
            <div className="grid min-w-0 gap-0.5">
              <NameCell row={row} />
              <span className="font-latin text-meta tabular-nums text-muted">
                {t("staffOfflineResults.upload.table.roll")} {row.roll}
              </span>
            </div>
            <div className="grid justify-items-end gap-0.5 text-end">
              <TotalCell row={row} />
              <span className="text-meta tabular-nums text-muted">
                {t("staffOfflineResults.upload.table.rank")} {rankOf(row)}
              </span>
            </div>
            {showResult && !row.absent && row.passed !== null ? (
              <div className="col-span-2">
                <PassCell row={row} />
              </div>
            ) : null}
          </li>
        ))}
      </ul>
    </>
  );
}

/** The name, with the "might be absent" tag for a zero row. */
function NameCell({ row }: { row: ResultRow }) {
  const { t } = useLocale();
  return (
    <span className="grid min-w-0 content-start gap-1">
      <span className="break-words font-medium text-text">{row.studentName ?? "—"}</span>
      {row.maybeAbsent && !row.absent ? (
        <span>
          <Pill tone="muted">{t("staffOfflineResults.upload.table.maybeAbsent")}</Pill>
        </span>
      ) : null}
    </span>
  );
}

/** The total, with the file's own total under it when this row differs. An absent student shows the word Absent. */
function TotalCell({ row }: { row: ResultRow }) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  if (row.absent) {
    return <Pill tone="muted">{t("staffOfflineResults.upload.table.absent")}</Pill>;
  }
  return (
    <span className="grid justify-items-end gap-0.5">
      <span className="font-semibold tabular-nums text-text">{row.total === null ? "—" : formatNumber(row.total)}</span>
      {row.mismatch && row.fileTotal !== null ? (
        <span className="text-meta tabular-nums text-warn">
          {t("staffOfflineResults.upload.table.inFile", { value: formatNumber(row.fileTotal) })}
        </span>
      ) : null}
    </span>
  );
}

/** Pass or fail, from the pass mark. Nothing for an absent student. */
function PassCell({ row }: { row: ResultRow }) {
  const { t } = useLocale();
  if (row.absent || row.passed === null) {
    return <span className="text-muted">—</span>;
  }
  return row.passed ? (
    <Pill tone="ok">{t("staffOfflineResults.upload.table.pass")}</Pill>
  ) : (
    <Pill tone="danger">{t("staffOfflineResults.upload.table.fail")}</Pill>
  );
}
