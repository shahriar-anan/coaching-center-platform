"use client";

import { useEffect, useRef, useState } from "react";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Tabs } from "@/components/ui/tabs";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { QuestionPaperPanel } from "@/components/staff/marking/question-paper-panel";
import { useLocale } from "@/i18n/locale-context";
import { answerPaperBlob, answerPaperPath, evaluatedPaperBlob, evaluatedPaperPath } from "@/lib/api/exam/staff-marking";

type View = "answer" | "question" | "evaluated";

/**
 * The answer script, the question paper and the marked script, one at a time in tabs. The answer is the first
 * tab, so on phones it is the first thing under the header. The question paper loads only when its tab is opened.
 * The marked script is the file the evaluator uploaded; the tab turns to it when the first one appears.
 */
export function PaperViews({
  answerId,
  studentName,
  examId,
  examTitle,
  marked,
  markedAt,
}: {
  answerId: string;
  studentName: string;
  examId: string;
  examTitle: string;
  /** Whether a marked script is stored for this paper (the paper's `evaluatedPdf`). */
  marked: boolean;
  /** When the marked script was last saved. A new upload reloads the viewer. */
  markedAt: string | null;
}) {
  const { t } = useLocale();
  const [view, setView] = useState<View>("answer");
  // The first upload of a marked script turns the tab to it. A paper that already had one opens on the answer.
  const wasMarked = useRef(marked);
  useEffect(() => {
    if (marked && !wasMarked.current) {
      setView("evaluated");
    }
    wasMarked.current = marked;
  }, [marked]);

  // Each file is kept for the page session (cacheKey), so moving between the tabs does not download a large scan again.
  // The marked script's key carries markedAt, so a new upload is fetched fresh.
  const content =
    view === "answer" ? (
      <PdfViewer
        load={(options) => answerPaperBlob(answerId, options)}
        cacheKey={answerPaperPath(answerId)}
        download={(options) => answerPaperBlob(answerId, options, true)}
        fileKind="answer"
        allowDownload
        fileName={`${studentName}-${examTitle}.pdf`}
      />
    ) : view === "question" ? (
      <QuestionPaperPanel examId={examId} participantId={answerId} examTitle={examTitle} />
    ) : (
      <PdfViewer
        key={markedAt ?? "marked"}
        load={(options) => evaluatedPaperBlob(answerId, options)}
        cacheKey={`${evaluatedPaperPath(answerId)}#${markedAt ?? ""}`}
        download={(options) => evaluatedPaperBlob(answerId, options, true)}
        fileKind="marked"
        allowDownload
        fileName={`${studentName}-${examTitle}-marked.pdf`}
      />
    );

  return (
    <div className="grid gap-4">
      <Tabs
        ariaLabel={t("staffMarking.views.label")}
        tabs={[
          { value: "answer", label: t("staffMarking.views.answer") },
          { value: "question", label: t("staffMarking.questionPaper.title") },
          { value: "evaluated", label: t("staffMarking.views.evaluated") },
        ]}
        value={view}
        onChange={(value) => setView(value as View)}
      />
      {view === "evaluated" && !marked ? (
        <EmptyState title={t("staffMarking.views.notMarkedTitle")} body={t("staffMarking.views.notMarkedBody")} />
      ) : (
        <Card>{content}</Card>
      )}
    </div>
  );
}
