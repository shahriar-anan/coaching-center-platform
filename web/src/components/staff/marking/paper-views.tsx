"use client";

import { useState } from "react";
import { Card } from "@/components/ui/card";
import { Tabs } from "@/components/ui/tabs";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { QuestionPaperPanel } from "@/components/staff/marking/question-paper-panel";
import { useLocale } from "@/i18n/locale-context";
import { answerPaperBlob } from "@/lib/api/exam/staff-marking";

type View = "answer" | "question";

/**
 * The answer script and the question paper, one at a time in tabs. The answer is the first
 * tab, so on phones it is the first thing under the header. The question paper loads only
 * when its tab is opened.
 */
export function PaperViews({
  answerId,
  studentName,
  examId,
  examTitle,
}: {
  answerId: string;
  studentName: string;
  examId: string;
  examTitle: string;
}) {
  const { t } = useLocale();
  const [view, setView] = useState<View>("answer");

  return (
    <div className="grid gap-4">
      <Tabs
        ariaLabel={t("staffMarking.views.label")}
        tabs={[
          { value: "answer", label: t("staffMarking.views.answer") },
          { value: "question", label: t("staffMarking.questionPaper.title") },
        ]}
        value={view}
        onChange={(value) => setView(value as View)}
      />
      <Card>
        {view === "answer" ? (
          <PdfViewer
            load={() => answerPaperBlob(answerId)}
            allowDownload
            fileName={`${studentName}-${examTitle}.pdf`}
            watermark={t("staffMarking.form.watermark")}
          />
        ) : (
          <QuestionPaperPanel examId={examId} examTitle={examTitle} />
        )}
      </Card>
    </div>
  );
}
