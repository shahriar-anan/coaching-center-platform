"use client";

import { Suspense, use, useEffect, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { SubmissionForm } from "@/components/staff/people/submission-form";
import { neighbour, nextToMark } from "@/components/staff/people/people-copy";
import { RequireStaff, useRole } from "@/lib/auth/guards";
import { useModuleSubmissions, useParticipation, useSubmission } from "@/lib/api/exam/staff-people";
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
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN", "INSTRUCTOR"]}>
      <Paper participantId={participantId} />
    </RequireStaff>
  );
}

/**
 * One written paper. The module's list (from `?m=`) gives the marking queue: previous
 * and next, "সেভ করে পরেরটি", and the keys J and K. Without a module, the paper stands alone.
 */
function Paper({ participantId }: { participantId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const search = useSearchParams();
  const moduleId = search?.get("m") ?? "";
  const { canEvaluate, canPublishResult } = useRole();

  const paper = useSubmission(participantId);
  const queue = useModuleSubmissions(moduleId);
  const exams = useParticipation({ page: 0, size: 100, moduleId, type: "WRITTEN", from: "", to: "", q: "", sort: "title" });

  const examTitle = useMemo(() => {
    const row = exams.data?.content.find((exam) => exam.examId === paper.data?.examId);
    return row ? format.pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }) : "";
  }, [exams.data, paper.data, format]);

  const rows = queue.data ?? [];
  const previous = paper.data ? neighbour(rows, participantId, -1) : null;
  const next = paper.data ? neighbour(rows, participantId, 1) : null;
  const toPaper = (id: string) => `/staff/submissions/${id}?m=${moduleId}`;

  // J and K move through the queue, unless focus is in a field.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null;
      if (target && (["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName) || target.isContentEditable)) {
        return;
      }
      if (event.key === "j" && next) {
        router.push(toPaper(next.id));
      } else if (event.key === "k" && previous) {
        router.push(toPaper(previous.id));
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // toPaper depends on moduleId only; the queue neighbours are the real inputs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [next, previous, router, moduleId]);

  if (paper.isError) {
    return <ErrorState onRetry={() => paper.refetch()} />;
  }
  if (paper.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (!paper.data) {
    return <EmptyState title={t("common.notFound.title")} />;
  }

  const answered = rows.filter((row) => row.answerPdf);
  const marked = answered.filter((row) => row.evaluatedPdf === true);

  return (
    <div className="grid gap-5">
      <PageHeader
        title={paper.data.studentName}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffPeople.submissions.title"), href: "/staff/submissions" },
          ...(moduleId ? [{ label: t("staffPeople.submissions.moduleCrumb"), href: `/staff/submissions/m/${moduleId}` }] : []),
          { label: examTitle || paper.data.studentName },
        ]}
        actions={
          moduleId ? (
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="secondary" disabled={!previous} href={previous ? toPaper(previous.id) : undefined}>
                {t("staffPeople.submission.previous")}
              </Button>
              <Button variant="secondary" disabled={!next} href={next ? toPaper(next.id) : undefined}>
                {t("staffPeople.submission.next")}
              </Button>
            </div>
          ) : null
        }
      />

      {moduleId ? (
        <p className="text-sm text-muted">
          {t("staffPeople.submission.progress", {
            total: format.formatNumber(answered.length),
            marked: format.formatNumber(marked.length),
          })}
        </p>
      ) : null}

      <SubmissionForm
        row={paper.data}
        examTitle={examTitle}
        canMark={canEvaluate}
        canPublish={canPublishResult}
        onSaved={({ saveAndNext }) => {
          if (!saveAndNext || !moduleId) {
            return;
          }
          const target = nextToMark(rows, participantId);
          router.push(target ? toPaper(target.id) : `/staff/submissions/m/${moduleId}`);
        }}
      />
    </div>
  );
}
