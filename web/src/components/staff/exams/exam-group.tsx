"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { cn } from "@/components/ui/cn";
import { StatusPill, type CatalogStatusValue } from "@/components/staff/catalog/status-pill";
import { ExamRow, type ExamAction } from "@/components/staff/exams/exam-row";
import { GROUP_PAGE, readGroupOpen, writeGroupOpen, type ExamGroup } from "@/components/staff/exams/exam-groups";
import { useFormat } from "@/lib/format/use-format";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";

/**
 * One group of exams under a tinted header: the name, the status of the package, the exam count, a pending dot
 * and an Open package link. The header button is the accordion; its open state is remembered in this browser.
 * Each group shows GROUP_PAGE rows and then a Load more.
 */
export function ExamGroupSection({
  group,
  defaultOpen,
  packageStatus,
  categoryName,
  canCopy,
  canRemove,
  onAction,
}: {
  group: ExamGroup;
  defaultOpen: boolean;
  packageStatus?: CatalogStatusValue;
  /** The category's name for a row in the "Not in a package" group. */
  categoryName: (id: string) => string | undefined;
  canCopy: boolean;
  canRemove: boolean;
  onAction: (action: ExamAction, exam: StaffExam) => void;
}) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  // The stored choice is read when the group first appears; before that, the default applies.
  const [open, setOpen] = useState(() => readGroupOpen(group.key, defaultOpen));
  const [shown, setShown] = useState(GROUP_PAGE);
  const listId = `exam-group-${group.key.replace(/[^a-zA-Z0-9-]/g, "-")}`;
  const name =
    group.kind === "none"
      ? t("staffExams.group.none")
      : locale === "bn"
        ? group.titleBn || group.titleEn
        : group.titleEn || group.titleBn;
  const pending = group.exams.some((exam) => exam.pendingChanges);

  function toggle() {
    const next = !open;
    setOpen(next);
    writeGroupOpen(group.key, next);
  }

  const packageId = group.key.startsWith("package:") ? group.key.slice("package:".length) : undefined;

  return (
    <section aria-label={name} className="grid gap-2">
      <div className="flex flex-wrap items-center gap-2 rounded-control bg-action/5 px-2 py-1">
        <button
          type="button"
          aria-expanded={open}
          aria-controls={listId}
          aria-label={open ? t("staffExams.group.collapse") : t("staffExams.group.expand")}
          onClick={toggle}
          className="grid min-h-11 min-w-11 place-items-center rounded-control text-text hover:bg-bg"
        >
          <ChevronDown aria-hidden="true" className={cn("size-5 transition-transform", open ? "rotate-0" : "-rotate-90")} />
        </button>
        <h2 className="min-w-0 flex-1 truncate text-label font-semibold text-text">{name}</h2>
        {packageStatus ? <StatusPill status={packageStatus} /> : null}
        <span className="text-meta text-muted tabular-nums">{t("staffExams.group.examCount", { count: formatNumber(group.exams.length) })}</span>
        {pending ? (
          <span role="img" aria-label={t("staffExams.group.pending")} className="size-2.5 rounded-full bg-warn-fill" />
        ) : null}
        {packageId ? (
          <Button href={`/staff/modules/${packageId}`} variant="ghost" className="min-h-11">
            {t("staffExams.group.openPackage")}
          </Button>
        ) : null}
      </div>

      {open ? (
        <div id={listId} className="grid gap-2">
          <ul className="grid gap-2">
            {group.exams.slice(0, shown).map((exam) => (
              <ExamRow
                key={exam.id}
                exam={exam}
                groupPackageId={packageId}
                categoryLabel={group.kind === "none" && exam.categoryId ? categoryName(exam.categoryId) : undefined}
                canCopy={canCopy}
                canRemove={canRemove}
                onAction={onAction}
              />
            ))}
          </ul>
          {group.exams.length > shown ? (
            <Button variant="ghost" onClick={() => setShown((count) => count + GROUP_PAGE)} className="min-h-11 justify-self-start">
              {t("staffExams.group.loadMore")}
            </Button>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}
