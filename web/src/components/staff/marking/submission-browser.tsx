"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill } from "@/components/ui/pill";
import { SearchInput } from "@/components/ui/search-input";
import { Select } from "@/components/ui/select";
import { SplitBrowser, type TreeNode } from "@/components/ui/split-browser";
import { Tabs } from "@/components/ui/tabs";
import { Table, type Column } from "@/components/ui/table";
import { BulkPublishSelected } from "@/components/staff/marking/bulk-publish";
import {
  buildBrowserTree,
  FEED_FILTERS,
  FEED_SORTS,
  feedStatus,
  selectedNode,
  selectionFor,
  sortRows,
  statusTone,
  statusWordKey,
} from "@/components/staff/marking/marking-queue";
import { useRole } from "@/lib/auth/guards";
import { useParticipationRows } from "@/lib/api/exam/staff-home";
import {
  FEED_PAGE_MAX,
  useFeed,
  useFeedByModules,
  useMarkingModules,
  type FeedRow,
  type FeedSort,
} from "@/lib/api/exam/staff-marking";
import { useFormat } from "@/lib/format/use-format";
import { useServerNow } from "@/lib/time/clock";
import { useListParams } from "@/lib/url/list-params";
import { useLocale } from "@/i18n/locale-context";

const PAGE_SIZE = 20;
const DASH = "—";
const SORT_KEY: Record<FeedSort, string> = {
  "submittedAt,desc": "newest",
  "submittedAt,asc": "oldest",
  studentName: "name",
};

/**
 * `/staff/submissions`: the papers across the modules this user may mark, in a tree of
 * category → module → exam on the left. The selection, filter, sort and search live in the URL.
 * Instructors start on the papers still waiting; Master Admin and System Admin start on all.
 */
export function SubmissionBrowser() {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const { canPublishResult, isInstructor } = useRole();
  const defaultStatus = isInstructor ? "AWAITING" : "ALL";

  const [params, setParams] = useListParams({
    cat: "",
    module: "",
    exam: "",
    status: defaultStatus,
    sort: "submittedAt,desc",
    q: "",
    page: 0,
  });
  const now = useServerNow(60_000);
  const modules = useMarkingModules();
  const written = useParticipationRows({ type: "WRITTEN", sort: "title", size: FEED_PAGE_MAX });
  const moduleList = useMemo(() => modules.data?.content ?? [], [modules.data]);
  const exams = useMemo(() => written.data?.content ?? [], [written.data]);
  const index = useMemo(
    () => buildBrowserTree(moduleList, exams, format.pickTitle, t("staffMarking.browser.allPapers")),
    [moduleList, exams, format.pickTitle, t],
  );

  const sort: FeedSort = FEED_SORTS.includes(params.sort as FeedSort) ? (params.sort as FeedSort) : "submittedAt,desc";
  const base = { status: feedStatus(params.status), examId: params.exam, q: params.q, sort, ready: params.status === "READY" };
  const categoryModules = params.cat ? (index.categoryModules.get(params.cat) ?? []) : [];

  // Every scope but a category is one feed call. A category reads each module's first page.
  const single = useFeed({ ...base, moduleId: params.module, page: params.page, size: PAGE_SIZE }, !params.cat);
  const byModule = useFeedByModules(categoryModules, base);
  const rows: FeedRow[] = params.cat ? sortRows(byModule.flatMap((query) => query.data?.content ?? []), sort) : (single.data?.content ?? []);
  const totalPages = params.cat ? 1 : (single.data?.totalPages ?? 0);
  const total = params.cat ? rows.length : (single.data?.totalElements ?? 0);
  const truncated = byModule.some((query) => (query.data?.totalElements ?? 0) > FEED_PAGE_MAX);
  const loading = params.cat ? byModule.some((query) => query.isPending) : single.isPending;
  const failed = params.cat ? byModule.some((query) => query.isError) : single.isError;

  const [ticked, setTicked] = useState<string[]>([]);
  const drafts = rows.filter((row) => row.status === "DRAFT");
  const tickedNow = ticked.filter((id) => drafts.some((row) => row.participantId === id));
  const allTicked = drafts.length > 0 && tickedNow.length === drafts.length;

  const moduleById = useMemo(() => new Map(moduleList.map((module) => [module.id, module])), [moduleList]);
  const selectedId = selectedNode(params);
  const headerTitle = labelOf(index.tree, selectedId) ?? t("staffMarking.browser.title");
  const selectedExam = params.exam ? exams.find((row) => row.examId === params.exam) : undefined;

  // The exams inside the selection (an exam, a module, a category, or everything). The tab counts read these.
  const scopeExams = useMemo(() => {
    if (params.exam) {
      return exams.filter((row) => row.examId === params.exam);
    }
    if (params.module) {
      return exams.filter((row) => row.moduleId === params.module);
    }
    if (params.cat) {
      const inside = new Set(index.categoryModules.get(params.cat) ?? []);
      return exams.filter((row) => inside.has(row.moduleId));
    }
    return exams;
  }, [exams, index, params.cat, params.exam, params.module]);

  /** The count on a status tab, for the selection on screen. READY has no count of its own yet, so it shows none. */
  function scopedCount(filter: string): number | undefined {
    const sum = (pick: (row: (typeof scopeExams)[number]) => number | null | undefined) =>
      scopeExams.reduce((total, row) => total + (pick(row) ?? 0), 0);
    if (filter === "AWAITING") {
      return sum((row) => row.awaitingMarking);
    }
    if (filter === "DRAFT") {
      return sum((row) => row.draftsAwaitingPublish);
    }
    if (filter === "PUBLISHED") {
      return sum((row) => row.published);
    }
    return undefined;
  }

  function choose(nodeId: string) {
    const next = selectionFor(nodeId, index);
    if (next) {
      setTicked([]);
      setParams({ ...next, page: 0 });
    }
  }

  function toggle(id: string) {
    setTicked((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  }

  function retry() {
    if (params.cat) {
      byModule.forEach((query) => void query.refetch());
    } else {
      void single.refetch();
    }
  }

  function submittedText(iso: string | null): string {
    if (!iso) {
      return DASH;
    }
    const age = now - Date.parse(iso);
    if (age >= 0 && age < 86_400_000) {
      return t("staffMarking.time.ago", { duration: format.formatRelative(age) });
    }
    return format.formatDateTime(iso);
  }

  const columns: Column<FeedRow>[] = [
    ...(canPublishResult
      ? [
          {
            key: "tick",
            header: (
              <input
                type="checkbox"
                aria-label={t("staffMarking.browser.tickAll")}
                disabled={drafts.length === 0}
                checked={allTicked}
                onChange={() => setTicked(allTicked ? [] : drafts.map((row) => row.participantId))}
              />
            ),
            cell: (row: FeedRow) =>
              row.status === "DRAFT" ? (
                <input
                  type="checkbox"
                  aria-label={t("staffMarking.browser.tickRow", { name: row.studentName })}
                  checked={ticked.includes(row.participantId)}
                  onClick={(event) => event.stopPropagation()}
                  onChange={() => toggle(row.participantId)}
                />
              ) : null,
          },
        ]
      : []),
    {
      key: "student",
      header: t("staffMarking.browser.student"),
      // A real link, so the paper opens from the keyboard too; the row click is kept for pointers.
      cell: (row) => (
        <Link
          href={`/staff/submissions/${row.participantId}?m=${row.moduleId}`}
          className="font-medium text-text underline-offset-4 hover:underline"
          onClick={(event) => event.stopPropagation()}
        >
          {row.studentName}
        </Link>
      ),
    },
    {
      key: "exam",
      header: t("staffMarking.browser.exam"),
      cell: (row) => format.pickTitle({ titleEn: row.examTitleEn, titleBn: row.examTitleBn }),
    },
    {
      key: "module",
      header: t("staffMarking.browser.module"),
      cell: (row) => format.pickTitle({ titleEn: row.moduleTitleEn, titleBn: row.moduleTitleBn }),
    },
    ...(canPublishResult
      ? [
          {
            key: "teacher",
            header: t("staffMarking.browser.teacher"),
            cell: (row: FeedRow) => moduleById.get(row.moduleId)?.instructor?.name ?? DASH,
          },
        ]
      : []),
    { key: "submitted", header: t("staffMarking.browser.submitted"), nowrap: true, cell: (row) => submittedText(row.submittedAt) },
    {
      key: "status",
      header: t("staffMarking.browser.status"),
      nowrap: true,
      cell: (row) => <Pill tone={statusTone(row.status)}>{t(`staffMarking.statusWord.${statusWordKey(row)}`)}</Pill>,
    },
  ];

  const emptyState =
    modules.isSuccess && moduleList.length === 0 ? (
      <EmptyState title={t("staffMarking.browser.noModules")} body={t("staffMarking.browser.noModulesBody")} />
    ) : (
      <EmptyState title={t("staffMarking.browser.noRows")} body={t("staffMarking.browser.noRowsBody")} />
    );

  return (
    <div className="grid gap-5">
      <PageHeader
        title={headerTitle}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffMarking.browser.title") },
        ]}
      />

      <SplitBrowser
        tree={index.tree}
        selectedId={selectedId}
        onSelect={choose}
        heading={t("staffMarking.browser.treeNote")}
        toggleLabel={(label, expanded) =>
          t(expanded ? "staffMarking.browser.hideGroup" : "staffMarking.browser.showGroup", { name: label })
        }
      >
        <div className="grid gap-4">
          <Tabs
            ariaLabel={t("staffMarking.browser.filterLabel")}
            tabs={FEED_FILTERS.filter((filter) => filter !== "READY" || canPublishResult).map((filter) => ({
              value: filter,
              label: t(`staffMarking.browser.filter.${filter}`),
              badge: scopedCount(filter),
            }))}
            value={params.status}
            onChange={(status) => setParams({ status, page: 0 })}
          />
          <p className="text-meta text-muted">{t("staffMarking.browser.countsFor", { scope: headerTitle })}</p>

          <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_minmax(0,12rem)_auto] md:items-end">
            <Field label={t("staffMarking.browser.searchLabel")}>
              <SearchInput
                id="browser-q"
                value={params.q}
                onChange={(q) => setParams({ q, page: 0 })}
                placeholder={t("staffMarking.browser.searchPlaceholder")}
              />
            </Field>
            <Field label={t("staffMarking.browser.sortLabel")}>
              <Select value={sort} onChange={(event) => setParams({ sort: event.target.value, page: 0 })}>
                {FEED_SORTS.map((item) => (
                  <option key={item} value={item}>
                    {t(`staffMarking.browser.sort.${SORT_KEY[item]}`)}
                  </option>
                ))}
              </Select>
            </Field>
            <Button
              variant="ghost"
              onClick={() => setParams({ status: defaultStatus, sort: "submittedAt,desc", q: "", page: 0 })}
            >
              {t("staffMarking.browser.reset")}
            </Button>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-meta text-muted">{t("staffMarking.browser.count", { count: format.formatNumber(total) })}</p>
            {canPublishResult ? (
              <div className="flex flex-wrap items-center gap-2">
                {selectedExam ? (
                  // The release gate and its counts live on the results page; the browser only links there.
                  <Button variant="secondary" href={`/staff/exams/${selectedExam.examId}/results`}>
                    {t("staffResults.release.title")}
                  </Button>
                ) : null}
                <BulkPublishSelected ids={tickedNow} onDone={() => setTicked([])} />
              </div>
            ) : null}
          </div>

          {truncated ? <p className="text-meta text-warn">{t("staffMarking.browser.truncated")}</p> : null}

          {failed ? (
            <ErrorState onRetry={retry} />
          ) : (
            <Table
              columns={columns}
              rows={rows}
              rowKey={(row) => row.participantId}
              loading={loading}
              onRowClick={(row) => router.push(`/staff/submissions/${row.participantId}?m=${row.moduleId}`)}
              empty={emptyState}
            />
          )}

          {totalPages > 1 ? (
            <Pagination page={params.page} totalPages={totalPages} onPage={(page) => setParams({ page })} />
          ) : null}

          <p className="text-meta text-muted">{t("staffMarking.browser.openNote")}</p>
        </div>
      </SplitBrowser>
    </div>
  );
}

/** The label of one tree node, found anywhere in the tree. */
function labelOf(nodes: TreeNode[], id: string | null): string | undefined {
  if (!id) {
    return undefined;
  }
  for (const node of nodes) {
    if (node.id === id) {
      return node.label;
    }
    const inside = labelOf(node.children ?? [], id);
    if (inside) {
      return inside;
    }
  }
  return undefined;
}
