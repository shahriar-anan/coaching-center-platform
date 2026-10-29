"use client";

import { useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useListParams } from "@/lib/url/list-params";
import { useCategoryTree, useModules, type CategoryTreeNode, type StaffExam } from "@/lib/api/exam/staff-catalog";
import {
  useCopyListedExam,
  useDeleteListedExam,
  useExamList,
  useWithdrawListedExam,
  type ExamListParams,
} from "@/lib/api/exam/staff-exams";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Select } from "@/components/ui/select";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { NewExamDialog } from "@/components/staff/catalog/new-exam-dialog";
import { ExamGroupSection } from "@/components/staff/exams/exam-group";
import { ExamRow, type ExamAction } from "@/components/staff/exams/exam-row";
import {
  categoryNames,
  defaultGroupOpen,
  emptyStateFor,
  groupExams,
  type ExamView,
} from "@/components/staff/exams/exam-groups";

/** The list filters, in the URL. Their names are the query names the server reads, apart from `package`, `category` and `view`. */
const DEFAULTS = {
  q: "",
  package: "",
  category: "",
  type: "",
  mode: "",
  status: "",
  phase: "",
  popular: false,
  pending: false,
  view: "package",
};

const MODES = ["LIVE", "PRACTICE"] as const;
const STATUSES = ["DRAFT", "PUBLISHED", "ARCHIVED"] as const;
const PHASES = ["UNANNOUNCED", "UPCOMING", "LIVE", "ENDED", "PRACTICE"] as const;

/** Category names with their depth, for the category select. */
function categoryRows(nodes: CategoryTreeNode[] | undefined, depth = 0): { id: string; depth: number; titleEn: string; titleBn: string }[] {
  return (nodes ?? []).flatMap((node) => [
    { id: node.id, depth, titleEn: node.titleEn, titleBn: node.titleBn },
    ...categoryRows(node.children, depth + 1),
  ]);
}

/** The Exams page: exams grouped by package (or by category, or flat), with the filters, search and actions. */
export function ExamsPage() {
  const { t, locale } = useLocale();
  const { pickTitle } = useFormat();
  const role = useRole();
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const [params, setParams] = useListParams(DEFAULTS);
  const [text, setText] = useState(params.q);

  // Search waits 300 ms after the last keystroke before it changes the URL and the server query.
  useEffect(() => {
    const timer = setTimeout(() => {
      if (text !== params.q) {
        setParams({ q: text });
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [text, params.q, setParams]);

  const filters: ExamListParams = {
    packageId: params.package || undefined,
    categoryId: params.category || undefined,
    type: params.type || undefined,
    mode: params.mode || undefined,
    status: params.status || undefined,
    phase: params.phase || undefined,
    popular: params.popular,
    pending: params.pending,
    q: params.q || undefined,
  };
  const list = useExamList(filters);
  // Every page that has arrived, in the order the server sent them. The groups are formed from all of them.
  const rows = useMemo(() => list.data?.pages.flatMap((page) => page.content) ?? [], [list.data]);
  const tree = useCategoryTree();
  const packages = useModules({});

  const view: ExamView = params.view === "category" || params.view === "flat" ? params.view : "package";
  const names = useMemo(() => categoryNames(tree.data), [tree.data]);
  const groups = useMemo(() => groupExams(rows, view, names), [rows, view, names]);
  const packageStatus = useMemo(
    () => new Map((packages.data?.content ?? []).map((item) => [item.id, item.status])),
    [packages.data],
  );

  const filtered = Boolean(
    params.q || params.package || params.category || params.type || params.mode || params.status || params.phase || params.popular || params.pending,
  );
  const empty = emptyStateFor(rows.length, filtered);
  const canPending = role.isMasterAdmin || role.isSystemAdmin;

  // The sidebar's "Create" link opens the dialog with ?new=1. Closing it removes the query again.
  const createRequested = search?.get("new") === "1";
  const [creating, setCreating] = useState(false);
  const dialogOpen = creating || createRequested;

  // Row actions. Copy runs at once; withdraw and delete ask first.
  const copy = useCopyListedExam();
  const withdraw = useWithdrawListedExam();
  const remove = useDeleteListedExam();
  const [confirming, setConfirming] = useState<{ action: "withdraw" | "delete"; exam: StaffExam } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  function onAction(action: ExamAction, exam: StaffExam) {
    setActionError(null);
    if (action === "copy") {
      copy.mutate(exam.id, { onError: () => setActionError(t("staffExams.actionFailed")) });
      return;
    }
    setConfirming({ action, exam });
  }

  // The dialog stays open until this resolves; a failure is shown on the page, not thrown.
  async function runConfirmed() {
    if (!confirming) {
      return;
    }
    const { action, exam } = confirming;
    setConfirming(null);
    const mutation = action === "withdraw" ? withdraw : remove;
    try {
      await mutation.mutateAsync(exam.id);
    } catch {
      setActionError(t("staffExams.actionFailed"));
    }
  }

  function closeDialog(open: boolean) {
    if (!open) {
      setCreating(false);
      if (createRequested && pathname) {
        router.replace(pathname);
      }
    }
  }

  const categoryOptions = categoryRows(tree.data);
  const packageOptions = packages.data?.content ?? [];
  const confirmName = confirming ? pickTitle(confirming.exam) : "";

  return (
    <div className="grid gap-4">
      <PageHeader
        title={t("staffExams.title")}
        breadcrumbs={[{ label: t("staffCatalog.nav.home"), href: "/staff" }, { label: t("staffExams.title") }]}
        actions={
          role.canAuthor ? (
            <Button onClick={() => setCreating(true)}>{t("staffExams.new")}</Button>
          ) : undefined
        }
      />

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div className="sm:col-span-2">
          <SearchInput value={text} onChange={setText} placeholder={t("staffExams.searchPlaceholder")} />
        </div>
        <Select aria-label={t("staffExams.filter.package")} value={params.package} onChange={(e) => setParams({ package: e.target.value })}>
          <option value="">{t("staffExams.filter.allPackages")}</option>
          {packageOptions.map((item) => (
            <option key={item.id} value={item.id}>
              {locale === "bn" ? item.titleBn || item.titleEn : item.titleEn || item.titleBn}
            </option>
          ))}
        </Select>
        <Select aria-label={t("staffExams.filter.category")} value={params.category} onChange={(e) => setParams({ category: e.target.value })}>
          <option value="">{t("staffExams.filter.allCategories")}</option>
          {categoryOptions.map((item) => (
            <option key={item.id} value={item.id}>
              {`${"— ".repeat(item.depth)}${locale === "bn" ? item.titleBn : item.titleEn}`}
            </option>
          ))}
        </Select>
        <Select aria-label={t("staffExams.filter.type")} value={params.type} onChange={(e) => setParams({ type: e.target.value })}>
          <option value="">{t("staffExams.filter.allTypes")}</option>
          <option value="MCQ">{t("staffExams.type.MCQ")}</option>
          <option value="WRITTEN">{t("staffExams.type.WRITTEN")}</option>
        </Select>
        <Select aria-label={t("staffExams.filter.mode")} value={params.mode} onChange={(e) => setParams({ mode: e.target.value })}>
          <option value="">{t("staffExams.filter.allModes")}</option>
          {MODES.map((mode) => (
            <option key={mode} value={mode}>
              {t(`staffExams.mode.${mode}`)}
            </option>
          ))}
        </Select>
        <Select aria-label={t("staffExams.filter.status")} value={params.status} onChange={(e) => setParams({ status: e.target.value })}>
          <option value="">{t("staffExams.filter.allStatuses")}</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {t(`staffCatalog.status.${status}`)}
            </option>
          ))}
        </Select>
        <Select aria-label={t("staffExams.filter.phase")} value={params.phase} onChange={(e) => setParams({ phase: e.target.value })}>
          <option value="">{t("staffExams.filter.allPhases")}</option>
          {PHASES.map((phase) => (
            <option key={phase} value={phase}>
              {t(`staffExams.phase.${phase}`)}
            </option>
          ))}
        </Select>
        <Select aria-label={t("staffExams.filter.view")} value={view} onChange={(e) => setParams({ view: e.target.value })}>
          <option value="package">{t("staffExams.view.package")}</option>
          <option value="category">{t("staffExams.view.category")}</option>
          <option value="flat">{t("staffExams.view.flat")}</option>
        </Select>
        <label className="flex min-h-11 items-center gap-2 text-body text-text">
          <input type="checkbox" checked={params.popular} onChange={(e) => setParams({ popular: e.target.checked })} className="size-5" />
          {t("staffExams.filter.popular")}
        </label>
        {canPending ? (
          <label className="flex min-h-11 items-center gap-2 text-body text-text">
            <input type="checkbox" checked={params.pending} onChange={(e) => setParams({ pending: e.target.checked })} className="size-5" />
            {t("staffExams.filter.pending")}
          </label>
        ) : null}
        {filtered ? (
          <Button
            variant="ghost"
            onClick={() => {
              setText("");
              setParams({ q: "", package: "", category: "", type: "", mode: "", status: "", phase: "", popular: false, pending: false });
            }}
            className="min-h-11 justify-self-start"
          >
            {t("staffExams.reset")}
          </Button>
        ) : null}
      </div>

      {list.isError && !list.data ? (
        <ErrorState onRetry={() => list.refetch()} message={t("staffExams.loadError")} />
      ) : list.isPending ? (
        <Skeleton variant="rows" count={5} />
      ) : (
        <>
          {list.isError ? (
            <p role="alert" className="text-meta text-danger">
              {t("staffExams.loadError")}
            </p>
          ) : null}
          {actionError ? (
            <p role="alert" className="text-meta text-danger">
              {actionError}
            </p>
          ) : null}
          {list.isFetchingNextPage ? (
            <p role="status" className="text-meta text-muted">
              {t("staffExams.loadingMore")}
            </p>
          ) : null}
          {empty === "empty" ? (
            <EmptyState
              title={t("staffExams.empty.title")}
              body={t("staffExams.empty.body")}
              action={role.canAuthor ? <Button onClick={() => setCreating(true)}>{t("staffExams.new")}</Button> : undefined}
            />
          ) : empty === "filtered" ? (
            <EmptyState
              title={t("staffExams.empty.filteredTitle")}
              body={t("staffExams.empty.filteredBody")}
              action={<Button variant="ghost" onClick={() => setParams({ q: "", package: "", category: "", type: "", mode: "", status: "", phase: "", popular: false, pending: false })}>{t("staffExams.reset")}</Button>}
            />
          ) : view === "flat" ? (
            <ul className="grid gap-2">
              {groups[0]?.exams.map((exam) => (
                <ExamRow key={exam.id} exam={exam} canCopy={role.canAuthor} canRemove={role.canDelete} onAction={onAction} />
              ))}
            </ul>
          ) : (
            <div className="grid gap-5">
              {groups.map((group) => (
                <ExamGroupSection
                  key={group.key}
                  group={group}
                  defaultOpen={defaultGroupOpen(groups.length)}
                  packageStatus={group.kind === "package" && group.key.startsWith("package:") ? packageStatus.get(group.key.slice("package:".length)) : undefined}
                  categoryName={(id) => {
                    const found = names.get(id);
                    return found ? (locale === "bn" ? found.titleBn : found.titleEn) : undefined;
                  }}
                  canCopy={role.canAuthor}
                  canRemove={role.canDelete}
                  onAction={onAction}
                />
              ))}
            </div>
          )}
        </>
      )}

      <NewExamDialog open={dialogOpen} onOpenChange={closeDialog} />

      {confirming ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => {
            if (!open) setConfirming(null);
          }}
          title={
            confirming.action === "withdraw"
              ? t("staffExams.withdrawTitle", { name: confirmName })
              : t("staffExams.deleteTitle", { name: confirmName })
          }
          body={confirming.action === "withdraw" ? t("staffExams.withdrawBody") : t("staffExams.deleteBody")}
          confirmLabel={confirming.action === "withdraw" ? t("staffExams.withdrawConfirm") : t("staffExams.deleteConfirm")}
          tone="danger"
          onConfirm={runConfirmed}
        />
      ) : null}
    </div>
  );
}
