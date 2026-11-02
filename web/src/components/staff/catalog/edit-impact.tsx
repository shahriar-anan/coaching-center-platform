"use client";

import { useLocale } from "@/i18n/locale-context";
import type { StaffExam } from "@/lib/api/exam/staff-catalog";

/** What an edit touches. `replace` is the Word upload, which also sets the exam time, so students may be told about it. */
export type EditKind = "maximum" | "questions" | "paper" | "replace";

type ExamFacts = Pick<StaffExam, "status" | "publishedAt" | "started">;

/** A published exam, or a withdrawn one that was ever published, keeps the edit as a staged change (D5). */
function isStaged(exam: ExamFacts) {
  return exam.status === "PUBLISHED" || (exam.status === "ARCHIVED" && Boolean(exam.publishedAt));
}

/**
 * Whether an edit can reach students, so staff are asked first. A draft nobody has sat is still being written, and asking on
 * every question would only get in the way. A restored draft can have attempts, so `started` counts as well as the status.
 */
export function editNeedsPrompt(exam: ExamFacts): boolean {
  return isStaged(exam) || exam.started === true;
}

/** The consequences of an edit, as message keys, in reading order. Follows D5, D6, D9, D10, D15, D16, D17, D19 and E29. */
export function editImpactKeys(
  exam: ExamFacts,
  kind: EditKind,
  { masterAdmin, totalChanges }: { masterAdmin: boolean; totalChanges: boolean },
): string[] {
  const started = exam.started === true;
  const keys: string[] = [];
  if (isStaged(exam)) {
    keys.push(masterAdmin ? "stagedMaster" : "stagedSystem");
  } else {
    keys.push("draft");
  }
  if (exam.status === "ARCHIVED") {
    keys.push("withdrawn");
  }
  keys.push(started ? (kind === "maximum" ? "startedMaximum" : kind === "paper" ? "startedPaper" : "startedQuestions") : "notStarted");
  keys.push("nextAttempts");
  if (started && totalChanges) {
    keys.push("leaderboard");
  }
  if (kind !== "replace") {
    keys.push("noNotice");
  }
  return keys.map((key) => `staffCatalog.editImpact.${key}`);
}

/** The consequences as a list, for the dialog that asks before the edit is saved. */
export function EditImpactNotes({
  exam,
  kind,
  masterAdmin,
  totalChanges = false,
}: {
  exam: ExamFacts;
  kind: EditKind;
  masterAdmin: boolean;
  totalChanges?: boolean;
}) {
  const { t } = useLocale();
  return (
    <ul className="grid list-disc gap-2 ps-5 text-body">
      {editImpactKeys(exam, kind, { masterAdmin, totalChanges }).map((key) => (
        <li key={key}>{t(key)}</li>
      ))}
    </ul>
  );
}
