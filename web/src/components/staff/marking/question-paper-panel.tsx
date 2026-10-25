"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { questionPaperBlob } from "@/lib/api/exam/staff-marking";

/** The codes the server answers for a question paper the viewer may not open. */
const NOT_ALLOWED = ["RESOURCE_NOT_FOUND", "ACCESS_DENIED"];

/**
 * The question paper, beside the answer while marking. Collapsed by default and loaded
 * only when opened. An instructor not assigned to the module gets a plain line, not an error.
 */
export function QuestionPaperPanel({ examId, examTitle }: { examId: string; examTitle: string }) {
  const { t } = useLocale();
  const [open, setOpen] = useState(false);
  const paper = useQuery({
    queryKey: ["staffMarking", "questionPaper", examId],
    queryFn: () => questionPaperBlob(examId),
    enabled: open,
    retry: false,
    staleTime: Infinity,
  });
  const blob = paper.data;

  return (
    <section className="grid gap-3 rounded-card border border-border bg-card p-4">
      <button
        type="button"
        aria-expanded={open}
        aria-controls="question-paper-body"
        onClick={() => setOpen((value) => !value)}
        className="flex min-h-10 items-center justify-between gap-3 rounded-control text-start text-label font-semibold text-text outline-none focus-visible:outline-accent"
      >
        {t("staffMarking.questionPaper.title")}
        <span aria-hidden="true" className="text-section text-muted">
          {open ? "−" : "+"}
        </span>
      </button>

      {open ? (
        <div id="question-paper-body" className="grid gap-3">
          {paper.isPending ? (
            <Skeleton variant="card" />
          ) : paper.isError ? (
            isNotAllowed(paper.error) ? (
              <p className="text-body text-muted">{t("staffMarking.questionPaper.notAllowed")}</p>
            ) : (
              <ErrorState onRetry={() => paper.refetch()} />
            )
          ) : blob ? (
            <PdfViewer
              load={() => Promise.resolve(blob)}
              fileName={`${examTitle}-question-paper.pdf`}
              watermark={t("staffMarking.questionPaper.watermark")}
            />
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function isNotAllowed(error: unknown): boolean {
  return error instanceof ApiError && NOT_ALLOWED.includes(error.code);
}
