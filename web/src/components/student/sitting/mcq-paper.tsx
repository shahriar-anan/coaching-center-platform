"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  useSaveAnswers,
  useSubmitExam,
  type PostedResult,
  type StudentExam,
} from "@/lib/api/exam/sitting";
import { ApiError } from "@/lib/api/errors";
import { useLocale } from "@/i18n/locale-context";
import { formatClock, formatNumber } from "@/lib/format";
import { useServerNow } from "@/lib/time/clock";
import { McqQuestion, type OptionLabels, type PaperLanguage } from "./mcq-question";
import { PaperTextSizeControl, usePaperTextSize } from "./paper-text-size";
import {
  backupKey as makeBackupKey,
  clearBackup,
  mergeSelection,
  nextRetryDelay,
  readBackup,
  SAVE_DEBOUNCE_MS,
  toAnswerList,
  toggleFlag,
  toggleOption,
  writeBackup,
  type Selection,
} from "./selection";
import { crossedWarning, inFinalMinute } from "./warnings";

const LETTERS_BN = ["ক", "খ", "গ", "ঘ", "ঙ", "চ"] as const;
const LETTERS_EN = ["A", "B", "C", "D", "E", "F"] as const;

/** Errors after which another save can never succeed, so the paper stops retrying. */
const CLOSED_CODES = new Set(["DEADLINE_PASSED", "SITTING_NOT_OPEN"]);

type SaveState = "saved" | "saving" | "failed" | "offline" | "idle";

type Props = {
  exam: StudentExam;
  studentId: string;
  /** When the student's own sitting started. Part of the backup key, so a new sitting never reads an old one. */
  startedAt: string;
  deadlineAt: string;
  /** "RETAKE" is the later attempt: its own timer, off the merit list. */
  kind: "LIVE" | "RETAKE";
  onClosed: () => void;
};

export function McqPaper({ exam, studentId, startedAt, deadlineAt, kind, onClosed }: Props) {
  const { t, locale } = useLocale();
  const questions = exam.questions ?? [];
  const paperLanguage: PaperLanguage = exam.paperLanguage === "EN" ? "EN" : "BN";
  const letters = paperLanguage === "BN" ? LETTERS_BN : LETTERS_EN;
  const n = (value: number) => formatNumber(value, locale);
  const bKey = makeBackupKey(exam.id, studentId, startedAt);

  const serverSelection = useMemo<Selection>(() => {
    const selection: Selection = {};
    for (const question of questions) {
      const picked = question.options.find((option) => option.selected === true);
      if (picked) {
        selection[question.id] = picked.id;
      }
    }
    return selection;
    // The server copy is read once per sitting; later changes come from the student's own taps.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exam.id]);

  const optionLabels = useMemo<OptionLabels>(
    () => ({
      selected: t("sitting.paper.selected"),
      flag: t("sitting.paper.flag"),
      yourAnswer: t("result.review.yourAnswer"),
      correct: t("result.review.correct"),
      explanation: t("result.review.explanation"),
    }),
    [t],
  );

  const [selection, setSelection] = useState<Selection>(() =>
    mergeSelection(serverSelection, null, readBackup(bKey)),
  );
  const [flagged, setFlagged] = useState<string[]>(() => readBackup(bKey)?.flagged ?? []);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [announcement, setAnnouncement] = useState("");
  const [gridOpen, setGridOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const textSize = usePaperTextSize();
  const [result, setResult] = useState<PostedResult | null>(null);
  const [submitError, setSubmitError] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [closed, setClosed] = useState(false);

  const now = useServerNow(1000);
  const remainingMs = Math.max(0, new Date(deadlineAt).getTime() - now);
  const deadlinePassed = remainingMs <= 0;
  const editable = !closed && !deadlinePassed;

  // --- Autosave: one request in flight, latest selection always wins -------------------------

  const selectionRef = useRef(selection);
  const flaggedRef = useRef(flagged);
  const dirtyRef = useRef(false);
  const attemptRef = useRef(0);
  const closedRef = useRef(false);
  const runningRef = useRef<Promise<void> | null>(null);
  const timerRef = useRef<number | null>(null);
  const saveMutation = useSaveAnswers(exam.id);
  const saveRef = useRef(saveMutation.mutateAsync);
  const onClosedRef = useRef(onClosed);
  useEffect(() => {
    saveRef.current = saveMutation.mutateAsync;
    onClosedRef.current = onClosed;
  }, [saveMutation.mutateAsync, onClosed]);

  const pump = useCallback((): Promise<void> => {
    if (runningRef.current) {
      return runningRef.current;
    }
    const run = async () => {
      while (dirtyRef.current && !closedRef.current) {
        if (typeof navigator !== "undefined" && navigator.onLine === false) {
          setSaveState("offline");
          return;
        }
        dirtyRef.current = false;
        const snapshot = selectionRef.current;
        setSaveState("saving");
        try {
          await saveRef.current(toAnswerList(snapshot));
          attemptRef.current = 0;
          setSaveState("saved");
        } catch (error) {
          if (error instanceof ApiError && CLOSED_CODES.has(error.code)) {
            closedRef.current = true;
            setClosed(true);
            return;
          }
          // Keep the latest selection and try again after the backoff.
          dirtyRef.current = true;
          const delay = nextRetryDelay(attemptRef.current);
          attemptRef.current += 1;
          if (delay === null) {
            setSaveState("failed");
            return;
          }
          await new Promise((resolve) => window.setTimeout(resolve, delay));
        }
      }
    };
    runningRef.current = run().finally(() => {
      runningRef.current = null;
    });
    return runningRef.current;
  }, []);

  const scheduleSave = useCallback(() => {
    dirtyRef.current = true;
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
    }
    timerRef.current = window.setTimeout(() => {
      timerRef.current = null;
      void pump();
    }, SAVE_DEBOUNCE_MS);
  }, [pump]);

  const retrySave = useCallback(() => {
    attemptRef.current = 0;
    dirtyRef.current = true;
    void pump();
  }, [pump]);

  // Back online: flush whatever was kept on this device.
  useEffect(() => {
    const online = () => {
      if (dirtyRef.current) {
        void pump();
      }
    };
    window.addEventListener("online", online);
    return () => window.removeEventListener("online", online);
  }, [pump]);

  // Warn before leaving while a save is pending or failed.
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (dirtyRef.current || runningRef.current) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, []);

  useEffect(() => () => {
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
    }
  }, []);

  // --- Taps and the local backup ---------------------------------------------------------------

  const pick = useCallback(
    (questionId: string, optionId: string) => {
      if (!editable) {
        return;
      }
      const next = toggleOption(selectionRef.current, questionId, optionId);
      selectionRef.current = next;
      setSelection(next);
      writeBackup(bKey, { selection: next, flagged: flaggedRef.current, savedAt: Date.now() });
      scheduleSave();
    },
    [bKey, editable, scheduleSave],
  );

  const flag = useCallback(
    (questionId: string) => {
      if (!editable) {
        return;
      }
      const next = toggleFlag(flaggedRef.current, questionId);
      flaggedRef.current = next;
      setFlagged(next);
      // The flag stays on this device only; it never goes to the server.
      writeBackup(bKey, { selection: selectionRef.current, flagged: next, savedAt: Date.now() });
    },
    [bKey, editable],
  );

  // --- Deadline and warnings ----------------------------------------------------------------

  const previousRef = useRef<number | null>(null);
  useEffect(() => {
    const crossed = crossedWarning(previousRef.current, remainingMs);
    previousRef.current = remainingMs;
    if (crossed !== null && !deadlinePassed) {
      setAnnouncement(t("sitting.warn.minutes", { minutes: n(crossed) }));
    }
    // `n` and `t` change identity with the locale only; the warning fires on the time.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remainingMs, deadlinePassed]);

  useEffect(() => {
    if (!deadlinePassed || closedRef.current) {
      return;
    }
    // Stop accepting taps, flush the last save, then move to the result without a refresh.
    closedRef.current = true;
    setClosed(true);
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    void (async () => {
      if (dirtyRef.current || runningRef.current) {
        await pump();
      }
      clearBackup(bKey);
      setAnnouncement(t("sitting.warn.ended"));
      onClosedRef.current();
    })();
  }, [deadlinePassed, bKey, pump, t]);

  // --- Submit --------------------------------------------------------------------------------

  const submit = useSubmitExam(exam.id);
  const answeredCount = Object.keys(selection).length;
  const unanswered = questions.length - answeredCount;
  const flaggedNumbers = questions
    .map((question, index) => (flagged.includes(question.id) ? index + 1 : null))
    .filter((value): value is number => value !== null);

  const doSubmit = async () => {
    setSubmitError(false);
    setSubmitting(true);
    try {
      if (dirtyRef.current || runningRef.current) {
        await pump();
      }
      const posted = await submit.mutateAsync();
      setResult(posted);
      setConfirmOpen(false);
    } catch {
      setSubmitError(true);
    } finally {
      setSubmitting(false);
    }
  };

  const jumpTo = (index: number) => {
    document.getElementById(`q-${index}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
    setGridOpen(false);
  };

  const barTone = inFinalMinute(remainingMs) ? "text-warn" : "text-text";
  const timeLabel = kind === "RETAKE" ? t("sitting.bar.yourTime") : t("sitting.bar.timeLeft");

  return (
    <div className="flex flex-col gap-4 pb-28">
      {/* Sticky bar: exam, countdown, save state, answered count, jump grid, submit. */}
      <div className="sticky top-0 z-10 flex flex-wrap items-center justify-between gap-3 rounded-card border border-border bg-card p-3 shadow-sm">
        <div className="min-w-0">
          <p className="truncate font-semibold text-text">{exam.titleBn ?? exam.titleEn}</p>
          <p className={`text-body ${barTone}`}>
            {timeLabel}: <span className="tabular-nums">{formatClock(remainingMs, locale)}</span>
          </p>
        </div>
        <div className="flex items-center gap-2">
          <SaveBadge state={saveState} t={t} onRetry={retrySave} />
          <span className="text-meta text-muted tabular-nums">
            {t("sitting.bar.answered", { answered: n(answeredCount), total: n(questions.length) })}
          </span>
          <button
            type="button"
            aria-expanded={gridOpen}
            onClick={() => setGridOpen((value) => !value)}
            className="min-h-11 rounded-control border border-border px-3 text-meta text-text focus-visible:outline-2 focus-visible:outline-accent"
          >
            {t("sitting.bar.questions")}
          </button>
        </div>
        <div className="h-1 w-full overflow-hidden rounded-full bg-border" aria-hidden="true">
          <div
            className="h-full bg-accent"
            style={{ width: `${questions.length ? (answeredCount / questions.length) * 100 : 0}%` }}
          />
        </div>
      </div>

      <div role="status" aria-live="polite" className="sr-only">
        {announcement}
      </div>

      {result ? <ScoreCard result={result} n={n} t={t} /> : null}

      {gridOpen ? (
        <nav aria-label={t("sitting.grid.title")} className="rounded-card border border-border bg-card p-3">
          <div className="grid grid-cols-5 gap-2 sm:grid-cols-10">
            {questions.map((question, index) => {
              const answered = selection[question.id] !== undefined;
              const isFlagged = flagged.includes(question.id);
              return (
                <button
                  key={question.id}
                  type="button"
                  onClick={() => jumpTo(index + 1)}
                  aria-label={`${t("sitting.grid.goTo", { number: n(index + 1) })}${answered ? `, ${t("sitting.grid.answered")}` : ""}${isFlagged ? `, ${t("sitting.grid.flagged")}` : ""}`}
                  className={`min-h-11 rounded-control text-meta tabular-nums focus-visible:outline-2 focus-visible:outline-accent ${
                    answered ? "bg-accent text-on-accent" : "border border-border text-text"
                  } ${isFlagged ? "ring-2 ring-warn" : ""}`}
                >
                  {n(index + 1)}
                </button>
              );
            })}
          </div>
        </nav>
      ) : null}

      {kind === "RETAKE" ? (
        <p role="note" className="rounded-card border border-warn bg-card p-3 text-body text-text">
          {t("sitting.paper.practice")}
        </p>
      ) : null}

      {closed && !deadlinePassed ? (
        <p role="alert" className="rounded-card border border-danger bg-card p-3 text-body text-text">
          {t("sitting.paper.locked")}
        </p>
      ) : null}

      {questions.length === 0 ? (
        <p className="text-center text-muted">{t("sitting.paper.empty")}</p>
      ) : (
        <div className="flex flex-col gap-3">
          <PaperTextSizeControl size={textSize} className="justify-end" />
          <div className="flex flex-col gap-3" style={textSize.style}>
          {questions.map((question, index) => (
            <McqQuestion
              key={question.id}
              question={question}
              index={index + 1}
              selectedOptionId={selection[question.id]}
              flagged={flagged.includes(question.id)}
              paperLanguage={paperLanguage}
              editable={editable}
              reviewing={false}
              labels={optionLabels}
              questionLabel={t("sitting.paper.question", { number: n(index + 1) })}
              letters={letters}
              onPick={pick}
              onFlag={flag}
            />
          ))}
          <p className="text-center text-body text-muted">{t("sitting.paper.end")}</p>
          </div>
        </div>
      )}

      {/* Submit: the primary action, fixed to the bottom on phones. */}
      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-border bg-card p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <div className="mx-auto flex max-w-[760px] items-center justify-between gap-3">
          <p className="text-body text-muted">
            {result ? t("sitting.score.canChange") : t("sitting.submit.counts", { answered: n(answeredCount), unanswered: n(Math.max(0, unanswered)) })}
          </p>
          <button
            type="button"
            disabled={!editable || submitting}
            onClick={() => setConfirmOpen(true)}
            className="min-h-11 rounded-control bg-accent px-5 font-semibold text-on-accent disabled:opacity-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          >
            {submitting ? t("sitting.bar.saving") : t("sitting.bar.submit")}
          </button>
        </div>
      </div>

      {confirmOpen ? (
        <SubmitDialog
          answered={answeredCount}
          unanswered={Math.max(0, unanswered)}
          flaggedNumbers={flaggedNumbers.map((value) => n(value)).join(", ")}
          busy={submitting}
          failed={submitError}
          onCancel={() => setConfirmOpen(false)}
          onConfirm={doSubmit}
          n={n}
          t={t}
        />
      ) : null}
    </div>
  );
}

function SaveBadge({
  state,
  t,
  onRetry,
}: {
  state: SaveState;
  t: (key: string) => string;
  onRetry: () => void;
}) {
  if (state === "failed") {
    return (
      <button
        type="button"
        onClick={onRetry}
        className="min-h-11 rounded-control px-2 text-meta font-semibold text-warn underline focus-visible:outline-2 focus-visible:outline-accent"
      >
        {t("sitting.bar.saveFailed")} · {t("sitting.bar.retry")}
      </button>
    );
  }
  if (state === "offline") {
    return <span className="text-meta text-warn">{t("sitting.offline")}</span>;
  }
  const label =
    state === "saving" ? t("sitting.bar.saving") : state === "saved" ? t("sitting.bar.saved") : "";
  return label ? <span className="text-meta text-muted" aria-live="polite">{label}</span> : null;
}

function ScoreCard({
  result,
  n,
  t,
}: {
  result: PostedResult;
  n: (value: number) => string;
  t: (key: string, params?: Record<string, string | number>) => string;
}) {
  return (
    <section aria-labelledby="score-title" className="rounded-card border border-border bg-card p-4">
      <h2 id="score-title" className="text-label font-semibold text-muted">
        {t("sitting.score.title")}
      </h2>
      <p className="mt-1 text-section font-semibold text-accent tabular-nums">
        {t("sitting.score.value", { score: n(result.score ?? 0), maximum: n(result.maximum) })}
        {result.passed !== null ? ` · ${result.passed ? t("sitting.score.passed") : t("sitting.score.failed")}` : ""}
      </p>
      <p className="mt-2 text-body text-muted">{t("sitting.score.canChange")}</p>
    </section>
  );
}

function SubmitDialog({
  answered,
  unanswered,
  flaggedNumbers,
  busy,
  failed,
  onCancel,
  onConfirm,
  n,
  t,
}: {
  answered: number;
  unanswered: number;
  flaggedNumbers: string;
  busy: boolean;
  failed: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  n: (value: number) => string;
  t: (key: string, params?: Record<string, string | number>) => string;
}) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) {
        onCancel();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [busy, onCancel]);

  return (
    <div className="fixed inset-0 z-30 flex items-end justify-center bg-black/40 p-4 sm:items-center">
      <div role="dialog" aria-modal="true" aria-labelledby="submit-title" className="w-full max-w-md rounded-card bg-card p-5">
        <h2 id="submit-title" className="text-card font-semibold text-text">
          {t("sitting.submit.title")}
        </h2>
        <p className="mt-2 text-text">
          {t("sitting.submit.counts", { answered: n(answered), unanswered: n(unanswered) })}
        </p>
        {flaggedNumbers ? <p className="mt-2 text-body text-muted">{t("sitting.submit.flagged", { numbers: flaggedNumbers })}</p> : null}
        <p className="mt-2 text-body text-muted">{t("sitting.submit.note")}</p>
        {failed ? (
          <p role="alert" className="mt-3 text-body text-danger">
            {t("sitting.submit.failed")}
          </p>
        ) : null}
        <div className="mt-5 flex justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="min-h-11 rounded-control border border-border px-4 text-text disabled:opacity-50"
          >
            {t("common.cancel")}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="min-h-11 rounded-control bg-accent px-4 font-semibold text-on-accent disabled:opacity-50"
          >
            {busy ? t("sitting.bar.saving") : t("sitting.submit.confirm")}
          </button>
        </div>
      </div>
    </div>
  );
}

