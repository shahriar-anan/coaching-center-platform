"use client";

import { useMemo, useState } from "react";
import { X } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { cn } from "@/components/ui/cn";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Select } from "@/components/ui/select";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { categoryRows } from "@/components/staff/catalog/category-rows";
import { ExamStatusPill, examStatusKey } from "@/components/staff/catalog/status-pill";
import {
  deselectIds,
  isSelectable,
  selectAllVisible,
  selectableIds,
  summarise,
  toggleOne,
} from "@/components/staff/catalog/add-exams-selection";
import {
  useAddExamsToPackage,
  useAllModuleOptions,
  useCategoryTree,
  type BulkAddResult,
  type StaffExam,
} from "@/lib/api/exam/staff-catalog";
import { useExamList } from "@/lib/api/exam/staff-exams";

/** Rows shown before "Show more". Select all still covers every matching row. */
const PAGE = 50;
/** The most exams one add call takes (the server's limit). */
const MAX_ADD = 500;

const TYPES = ["MCQ", "WRITTEN"] as const;
const MODES = ["LIVE", "PRACTICE"] as const;
const STATUSES = ["DRAFT", "PUBLISHED"] as const;
const PHASES = ["UNANNOUNCED", "UPCOMING", "LIVE", "ENDED", "PRACTICE"] as const;

type Filters = {
  q: string;
  packageId: string;
  categoryId: string;
  includeSub: boolean;
  type: string;
  mode: string;
  status: string;
  phase: string;
};

const NO_FILTERS: Filters = { q: "", packageId: "", categoryId: "", includeSub: true, type: "", mode: "", status: "", phase: "" };

type SkipReason = BulkAddResult["skipped"][number]["reason"];

const REASON_KEYS: Record<SkipReason, string> = {
  ALREADY_IN_PACKAGE: "staffCatalog.moduleExams.addExams.reasons.ALREADY_IN_PACKAGE",
  ARCHIVED: "staffCatalog.moduleExams.addExams.reasons.ARCHIVED",
  NOT_FOUND: "staffCatalog.moduleExams.addExams.reasons.NOT_FOUND",
};

/**
 * Picks existing exams and adds them to a package together. A wide card: the filters and the matching exams on the left,
 * the exams picked so far on the right, with the count and the Add action under them. Filters narrow the list; the picks are
 * kept while the filters change, so exams can be gathered across several searches. Exams already in the package are shown
 * but cannot be picked. The dialog is mounted only while it is open.
 */
export function AddExamsDialog({
  moduleId,
  memberIds,
  open,
  onOpenChange,
  packageTitle,
}: {
  moduleId: string;
  memberIds: Set<string>;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  packageTitle?: string;
}) {
  const { t } = useLocale();
  const { pickTitle, formatDateTime, formatNumber } = useFormat();
  const { toast } = useToast();
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [shown, setShown] = useState(PAGE);
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  // Every exam picked so far, by id. Its status is kept here, so the draft note still counts it after the filters change.
  const [picked, setPicked] = useState<Map<string, StaffExam>>(() => new Map());
  const [confirming, setConfirming] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [skipped, setSkipped] = useState<BulkAddResult["skipped"]>([]);

  const list = useExamList({
    packageId: filters.packageId || undefined,
    categoryId: filters.categoryId || undefined,
    branch: filters.categoryId && filters.includeSub ? true : undefined,
    type: filters.type || undefined,
    mode: filters.mode || undefined,
    status: filters.status || undefined,
    phase: filters.phase || undefined,
    q: filters.q || undefined,
  });
  // Every page is loaded by the hook, so `rows` is all the matching exams. The server has one status filter, so withdrawn ones go here.
  const rows = useMemo(
    () => (list.data?.pages.flatMap((page) => page.content) ?? []).filter((exam) => exam.status !== "ARCHIVED"),
    [list.data],
  );
  // Select all waits until every page has arrived, and until the rows shown are for the current filters.
  const loaded = !list.isPending && !list.isPlaceholderData && !list.isFetchingNextPage && !list.hasNextPage && !list.isError;

  const packages = useAllModuleOptions();
  const packageOptions = (packages.data ?? []).filter((item) => item.id !== moduleId);
  const tree = useCategoryTree();
  const categoryOptions = categoryRows(tree.data);

  const selectable = selectableIds(rows, memberIds);
  const allSelected = selectable.length > 0 && selectable.every((id) => selected.has(id));
  const summary = summarise(selected, Array.from(picked.values()));
  const tooMany = summary.count > MAX_ADD;
  const filtersOn = Boolean(filters.q || filters.packageId || filters.categoryId || filters.type || filters.mode || filters.status || filters.phase);
  const visible = rows.slice(0, shown);
  // The exams picked and still ticked, in the order they were picked, for the right-hand panel.
  const pickedRows = Array.from(selected)
    .map((id) => picked.get(id))
    .filter((exam): exam is StaffExam => Boolean(exam));

  const add = useAddExamsToPackage(moduleId);

  function changeFilters(patch: Partial<Filters>) {
    setFilters((prev) => ({ ...prev, ...patch }));
    setShown(PAGE);
  }

  function togglePick(exam: StaffExam) {
    setSelected((prev) => toggleOne(prev, exam.id));
    setPicked((prev) => new Map(prev).set(exam.id, exam));
  }

  function selectAllMatching() {
    setSelected((prev) => selectAllVisible(prev, rows, memberIds));
    setPicked((prev) => {
      const next = new Map(prev);
      for (const exam of rows) {
        if (isSelectable(exam, memberIds)) {
          next.set(exam.id, exam);
        }
      }
      return next;
    });
  }

  function clearMatching() {
    setSelected((prev) => deselectIds(prev, selectable));
  }

  async function addSelected() {
    setFailure(null);
    setSkipped([]);
    try {
      const result = await add.mutateAsync(Array.from(selected));
      toast({
        title: t(result.added.length === 1 ? "staffCatalog.moduleExams.addExams.addedOne" : "staffCatalog.moduleExams.addExams.addedMany", {
          count: formatNumber(result.added.length),
        }),
        tone: "ok",
      });
      setSelected((prev) => deselectIds(prev, [...result.added, ...result.skipped.map((item) => item.examId)]));
      setConfirming(false);
      if (result.skipped.length === 0) {
        onOpenChange(false);
        return;
      }
      setSkipped(result.skipped);
    } catch (e) {
      setConfirming(false);
      setFailure(t(catalogErrorKey(e)));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={packageTitle ? t("staffCatalog.moduleExams.addExams.titleFor", { name: packageTitle }) : t("staffCatalog.moduleExams.addExams.title")}
      description={t("staffCatalog.moduleExams.addExams.body")}
      // Important, because the Dialog's own sm:max-w-md comes later in the stylesheet and would otherwise win.
      className="sm:max-w-6xl!"
    >
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="grid content-start gap-4">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <div className="sm:col-span-2 xl:col-span-3">
              <SearchInput value={filters.q} onChange={(q) => changeFilters({ q })} placeholder={t("staffExams.searchPlaceholder")} />
            </div>
            <Select aria-label={t("staffExams.filter.package")} value={filters.packageId} onChange={(e) => changeFilters({ packageId: e.target.value })}>
              <option value="">{t("staffExams.filter.allPackages")}</option>
              {packageOptions.map((item) => (
                <option key={item.id} value={item.id}>
                  {pickTitle(item)}
                </option>
              ))}
            </Select>
            <Select aria-label={t("staffExams.filter.category")} value={filters.categoryId} onChange={(e) => changeFilters({ categoryId: e.target.value })}>
              <option value="">{t("staffExams.filter.allCategories")}</option>
              {categoryOptions.map((item) => (
                <option key={item.id} value={item.id}>
                  {`${"— ".repeat(item.depth)}${pickTitle(item)}`}
                </option>
              ))}
            </Select>
            <Select aria-label={t("staffExams.filter.type")} value={filters.type} onChange={(e) => changeFilters({ type: e.target.value })}>
              <option value="">{t("staffExams.filter.allTypes")}</option>
              {TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`staffExams.type.${type}`)}
                </option>
              ))}
            </Select>
            <Select aria-label={t("staffExams.filter.mode")} value={filters.mode} onChange={(e) => changeFilters({ mode: e.target.value })}>
              <option value="">{t("staffExams.filter.allModes")}</option>
              {MODES.map((mode) => (
                <option key={mode} value={mode}>
                  {t(`staffExams.mode.${mode}`)}
                </option>
              ))}
            </Select>
            <Select aria-label={t("staffExams.filter.status")} value={filters.status} onChange={(e) => changeFilters({ status: e.target.value })}>
              <option value="">{t("staffExams.filter.allStatuses")}</option>
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {t(examStatusKey(status))}
                </option>
              ))}
            </Select>
            <Select aria-label={t("staffExams.filter.phase")} value={filters.phase} onChange={(e) => changeFilters({ phase: e.target.value })}>
              <option value="">{t("staffExams.filter.allPhases")}</option>
              {PHASES.map((phase) => (
                <option key={phase} value={phase}>
                  {t(`staffExams.phase.${phase}`)}
                </option>
              ))}
            </Select>
            <div className="flex flex-wrap items-center justify-between gap-2 sm:col-span-2 xl:col-span-3">
              <label className="flex min-h-11 items-center gap-2 text-body text-text">
                <input
                  type="checkbox"
                  checked={filters.includeSub}
                  onChange={(e) => changeFilters({ includeSub: e.target.checked })}
                  className="size-5"
                />
                {t("staffCatalog.moduleExams.addExams.includeSub")}
              </label>
              {filtersOn ? (
                <Button
                  variant="ghost"
                  onClick={() => {
                    setFilters(NO_FILTERS);
                    setShown(PAGE);
                  }}
                  className="min-h-11"
                >
                  {t("staffExams.reset")}
                </Button>
              ) : null}
            </div>
          </div>

          {skipped.length > 0 ? (
            <div role="alert" className="grid gap-2 rounded-control border border-border p-3">
              <p className="font-medium text-warn">{t("staffCatalog.moduleExams.addExams.skippedTitle")}</p>
              <ul className="grid gap-1 text-meta">
                {skipped.map((item) => {
                  const exam = picked.get(item.examId);
                  const reason = t(REASON_KEYS[item.reason]);
                  return <li key={item.examId}>{exam ? `${pickTitle(exam)}: ${reason}` : reason}</li>;
                })}
              </ul>
            </div>
          ) : null}

          {list.isError ? (
            <ErrorState onRetry={() => list.refetch()} message={t("staffExams.loadError")} error={list.error} />
          ) : list.isPending ? (
            <Skeleton variant="rows" count={5} />
          ) : (
            <div className="grid gap-2">
              <label className="flex min-h-11 items-center gap-3 text-body font-medium text-text">
                <input
                  type="checkbox"
                  className="size-5"
                  checked={allSelected}
                  disabled={!loaded || selectable.length === 0}
                  onChange={() => (allSelected ? clearMatching() : selectAllMatching())}
                />
                {t("staffCatalog.moduleExams.addExams.selectAll", { count: formatNumber(selectable.length) })}
              </label>
              {!loaded ? (
                <p role="status" className="text-meta text-muted">
                  {t("staffExams.loadingMore")}
                </p>
              ) : null}
              {rows.length === 0 && loaded ? (
                <EmptyState title={t("staffExams.empty.filteredTitle")} body={t("staffExams.empty.filteredBody")} />
              ) : (
                <ul className="grid gap-2 sm:grid-cols-2">
                  {visible.map((exam) => {
                    const inside = memberIds.has(exam.id);
                    const when = exam.mode === "PRACTICE" || !exam.liveAt ? t("staffCatalog.dateNotSet") : formatDateTime(exam.liveAt);
                    return (
                      <li key={exam.id}>
                        <label
                          className={cn(
                            "flex min-h-11 items-start gap-3 rounded-control border border-border bg-card p-3",
                            inside ? "cursor-default opacity-70" : "cursor-pointer",
                          )}
                        >
                          <input
                            type="checkbox"
                            className="mt-1 size-5 shrink-0"
                            checked={inside || selected.has(exam.id)}
                            disabled={inside}
                            onChange={() => togglePick(exam)}
                          />
                          <span className="grid min-w-0 flex-1 gap-1">
                            <span className="font-medium break-words">{pickTitle(exam)}</span>
                            <span className="text-meta text-muted">
                              {[t(`staffExams.type.${exam.examType}`), t(`staffExams.mode.${exam.mode ?? "LIVE"}`), when].join(" · ")}
                            </span>
                            <span className="flex flex-wrap items-center gap-2">
                              <ExamStatusPill status={exam.status} />
                              {inside ? <span className="text-meta text-muted">{t("staffCatalog.moduleExams.alreadyIn")}</span> : null}
                            </span>
                          </span>
                        </label>
                      </li>
                    );
                  })}
                </ul>
              )}
              {rows.length > shown ? (
                <Button variant="secondary" onClick={() => setShown((count) => count + PAGE)} className="justify-self-center">
                  {t("staffCatalog.moduleExams.addExams.showMore")}
                </Button>
              ) : null}
            </div>
          )}
        </div>

        <aside className="grid content-start gap-3 rounded-card border border-border bg-bg p-4 lg:sticky lg:top-0 lg:self-start">
          <div className="flex items-baseline justify-between gap-2">
            <h3 className="font-semibold">{t("staffCatalog.moduleExams.addExams.selectedTitle")}</h3>
            <p role="status" className="text-body font-medium tabular-nums">
              {t("staffCatalog.moduleExams.addExams.selectedCount", { count: formatNumber(summary.count) })}
            </p>
          </div>
          {summary.drafts > 0 ? (
            <p className="text-meta text-muted">{t("staffCatalog.moduleExams.addExams.draftsNote", { count: formatNumber(summary.drafts) })}</p>
          ) : null}

          {pickedRows.length === 0 ? (
            <p className="text-meta text-muted">{t("staffCatalog.moduleExams.addExams.selectedEmpty")}</p>
          ) : (
            <ul className="grid max-h-[40dvh] gap-2 overflow-y-auto pr-1">
              {pickedRows.map((exam) => (
                <li key={exam.id} className="flex items-center justify-between gap-2 rounded-control border border-border bg-card px-3 py-2">
                  <span className="grid min-w-0 gap-0.5">
                    <span className="truncate text-body">{pickTitle(exam)}</span>
                    <span className="text-meta text-muted">{t(`staffExams.type.${exam.examType}`)}</span>
                  </span>
                  <button
                    type="button"
                    aria-label={t("staffCatalog.moduleExams.addExams.removePick", { name: pickTitle(exam) })}
                    onClick={() => setSelected((prev) => deselectIds(prev, [exam.id]))}
                    className="inline-flex size-11 shrink-0 items-center justify-center rounded-control hover:bg-field"
                  >
                    <X aria-hidden="true" className="size-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}

          {failure ? (
            <p role="alert" className="text-meta text-danger">
              {failure}
            </p>
          ) : null}
          {tooMany ? <p className="text-meta text-warn">{t("staffCatalog.moduleExams.addExams.maxHint")}</p> : null}

          {confirming ? (
            <div className="grid gap-3 rounded-control border border-border bg-card p-3">
              <p className="font-medium">{t("staffCatalog.moduleExams.addExams.confirmTitle")}</p>
              <p className="text-meta text-muted">{t("staffCatalog.moduleExams.addExams.confirmBody")}</p>
              <div className="flex flex-wrap justify-end gap-2">
                <Button variant="secondary" onClick={() => setConfirming(false)} disabled={add.isPending}>
                  {t("staffCatalog.moduleExams.addExams.back")}
                </Button>
                <Button onClick={addSelected} loading={add.isPending} disabled={summary.count === 0 || tooMany}>
                  {t("staffCatalog.moduleExams.addExams.confirmButton")}
                </Button>
              </div>
            </div>
          ) : (
            <Button onClick={() => setConfirming(true)} disabled={summary.count === 0 || tooMany || add.isPending} className="w-full">
              {summary.count === 1
                ? t("staffCatalog.moduleExams.addExams.addOne")
                : t("staffCatalog.moduleExams.addExams.addMany", { count: formatNumber(summary.count) })}
            </Button>
          )}

        </aside>
      </div>
    </Dialog>
  );
}
