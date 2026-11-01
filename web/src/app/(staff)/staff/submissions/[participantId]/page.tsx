"use client";

import Link from "next/link";
import { Suspense, use, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { ApiError } from "@/lib/api/errors";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { ShortcutsPopover } from "@/components/ui/shortcuts-popover";
import { Tabs } from "@/components/ui/tabs";
import { PaperViews } from "@/components/staff/marking/paper-views";
import { SubmissionForm } from "@/components/staff/marking/submission-form";
import {
  failureKind,
  FEED_FILTERS,
  FEED_SORTS,
  idParam,
  neighbour,
  nextToMark,
  paperStatus,
  uuidParam,
  withQuery,
} from "@/components/staff/marking/marking-queue";
import { RequireStaff, useRole } from "@/lib/auth/guards";
import { FEED_PAGE_MAX, useFeed, useMarkingPaper, type FeedSort } from "@/lib/api/exam/staff-marking";
import { useFormat } from "@/lib/format/use-format";
import { useLocale } from "@/i18n/locale-context";

export default function StaffSubmissionPage({ params }: { params: Promise<{ participantId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <PaperRoute params={params} />
    </Suspense>
  );
}

function PaperRoute({ params }: { params: Promise<{ participantId: string }> }) {
  const { participantId } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "INSTRUCTOR"]}>
      <Paper participantId={participantId} />
    </RequireStaff>
  );
}

/**
 * One written paper. The paper's own read gives the titles, the marks and the comment. The queue is the
 * papers of `?exam=` (every status, the browser's sort): previous, next, "সেভ করে পরেরটি", and the keys J and K.
 * An old `?m=` package link still sets the queue, but only when it is a real id. The other query values are the
 * browser's view, kept so "back to list" returns to it.
 */
function Paper({ participantId }: { participantId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const search = useSearchParams();
  const { canEvaluate, canPublishResult } = useRole();

  const examId = idParam(search?.get("exam"));
  const legacyModule = uuidParam(search?.get("m"));
  const cat = idParam(search?.get("cat"));
  const statusParam = search?.get("status") ?? "";
  const status = (FEED_FILTERS as readonly string[]).includes(statusParam) ? statusParam : "";
  const sortParam = search?.get("sort") ?? "";
  const sort: FeedSort = (FEED_SORTS as string[]).includes(sortParam) ? (sortParam as FeedSort) : "submittedAt,desc";
  const q = search?.get("q") ?? "";
  const queueOn = examId !== "" || legacyModule !== "";

  const paper = useMarkingPaper(participantId);
  const queue = useFeed(
    { categoryId: "", status: "", moduleId: legacyModule, examId, q: "", sort, page: 0, size: FEED_PAGE_MAX },
    queueOn,
  );
  const rows = queue.data?.content ?? [];
  const current = paper.data;
  const previous = current && queueOn ? neighbour(rows, participantId, -1) : null;
  const next = current && queueOn ? neighbour(rows, participantId, 1) : null;
  const waiting = current && queueOn ? nextToMark(rows, participantId) : null;

  // Every paper link keeps the browser's view. The back link goes to the category when the list was one, else to the exam.
  const sortValue = sort === "submittedAt,desc" ? "" : sort;
  const toPaper = (id: string) =>
    withQuery(`/staff/submissions/${id}`, { exam: examId, m: legacyModule, cat, status, sort: sortValue, q });
  const browser = withQuery("/staff/submissions", {
    cat,
    exam: cat ? "" : examId,
    module: examId ? "" : legacyModule,
    status,
    sort: sortValue,
    q,
  });

  // On a phone the script and the mark are two tabs. The script comes first: it is what the marker reads.
  const [phoneTab, setPhoneTab] = useState<"script" | "mark">("script");

  // J and K move through the queue, unless focus is in a field.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      if (target && (["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName) || target.isContentEditable)) {
        return;
      }
      if (event.key === "j" && next) {
        router.push(toPaper(next.participantId));
      } else if (event.key === "k" && previous) {
        router.push(toPaper(previous.participantId));
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // toPaper reads only the URL values, which the search params already cover; the queue neighbours are the real inputs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [next, previous, router, search]);

  if (paper.isError) {
    // A refused submission (another instructor's module) is not a network fault, so it gets its own message.
    const refused = paper.error instanceof ApiError && (paper.error.code === "ACCESS_DENIED" || paper.error.code === "RESOURCE_NOT_FOUND");
    const failure = failureKind(paper.error);
    return refused ? (
      <EmptyState title={t("staffMarking.submission.unavailable")} />
    ) : (
      <ErrorState onRetry={() => paper.refetch()} message={failure ? t(`staffMarking.errors.${failure}`) : undefined} error={paper.error} />
    );
  }
  if (paper.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (!current) {
    return <EmptyState title={t("common.notFound.title")} />;
  }

  const examTitle = format.pickTitle({ titleEn: current.examTitleEn, titleBn: current.examTitleBn });
  const moduleTitle = format.pickTitle({ titleEn: current.moduleTitleEn ?? "", titleBn: current.moduleTitleBn ?? "" });
  const answered = rows.filter((row) => row.answerPdf);
  const marked = answered.filter((row) => row.status === "DRAFT" || row.status === "PUBLISHED");
  const hasAnswer = current.answerPdf && current.status !== "FAILED_TO_SUBMIT";

  return (
    <div className="grid gap-5">
      <PageHeader
        title={current.studentName}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffMarking.browser.title"), href: "/staff/submissions" },
          ...(legacyModule ? [{ label: moduleTitle, href: browser }] : []),
          { label: examTitle },
        ]}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <ShortcutsPopover
              showLabel
              shortcuts={[
                { keys: ["J"], label: t("staffMarking.shortcuts.next") },
                { keys: ["K"], label: t("staffMarking.shortcuts.previous") },
              ]}
            />
            {queueOn ? (
              <>
                <Button variant="secondary" disabled={!previous} href={previous ? toPaper(previous.participantId) : undefined}>
                  {t("staffMarking.form.previous")}
                </Button>
                <Button variant="secondary" disabled={!next} href={next ? toPaper(next.participantId) : undefined}>
                  {t("staffMarking.form.next")}
                </Button>
              </>
            ) : null}
          </div>
        }
      />

      {queueOn ? (
        <p className="text-meta text-muted">
          {t("staffMarking.form.progress", {
            total: format.formatNumber(answered.length),
            marked: format.formatNumber(marked.length),
          })}
          {paperStatus(current) !== "AWAITING" && !waiting && !queue.isPending ? (
            <>
              {" "}
              <Link href={browser} className="font-medium text-action hover:underline">
                {t("staffMarking.form.allDone")}
              </Link>
            </>
          ) : null}
        </p>
      ) : null}

      {canEvaluate && hasAnswer ? (
        <div className="grid gap-2 lg:hidden">
          <Tabs
            ariaLabel={t("staffMarking.phone.label")}
            tabs={[
              { value: "script", label: t("staffMarking.phone.script") },
              { value: "mark", label: t("staffMarking.phone.mark") },
            ]}
            value={phoneTab}
            onChange={(value) => setPhoneTab(value === "mark" ? "mark" : "script")}
          />
          {phoneTab === "script" && current.score !== null ? (
            <p className="text-meta text-muted">
              {t("staffMarking.phone.current", {
                mark: format.formatNumber(current.score),
                max: format.formatNumber(current.maximum),
              })}
            </p>
          ) : null}
        </div>
      ) : null}

      {/* The script takes the wide column and stays in view while the grading panel scrolls beside it. */}
      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(22rem,26rem)]">
        {canEvaluate && hasAnswer ? (
          <div className={phoneTab === "script" ? "lg:sticky lg:top-20" : "hidden lg:sticky lg:top-20 lg:block"}>
            <PaperViews
              key={current.id}
              answerId={current.id}
              studentName={current.studentName}
              examId={current.examId}
              examTitle={examTitle}
              marked={current.evaluatedPdf === true}
              markedAt={current.evaluatedAt}
            />
          </div>
        ) : null}

        <div className={canEvaluate && hasAnswer && phoneTab === "script" ? "hidden lg:block" : undefined}>
          <SubmissionForm
            key={current.id}
            paper={current}
            canMark={canEvaluate}
            canPublish={canPublishResult}
            onSaved={({ saveAndNext }) => {
              if (!saveAndNext || !queueOn) {
                return;
              }
              const target = nextToMark(rows, participantId);
              router.push(target ? toPaper(target.participantId) : browser);
            }}
          />
        </div>
      </div>
    </div>
  );
}
