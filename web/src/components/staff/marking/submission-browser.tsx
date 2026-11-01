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
  failureKind,
  FEED_FILTERS,
  FEED_SORTS,
  feedStatus,
  selectedNode,
  selectionFor,
  selectionOf,
  statusTone,
  statusWordKey,
  uuidParam,
  withQuery,
} from "@/components/staff/marking/marking-queue";
import { useRole } from "@/lib/auth/guards";
import { useParticipationRows } from "@/lib/api/exam/staff-home";
import { FEED_PAGE_MAX, useFeed, type FeedRow, type FeedSort } from "@/lib/api/exam/staff-marking";
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
 * `/staff/submissions`: the written papers in a tree of All scripts → category → exam on the left, and the submission
 * table beside it. The selection, filter, sort and search live in the URL. Instructors start on the papers still waiting;
 * Master Admin and System Admin start on all.
 */
/** The submitted answer sheets: an exam tree beside the submission table. `embedded` hides its own header (the admin dashboard tab). */
export function SubmissionBrowser({ embedded = false }: { embedded?: boolean } = {}) {
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
  const written = useParticipationRows({ type: "WRITTEN", sort: "title", size: FEED_PAGE_MAX });
  const exams = useMemo(() => written.data?.content ?? [], [written.data]);
  const index = useMemo(
    () =>
      buildBrowserTree(exams, format.pickTitle, t("staffMarking.browser.allPapers"), t("staffMarking.browser.uncategorised")),
    [exams, format.pickTitle, t],
  );

  const sort: FeedSort = FEED_SORTS.includes(params.sort as FeedSort) ? (params.sort as FeedSort) : "submittedAt,desc";
  const selection = selectionOf(params.cat, params.exam);
  // An old package link still filters by its package. A value that is not an id is ignored, never sent.
  const packageId = uuidParam(params.module);

  // One feed call for every scope. The server takes a category's sub-categories too, so a category pages like any other view.
  const feed = useFeed({
    categoryId: selection.cat,
    examId: selection.exam,
    moduleId: packageId,
    status: feedStatus(params.status),
    q: params.q,
    sort,
    ready: params.status === "READY",
    page: params.page,
    size: PAGE_SIZE,
  });
  const rows: FeedRow[] = feed.data?.content ?? [];
  const totalPages = feed.data?.totalPages ?? 0;
  const total = feed.data?.totalElements ?? 0;
  const failed = feed.isError;
  const failure = failureKind(feed.error);
  // The exam list is one page; the API caps it at its size. Past that, the tree and the tab counts leave exams out.
  const examCap = written.data?.size ?? 0;
  const truncated = examCap > 0 && (written.data?.totalElements ?? 0) > examCap;

  const [ticked, setTicked] = useState<string[]>([]);
  const drafts = rows.filter((row) => row.status === "DRAFT");
  const tickedNow = ticked.filter((id) => drafts.some((row) => row.participantId === id));
  const allTicked = drafts.length > 0 && tickedNow.length === drafts.length;

  const selectedId = selectedNode(selection);
  const headerTitle = labelOf(index.tree, selectedId) ?? t("staffMarking.browser.title");
  const selectedExam = selection.exam ? exams.find((row) => row.examId === selection.exam) : undefined;
  const packageRow = packageId ? exams.find((row) => row.moduleId === packageId) : undefined;
  const packageName = format.pickTitle({
    titleEn: packageRow?.moduleTitleEn ?? "",
    titleBn: packageRow?.moduleTitleBn ?? "",
  });

  // The exams inside the selection (an exam, a category, or everything). The tab counts read these.
  const scopeExams = useMemo(() => {
    if (selection.exam) {
      return exams.filter((row) => row.examId === selection.exam);
    }
    if (packageId) {
      return exams.filter((row) => row.moduleId === packageId);
    }
    if (selection.cat) {
      const inside = new Set(index.categoryExams.get(selection.cat) ?? []);
      return exams.filter((row) => inside.has(row.examId));
    }
    return exams;
  }, [exams, index, packageId, selection.cat, selection.exam]);

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

  /** The paper's address. It keeps this view (category, status, sort, search) so "back to list" returns to it. */
  function paperLink(row: FeedRow): string {
    return withQuery(`/staff/submissions/${row.participantId}`, {
      exam: row.examId,
      cat: selection.cat,
      status: params.status === defaultStatus ? "" : params.status,
      sort: sort === "submittedAt,desc" ? "" : sort,
      q: params.q,
    });
  }

  function choose(nodeId: string) {
    const next = selectionFor(nodeId);
    if (next) {
      setTicked([]);
      setParams({ ...next, module: "", page: 0 });
    }
  }

  function toggle(id: string) {
    setTicked((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
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
          href={paperLink(row)}
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
      cell: (row) =>
        row.moduleId ? format.pickTitle({ titleEn: row.moduleTitleEn ?? "", titleBn: row.moduleTitleBn ?? "" }) : DASH,
    },
    ...(canPublishResult
      ? [
          {
            key: "teacher",
            header: t("staffMarking.browser.teacher"),
            cell: (row: FeedRow) => row.instructorName ?? DASH,
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
    written.isSuccess && exams.length === 0 ? (
      <EmptyState title={t("staffMarking.browser.noExams")} body={t("staffMarking.browser.noExamsBody")} />
    ) : (
      <EmptyState title={t("staffMarking.browser.noRows")} body={t("staffMarking.browser.noRowsBody")} />
    );

  return (
    <div className="grid gap-5">
      {embedded ? null : (
        <PageHeader
          title={headerTitle}
          breadcrumbs={[
            { label: t("common.staffNav.dashboard"), href: "/staff" },
            { label: t("staffMarking.browser.title") },
          ]}
        />
      )}

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

          {truncated ? (
            <p className="text-meta text-warn">{t("staffMarking.browser.truncated", { max: format.formatNumber(examCap) })}</p>
          ) : null}

          {packageId ? (
            <p className="flex flex-wrap items-center gap-2 text-meta text-muted">
              {t("staffMarking.browser.packageFilter", { name: packageName || DASH })}
              <Button variant="ghost" onClick={() => setParams({ module: "", page: 0 })}>
                {t("staffMarking.browser.removePackage")}
              </Button>
            </p>
          ) : null}

          {failed ? (
            <ErrorState
              onRetry={() => void feed.refetch()}
              message={failure ? t(`staffMarking.errors.${failure}`) : undefined}
              error={feed.error}
            />
          ) : (
            <Table
              columns={columns}
              rows={rows}
              rowKey={(row) => row.participantId}
              loading={feed.isPending}
              onRowClick={(row) => router.push(paperLink(row))}
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
