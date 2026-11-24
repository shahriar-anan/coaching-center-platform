"use client";

import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import type { InstructorRow } from "@/lib/api/exam/offline-batch-pay";
import { offlineErrorText } from "./offline-error";

/** One instructor to choose: an existing instructor (with an id), or a name the routine uses that is not in the list. */
export type InstructorChoice = { id: string | null; name: string };

/** A name for matching: trimmed, inner spaces collapsed, case ignored (the API matches names the same way). */
function key(name: string): string {
  return name.trim().replace(/\s+/g, " ").toLowerCase();
}

/** The existing instructor a typed name means, by name or alias, or null when it is a new name. */
function existingByName(name: string, instructors: InstructorRow[]): InstructorRow | null {
  const wanted = key(name);
  return (
    instructors.find((instructor) => key(instructor.name) === wanted || (instructor.aliases ?? []).some((alias) => key(alias) === wanted)) ??
    null
  );
}

type Picked = { kind: "choice"; index: number } | { kind: "other" } | { kind: "none" };

/**
 * Changes who teaches a class, the same dialog on the routine preview and on the Classes tab (13 Nov 2026). It says first who the routine
 * names ("Instructor as per routine: Kamal Hossain"), then offers the instructors of that routine to pick from, or a name to type. A typed
 * name that an instructor already has (by name or alias) picks that instructor. Since 24 Nov a name that is not in the list is refused:
 * only the Master Admin adds instructors, on Offline instructors. `allowNone` offers "No instructor".
 */
export function InstructorChangeDialog({
  open,
  onOpenChange,
  asPerRoutine,
  currentId,
  currentName,
  choices,
  instructors,
  allowNone = false,
  onSave,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The name the routine writes for this class, or null when it names nobody. */
  asPerRoutine: string | null | undefined;
  currentId: string | null | undefined;
  currentName: string | null | undefined;
  /** The instructors of this routine (or batch), the quick picks. */
  choices: InstructorChoice[];
  /** Every live instructor, to match a typed name. */
  instructors: InstructorRow[];
  allowNone?: boolean;
  /** Saves the instructor chosen (null for none). It throws on failure; the dialog shows the message. */
  onSave: (instructorId: string | null) => Promise<void>;
}) {
  const { t } = useLocale();
  const listId = useId();
  // The API leaves out empty fields, so a class with no instructor has none of these: treat a missing one as empty.
  const asPer = asPerRoutine || null;
  const startIndex = choices.findIndex((choice) => (currentId ? choice.id === currentId : asPer !== null && key(choice.name) === key(asPer)));
  const [picked, setPicked] = useState<Picked>(
    startIndex >= 0 ? { kind: "choice", index: startIndex } : currentId || !allowNone ? { kind: "other" } : { kind: "none" },
  );
  const [typed, setTyped] = useState(startIndex >= 0 ? "" : currentName ?? "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  /** The instructor a name means. A name no instructor has is refused, with a message that says who adds instructors. */
  function idForName(name: string): string {
    const existing = existingByName(name, instructors);
    if (!existing) {
      throw new Error(t("batchPay.instructor.notInList", { name: name.trim() }));
    }
    return existing.id;
  }

  async function save() {
    setError(null);
    let target: string | null = null;
    if (picked.kind === "other" && !typed.trim()) {
      setError(t("batchPay.instructor.typeName"));
      return;
    }
    setSaving(true);
    try {
      if (picked.kind === "choice") {
        const choice = choices[picked.index];
        target = choice.id ?? idForName(choice.name);
      } else if (picked.kind === "other") {
        target = idForName(typed);
      }
      await onSave(target);
      onOpenChange(false);
    } catch (failure) {
      // A name not in the list is this dialog's own refusal, with its own words; the API's refusals get the offline sentences.
      setError(failure instanceof Error && !(failure instanceof ApiError) ? failure.message : offlineErrorText(failure, t));
    } finally {
      setSaving(false);
    }
  }

  const radio = "size-4 shrink-0 accent-[var(--accent)]";
  const option = "flex min-h-11 cursor-pointer items-center gap-3 rounded-control border px-3 py-2";
  const optionTone = (active: boolean) => (active ? "border-accent bg-accent/10" : "border-border-control bg-card hover:bg-bg");

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("batchPay.instructor.title")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button onClick={() => void save()} loading={saving}>
            {t("batchPay.instructor.save")}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <div className="grid gap-0.5 rounded-control bg-bg px-3 py-2.5">
          <span className="text-meta text-muted">{t("batchPay.instructor.asPerRoutine")}</span>
          <span className="break-words font-semibold text-text">{asPerRoutine || t("batchPay.instructor.routineNone")}</span>
          {currentName && (!asPerRoutine || key(currentName) !== key(asPerRoutine)) ? (
            <span className="text-meta text-muted">{t("batchPay.instructor.teachingNow", { name: currentName })}</span>
          ) : null}
        </div>

        <fieldset className="grid gap-2">
          <legend className="mb-2 text-label font-semibold text-text">{t("batchPay.instructor.choose")}</legend>
          {choices.map((choice, index) => {
            const active = picked.kind === "choice" && picked.index === index;
            return (
              <label key={`${choice.id ?? "new"}-${choice.name}`} className={`${option} ${optionTone(active)}`}>
                <input
                  type="radio"
                  name="instructor-choice"
                  className={radio}
                  checked={active}
                  onChange={() => setPicked({ kind: "choice", index })}
                />
                <span className="min-w-0 flex-1 break-words text-text">{choice.name}</span>
                {choice.id ? null : <span className="text-meta text-muted">{t("batchPay.instructor.newTag")}</span>}
              </label>
            );
          })}

          <label className={`${option} ${optionTone(picked.kind === "other")} flex-wrap`}>
            <input
              type="radio"
              name="instructor-choice"
              className={radio}
              checked={picked.kind === "other"}
              onChange={() => setPicked({ kind: "other" })}
            />
            <span className="text-text">{t("batchPay.instructor.someoneElse")}</span>
          </label>
          {picked.kind === "other" ? (
            <div className="grid gap-1 ps-7">
              <TextInput
                aria-label={t("batchPay.instructor.typeLabel")}
                list={listId}
                value={typed}
                autoFocus
                placeholder={t("batchPay.instructor.typePlaceholder")}
                onChange={(event) => setTyped(event.target.value)}
              />
              <datalist id={listId}>
                {instructors.map((instructor) => (
                  <option key={instructor.id} value={instructor.name} />
                ))}
              </datalist>
              <span className="text-meta text-muted">
                {typed.trim() && !existingByName(typed, instructors)
                  ? t("batchPay.instructor.notInList", { name: typed.trim() })
                  : t("batchPay.instructor.typeHint")}
              </span>
            </div>
          ) : null}

          {allowNone ? (
            <label className={`${option} ${optionTone(picked.kind === "none")}`}>
              <input
                type="radio"
                name="instructor-choice"
                className={radio}
                checked={picked.kind === "none"}
                onChange={() => setPicked({ kind: "none" })}
              />
              <span className="text-text">{t("batchPay.instructor.none")}</span>
            </label>
          ) : null}
        </fieldset>

        {error ? (
          <p role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
      </div>
    </Dialog>
  );
}
