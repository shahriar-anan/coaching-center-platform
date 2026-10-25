"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useServerNow } from "@/lib/time/clock";
import { useModule, useDeleteExam, useExam, useExamAction, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { apiBlob } from "@/lib/api/client";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { ExamChecklist, type ChecklistItem } from "@/components/staff/catalog/exam-checklist";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import { McqQuestions } from "@/components/staff/catalog/mcq-questions";
import { WrittenPaper } from "@/components/staff/catalog/written-paper";

/** What publish waits for, each item linked to the place that fixes it. */
export function examChecklist(exam: StaffExam, t: (key: string) => string): ChecklistItem[] {
  const items: ChecklistItem[] = [
    { key: "finalSave", label: t("staffCatalog.checklist.examFinalSave"), done: exam.finallySaved, anchor: "exam-final-save" },
    { key: "passPercent", label: t("staffCatalog.checklist.passPercent"), done: exam.passPercent !== null, anchor: "exam-meta" },
  ];
  if (exam.examType === "MCQ") {
    items.push({
      key: "questions",
      label: t("staffCatalog.checklist.questions"),
      done: (exam.questions?.length ?? 0) > 0,
      anchor: "exam-questions",
    });
  } else {
    items.push({
      key: "questionPdf",
      label: t("staffCatalog.checklist.questionPdf"),
      done: exam.questionPdf === true,
      anchor: "exam-question-pdf",
    });
  }
  return items;
}

/** A read-only look at the paper as a student sees it: no correct marks, no explanations. */
function StudentPreview({ exam }: { exam: StaffExam }) {
  const { t, locale } = useLocale();
  const { formatNumber } = useFormat();
  if (exam.examType === "WRITTEN") {
    return (
      <PdfViewer
        load={() => apiBlob(`/api/v1/live-exams/${exam.id}/question-pdf`)}
        watermark={t("staffCatalog.preview.watermark")}
        allowDownload={false}
        fileName={locale === "bn" ? exam.titleBn : exam.titleEn}
      />
    );
  }
  const questions = [...(exam.questions ?? [])].sort((a, b) => a.position - b.position);
  return (
    <div lang={exam.paperLanguage === "BN" ? "bn" : "en"} className="grid gap-5">
      {questions.map((q, index) => (
        <div key={q.id} className="grid gap-2">
          <p className="font-medium">
            {formatNumber(index + 1)}. {q.stem}
          </p>
          <ul className="grid gap-1 ps-6">
            {[...q.options]
              .sort((a, b) => a.position - b.position)
              .map((o) => (
                <li key={o.id} className="list-[lower-alpha]">
                  {o.text}
                </li>
              ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

function ExamEditorBody({ exam }: { exam: StaffExam }) {
  const { t, locale } = useLocale();
  const { pickTitle } = useFormat();
  const role = useRole();
  const router = useRouter();
  const { toast } = useToast();
  const now = useServerNow(1000);
  const moduleQuery = useModule(exam.moduleId);
  const finalSave = useExamAction(exam.id, "final-save");
  const publish = useExamAction(exam.id, "publish");
  const remove = useDeleteExam(exam.id, exam.moduleId);
  const [formError, setFormError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [previewing, setPreviewing] = useState(false);

  // After the start instant the paper, the file and the schedule are locked.
  const locked = exam.liveAt !== null && new Date(exam.liveAt).getTime() <= now;
  const items = examChecklist(exam, (key) => t(key));
  const publishable = items.every((item) => item.done) && exam.status !== "PUBLISHED";
  const title = pickTitle(exam);
  const moduleTitle = moduleQuery.data ? pickTitle(moduleQuery.data) : null;
  const canEdit = role.canAuthor && !locked;

  async function run(action: "final-save" | "publish") {
    setFormError(null);
    try {
      if (action === "final-save") {
        await finalSave.mutateAsync();
      } else {
        await publish.mutateAsync();
      }
      toast({
        title: action === "final-save" ? t("staffCatalog.editor.finalSaved") : t("staffCatalog.editor.published"),
        tone: "ok",
      });
    } catch (e) {
      setFormError(t(catalogErrorKey(e)));
    }
  }

  return (
    <>
      <PageHeader
        title={title}
        breadcrumbs={[
          { label: t("staffCatalog.nav.modules"), href: "/staff/modules" },
          ...(moduleTitle ? [{ label: moduleTitle, href: `/staff/modules/${exam.moduleId}?tab=exams` }] : []),
          { label: title },
        ]}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <StatusPill status={exam.status} />
            <Button variant="secondary" onClick={() => setPreviewing(true)}>
              {t("staffCatalog.preview.open")}
            </Button>
            {canEdit ? (
              <span id="exam-final-save">
                <Button
                  variant="secondary"
                  loading={finalSave.isPending}
                  disabled={finalSave.isPending || exam.finallySaved || exam.status === "PUBLISHED"}
                  onClick={() => run("final-save")}
                >
                  {t("staffCatalog.editor.finalSave")}
                </Button>
              </span>
            ) : null}
            {canEdit ? (
              <Button loading={publish.isPending} disabled={!publishable || publish.isPending} onClick={() => run("publish")}>
                {t("staffCatalog.editor.publish")}
              </Button>
            ) : null}
          </div>
        }
      />

      {locked ? (
        <p role="status" className="mb-6 rounded-control border border-warn/40 bg-warn/10 px-4 py-3 text-sm text-warn">
          {t("staffCatalog.editor.lockedAfterStart")}
        </p>
      ) : null}

      {formError ? (
        <p role="alert" className="mb-6 text-sm text-danger">
          {formError}
        </p>
      ) : null}

      <div className="grid gap-10">
        {canEdit ? (
          <section aria-labelledby="exam-checklist-heading" className="grid max-w-3xl gap-3 rounded-card border border-border bg-card p-5">
            <h2 id="exam-checklist-heading" className="text-base font-semibold">
              {t("staffCatalog.checklist.heading")}
            </h2>
            <ExamChecklist items={items} />
          </section>
        ) : null}

        <section aria-labelledby="exam-meta-heading" className="grid gap-4">
          <h2 id="exam-meta-heading" className="text-base font-semibold">
            {t("staffCatalog.examMeta.heading")}
          </h2>
          <ExamMetaForm exam={exam} locked={!canEdit} />
        </section>

        {exam.examType === "MCQ" ? (
          <McqQuestions exam={exam} locked={!canEdit} canDelete={role.canDelete} />
        ) : (
          <WrittenPaper exam={exam} locked={!canEdit} />
        )}

        {role.canDelete ? (
          <section className="border-t border-border pt-6">
            <Button variant="danger" onClick={() => setDeleting(true)}>
              {t("staffCatalog.editor.delete")}
            </Button>
          </section>
        ) : null}
      </div>

      <Dialog open={previewing} onOpenChange={setPreviewing} title={t("staffCatalog.preview.title")} description={t("staffCatalog.preview.body")}>
        {previewing ? <StudentPreview exam={exam} /> : null}
      </Dialog>

      {role.canDelete ? (
        <ConfirmDialog
          open={deleting}
          onOpenChange={setDeleting}
          title={t("staffCatalog.editor.deleteTitle", { name: locale === "bn" ? exam.titleBn : exam.titleEn })}
          body={t("staffCatalog.editor.deleteBody")}
          confirmLabel={t("staffCatalog.editor.deleteConfirm")}
          tone="danger"
          typeToConfirm={locale === "bn" ? exam.titleBn : exam.titleEn}
          onConfirm={async () => {
            try {
              await remove.mutateAsync();
              router.replace(`/staff/modules/${exam.moduleId}?tab=exams`);
            } catch (e) {
              throw new Error(t(catalogErrorKey(e)));
            }
          }}
        />
      ) : null}
    </>
  );
}

/** The exam editor for an MCQ or written exam. It always reads the cached exam, so edits show at once. */
export function ExamEditor({ examId }: { examId: string }) {
  const { t } = useLocale();
  const exam = useExam(examId);
  if (exam.isPending) {
    return <Skeleton variant="card" count={3} />;
  }
  if (exam.isError || !exam.data) {
    return <ErrorState onRetry={() => exam.refetch()} message={t("staffCatalog.editor.loadError")} />;
  }
  return <ExamEditorBody key={exam.data.id} exam={exam.data} />;
}
