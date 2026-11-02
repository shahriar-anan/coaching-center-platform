"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { ApiError, isUnknownItemError } from "@/lib/api/errors";
import { studentHome } from "@/lib/config";
import { useUser } from "@/lib/auth/session-context";
import {
  invalidateSittingReads,
  useRetakeExam,
  useStudentExam,
  useTakeExam,
  type SittingBlock,
} from "@/lib/api/exam/sitting";
import { useLocale } from "@/i18n/locale-context";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { McqPaper } from "./mcq-paper";
import { PaperLayer } from "./paper-layer";
import { WrittenSitting } from "./written-sitting";

export type Start = "live" | "retake" | null;

/** Only one sitting exists per student per exam; the take call is idempotent for an open one. */
export function SittingEntry({ examId, start }: { examId: string; start: Start }) {
  const router = useRouter();
  const { t } = useLocale();
  const exam = useStudentExam(examId);
  const user = useUser();
  const take = useTakeExam(examId);
  const retake = useRetakeExam(examId);
  const [error, setError] = useState<string | null>(null);
  const [phoneMissing, setPhoneMissing] = useState(false);
  const started = useRef(false);
  // Right after a take, before the exam read refetches, the take result stands in for `sitting`.
  // After that the server's `sitting` is the source, so a reload or a second device sees the same deadline.
  const [taken, setTaken] = useState<SittingBlock | null>(null);
  const sitting = taken ?? exam.data?.sitting ?? null;
  const queryClient = useQueryClient();
  // A submitted paper is final: its questions do not come back here, so the student goes to the result, where the score is.
  const submittedMcq = exam.data?.examType === "MCQ" && Boolean(sitting?.submittedAt);
  // Set when the paper hands the student on (submit or time-up), so this redirect never replaces that navigation.
  const leaving = useRef(false);
  useEffect(() => {
    if (submittedMcq && !leaving.current) {
      router.replace(`/exam/${examId}/result`);
    }
  }, [submittedMcq, examId, router]);

  // The other redirects of this page. They run after render, never during it (a navigation during render is refused by React).
  const written = exam.data?.examType === "WRITTEN";
  const answerStored = Boolean(exam.data?.answerStored);
  const hasOpenSitting = Boolean(exam.data?.questions) && (taken ?? exam.data?.sitting ?? null) !== null;
  useEffect(() => {
    if (!exam.data || !user) {
      return;
    }
    if (written) {
      if (!sitting && !start) {
        router.replace(`/exam/${examId}`);
      } else if (sitting && answerStored) {
        // A script is submitted once and cannot be changed, so there is nothing left to do on this page.
        router.replace(`/exam/${examId}/submitted`);
      }
      return;
    }
    // Without an open sitting, the exam page owns the decision (offer, closed, or result). A submitted MCQ is sent on above.
    if (!hasOpenSitting && !start && !submittedMcq) {
      router.replace(`/exam/${examId}`);
    }
  }, [exam.data, user, sitting, start, submittedMcq, examId, router, written, answerStored, hasOpenSitting]);

  // `?start=live|retake`: take the exam, then replace the URL so a reload does not take it again.
  useEffect(() => {
    if (!start || started.current) {
      return;
    }
    started.current = true;
    const run = start === "retake" ? retake.mutateAsync : take.mutateAsync;
    run()
      .then((result) => {
        setTaken({ kind: result.kind, startedAt: result.startedAt, deadlineAt: result.deadlineAt, submittedAt: null });
        router.replace(`/exam/${examId}/sit`);
      })
      .catch((caught: unknown) => {
        if (caught instanceof ApiError && caught.code === "PHONE_REQUIRED") {
          setPhoneMissing(true);
          return;
        }
        if (caught instanceof ApiError && (caught.code === "SITTING_NOT_OPEN" || caught.code === "RETAKE_NOT_ALLOWED")) {
          router.replace(`/exam/${examId}`);
          return;
        }
        setError(isUnknownItemError(caught) ? "notFound" : "failed");
      });
    // The mutations are stable for this mount; the effect must run once per take.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [start, examId]);

  if (phoneMissing) {
    return (
      <div className="rounded-card border border-border bg-card p-6 text-center">
        <p className="text-text">{t("sitting.entry.phoneRequired")}</p>
        <Link
          href="/account"
          className="mt-4 inline-flex min-h-11 items-center rounded-control bg-accent px-5 font-semibold text-on-accent"
        >
          {t("sitting.entry.addPhone")}
        </Link>
      </div>
    );
  }

  if (error) {
    return (
      <div role="alert" className="rounded-card border border-border bg-card p-6 text-center">
        <p className="text-text">
          {error === "notFound" ? t("sitting.entry.notFound") : t("sitting.entry.failed")}
        </p>
        <Link href={`/exam/${examId}`} className="mt-4 inline-block min-h-11 text-accent underline">
          {t("sitting.entry.backToExam")}
        </Link>
      </div>
    );
  }

  if (exam.isPending || (start && !sitting)) {
    return <p role="status" className="p-8 text-center text-muted">{t("sitting.entry.opening")}</p>;
  }

  if (exam.isError) {
    // The API answers 404 for a missing exam, and for one with no roster row or paid grant; both read as not found here.
    if (isUnknownItemError(exam.error)) {
      return (
        <NotFoundState title={t("common.notFound.exam")} body={t("common.notFound.itemBody")} backHref={studentHome} backLabel={t("common.guard.goHome")} />
      );
    }
    return <ErrorState error={exam.error} onRetry={() => void exam.refetch()} />;
  }

  const data = exam.data;
  if (!data || !user) {
    return null;
  }

  if (data.examType === "WRITTEN") {
    // Nothing renders here; the effect above sends the student on.
    if (!sitting || data.answerStored) {
      return null;
    }
    const studentLine = user.studentCode ? `${user.fullName} · ${user.studentCode}` : user.fullName;
    return (
      <WrittenSitting
        exam={data}
        deadlineAt={sitting.deadlineAt}
        studentLine={studentLine}
        onSubmitted={() => router.replace(`/exam/${examId}/submitted`)}
        onClosed={(hasScript) => {
          void invalidateSittingReads(queryClient, examId);
          router.replace(`/exam/${examId}/result?notice=${hasScript ? "timeUpScript" : "timeUpNoScript"}`);
        }}
      />
    );
  }

  // A submitted MCQ never shows the paper again. The effect above sends the student to the result.
  if (submittedMcq) {
    return null;
  }

  const open = Boolean(data.questions) && sitting !== null;
  if (!open || !sitting || !data.questions) {
    // Without an open sitting, the exam page owns the decision; the effect above sends the student there.
    return null;
  }

  return (
    <PaperLayer>
      <McqPaper
        exam={data}
        studentId={user.id}
        studentLine={user.studentCode ? `${user.fullName} · ${user.studentCode}` : user.fullName}
        startedAt={sitting.startedAt}
        deadlineAt={sitting.deadlineAt}
        kind={sitting.kind}
        onClosed={() => {
          leaving.current = true;
          void invalidateSittingReads(queryClient, examId);
          router.replace(`/exam/${examId}/result?notice=timeUp`);
        }}
        onSubmitted={() => {
          leaving.current = true;
          router.replace(`/exam/${examId}/result?notice=submitted`);
        }}
      />
    </PaperLayer>
  );
}
