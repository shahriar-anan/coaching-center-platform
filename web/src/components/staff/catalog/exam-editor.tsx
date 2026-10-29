"use client";

import { useCallback, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { siteHref } from "@/lib/config";
import {
  primaryExamAction,
  useArchiveExam,
  useDeleteExam,
  useDiscardPendingChanges,
  useExam,
  useExamAction,
  useModule,
  usePendingChanges,
  usePublishPendingChanges,
  type StaffExam,
} from "@/lib/api/exam/staff-catalog";
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
import { ExamChecklist } from "@/components/staff/catalog/exam-checklist";
import { ExamMetaForm } from "@/components/staff/catalog/exam-meta-form";
import { McqQuestions } from "@/components/staff/catalog/mcq-questions";
import { SectionBlock, type SectionState } from "@/components/staff/catalog/section-block";
import { WrittenPaper } from "@/components/staff/catalog/written-paper";
import { ExamCoverUploader } from "@/components/staff/catalog/exam-cover-uploader";
import {
  examChecklist,
  examPublishable,
  hasExamText,
  windowFits,
  type ExamSection,
} from "@/components/staff/catalog/exam-form-state";
import { ChangeListDialog } from "@/components/staff/catalog/change-list-dialog";
import { PendingChangesBanner } from "@/components/staff/catalog/pending-changes-banner";

/** The exam's own forms that have a save button. Save all submits each of them that has changes. */
const SECTION_FORMS = ["exam-details-form", "exam-text-form", "exam-schedule-form", "exam-price-form"];

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
  // BN and EN set the letters' language; with no paper language (or Both) the letters follow the interface (C2).
  const lang = exam.paperLanguage === "BN" || exam.paperLanguage === "EN" ? exam.paperLanguage.toLowerCase() : locale;
  return (
    <div lang={lang} className="grid gap-5">
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
 * What students get once this exam is published, stated before the publish button is pressed: title, date, price,
 * content, and the package and door it appears in. A standalone exam has no package and says so.
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
          <dd>
            {moduleTitle ? t("staffCatalog.publish.whereValue", { module: moduleTitle, door }) : t("staffCatalog.publish.whereStandalone", { door })}
          </dd>
        </div>
      </dl>
    </Dialog>
  );
}

/** What to do once an exam is saved: another exam, or the instructor for its package. A standalone exam has no package. */
function NextStepCard({ moduleId, canAssign }: { moduleId: string | null; canAssign: boolean }) {
  const { t } = useLocale();
  const choice = "grid min-h-11 gap-1 rounded-control border border-border-control bg-field p-4 hover:border-muted";
  return (
    <section aria-labelledby="exam-next-heading" className="grid gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-5">
      <div className="grid gap-1">
        <h2 id="exam-next-heading" className="text-card-title font-semibold">
          {t("staffCatalog.next.heading")}
        </h2>
        <p className="text-meta text-muted">{t("staffCatalog.next.body")}</p>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <Link href={moduleId ? `/staff/modules/${moduleId}?tab=exams&new=1` : "/staff/exams"} className={choice}>
          <span className="font-medium">{t("staffCatalog.next.anotherTitle")}</span>
          <span className="text-meta text-muted">{t("staffCatalog.next.anotherBody")}</span>
        </Link>
        {moduleId && canAssign ? (
          <Link href={`/staff/modules/${moduleId}?tab=instructor`} className={choice}>
            <span className="font-medium">{t("staffCatalog.next.assignTitle")}</span>
            <span className="text-meta text-muted">{t("staffCatalog.next.assignBody")}</span>
          </Link>
        ) : moduleId ? (
          // A role that cannot assign sees the choice disabled, with the reason (plan 7.1: shown, not hidden).
          <div className="grid gap-1 rounded-control border border-border p-4">
            <span className="font-medium">{t("staffCatalog.next.assignTitle")}</span>
            <span className="text-meta text-muted">{t("staffCatalog.next.assignMasterOnly")}</span>
          </div>
        ) : null}
      </div>
    </section>
  );
}

function ExamEditorBody({ exam }: { exam: StaffExam }) {
  const { t, locale } = useLocale();
  const { pickTitle, formatNumber } = useFormat();
  const role = useRole();
  const router = useRouter();
  const { toast } = useToast();
  const moduleId = exam.moduleId;
  const moduleQuery = useModule(moduleId ?? undefined);
  const finalSave = useExamAction(exam.id, "final-save");
  const publish = useExamAction(exam.id, "publish");
  const remove = useDeleteExam(exam.id, moduleId ?? "");
  const archive = useArchiveExam(exam.id, moduleId ?? "");
  // The change list is read only when the server says something is staged.
  const changes = usePendingChanges("exam", exam.pendingChanges ? exam.id : undefined);
  const publishChanges = usePublishPendingChanges("exam", exam.id);
  const discardChanges = useDiscardPendingChanges("exam", exam.id);
  const [dirtySections, setDirtySections] = useState<Partial<Record<ExamSection, boolean>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [publishing, setPublishing] = useState(false);
  // True right after the exam was created (the create dialog opens the editor with ?created=1).
  const created = useSearchParams().get("created") === "1";

  const onDirtyChange = useCallback((section: ExamSection, value: boolean) => {
    setDirtySections((current) => (current[section] === value ? current : { ...current, [section]: value }));
  }, []);
  const dirty = Object.values(dirtySections).some(Boolean);

  // An archived exam is read-only for everyone: no edits, no final save, no publish (the API refuses them too).
  const archived = exam.status === "ARCHIVED";
  const canEdit = role.canAuthor && !archived;
  // Publishing is the Master Admin's act; a System Admin edits and sees the staged state.
  const masterAdmin = role.canPublishExam;
  const items = examChecklist(exam, (key) => t(key));
  const publishable = examPublishable(exam, items);
  const primary = primaryExamAction(exam, { dirty, masterAdmin });
  const title = pickTitle(exam);
  const moduleTitle = moduleQuery.data ? pickTitle(moduleQuery.data) : null;
  const hasTeacher = moduleQuery.data?.instructor != null;

  const sectionState: Record<"details" | "text" | "schedule" | "content" | "price" | "publish", SectionState> = {
    details: "done",
    text: hasExamText(exam) ? "done" : "todo",
    schedule: exam.passPercent !== null && windowFits(exam) ? "done" : "todo",
    content: (exam.examType === "MCQ" ? (exam.questions?.length ?? 0) > 0 : exam.questionPdf === true) ? "done" : "todo",
    price: exam.price !== null || exam.free ? "done" : "todo",
    publish: exam.status === "PUBLISHED" ? "done" : "todo",
  };

  const numberFor = (index: number) => formatNumber(index + 1);
  const contentId = exam.examType === "MCQ" ? "exam-questions" : "exam-question-pdf";
  const contentTitle = exam.examType === "MCQ" ? t("staffCatalog.sections.questions") : t("staffCatalog.sections.questionPdf");
  const navSections: SectionNavItem[] = [
    { id: "exam-details", label: `${numberFor(0)} ${t("staffCatalog.sections.details")}`, state: sectionState.details },
    { id: "exam-text", label: `${numberFor(1)} ${t("staffCatalog.sections.text")}`, state: sectionState.text },
    { id: "exam-schedule", label: `${numberFor(2)} ${t("staffCatalog.sections.schedule")}`, state: sectionState.schedule },
    { id: contentId, label: `${numberFor(3)} ${contentTitle}`, state: sectionState.content },
    { id: "exam-price", label: `${numberFor(4)} ${t("staffCatalog.sections.price")}`, state: sectionState.price },
    { id: "exam-publish", label: `${numberFor(5)} ${t("staffCatalog.sections.publish")}`, state: sectionState.publish },
  ];
  const banner = exam.pendingChanges ? <PendingChangesBanner masterAdmin={masterAdmin} /> : null;

  function saveAll() {
    for (const id of SECTION_FORMS) {
      (document.getElementById(id) as HTMLFormElement | null)?.requestSubmit();
    }
  }

  /** Publish records the final save itself (the server needs it), so there is no separate Final save button. */
  async function publishExam() {
    setFormError(null);
    try {
      await finalSave.mutateAsync();
      await publish.mutateAsync();
      setPublishing(false);
      toast({ title: `${t("staffCatalog.editor.published")} ${t(masterAdmin ? "staffCatalog.nextStep.masterAdmin" : "staffCatalog.nextStep.systemAdmin")}`, tone: "ok" });
    } catch (e) {
      setPublishing(false);
      setFormError(t(catalogErrorKey(e)));
    }
  }

  async function finalSaveNow() {
    setFormError(null);
    try {
      await finalSave.mutateAsync();
      toast({ title: t("staffCatalog.editor.finalSaved"), tone: "ok" });
    } catch (e) {
      setFormError(t(catalogErrorKey(e)));
    }
  }

  return (
    <>
      <PageHeader
        title={title}
        breadcrumbs={
          moduleId
            ? [
                { label: t("staffCatalog.nav.modules"), href: "/staff/modules" },
                ...(moduleTitle ? [{ label: moduleTitle, href: `/staff/modules/${moduleId}?tab=exams` }] : []),
                { label: title },
              ]
            : [{ label: t("staffCatalog.nav.exams"), href: "/staff/exams" }, { label: title }]
        }
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <StatusPill status={exam.status} />
            <Button variant="secondary" onClick={() => setPreviewing(true)}>
              {t("staffCatalog.preview.open")}
            </Button>
            {canEdit && primary === "save" ? <Button onClick={saveAll}>{t("staffCatalog.next.saveAll")}</Button> : null}
            {canEdit && primary === "finalSave" ? (
              <Button variant="secondary" loading={finalSave.isPending} disabled={finalSave.isPending} onClick={finalSaveNow}>
                {t("staffCatalog.editor.finalSave")}
              </Button>
            ) : null}
            {canEdit && primary === "publish" ? (
              <Button loading={publish.isPending} disabled={!publishable || publish.isPending} onClick={() => setPublishing(true)}>
                {t("staffCatalog.editor.publish")}
              </Button>
            ) : null}
            {primary === "publishChanges" ? <Button onClick={() => setReviewing(true)}>{t("staffCatalog.changes.publishChanges")}</Button> : null}
            {exam.pendingChanges && masterAdmin ? (
              <Button variant="secondary" onClick={() => setDiscarding(true)}>
                {t("staffCatalog.changes.discardChanges")}
              </Button>
            ) : null}
          </div>
        }
      />

      {created ? (
        <div className="mb-6">
          <NextStepCard moduleId={moduleId} canAssign={role.canAssignInstructor} />
        </div>
      ) : null}

      {archived ? (
        <p role="status" className="mb-6 rounded-control border border-border bg-card px-4 py-3 text-meta text-muted">
          {t("staffCatalog.editor.archivedNotice")}
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
            {banner}
            <ExamMetaForm exam={exam} section="details" canEdit={canEdit} onDirtyChange={onDirtyChange} />
            {role.canManageCover ? <ExamCoverUploader exam={exam} /> : null}
          </SectionBlock>

          <SectionBlock id="exam-text" number={numberFor(1)} title={t("staffCatalog.sections.text")} state={sectionState.text}>
            {banner}
            <ExamMetaForm exam={exam} section="text" canEdit={canEdit} onDirtyChange={onDirtyChange} />
          </SectionBlock>

          <SectionBlock id="exam-schedule" number={numberFor(2)} title={t("staffCatalog.sections.schedule")} state={sectionState.schedule}>
            {banner}
            <ExamMetaForm exam={exam} section="schedule" canEdit={canEdit} onDirtyChange={onDirtyChange} />
          </SectionBlock>

          <SectionBlock id={contentId} number={numberFor(3)} title={contentTitle} state={sectionState.content}>
            {banner}
            {exam.examType === "MCQ" ? (
              <McqQuestions exam={exam} locked={!canEdit} canDelete={role.canAuthor} />
            ) : (
              <WrittenPaper exam={exam} locked={!canEdit} />
            )}
          </SectionBlock>

          <SectionBlock id="exam-price" number={numberFor(4)} title={t("staffCatalog.sections.price")} state={sectionState.price}>
            {banner}
            <ExamMetaForm exam={exam} section="price" canEdit={canEdit} onDirtyChange={onDirtyChange} />
          </SectionBlock>

          <SectionBlock id="exam-publish" number={numberFor(5)} title={t("staffCatalog.sections.publish")} state={sectionState.publish}>
            {canEdit ? (
              <section aria-labelledby="exam-checklist-heading" className="grid max-w-3xl gap-3 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-5">
                <h3 id="exam-checklist-heading" className="text-label font-semibold">
                  {t("staffCatalog.checklist.heading")}
                </h3>
                <ExamChecklist items={items} />
                <p className="text-meta text-muted">{t("staffCatalog.publish.priceNote")}</p>
              </section>
            ) : null}

            {canEdit && !masterAdmin ? (
              <p role="status" className="max-w-3xl text-meta text-muted">
                {t("staffCatalog.checklist.publishMasterOnly")}
              </p>
            ) : null}

            {exam.examType === "WRITTEN" && moduleId && !hasTeacher && moduleQuery.data ? (
              <p role="status" className="max-w-3xl rounded-control border border-warn/40 bg-warn/10 px-4 py-3 text-meta text-warn">
                {role.canAssignInstructor ? (
                  <>
                    {t("staffCatalog.editor.noTeacherWarning")}{" "}
                    <a href={`/staff/modules/${moduleId}?tab=instructor`} className="font-medium underline-offset-2 hover:underline">
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

          {created ? null : <NextStepCard moduleId={moduleId} canAssign={role.canAssignInstructor} />}

          {role.canDelete ? (
            <section className="flex flex-wrap gap-3 border-t border-border pt-6">
              {!archived ? (
                <Button variant="secondary" onClick={() => setArchiving(true)}>
                  {t("staffCatalog.editor.archive")}
                </Button>
              ) : null}
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
        onConfirm={() => publishExam()}
      />

      {masterAdmin && changes.data ? (
        <ChangeListDialog
          open={reviewing}
          onOpenChange={setReviewing}
          changes={changes.data}
          onPublish={(revisionId) => publishChanges.mutateAsync(revisionId)}
          onFailed={(message) => setFormError(message)}
        />
      ) : null}

      {masterAdmin ? (
        <ConfirmDialog
          open={discarding}
          onOpenChange={setDiscarding}
          title={t("staffCatalog.changes.discardTitle")}
          body={t("staffCatalog.changes.discardBody")}
          confirmLabel={t("staffCatalog.changes.discard")}
          tone="danger"
          onConfirm={async () => {
            try {
              await discardChanges.mutateAsync();
              toast({ title: t("staffCatalog.changes.discarded"), tone: "ok" });
            } catch (e) {
              throw new Error(t(catalogErrorKey(e)));
            }
          }}
        />
      ) : null}

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
              router.replace(moduleId ? `/staff/modules/${moduleId}?tab=exams` : "/staff/exams");
            } catch (e) {
              throw new Error(t(catalogErrorKey(e)));
            }
          }}
        />
      ) : null}

      {role.canDelete && !archived ? (
        <ConfirmDialog
          open={archiving}
          onOpenChange={setArchiving}
          title={t("staffCatalog.editor.archiveTitle", { name: locale === "bn" ? exam.titleBn : exam.titleEn })}
          body={t("staffCatalog.editor.archiveBody")}
          confirmLabel={t("staffCatalog.editor.archiveConfirm")}
          tone="danger"
          onConfirm={async () => {
            try {
              await archive.mutateAsync();
              setArchiving(false);
              toast({ title: t("staffCatalog.editor.archived"), tone: "ok" });
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
