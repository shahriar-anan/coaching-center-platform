"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError } from "@/lib/api/errors";
import { useUser } from "@/lib/auth/session-context";
import {
  useRetakeExam,
  useStudentExam,
  useTakeExam,
  type SittingBlock,
} from "@/lib/api/exam/sitting";
import { useLocale } from "@/i18n/locale-context";
import { McqPaper } from "./mcq-paper";
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
        setError(caught instanceof ApiError && caught.code === "RESOURCE_NOT_FOUND" ? "notFound" : "failed");
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
    if (exam.error instanceof ApiError && exam.error.code === "RESOURCE_NOT_FOUND") {
      // No roster row or paid grant for this exam: say so, and offer the way back to the exam page.
      return (
        <div role="status" className="rounded-card border border-border bg-card p-6 text-center">
          <p className="text-text">{t("sitting.entry.noAccess")}</p>
          <Link href={`/exam/${examId}`} className="mt-4 inline-flex min-h-11 items-center text-accent underline">
            {t("sitting.entry.backToExam")}
          </Link>
        </div>
      );
    }
    return (
      <div role="alert" className="rounded-card border border-border bg-card p-6 text-center">
        <p className="text-text">{t("sitting.entry.failed")}</p>
        <button
          type="button"
          onClick={() => exam.refetch()}
          className="mt-4 min-h-11 rounded-control border border-border px-4 text-text"
        >
          {t("common.retry")}
        </button>
      </div>
    );
  }

  const data = exam.data;
  if (!data || !user) {
    return null;
  }

  if (data.examType === "WRITTEN") {
    if (!sitting) {
      if (!start) {
        router.replace(`/exam/${examId}`);
      }
      return null;
    }
    if (data.answerStored) {
      // A script is submitted once and cannot be changed, so there is nothing left to do on this page.
      router.replace(`/exam/${examId}/submitted`);
      return null;
    }
    const watermark = user.studentCode ? `${user.fullName} · ${user.studentCode}` : user.fullName;
    return (
      <WrittenSitting
        exam={data}
        deadlineAt={sitting.deadlineAt}
        watermark={watermark}
        onSubmitted={() => router.replace(`/exam/${examId}/submitted`)}
        onClosed={(hasScript) => router.replace(`/exam/${examId}/result?notice=${hasScript ? "timeUpScript" : "timeUpNoScript"}`)}
      />
    );
  }

  const open = Boolean(data.questions) && sitting !== null;
  if (!open || !sitting || !data.questions) {
    // Without an open sitting, the exam page owns the decision (offer, closed, or result).
    if (!start && !open) {
      router.replace(`/exam/${examId}`);
    }
    return null;
  }

  return (
    <McqPaper
      exam={data}
      studentId={user.id}
      startedAt={sitting.startedAt}
      deadlineAt={sitting.deadlineAt}
      kind={sitting.kind}
      onClosed={() => router.replace(`/exam/${examId}/result?notice=timeUp`)}
      onSubmitted={() => router.replace(`/exam/${examId}?notice=submitted`)}
    />
  );
}
