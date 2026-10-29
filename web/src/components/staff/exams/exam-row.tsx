"use client";

import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { RowMenu, type RowMenuItem } from "@/components/ui/row-menu";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { useFormat } from "@/lib/format/use-format";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";

export type ExamAction = "copy" | "withdraw" | "delete";

/**
 * One exam on the Exams page: title, type, mode, window, price, status, a pending dot, an explicit Open
 * button, and a row menu. The menu offers Results and Leaderboard, Copy where the role may copy, and
 * Withdraw (published) or Delete (draft) where the role may remove. `groupPackageId` is the package this row
 * is listed under, so "Also in" counts the other packages.
 */
export function ExamRow({
  exam,
  groupPackageId,
  categoryLabel,
  canCopy,
  canRemove,
  onAction,
}: {
  exam: StaffExam;
  groupPackageId?: string;
  /** Shown under the title in the "Not in a package" group, where the category is the only place. */
  categoryLabel?: string;
  canCopy: boolean;
  canRemove: boolean;
  onAction: (action: ExamAction, exam: StaffExam) => void;
}) {
  const { t } = useLocale();
  const { pickTitle, formatDateTime, formatMoney, formatNumber } = useFormat();
  const title = pickTitle(exam);
  const otherPackages = (exam.packages ?? []).filter((ref) => ref.id !== groupPackageId).length;
  const window =
    exam.mode === "PRACTICE" ? t("staffExams.mode.PRACTICE") : exam.liveAt ? `${formatDateTime(exam.liveAt)}${exam.endAt ? ` – ${formatDateTime(exam.endAt)}` : ""}` : t("staffExams.row.undated");
  const price = exam.free ? t("staffExams.row.free") : exam.price !== null ? formatMoney(exam.price) : t("staffExams.row.noPrice");

  const items: RowMenuItem[] = [
    { label: t("staffExams.row.results"), href: `/staff/exams/${exam.id}/results` },
    { label: t("staffExams.row.leaderboard"), href: `/staff/exams/${exam.id}/leaderboard` },
  ];
  if (canCopy) {
    items.push({ label: t("staffExams.row.copy"), onClick: () => onAction("copy", exam) });
  }
  if (canRemove && exam.status === "PUBLISHED") {
    items.push({ label: t("staffExams.row.withdraw"), onClick: () => onAction("withdraw", exam), tone: "danger" });
  }
  if (canRemove && exam.status === "DRAFT") {
    items.push({ label: t("staffExams.row.delete"), onClick: () => onAction("delete", exam), tone: "danger" });
  }

  return (
    <li className="grid min-w-0 gap-2 rounded-control border border-border bg-card p-3 sm:grid-cols-[minmax(0,2.4fr)_minmax(0,1fr)_minmax(0,1.6fr)_minmax(0,0.9fr)_auto_auto] sm:items-center">
      <div className="flex min-w-0 items-start gap-2">
        {exam.pendingChanges ? (
          <span role="img" aria-label={t("staffExams.row.pending")} className="mt-2 size-2.5 shrink-0 rounded-full bg-warn-fill" />
        ) : null}
        <div className="min-w-0">
          <p className="break-words font-semibold text-text">{title}</p>
          {otherPackages > 0 ? (
            <p className="text-meta text-muted">{t("staffExams.row.also", { count: formatNumber(otherPackages) })}</p>
          ) : null}
          {categoryLabel ? <p className="text-meta text-muted">{categoryLabel}</p> : null}
        </div>
      </div>
      <p className="text-meta text-muted">
        {t(`staffExams.type.${exam.examType}`)} · {t(`staffExams.mode.${exam.mode ?? "LIVE"}`)}
      </p>
      <p className="text-meta text-muted tabular-nums">{window}</p>
      <p className="text-meta text-text tabular-nums">{price}</p>
      <div className="flex items-center gap-2">
        <StatusPill status={exam.status} />
        <Button href={`/staff/exams/${exam.id}`} variant="secondary" className="min-h-11">
          {t("staffExams.row.open")}
        </Button>
      </div>
      {items.length > 0 ? <RowMenu items={items} label={t("staffExams.row.menu")} /> : null}
    </li>
  );
}
