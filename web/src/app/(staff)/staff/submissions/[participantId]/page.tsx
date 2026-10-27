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
import { neighbour, nextToMark, paperStatus } from "@/components/staff/marking/marking-queue";
import { ALL_STAFF, RequireStaff, useRole } from "@/lib/auth/guards";
import { FEED_PAGE_MAX, useFeed, useMarkingPaper } from "@/lib/api/exam/staff-marking";
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
    <RequireStaff roles={ALL_STAFF}>
      <Paper participantId={participantId} />
    </RequireStaff>
  );
}

/**
 * One written paper. The paper's own read gives the titles, the marks and the comment.
 * `?m=` (the module) only sets the queue's order: previous, next, "সেভ করে পরেরটি", and
 * the keys J and K. Without it the paper stands alone.
 */
function Paper({ participantId }: { participantId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const search = useSearchParams();
  const moduleId = search?.get("m") ?? "";
  const { canEvaluate, canPublishResult } = useRole();

  const paper = useMarkingPaper(participantId);
  const queue = useFeed(
    { status: "", moduleId, examId: "", q: "", sort: "submittedAt,desc", page: 0, size: FEED_PAGE_MAX },
    moduleId.length > 0,
  );
  const rows = queue.data?.content ?? [];
  const current = paper.data;
  const previous = current && moduleId ? neighbour(rows, participantId, -1) : null;
  const next = current && moduleId ? neighbour(rows, participantId, 1) : null;
  const waiting = current && moduleId ? nextToMark(rows, participantId) : null;
  const toPaper = (id: string) => `/staff/submissions/${id}?m=${moduleId}`;
  const browser = moduleId ? `/staff/submissions?module=${moduleId}` : "/staff/submissions";

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
    // toPaper depends on moduleId only; the queue neighbours are the real inputs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [next, previous, router, moduleId]);

  if (paper.isError) {
    // A refused submission (another instructor's module) is not a network fault, so it gets its own message.
    const refused = paper.error instanceof ApiError && (paper.error.code === "ACCESS_DENIED" || paper.error.code === "RESOURCE_NOT_FOUND");
    return refused ? (
      <EmptyState title={t("staffMarking.submission.unavailable")} />
    ) : (
      <ErrorState onRetry={() => paper.refetch()} />
    );
  }
  if (paper.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (!current) {
    return <EmptyState title={t("common.notFound.title")} />;
  }

  const examTitle = format.pickTitle({ titleEn: current.examTitleEn, titleBn: current.examTitleBn });
  const moduleTitle = format.pickTitle({ titleEn: current.moduleTitleEn, titleBn: current.moduleTitleBn });
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
          ...(moduleId ? [{ label: moduleTitle, href: browser }] : []),
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
            {moduleId ? (
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

      {moduleId ? (
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
              answerId={current.id}
              studentName={current.studentName}
              examId={current.examId}
              examTitle={examTitle}
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
              if (!saveAndNext || !moduleId) {
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
