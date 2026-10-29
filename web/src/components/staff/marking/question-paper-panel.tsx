"use client";

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
 * The question paper, in its own tab while marking. It is requested only when the tab
 * is shown. An instructor not assigned to the module gets a plain line, not an error.
 */
export function QuestionPaperPanel({ examId, examTitle }: { examId: string; examTitle: string }) {
  const { t } = useLocale();
  const paper = useQuery({
    queryKey: ["staffMarking", "questionPaper", examId],
    queryFn: () => questionPaperBlob(examId),
    retry: false,
    staleTime: Infinity,
  });
  const blob = paper.data;

  if (paper.isPending) {
    return <Skeleton variant="card" />;
  }
  if (paper.isError) {
    return isNotAllowed(paper.error) ? (
      <p className="text-body text-muted">{t("staffMarking.questionPaper.notAllowed")}</p>
    ) : (
      <ErrorState onRetry={() => paper.refetch()} />
    );
  }
  if (!blob) {
    return null;
  }
  return (
    <PdfViewer
      load={() => Promise.resolve(blob)}
      fileName={`${examTitle}-question-paper.pdf`}
    />
  );
}

function isNotAllowed(error: unknown): boolean {
  return error instanceof ApiError && NOT_ALLOWED.includes(error.code);
}
