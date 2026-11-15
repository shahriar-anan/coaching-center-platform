"use client";

import { useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useBatch } from "@/lib/api/exam/offline-admin";
import {
  useConfirmRoutine,
  useDiscardRoutine,
  useEditRoutineRow,
  useInstructors,
  useMatchInstructor,
  useRoutine,
  type InstructorRow,
  type RoutineChange,
  type RoutineClassRef,
  type RoutineDifference,
  type RoutineRow,
  type RoutineView,
} from "@/lib/api/exam/offline-batch-pay";
import { useFormat } from "@/lib/format/use-format";
import { dhakaInstant, flagKey, kindKey, weekdayName } from "./batch-pay-format";
import { InstructorChangeDialog, type InstructorChoice } from "./instructor-change-dialog";
import { offlineErrorText } from "./offline-error";

/** The fields of a row a PATCH may change. */
type RowPatch = Partial<Record<"date" | "day" | "time" | "subject" | "topic" | "kind" | "instructorId", string | null>>;

/**
 * The import preview of one routine (plan 8.1, changed on 13 Nov 2026), a full page. The routine counts from the day it was uploaded: the
 * rows from that day on come first, and the earlier dates (still added, as earlier classes) are folded below. Each cell edits in place;
 * the instructor is the one the routine names, changed from its Change button. Flags only mark rows; a name that is not an instructor yet
 * is added when the routine is confirmed.
 */
export function RoutinePreview({ batchId, routineId }: { batchId: string; routineId: string }) {
  const { t } = useLocale();
  const { pickTitle, formatDate } = useFormat();
  const routine = useRoutine(routineId);
  const batch = useBatch(batchId);
  const instructors = useInstructors();

  if (routine.isPending || batch.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (routine.isError) {
    if (routine.error instanceof ApiError && routine.error.status === 404) {
      return <EmptyState title={t("batchPay.preview.notFound")} />;
    }
    return <ErrorState onRetry={() => routine.refetch()} error={routine.error} />;
  }

  const r = routine.data;
  const editable = r.status === "PREVIEW";
  const name = batch.data ? pickTitle({ titleEn: batch.data.nameEn, titleBn: batch.data.nameBn }) : "";
  // The year is left out for the current year, as the rest of the site writes dates.
  const from = formatDate(r.effectiveFrom, { year: "auto" });

  return (
    <div className="grid gap-6">
      <PageHeader
        title={t("batchPay.preview.title")}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffOffline.batches.title"), href: "/staff/offline-batches" },
          { label: name, href: `/staff/offline-batches/${batchId}?tab=routines` },
          { label: from },
        ]}
      />

      <Card padding="sm" className="grid gap-3">
        <div className="grid gap-1">
          <p className="font-semibold text-text">{t("batchPay.preview.fromLine", { date: from })}</p>
          {editable ? <p className="text-meta text-muted">{t("batchPay.preview.fromNote", { date: from })}</p> : null}
        </div>
        <dl className="grid grid-cols-3 gap-3">
          <Stat label={t("batchPay.preview.rows")} value={r.rowCount} />
          <Stat label={t("batchPay.preview.flagged")} value={r.flaggedCount} tone={r.flaggedCount > 0 ? "warn" : undefined} />
          <Stat label={t("batchPay.preview.unknown")} value={r.unmatchedNames.length} />
        </dl>
        {editable ? (
          <ConfirmBar routine={r} batchId={batchId} />
        ) : (
          <p className="text-meta text-muted">
            {r.status === "CONFIRMED" ? t("batchPay.preview.readOnlyConfirmed") : t("batchPay.preview.readOnlySuperseded")}
          </p>
        )}
      </Card>

      {r.difference ? <DifferenceView difference={r.difference} /> : null}

      {r.rows.length === 0 ? (
        <EmptyState title={t("batchPay.preview.emptyRows")} />
      ) : (
        <RowsList routine={r} editable={editable} instructors={instructors.data ?? []} />
      )}
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: "warn" | "danger" }) {
  const { formatNumber } = useFormat();
  const color = tone === "warn" ? "text-warn" : tone === "danger" ? "text-danger" : "text-text";
  return (
    <div className="grid gap-0.5">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className={`text-section font-semibold ${color}`}>{formatNumber(value)}</dd>
    </div>
  );
}

/** Confirm, discard, and the reason Confirm is off. */
function ConfirmBar({ routine, batchId }: { routine: RoutineView; batchId: string }) {
  const { t } = useLocale();
  const router = useRouter();
  const { toast } = useToast();
  const confirm = useConfirmRoutine(routine.id);
  const discard = useDiscardRoutine(routine.id);
  const [discarding, setDiscarding] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Names no instructor has do not block: confirming adds them as new instructors (the PDF assigns the instructors).
  // A wrong date blocks (15 Nov 2026): the rows say which; this line counts them in the reader's language. Other blockers (no time,
  // no subject) keep the API's own sentence.
  const wrongDates = routine.rows.filter(hasWrongDate).length;
  const reason = wrongDates > 0 ? t("batchPay.preview.fixDates", { count: wrongDates }) : routine.blockers.join(" ");
  const newNames = routine.unmatchedNames.map((name) => name.name).join(", ");

  async function onConfirm() {
    setError(null);
    try {
      await confirm.mutateAsync();
      toast({ title: t("batchPay.preview.confirmed"), tone: "ok" });
      router.push(`/staff/offline-batches/${batchId}?tab=routines`);
    } catch (failure) {
      setError(offlineErrorText(failure, t));
    }
  }

  return (
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center gap-3">
        <Button onClick={() => void onConfirm()} disabled={!routine.canConfirm} loading={confirm.isPending}>
          {t("batchPay.preview.confirm", { count: routine.rowCount })}
        </Button>
        <Button variant="secondary" onClick={() => setDiscarding(true)}>
          {t("batchPay.preview.discard")}
        </Button>
        {!routine.canConfirm && reason ? <p className="text-meta text-warn">{reason}</p> : null}
      </div>
      {newNames ? <p className="text-meta text-muted">{t("batchPay.preview.newInstructors", { names: newNames })}</p> : null}
      {error ? (
        <p role="alert" className="text-meta text-danger">
          {error}
        </p>
      ) : null}
      <ConfirmDialog
        open={discarding}
        onOpenChange={setDiscarding}
        title={t("batchPay.preview.discardTitle")}
        body={t("batchPay.preview.discardBody")}
        confirmLabel={t("batchPay.preview.discardConfirm")}
        tone="danger"
        onConfirm={async () => {
          try {
            await discard.mutateAsync();
            toast({ title: t("batchPay.preview.discarded"), tone: "ok" });
            router.push(`/staff/offline-batches/${batchId}?tab=routines`);
          } catch (failure) {
            throw new Error(offlineErrorText(failure, t));
          }
        }}
      />
    </div>
  );
}

/** A class with a result. The routine keeps it (results rule A1), so it is neither removed nor moved. */
function isKept(item: RoutineClassRef): boolean {
  return item.keptForResult === true;
}

/** What changes against the confirmed routine of the month: added, changed, cancelled, and the settled classes that stay as they are. */
function DifferenceView({ difference }: { difference: RoutineDifference }) {
  const { t } = useLocale();
  // A class that has a result is kept as it is: it is not counted as removed or moved (results rule A1), but is listed on its own.
  const kept = [...difference.cancelled, ...difference.settled, ...difference.changed.map((change) => change.before)].filter(isKept);
  const removed = difference.cancelled.filter((item) => !isKept(item));
  const moved = difference.changed.filter((change) => !isKept(change.before));
  const settled = difference.settled.filter((item) => !isKept(item));
  const nothingMoves = difference.added.length === 0 && moved.length === 0 && removed.length === 0 && settled.length === 0 && kept.length === 0;
  return (
    <Card className="grid gap-4" aria-labelledby="routine-diff">
      <h3 id="routine-diff" className="text-card-title text-text">
        {t("batchPay.preview.diff.title")}
      </h3>
      {nothingMoves ? <p className="text-meta text-muted">{t("batchPay.preview.diff.none")}</p> : null}
      {difference.added.length > 0 ? (
        <DiffGroup title={t("batchPay.preview.diff.added", { count: difference.added.length })} tone="text-ok-text">
          {difference.added.map((row) => (
            <li key={`add-${row.index}`}>
              <AddedLine row={row} />
            </li>
          ))}
        </DiffGroup>
      ) : null}
      {moved.length > 0 ? (
        <DiffGroup title={t("batchPay.preview.diff.changed", { count: moved.length })} tone="text-accent-text">
          {moved.map((change) => (
            <li key={`chg-${change.after.index}`}>
              <ChangeLine change={change} />
            </li>
          ))}
        </DiffGroup>
      ) : null}
      {removed.length > 0 ? (
        <DiffGroup title={t("batchPay.preview.diff.cancelled", { count: removed.length })} tone="text-danger">
          {removed.map((item) => (
            <li key={`can-${item.id}`} className="line-through text-muted">
              <ClassRefText item={item} />
            </li>
          ))}
        </DiffGroup>
      ) : null}
      {settled.length > 0 ? (
        <DiffGroup title={t("batchPay.preview.diff.settled", { count: settled.length })} tone="text-muted">
          {settled.map((item) => (
            <li key={`set-${item.id}`} className="flex flex-wrap items-center gap-2 text-muted">
              <ClassRefText item={item} />
              <Pill tone="muted">{t("batchPay.preview.diff.locked")}</Pill>
            </li>
          ))}
        </DiffGroup>
      ) : null}
      {kept.length > 0 ? (
        <DiffGroup title={t("batchPay.preview.diff.kept", { count: kept.length })} tone="text-accent-text">
          {kept.map((item) => (
            <li key={`kept-${item.id}`} className="flex flex-wrap items-center gap-2">
              <ClassRefText item={item} />
              <Pill tone="accent">{t("batchPay.preview.diff.keptPill")}</Pill>
            </li>
          ))}
        </DiffGroup>
      ) : null}
      {difference.unchanged > 0 ? (
        <p className="text-meta text-muted">{t("batchPay.preview.diff.unchanged", { count: difference.unchanged })}</p>
      ) : null}
    </Card>
  );
}

function DiffGroup({ title, tone, children }: { title: string; tone: string; children: ReactNode }) {
  return (
    <div className="grid gap-1.5">
      <h4 className={`text-label font-semibold ${tone}`}>{title}</h4>
      <ul className="grid gap-1 text-body text-text">{children}</ul>
    </div>
  );
}

function AddedLine({ row }: { row: RoutineRow }) {
  const { formatDate } = useFormat();
  return (
    <span>
      {row.date ? formatDate(row.date) : "—"} · {row.time ?? "—"} · {row.subject ?? ""}
      {row.instructorName ? ` · ${row.instructorName}` : ""}
    </span>
  );
}

function ChangeLine({ change }: { change: RoutineChange }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const after = change.after;
  return (
    <span className="grid gap-0.5">
      <span className="text-muted">
        {t("batchPay.preview.diff.was")}: <ClassRefText item={change.before} />
      </span>
      <span>
        {t("batchPay.preview.diff.now")}: {formatDate(after.date ?? change.before.date)} · {after.time ?? "—"} · {after.subject ?? ""}
        {after.instructorName ? ` · ${after.instructorName}` : ""}
      </span>
    </span>
  );
}

function ClassRefText({ item }: { item: RoutineClassRef }) {
  const { formatDate, formatTime } = useFormat();
  const when = item.startsAt ? formatTime(dhakaInstant(item.date, item.startsAt)) : "—";
  return (
    <span>
      {formatDate(item.date)} · {when} · {item.subject ?? ""}
    </span>
  );
}

/** A name for matching, as the API matches names: trimmed, inner spaces collapsed, case ignored. */
function nameKey(name: string): string {
  return name.trim().replace(/\s+/g, " ").toLowerCase();
}

/**
 * The instructors of this routine, the quick picks of the Change dialog: the instructors its rows are assigned to, then the names it
 * writes that are not instructors yet (added on confirm, or at once when picked).
 */
function routineChoices(rows: RoutineRow[]): InstructorChoice[] {
  const choices: InstructorChoice[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    const name = row.instructorId ? (row.instructorName ?? row.instructorText ?? "") : (row.instructorText ?? "");
    const seenKey = row.instructorId ?? `new:${nameKey(name)}`;
    if (!name.trim() || seen.has(seenKey)) {
      continue;
    }
    seen.add(seenKey);
    choices.push({ id: row.instructorId, name });
  }
  return choices.sort((a, b) => Number(a.id == null) - Number(b.id == null) || a.name.localeCompare(b.name));
}

/**
 * The rows grouped by date. The rows from the day the routine counts from come first; the earlier dates are folded below them, since
 * they are added as earlier classes and are not what the routine is about. Each row is a line of cells on a wide screen and a stacked
 * card on a phone.
 */
function RowsList({ routine, editable, instructors }: { routine: RoutineView; editable: boolean; instructors: InstructorRow[] }) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const edit = useEditRoutineRow(routine.id);
  const match = useMatchInstructor(routine.id);
  const [errors, setErrors] = useState<Record<number, string>>({});
  const [changing, setChanging] = useState<RoutineRow | null>(null);
  const [showEarlier, setShowEarlier] = useState(false);
  const isEarlier = (row: RoutineRow) => row.date != null && row.date < routine.effectiveFrom;
  const current = routine.rows.filter((row) => !isEarlier(row));
  const earlier = routine.rows.filter(isEarlier);
  // An earlier row with a wrong date (the typo case) is not folded away: the admin must see it to fix it.
  const earlierNeedsFix = earlier.some(hasWrongDate);

  const groups = (rows: RoutineRow[]) =>
    groupByDate(rows).map((group) => (
      <section key={`${group.key}-${group.rows[0]?.index ?? 0}`} aria-label={group.key ? formatDate(group.key) : "—"} className="grid gap-2">
        <h4 className="text-label font-semibold text-muted">
          {group.key ? `${formatDate(group.key)} · ${weekdayName(group.key, locale)}` : "—"}
        </h4>
        <ul className="grid gap-2">
          {group.rows.map((row) => (
            <li key={row.index}>
              <RowLine
                row={row}
                editable={editable}
                error={errors[row.index]}
                onPatch={(body) => void patch(row.index, body)}
                onChangeInstructor={() => setChanging(row)}
              />
            </li>
          ))}
        </ul>
      </section>
    ));

  async function patch(index: number, body: RowPatch) {
    setErrors((current) => {
      const next = { ...current };
      delete next[index];
      return next;
    });
    try {
      await edit.mutateAsync({ index, body });
    } catch (failure) {
      const field = failure instanceof ApiError ? failure.fieldErrors?.[0]?.message : undefined;
      setErrors((current) => ({ ...current, [index]: field ?? offlineErrorText(failure, t) }));
    }
  }

  return (
    <div className="grid gap-5">
      <div
        aria-hidden="true"
        className="hidden text-meta font-semibold text-text xl:grid xl:grid-cols-[8.5rem_6rem_9.5rem_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_8.5rem] xl:gap-2 xl:px-3"
      >
        <span>{t("batchPay.preview.columns.date")}</span>
        <span>{t("batchPay.preview.columns.day")}</span>
        <span>{t("batchPay.preview.columns.time")}</span>
        <span>{t("batchPay.preview.columns.subject")}</span>
        <span>{t("batchPay.preview.columns.topic")}</span>
        <span>{t("batchPay.preview.columns.instructor")}</span>
        <span>{t("batchPay.preview.columns.kind")}</span>
      </div>
      {current.length > 0 ? groups(current) : <p className="text-meta text-muted">{t("batchPay.preview.noCurrentRows")}</p>}
      {earlier.length > 0 ? (
        <section aria-labelledby="earlier-rows" className="grid gap-3 rounded-card border border-border bg-bg/60 p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="grid gap-0.5">
              <h3 id="earlier-rows" className="text-label font-bold text-text">
                {t("batchPay.preview.earlierTitle", { count: earlier.length })}
              </h3>
              <p className="text-meta text-muted">{t("batchPay.preview.earlierNote", { date: formatDate(routine.effectiveFrom) })}</p>
            </div>
            <Button size="sm" variant="secondary" aria-expanded={showEarlier} onClick={() => setShowEarlier((open) => !open)}>
              {showEarlier ? t("batchPay.preview.hideEarlier") : t("batchPay.preview.showEarlier")}
            </Button>
          </div>
          {showEarlier || earlierNeedsFix ? groups(earlier) : null}
        </section>
      ) : null}
      {changing ? (
        <InstructorChangeDialog
          open
          onOpenChange={(open) => {
            if (!open) {
              setChanging(null);
            }
          }}
          asPerRoutine={changing.instructorText || null}
          currentId={changing.instructorId}
          currentName={changing.instructorName}
          choices={routineChoices(routine.rows)}
          instructors={instructors}
          onSave={async (instructorId) => {
            if (!changing.instructorId && changing.instructorText && instructorId) {
              // A name the routine writes that is not an instructor yet: every row with that name takes the instructor, and the name
              // is kept as their alias, so the next routine matches it on its own.
              await match.mutateAsync({ name: changing.instructorText, instructorId });
            } else {
              // A row that has an instructor changes on its own (a substitute for that class).
              await edit.mutateAsync({ index: changing.index, body: { instructorId } });
            }
          }}
        />
      ) : null}
    </div>
  );
}

function groupByDate(rows: RoutineRow[]): { key: string; rows: RoutineRow[] }[] {
  const groups: { key: string; rows: RoutineRow[] }[] = [];
  for (const row of rows) {
    const key = row.date ?? "";
    const last = groups[groups.length - 1];
    if (last && last.key === key) {
      last.rows.push(row);
    } else {
      groups.push({ key, rows: [row] });
    }
  }
  return groups;
}

function RowLine({
  row,
  editable,
  error,
  onPatch,
  onChangeInstructor,
}: {
  row: RoutineRow;
  editable: boolean;
  error: string | undefined;
  onPatch: (body: RowPatch) => void;
  onChangeInstructor: () => void;
}) {
  const { t, locale } = useLocale();
  const { formatDate } = useFormat();
  const flagged = row.flags.length > 0;
  const dayText = row.day ?? (row.date ? weekdayName(row.date, locale) : "");
  const dateText = row.date ? formatDate(row.date) : "—";

  return (
    <div
      className={`grid gap-2 rounded-control border px-3 py-2 sm:grid-cols-4 xl:grid-cols-[8.5rem_6rem_9.5rem_minmax(0,1.2fr)_minmax(0,1.2fr)_minmax(0,1.2fr)_8.5rem] xl:items-center xl:gap-2 ${
        flagged ? "border-warn/50 bg-warn/5" : "border-border-control bg-card"
      }`}
    >
      <Cell label={t("batchPay.preview.columns.date")}>
        <EditableText
          label={t("batchPay.preview.columns.date")}
          value={row.date ?? ""}
          display={dateText}
          type="date"
          disabled={!editable}
          onCommit={(next) => onPatch({ date: next })}
        />
      </Cell>
      <Cell label={t("batchPay.preview.columns.day")}>
        <EditableText
          label={t("batchPay.preview.columns.day")}
          value={dayText}
          display={dayText || "—"}
          disabled={!editable}
          onCommit={(next) => onPatch({ day: next })}
        />
      </Cell>
      <Cell label={t("batchPay.preview.columns.time")}>
        <EditableText
          label={t("batchPay.preview.columns.time")}
          value={row.time ?? ""}
          display={row.time ?? "—"}
          disabled={!editable}
          onCommit={(next) => onPatch({ time: next })}
        />
      </Cell>
      <Cell label={t("batchPay.preview.columns.subject")}>
        <EditableText
          label={t("batchPay.preview.columns.subject")}
          value={row.subject ?? ""}
          display={row.subject ?? "—"}
          disabled={!editable}
          onCommit={(next) => onPatch({ subject: next })}
        />
      </Cell>
      <Cell label={t("batchPay.preview.columns.topic")}>
        <EditableText
          label={t("batchPay.preview.columns.topic")}
          value={row.topic ?? ""}
          display={row.topic ?? "—"}
          disabled={!editable}
          onCommit={(next) => onPatch({ topic: next })}
        />
      </Cell>
      <Cell label={t("batchPay.preview.columns.instructor")}>
        <InstructorCell
          asPerRoutine={row.instructorText}
          assignedName={row.instructorName}
          assignedId={row.instructorId}
          canChange={editable}
          onChange={onChangeInstructor}
        />
      </Cell>
      <Cell label={t("batchPay.preview.columns.kind")}>
        {editable ? (
          <Select
            aria-label={t("batchPay.preview.columns.kind")}
            className="h-9 py-1 text-meta"
            value={row.kind}
            onChange={(event) => onPatch({ kind: event.target.value })}
          >
            {(["LECTURE", "EXAM", "OTHER"] as const).map((kind) => (
              <option key={kind} value={kind}>
                {t(kindKey(kind))}
              </option>
            ))}
          </Select>
        ) : (
          <span>{t(kindKey(row.kind))}</span>
        )}
      </Cell>
      {flagged ? (
        <ul className="grid gap-0.5 text-meta text-warn sm:col-span-4 xl:col-span-7">
          {row.flags.map((flag) => {
            const key = flagKey(flag.code);
            return <li key={flag.code}>{key ? t(key) : flag.reason}</li>;
          })}
        </ul>
      ) : null}
      {error ? (
        <p role="alert" className="text-meta text-danger sm:col-span-4 xl:col-span-7">
          {error}
        </p>
      ) : null}
    </div>
  );
}

/**
 * Who teaches the class: the instructor assigned, and the name the routine writes when it differs ("As per routine: Kamal Hossain"). A
 * name that is not an instructor yet shows as new: it is added when the routine is confirmed. Change opens the instructor dialog.
 */
export function InstructorCell({
  asPerRoutine,
  assignedName,
  assignedId,
  canChange,
  onChange,
}: {
  asPerRoutine: string | null;
  assignedName: string | null;
  assignedId: string | null;
  canChange: boolean;
  onChange: () => void;
}) {
  const { t } = useLocale();
  const routineName = asPerRoutine?.trim() ? asPerRoutine.trim() : null;
  const shown = assignedId ? (assignedName ?? routineName ?? "") : routineName ? t("batchPay.preview.newName", { name: routineName }) : null;
  const differs = Boolean(assignedId && routineName && assignedName && nameKey(assignedName) !== nameKey(routineName));
  return (
    <span className="grid min-w-0 justify-items-start gap-0.5">
      {shown ? (
        <span className="min-w-0 break-words font-semibold text-text">{shown}</span>
      ) : (
        <span className="text-muted">{t("batchPay.preview.noInstructor")}</span>
      )}
      {differs ? (
        <span className="min-w-0 break-words text-meta text-muted">{t("batchPay.instructor.asPerRoutineShort", { name: routineName ?? "" })}</span>
      ) : null}
      {canChange ? (
        <Button size="sm" variant="ghost" className="min-h-8 px-1.5 text-text-accent" onClick={onChange}>
          {shown ? t("batchPay.instructor.change") : t("batchPay.instructor.assign")}
        </Button>
      ) : null}
    </span>
  );
}

/** A cell: its label shows on a phone only; the column header does the job on a wide screen. */
function Cell({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid min-w-0 gap-0.5">
      <span className="text-meta text-muted xl:hidden">{label}</span>
      <div className="min-w-0 text-body text-text">{children}</div>
    </div>
  );
}

/**
 * A text cell that edits in place: it shows its value, and a click turns it into an input. Enter or leaving the field saves a changed
 * value; Escape drops the change.
 */
function EditableText({
  label,
  value,
  display,
  type = "text",
  disabled,
  onCommit,
}: {
  label: string;
  value: string;
  display: string;
  type?: string;
  disabled?: boolean;
  onCommit: (next: string) => void;
}) {
  const [draft, setDraft] = useState<string | null>(null);
  // Set once a value is committed or dropped, so the blur that follows Enter or Escape does not save twice.
  const closing = useRef(false);

  if (draft === null || disabled) {
    return (
      <button
        type="button"
        disabled={disabled}
        onClick={() => {
          closing.current = false;
          setDraft(value);
        }}
        className="min-h-9 max-w-full truncate rounded-control px-1 text-start hover:bg-bg disabled:cursor-default disabled:hover:bg-transparent"
      >
        {display}
      </button>
    );
  }

  function finish() {
    if (closing.current) {
      return;
    }
    closing.current = true;
    const next = draft ?? "";
    setDraft(null);
    if (next !== value) {
      onCommit(next);
    }
  }

  function onKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter") {
      event.preventDefault();
      finish();
    } else if (event.key === "Escape") {
      closing.current = true;
      setDraft(null);
    }
  }

  return (
    <TextInput
      aria-label={label}
      type={type}
      autoFocus
      className="h-9 py-1 text-meta"
      value={draft}
      onChange={(event) => setDraft(event.target.value)}
      onBlur={finish}
      onKeyDown={onKey}
    />
  );
}

/** The flags that block a confirm because the date itself is wrong. A weekday mismatch only warns. */
const WRONG_DATE_FLAGS = ["DATE_UNREADABLE", "DATE_OUT_OF_ORDER", "DATE_OUTSIDE_BATCH"];

function hasWrongDate(row: RoutineRow): boolean {
  return row.flags.some((flag) => WRONG_DATE_FLAGS.includes(flag.code));
}
