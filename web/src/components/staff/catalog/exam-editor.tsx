"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useServerNow } from "@/lib/time/clock";
import { siteHref } from "@/lib/config";
import { useModule, useDeleteExam, useExam, useExamAction, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { apiBlob } from "@/lib/api/client";
import { PdfViewer } from "@/components/pdf/pdf-viewer";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { SectionNav, type SectionNavItem } from "@/components/ui/section-nav";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { ExamChecklist, type ChecklistItem } from "@/components/staff/catalog/exam-checklist";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import { McqQuestions } from "@/components/staff/catalog/mcq-questions";
import { SectionBlock, type SectionState } from "@/components/staff/catalog/section-block";
import { WrittenPaper } from "@/components/staff/catalog/written-paper";

/** What publish waits for, each item linked to the place that fixes it. */
export function examChecklist(exam: StaffExam, t: (key: string) => string): ChecklistItem[] {
  const items: ChecklistItem[] = [
    { key: "finalSave", label: t("staffCatalog.checklist.examFinalSave"), done: exam.finallySaved, anchor: "exam-final-save" },
    { key: "passPercent", label: t("staffCatalog.checklist.passPercent"), done: exam.passPercent !== null, anchor: "exam-schedule" },
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

/**
 * What students get once this exam is published, stated before the publish button
 * is pressed: title, date, price, content, and the module and door it appears in.
 */
function PublishSummary({
  exam,
  moduleTitle,
  open,
  pending,
  onOpenChange,
  onConfirm,
}: {
  exam: StaffExam;
  moduleTitle: string | null;
  open: boolean;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}) {
  const { t } = useLocale();
  const { formatDateTime, formatMoney, formatNumber, pickTitle } = useFormat();
  const isWritten = exam.examType === "WRITTEN";
  const total = (exam.questions ?? []).reduce((sum, q) => sum + q.marks, 0);
  const door = t(`staffCatalog.examType.${exam.examType}`);

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffCatalog.publish.title")}
      description={t("staffCatalog.publish.body")}
      footer={
        <>
          <Button variant="secondary" onClick={() => onOpenChange(false)} disabled={pending}>
            {t("common.actions.cancel")}
          </Button>
          <Button loading={pending} disabled={pending} onClick={onConfirm}>
            {t("staffCatalog.editor.publish")}
          </Button>
        </>
      }
    >
      <dl className="grid gap-3 text-body">
        <div className="grid gap-0.5">
          <dt className="text-meta text-muted">{t("staffCatalog.publish.exam")}</dt>
          <dd className="font-medium">{pickTitle(exam)}</dd>
        </div>
        <div className="grid gap-0.5">
          <dt className="text-meta text-muted">{t("staffCatalog.publish.date")}</dt>
          <dd>{exam.liveAt ? formatDateTime(exam.liveAt) : t("staffCatalog.dateNotSet")}</dd>
        </div>
        <div className="grid gap-0.5">
          <dt className="text-meta text-muted">{t("staffCatalog.publish.price")}</dt>
          <dd>{formatMoney(exam.price) ?? t("staffCatalog.examMeta.noPrice")}</dd>
        </div>
        <div className="grid gap-0.5">
          <dt className="text-meta text-muted">{t("staffCatalog.publish.content")}</dt>
          <dd>
            {isWritten
              ? exam.questionPdf
                ? t("staffCatalog.publish.pdfPresent", { maximum: formatNumber(exam.maximum) })
                : t("staffCatalog.publish.pdfMissing")
              : t("staffCatalog.publish.questions", {
                  count: formatNumber(exam.questions?.length ?? 0),
                  total: formatNumber(total),
                })}
          </dd>
        </div>
        <div className="grid gap-0.5">
          <dt className="text-meta text-muted">{t("staffCatalog.publish.where")}</dt>
          <dd>{t("staffCatalog.publish.whereValue", { module: moduleTitle ?? "", door })}</dd>
        </div>
      </dl>
    </Dialog>
  );
}

function ExamEditorBody({ exam }: { exam: StaffExam }) {
  const { t, locale } = useLocale();
  const { pickTitle, formatNumber } = useFormat();
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
  const [publishing, setPublishing] = useState(false);

  // After the start instant the paper, the file and the schedule are locked.
  const locked = exam.liveAt !== null && new Date(exam.liveAt).getTime() <= now;
  const items = examChecklist(exam, (key) => t(key));
  const publishable = items.every((item) => item.done) && exam.status !== "PUBLISHED";
  const title = pickTitle(exam);
  const moduleTitle = moduleQuery.data ? pickTitle(moduleQuery.data) : null;
  const canEdit = role.canAuthor && !locked;
  const hasTeacher = moduleQuery.data?.instructor != null;

  // Done means the section holds what publish needs. The price is optional, so it is only "done" when set.
  const sectionState: Record<"details" | "schedule" | "content" | "price" | "publish", SectionState> = {
    details: "done",
    schedule: exam.passPercent !== null ? "done" : "todo",
    content: (exam.examType === "MCQ" ? (exam.questions?.length ?? 0) > 0 : exam.questionPdf === true) ? "done" : "todo",
    price: exam.price !== null ? "done" : "todo",
    publish: exam.status === "PUBLISHED" ? "done" : "todo",
  };

  const numberFor = (index: number) => formatNumber(index + 1);
  const contentId = exam.examType === "MCQ" ? "exam-questions" : "exam-question-pdf";
  const contentTitle = exam.examType === "MCQ" ? t("staffCatalog.sections.questions") : t("staffCatalog.sections.questionPdf");
  const navSections: SectionNavItem[] = [
    { id: "exam-details", label: `${numberFor(0)} ${t("staffCatalog.sections.details")}`, state: sectionState.details },
    { id: "exam-schedule", label: `${numberFor(1)} ${t("staffCatalog.sections.schedule")}`, state: sectionState.schedule },
    { id: contentId, label: `${numberFor(2)} ${contentTitle}`, state: sectionState.content },
    { id: "exam-price", label: `${numberFor(3)} ${t("staffCatalog.sections.price")}`, state: sectionState.price },
    { id: "exam-publish", label: `${numberFor(4)} ${t("staffCatalog.sections.publish")}`, state: sectionState.publish },
  ];

  async function run(action: "final-save" | "publish") {
    setFormError(null);
    try {
      if (action === "final-save") {
        await finalSave.mutateAsync();
        toast({ title: t("staffCatalog.editor.finalSaved"), tone: "ok" });
      } else {
        await publish.mutateAsync();
        setPublishing(false);
        toast({ title: `${t("staffCatalog.editor.published")} ${t(role.canEditPrice ? "staffCatalog.nextStep.masterAdmin" : "staffCatalog.nextStep.systemAdmin")}`, tone: "ok" });
      }
    } catch (e) {
      setPublishing(false);
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
            {/* SLOT: answer release (phase 2, STAFF-ADMIN-UI) */}
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
              <Button loading={publish.isPending} disabled={!publishable || publish.isPending} onClick={() => setPublishing(true)}>
                {t("staffCatalog.editor.publish")}
              </Button>
            ) : null}
          </div>
        }
      />

      {locked ? (
        <p role="status" className="mb-6 rounded-control border border-warn/40 bg-warn/10 px-4 py-3 text-meta text-warn">
          {t("staffCatalog.editor.lockedAfterStart")}
        </p>
      ) : null}

      {formError ? (
        <p role="alert" className="mb-6 text-meta text-danger">
          {formError}
        </p>
      ) : null}

      <div className="grid gap-8 md:grid-cols-[13rem_minmax(0,1fr)] md:items-start">
        <SectionNav sections={navSections} />

        <div className="grid min-w-0 gap-10">
          <SectionBlock id="exam-details" number={numberFor(0)} title={t("staffCatalog.sections.details")} state={sectionState.details}>
            <ExamMetaForm exam={exam} locked={!canEdit} section="details" />
          </SectionBlock>

          <SectionBlock id="exam-schedule" number={numberFor(1)} title={t("staffCatalog.sections.schedule")} state={sectionState.schedule}>
            <ExamMetaForm exam={exam} locked={!canEdit} section="schedule" />
          </SectionBlock>

          <SectionBlock id={contentId} number={numberFor(2)} title={contentTitle} state={sectionState.content}>
            {exam.examType === "MCQ" ? (
              <McqQuestions exam={exam} locked={!canEdit} canDelete={role.canDelete} />
            ) : (
              <WrittenPaper exam={exam} locked={!canEdit} />
            )}
          </SectionBlock>

          <SectionBlock id="exam-price" number={numberFor(3)} title={t("staffCatalog.sections.price")} state={sectionState.price}>
            <ExamMetaForm exam={exam} locked={!canEdit} section="price" />
          </SectionBlock>

          <SectionBlock id="exam-publish" number={numberFor(4)} title={t("staffCatalog.sections.publish")} state={sectionState.publish}>
            {canEdit ? (
              <section aria-labelledby="exam-checklist-heading" className="grid max-w-3xl gap-3 rounded-card border border-border bg-card p-5">
                <h3 id="exam-checklist-heading" className="text-label font-semibold">
                  {t("staffCatalog.checklist.heading")}
                </h3>
                <ExamChecklist items={items} />
                <p className="text-meta text-muted">{t("staffCatalog.publish.priceNote")}</p>
              </section>
            ) : null}

            {exam.examType === "WRITTEN" && !hasTeacher && moduleQuery.data ? (
              <p role="status" className="max-w-3xl rounded-control border border-warn/40 bg-warn/10 px-4 py-3 text-meta text-warn">
                {role.canAssignInstructor ? (
                  <>
                    {t("staffCatalog.editor.noTeacherWarning")}{" "}
                    <a href={`/staff/modules/${exam.moduleId}?tab=instructor`} className="font-medium underline-offset-2 hover:underline">
                      {t("staffCatalog.editor.assignTeacher")}
                    </a>
                  </>
                ) : (
                  t("staffCatalog.editor.noTeacherInfo")
                )}
              </p>
            ) : null}

            {exam.status === "PUBLISHED" ? (
              <p className="text-meta">
                <a href={siteHref(`/exam/${exam.id}`)} className="font-medium text-action underline-offset-2 hover:underline">
                  {t("staffCatalog.editor.viewAsStudent")}
                </a>
              </p>
            ) : null}
          </SectionBlock>

          {role.canDelete ? (
            <section className="border-t border-border pt-6">
              <Button variant="danger" onClick={() => setDeleting(true)}>
                {t("staffCatalog.editor.delete")}
              </Button>
            </section>
          ) : null}
        </div>
      </div>

      <Dialog open={previewing} onOpenChange={setPreviewing} title={t("staffCatalog.preview.title")} description={t("staffCatalog.preview.body")}>
        {previewing ? <StudentPreview exam={exam} /> : null}
      </Dialog>

      <PublishSummary
        exam={exam}
        moduleTitle={moduleTitle}
        open={publishing}
        pending={publish.isPending}
        onOpenChange={setPublishing}
        onConfirm={() => run("publish")}
      />

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
