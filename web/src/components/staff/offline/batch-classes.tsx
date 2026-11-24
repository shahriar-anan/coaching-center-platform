"use client";

import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useListParams } from "@/lib/url/list-params";
import { useBatchClasses, useDeleteClass, useEditClass, useInstructors, type BatchClass, type ClassStatus } from "@/lib/api/exam/offline-batch-pay";
import { ApiError } from "@/lib/api/errors";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { offlineErrorText } from "./offline-error";
import { useFormat } from "@/lib/format/use-format";
import { classStatusKey, classStatusTone, dhakaInstant } from "./batch-pay-format";
import { AddClassDialog } from "./add-class-dialog";
import { ClassEditDialog } from "./class-dialogs";
import { InstructorChangeDialog, type InstructorChoice } from "./instructor-change-dialog";
import { InstructorCell } from "./routine-preview";

// 24 Nov: classes carry no pay. Paid and not-taken are classes settled before then, kept for the record.
const STATUSES: ClassStatus[] = ["SCHEDULED", "HELD", "PAID", "NOT_TAKEN"];

type OpenDialog = { kind: "edit" | "instructor" | "delete"; item: BatchClass };

/** The instructors who teach this batch's classes, the quick picks of the instructor dialog. */
function batchChoices(items: BatchClass[]): InstructorChoice[] {
  const seen = new Set<string>();
  const choices: InstructorChoice[] = [];
  for (const item of items) {
    if (item.instructorId && !seen.has(item.instructorId)) {
      seen.add(item.instructorId);
      choices.push({ id: item.instructorId, name: item.instructorName ?? item.instructorText ?? "" });
    }
  }
  return choices.sort((a, b) => a.name.localeCompare(b.name));
}

/**
 * The classes of a batch (plan 8.1). The filters (month, instructor, status) live in the URL. The list is grouped by date; a class is a
 * row on a wide screen and a card on a phone. Since 24 Nov a class carries no pay: instructors are paid on their own page. A class
 * settled before then is read only.
 */
export function BatchClasses({ batchId, archived = false, classMinutes = 60 }: { batchId: string; archived?: boolean; classMinutes?: number }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const role = useRole();
  const [params, setParams] = useListParams({ month: "", instructor: "", status: "" });
  const classes = useBatchClasses(batchId, {
    month: params.month || undefined,
    instructorId: params.instructor || undefined,
    status: params.status || undefined,
  });
  const instructors = useInstructors();
  const edit = useEditClass();
  const remove = useDeleteClass();
  const { toast } = useToast();
  const canDelete = role.canUploadRoutine && !archived;
  const [dialog, setDialog] = useState<OpenDialog | null>(null);
  const [adding, setAdding] = useState(false);

  if (classes.isPending) {
    return <Skeleton variant="rows" count={5} />;
  }
  if (classes.isError) {
    return <ErrorState onRetry={() => classes.refetch()} error={classes.error} />;
  }

  const groups = groupByDate(classes.data);

  return (
    <div className="grid gap-4">
      {role.canUploadRoutine && !archived ? (
        <div className="flex justify-end">
          <Button onClick={() => setAdding(true)}>{t("batchPay.classes.add.button")}</Button>
        </div>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="grid gap-1.5 text-label font-medium text-text">
          {t("batchPay.classes.filters.month")}
          <TextInput type="month" value={params.month} onChange={(event) => setParams({ month: event.target.value })} />
        </label>
        <label className="grid gap-1.5 text-label font-medium text-text">
          {t("batchPay.classes.filters.instructor")}
          <Select value={params.instructor} onChange={(event) => setParams({ instructor: event.target.value })}>
            <option value="">{t("batchPay.classes.filters.allInstructors")}</option>
            {(instructors.data ?? []).map((instructor) => (
              <option key={instructor.id} value={instructor.id}>
                {instructor.name}
              </option>
            ))}
          </Select>
        </label>
        <label className="grid gap-1.5 text-label font-medium text-text">
          {t("batchPay.classes.filters.status")}
          <Select value={params.status} onChange={(event) => setParams({ status: event.target.value })}>
            <option value="">{t("batchPay.classes.filters.allStatuses")}</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {t(classStatusKey(status))}
              </option>
            ))}
          </Select>
        </label>
      </div>

      {groups.length === 0 ? (
        <EmptyState title={t("batchPay.classes.empty")} body={t("batchPay.classes.emptyBody")} />
      ) : (
        <>
          <div
            aria-hidden="true"
            className="hidden text-meta font-semibold text-text sm:grid sm:grid-cols-[9rem_minmax(0,1.6fr)_minmax(0,1.2fr)_7.5rem_auto] sm:gap-3 sm:px-3"
          >
            <span>{t("batchPay.preview.columns.time")}</span>
            <span>{t("batchPay.preview.columns.subject")}</span>
            <span>{t("batchPay.preview.columns.instructor")}</span>
            <span>{t("batchPay.classes.filters.status")}</span>
            <span />
          </div>
          {groups.map((group) => (
            <section key={group.date} aria-label={formatDate(group.date, { weekday: true })} className="grid gap-2">
              <h3 className="text-label font-semibold text-muted">{formatDate(group.date, { weekday: true })}</h3>
              <ul className="grid gap-2">
                {group.items.map((item) => (
                  <li key={item.id}>
                    <ClassRow item={item} canDelete={canDelete} onAction={(kind) => setDialog({ kind, item })} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </>
      )}

      {adding ? (
        <AddClassDialog
          batchId={batchId}
          classMinutes={classMinutes}
          open
          onOpenChange={(open) => !open && setAdding(false)}
          onAdded={(heldOn) => setParams({ month: heldOn.slice(0, 7) })}
        />
      ) : null}
      {dialog?.kind === "edit" ? (
        <ClassEditDialog item={dialog.item} open onOpenChange={(open) => !open && setDialog(null)} />
      ) : null}
      {dialog?.kind === "instructor" ? (
        <InstructorChangeDialog
          open
          onOpenChange={(open) => !open && setDialog(null)}
          asPerRoutine={dialog.item.instructorText}
          currentId={dialog.item.instructorId}
          currentName={dialog.item.instructorName}
          choices={batchChoices(classes.data)}
          instructors={instructors.data ?? []}
          allowNone
          onSave={async (instructorId) => {
            await edit.mutateAsync({ id: dialog.item.id, body: { version: dialog.item.version, instructorId } });
          }}
        />
      ) : null}
      {dialog?.kind === "delete" ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => !open && setDialog(null)}
          title={t("batchPay.classes.delete.title", { subject: dialog.item.subject, date: formatDate(dialog.item.heldOn) })}
          body={t("batchPay.classes.delete.body")}
          confirmLabel={t("batchPay.classes.delete.confirm")}
          tone="danger"
          onConfirm={async () => {
            try {
              await remove.mutateAsync(dialog.item.id);
            } catch (error) {
              // The dialog shows the message it is given, so it gets this page's own words for the two refusals.
              throw new Error(
                error instanceof ApiError && error.code === "OFFLINE_CLASS_HAS_RESULT"
                  ? t("batchPay.classes.delete.hasResult")
                  : error instanceof ApiError && error.status === 409
                    ? t("batchPay.classes.delete.settled")
                    : offlineErrorText(error, t),
              );
            }
            toast({ title: t("batchPay.classes.delete.done"), tone: "ok" });
          }}
        />
      ) : null}
    </div>
  );
}

function groupByDate(items: BatchClass[]): { date: string; items: BatchClass[] }[] {
  const groups: { date: string; items: BatchClass[] }[] = [];
  for (const item of items) {
    const last = groups[groups.length - 1];
    if (last && last.date === item.heldOn) {
      last.items.push(item);
    } else {
      groups.push({ date: item.heldOn, items: [item] });
    }
  }
  return groups;
}

/** One class: time, subject and topic, instructor, status, and the actions its status allows. */
function ClassRow({ item, canDelete, onAction }: { item: BatchClass; canDelete: boolean; onAction: (kind: OpenDialog["kind"]) => void }) {
  const { t } = useLocale();
  const { formatTimeRange } = useFormat();
  const open = item.status === "SCHEDULED" || item.status === "HELD";
  const time = item.startsAt
    ? formatTimeRange(dhakaInstant(item.heldOn, item.startsAt), dhakaInstant(item.heldOn, item.endsAt ?? item.startsAt))
    : "—";

  return (
    <div className="grid gap-2 rounded-card border border-border-control bg-card px-3 py-3 sm:grid-cols-[9rem_minmax(0,1.6fr)_minmax(0,1.2fr)_7.5rem_auto] sm:items-center sm:gap-3">
      <Cell label={t("batchPay.preview.columns.time")}>
        <span className="whitespace-nowrap">{time}</span>
      </Cell>
      <Cell label={t("batchPay.preview.columns.subject")}>
        <span className="grid min-w-0">
          <span className="break-words font-medium text-text">{item.subject}</span>
          {item.topic ? <span className="break-words text-meta text-muted">{item.topic}</span> : null}
        </span>
      </Cell>
      <Cell label={t("batchPay.preview.columns.instructor")}>
        <InstructorCell
          asPerRoutine={item.instructorText}
          assignedName={item.instructorName}
          assignedId={item.instructorId}
          canChange={open}
          onChange={() => onAction("instructor")}
        />
      </Cell>
      <Cell label={t("batchPay.classes.filters.status")}>
        <span className="grid justify-items-start gap-1">
          <Pill tone={classStatusTone(item.status)}>{t(classStatusKey(item.status))}</Pill>
          {item.status === "CANCELLED" ? <span className="text-meta text-muted">{t("batchPay.classes.cancelledNote")}</span> : null}
        </span>
      </Cell>
      <div className="flex flex-wrap items-center justify-start gap-2 sm:justify-end">
        <Actions item={item} onAction={onAction} />
        {/* 16 Nov: a class can be deleted unless it was settled before 24 Nov. */}
        {canDelete && open ? (
          <Button size="sm" variant="ghost" onClick={() => onAction("delete")}>
            {t("batchPay.classes.delete.button")}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

function Actions({ item, onAction }: { item: BatchClass; onAction: (kind: OpenDialog["kind"]) => void }) {
  const { t } = useLocale();
  if (item.status === "SCHEDULED" || item.status === "HELD") {
    return (
      <Button size="sm" variant="secondary" onClick={() => onAction("edit")}>
        {t("batchPay.classes.actions.edit")}
      </Button>
    );
  }
  // Classes settled before 24 Nov and cancelled classes are read only.
  return <span className="text-meta text-muted">{t("batchPay.classes.actions.readOnly")}</span>;
}

function Cell({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid min-w-0 gap-0.5 text-body text-text">
      <span className="text-meta text-muted sm:hidden">{label}</span>
      {children}
    </div>
  );
}

