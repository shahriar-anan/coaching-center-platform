"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AlertTriangle, ArrowDown, ArrowUp } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import {
  useAddQuestion,
  useDeleteQuestion,
  useExam,
  useReorderQuestions,
  useUpdateQuestion,
  type StaffExam,
  type StaffQuestion,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState } from "@/components/ui/error-state";
import { IconButton } from "@/components/ui/icon-button";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { ExamStatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { PasteQuestions } from "@/components/staff/catalog/paste-questions";
import {
  blankDraft,
  draftFromQuestion,
  keepOneCorrect,
  questionRequest,
  setMultipleAnswer,
  validateDraft,
  type QuestionDraft,
} from "@/components/staff/catalog/question-draft";
import { QuestionForm } from "@/components/staff/questions/question-form";
import { QuestionPreview } from "@/components/staff/questions/question-preview";

/** Whether the student-view preview is open. Remembered on this browser; the page works without it. */
const PREVIEW_KEY = "cc.staff.questionPreview";

function readPreviewPreference(): boolean {
  try {
    return window.localStorage.getItem(PREVIEW_KEY) === "on";
  } catch {
    return false;
  }
}

function writePreviewPreference(on: boolean) {
  try {
    window.localStorage.setItem(PREVIEW_KEY, on ? "on" : "off");
  } catch {
    // A private window cannot store the choice; the preview still opens for this visit.
  }
}

function shortStem(stem: string) {
  return stem.length > 48 ? `${stem.slice(0, 48)}…` : stem;
}

/**
 * The question editor for an MCQ paper: a navigator of every question, the editor for the selected one, and an optional
 * student-view preview. Saving follows the exam's rules: a published exam stages the change for the Master Admin. A sitting
 * that has started keeps the questions it started with.
 */
export function QuestionPage({ examId }: { examId: string }) {
  const { t } = useLocale();
  const exam = useExam(examId);
  if (exam.isPending) {
    return <Skeleton variant="card" count={3} />;
  }
  if (exam.isError || !exam.data) {
    return <ErrorState onRetry={() => exam.refetch()} message={t("staffCatalog.editor.loadError")} error={exam.error} />;
  }
  if (exam.data.examType !== "MCQ") {
    return (
      <>
        <PageHeader
          title={t("staffCatalog.questionPage.title")}
          breadcrumbs={[{ label: t("staffCatalog.nav.exams"), href: "/staff/exams" }, { label: t("staffCatalog.questionPage.title") }]}
        />
        <p className="text-body text-muted">{t("staffCatalog.questionPage.notMcq")}</p>
      </>
    );
  }
  return <QuestionWorkspace key={exam.data.id} exam={exam.data} />;
}

function QuestionWorkspace({ exam }: { exam: StaffExam }) {
  const { t } = useLocale();
  const { formatNumber, pickTitle } = useFormat();
  const { toast } = useToast();
  const router = useRouter();
  const params = useSearchParams();
  const role = useRole();
  // An account that cannot author is read-only (the API refuses those saves too). A withdrawn exam takes edits (D5).
  const readOnly = !role.canAuthor;

  const questions = useMemo(() => [...(exam.questions ?? [])].sort((a, b) => a.position - b.position), [exam.questions]);
  const total = questions.reduce((sum, question) => sum + question.marks, 0);
  const missingCount = questions.filter((question) => !question.options.some((option) => option.correct)).length;
  const nextPosition = questions.length === 0 ? 1 : Math.max(...questions.map((question) => question.position)) + 1;

  // The question in the URL (`?q=`) opens first; otherwise the first one, or a blank new question when there are none.
  const requested = params.get("q");
  const initial = questions.find((question) => question.id === requested) ?? (requested === "new" ? null : (questions[0] ?? null));
  const [current, setCurrent] = useState<StaffQuestion | null>(initial);
  const [draft, setDraft] = useState<QuestionDraft>(() => (initial ? draftFromQuestion(initial) : blankDraft()));
  const [baseline, setBaseline] = useState<QuestionDraft>(draft);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [keepChoice, setKeepChoice] = useState(false);
  const [query, setQuery] = useState("");
  const [pasting, setPasting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [preview, setPreview] = useState(readPreviewPreference);
  // The question just saved with Save & next, shown above the blank question that follows it.
  const [previous, setPrevious] = useState<StaffQuestion | null>(null);

  const dirty = JSON.stringify(draft) !== JSON.stringify(baseline);
  const index = current ? questions.findIndex((question) => question.id === current.id) : -1;

  const add = useAddQuestion(exam.id);
  const update = useUpdateQuestion(exam.id);
  const remove = useDeleteQuestion(exam.id);
  const reorder = useReorderQuestions(exam.id);
  const busy = add.isPending || update.isPending || remove.isPending || reorder.isPending;

  // Leaving with unsaved changes asks first; switching questions asks too (see open).
  useEffect(() => {
    if (!dirty) {
      return;
    }
    function warn(event: BeforeUnloadEvent) {
      event.preventDefault();
    }
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  /** Shows one question in the editor and moves the URL to it. */
  function show(question: StaffQuestion | null) {
    setPrevious(null);
    const next = question ? draftFromQuestion(question) : blankDraft();
    setCurrent(question);
    setDraft(next);
    setBaseline(next);
    setErrors({});
    setFormError(null);
    setKeepChoice(false);
    router.replace(`?q=${question?.id ?? "new"}`, { scroll: false });
  }

  /** Opens a question from the navigator, asking first when the open one has unsaved changes. */
  function open(question: StaffQuestion | null) {
    const same = question ? question.id === current?.id : current === null;
    if (same) {
      return;
    }
    if (dirty && !window.confirm(t("staffCatalog.questionPage.discardPrompt"))) {
      return;
    }
    show(question);
  }

  function onKindChange(on: boolean) {
    const next = setMultipleAnswer(draft, on);
    setDraft(next.draft);
    setKeepChoice(next.keepChoice);
  }

  function onKeep(optionIndex: number) {
    setDraft((c) => keepOneCorrect(c, optionIndex));
    setKeepChoice(false);
  }

  async function save(goNext: boolean) {
    setFormError(null);
    if (keepChoice) {
      setFormError(t("staffCatalog.question.keepPrompt"));
      return;
    }
    const messages: Record<string, string> = {};
    for (const [field, key] of Object.entries(validateDraft(draft))) {
      messages[field] = t(key as string);
    }
    setErrors(messages);
    if (Object.keys(messages).length > 0) {
      return;
    }
    const body = questionRequest(draft, current?.position ?? nextPosition);
    try {
      const saved = current ? await update.mutateAsync({ questionId: current.id, body }) : await add.mutateAsync(body);
      toast({ title: t("staffCatalog.question.saved"), tone: "ok" });
      const following = goNext ? questions.find((question) => question.id !== saved.id && question.position > saved.position) : undefined;
      if (goNext && !following) {
        // The last question: Save & next opens a blank question, with the one just saved shown above it.
        show(null);
        setPrevious(saved);
      } else {
        show(following ?? saved);
      }
    } catch (caught) {
      setFormError(t(catalogErrorKey(caught)));
    }
  }

  async function confirmDelete() {
    if (!current) {
      return;
    }
    const neighbour = questions[index + 1] ?? questions[index - 1] ?? null;
    try {
      await remove.mutateAsync(current.id);
      setDeleting(false);
      toast({ title: t("staffCatalog.mcq.deleted"), tone: "ok" });
      show(neighbour);
    } catch (caught) {
      throw new Error(t(catalogErrorKey(caught)));
    }
  }

  /** Swaps the selected question with its neighbour: one call with the whole new order. */
  async function move(direction: -1 | 1) {
    const target = index + direction;
    if (index < 0 || target < 0 || target >= questions.length) {
      return;
    }
    const ids = questions.map((question) => question.id);
    [ids[index], ids[target]] = [ids[target], ids[index]];
    setFormError(null);
    try {
      await reorder.mutateAsync(ids);
    } catch (caught) {
      setFormError(t(catalogErrorKey(caught)));
    }
  }

  // Ctrl+S saves; Ctrl+Enter saves and goes to the next question.
  useEffect(() => {
    if (readOnly) {
      return;
    }
    function onKey(event: KeyboardEvent) {
      if (!(event.ctrlKey || event.metaKey)) {
        return;
      }
      if (event.key.toLowerCase() === "s") {
        event.preventDefault();
        void save(false);
      } else if (event.key === "Enter") {
        event.preventDefault();
        void save(true);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  function togglePreview(on: boolean) {
    setPreview(on);
    writePreviewPreference(on);
  }

  const needle = query.trim().toLowerCase();
  const visible = needle ? questions.filter((question) => question.stem.toLowerCase().includes(needle)) : questions;
  const title = pickTitle({ titleEn: exam.titleEn, titleBn: exam.titleBn });

  return (
    <>
      <PageHeader
        title={title}
        breadcrumbs={[
          { label: t("staffCatalog.nav.exams"), href: "/staff/exams" },
          { label: title, href: `/staff/exams/${exam.id}` },
          { label: t("staffCatalog.questionPage.title") },
        ]}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <ExamStatusPill status={exam.status} />
            <Button variant="secondary" href={`/staff/exams/${exam.id}`}>
              {t("staffCatalog.questionPage.back")}
            </Button>
          </div>
        }
      />

      <p className="mb-2 text-label font-medium tabular-nums" aria-live="polite">
        {t("staffCatalog.mcq.summary", { count: formatNumber(questions.length), total: formatNumber(total) })}
        {missingCount > 0 ? <> · {t("staffCatalog.mcq.missingCorrect", { n: formatNumber(missingCount) })}</> : null}
      </p>
      <p className="mb-4 text-meta text-muted">{t("staffCatalog.questionPage.shortcuts")}</p>

      {readOnly ? (
        <p role="status" className="mb-4 rounded-control border border-border bg-field p-3 text-meta">
          {t("staffCatalog.questionPage.readOnly")}
        </p>
      ) : null}

      <div className={`grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]${preview ? " xl:grid-cols-[16rem_minmax(0,1fr)_20rem]" : ""}`}>
        <nav aria-label={t("staffCatalog.questionPage.navHeading")} className="grid content-start gap-3 lg:sticky lg:top-24">
          <p className="text-label font-semibold">{t("staffCatalog.questionPage.navHeading")}</p>
          <TextInput
            aria-label={t("staffCatalog.questionPage.navSearch")}
            placeholder={t("staffCatalog.questionPage.navSearch")}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          {questions.length > 0 && visible.length === 0 ? (
            <p className="text-meta text-muted">{t("staffCatalog.questionPage.noMatch")}</p>
          ) : null}
          <ol className="grid max-h-[60vh] gap-1 overflow-y-auto">
            {visible.map((question) => {
              const selected = question.id === current?.id;
              const missing = !question.options.some((option) => option.correct);
              return (
                <li key={question.id}>
                  <button
                    type="button"
                    aria-current={selected ? "true" : undefined}
                    onClick={() => open(question)}
                    className={`flex min-h-11 w-full items-center gap-2 rounded-control px-2 py-1.5 text-left text-meta ${
                      selected ? "bg-field font-semibold" : "hover:bg-field/60"
                    }`}
                  >
                    <span className="w-7 shrink-0 tabular-nums text-muted">{formatNumber(questions.indexOf(question) + 1)}</span>
                    <span className="min-w-0 flex-1 truncate">{question.stem}</span>
                    {selected && dirty ? <span role="img" aria-label={t("staffCatalog.questionPage.unsaved")} className="size-2 rounded-full bg-warn" /> : null}
                    {missing ? <AlertTriangle role="img" aria-label={t("staffCatalog.questionPage.noAnswer")} className="size-4 shrink-0 text-warn" /> : null}
                  </button>
                </li>
              );
            })}
          </ol>
          {!readOnly ? (
            <div className="grid gap-2 border-t border-border pt-3">
              <Button variant="secondary" onClick={() => open(null)}>
                {t("staffCatalog.questionPage.newQuestion")}
              </Button>
              <Button variant="secondary" onClick={() => setPasting(true)}>
                {t("staffCatalog.paste.open")}
              </Button>
            </div>
          ) : null}
        </nav>

        <section aria-labelledby="question-editor-heading" className="grid content-start gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="question-editor-heading" className="text-label font-semibold">
              {index >= 0
                ? t("staffCatalog.questionPage.position", { n: formatNumber(index + 1), total: formatNumber(questions.length) })
                : t("staffCatalog.questionPage.newTitle")}
            </h2>
            <div className="flex flex-wrap items-center gap-2">
              <Switch id="question-preview" checked={preview} onCheckedChange={togglePreview} label={t("staffCatalog.questionPage.showPreview")} />
              {index >= 0 && !readOnly ? (
                <div className="flex items-center gap-1">
                  <IconButton label={t("staffCatalog.mcq.moveUp")} icon={<ArrowUp className="size-4" />} disabled={index === 0 || busy} onClick={() => void move(-1)} />
                  <IconButton
                    label={t("staffCatalog.mcq.moveDown")}
                    icon={<ArrowDown className="size-4" />}
                    disabled={index === questions.length - 1 || busy}
                    onClick={() => void move(1)}
                  />
                </div>
              ) : null}
            </div>
          </div>

          {previous ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-field p-3 text-meta">
              <p className="min-w-0 truncate">
                <span className="text-muted">{t("staffCatalog.questionPage.previousSaved")} </span>
                {previous.stem}
              </p>
              <Button variant="secondary" onClick={() => open(previous)}>
                {t("staffCatalog.questionPage.openPrevious")}
              </Button>
            </div>
          ) : null}

          <fieldset disabled={readOnly} className="min-w-0">
            <QuestionForm
              draft={draft}
              setDraft={setDraft}
              errors={errors}
              keepChoice={keepChoice}
              onKindChange={onKindChange}
              onKeep={onKeep}
            />
          </fieldset>

          {formError ? (
            <p role="alert" className="text-meta text-danger">
              {formError}
            </p>
          ) : null}

          {!readOnly ? (
            <div className="sticky bottom-0 flex flex-wrap items-center justify-between gap-3 border-t border-border bg-bg/95 py-3 backdrop-blur">
              <p role="status" className="text-meta text-muted">
                {dirty ? t("staffCatalog.questionPage.unsaved") : t("staffCatalog.questionPage.allSaved")}
              </p>
              <div className="flex flex-wrap items-center gap-2">
                {current ? (
                  <Button variant="secondary" disabled={busy} onClick={() => setDeleting(true)}>
                    {t("staffCatalog.mcq.delete")}
                  </Button>
                ) : null}
                <Button variant="secondary" loading={add.isPending || update.isPending} disabled={busy} onClick={() => void save(false)}>
                  {t("staffCatalog.question.save")}
                </Button>
                <Button loading={busy} disabled={busy} onClick={() => void save(true)}>
                  {t("staffCatalog.questionPage.saveNext")}
                </Button>
              </div>
            </div>
          ) : null}
        </section>

        {preview ? <QuestionPreview draft={draft} /> : null}
      </div>

      {!readOnly ? (
        <PasteQuestions
          exam={exam}
          nextPosition={nextPosition}
          open={pasting}
          onOpenChange={setPasting}
        />
      ) : null}

      <ConfirmDialog
        open={deleting}
        onOpenChange={(next) => !next && setDeleting(false)}
        title={t("staffCatalog.mcq.deleteTitle", { stem: shortStem(current?.stem ?? "") })}
        body={t("staffCatalog.mcq.deleteBody")}
        confirmLabel={t("staffCatalog.mcq.deleteConfirm")}
        tone="danger"
        onConfirm={confirmDelete}
      />
    </>
  );
}
