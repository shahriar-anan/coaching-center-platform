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
import { useBatchClasses, useEditClass, useInstructors, type BatchClass, type ClassStatus } from "@/lib/api/exam/offline-batch-pay";
import { useFormat } from "@/lib/format/use-format";
import { amountOf, classStatusKey, classStatusTone, dhakaInstant } from "./batch-pay-format";
import { methodLabelKey } from "./labels";
import { AddClassDialog } from "./add-class-dialog";
import { ClassEditDialog, ClassNotTakenDialog, ClassPayDialog } from "./class-dialogs";
import { InstructorChangeDialog, type InstructorChoice } from "./instructor-change-dialog";
import { InstructorCell } from "./routine-preview";

const STATUSES: ClassStatus[] = ["SCHEDULED", "HELD", "PAID_PENDING", "PAID", "NOT_TAKEN_PENDING", "NOT_TAKEN"];

type OpenDialog = { kind: "edit" | "pay" | "notTaken" | "instructor"; item: BatchClass };

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
 * row on a wide screen and a card on a phone. The row's actions follow its status.
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
            className="hidden text-meta font-semibold text-text sm:grid sm:grid-cols-[9rem_minmax(0,1.6fr)_minmax(0,1.2fr)_7.5rem_8rem_auto] sm:gap-3 sm:px-3"
          >
            <span>{t("batchPay.preview.columns.time")}</span>
            <span>{t("batchPay.preview.columns.subject")}</span>
            <span>{t("batchPay.preview.columns.instructor")}</span>
            <span>{t("batchPay.classes.filters.status")}</span>
            <span>{t("batchPay.classes.amount")}</span>
            <span />
          </div>
          {groups.map((group) => (
            <section key={group.date} aria-label={formatDate(group.date, { weekday: true })} className="grid gap-2">
              <h3 className="text-label font-semibold text-muted">{formatDate(group.date, { weekday: true })}</h3>
              <ul className="grid gap-2">
                {group.items.map((item) => (
                  <li key={item.id}>
                    <ClassRow item={item} onAction={(kind) => setDialog({ kind, item })} />
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
      {dialog?.kind === "pay" ? <ClassPayDialog item={dialog.item} open onOpenChange={(open) => !open && setDialog(null)} /> : null}
      {dialog?.kind === "notTaken" ? (
        <ClassNotTakenDialog item={dialog.item} open onOpenChange={(open) => !open && setDialog(null)} />
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

/** One class: time, subject and topic, instructor, amount, status, and the actions its status allows. */
function ClassRow({ item, onAction }: { item: BatchClass; onAction: (kind: OpenDialog["kind"]) => void }) {
  const { t } = useLocale();
  const { formatDate, formatMoney, formatTimeRange } = useFormat();
  const amount = amountOf(item.amount);
  const open = item.status === "SCHEDULED" || item.status === "HELD";
  const time = item.startsAt
    ? formatTimeRange(dhakaInstant(item.heldOn, item.startsAt), dhakaInstant(item.heldOn, item.endsAt ?? item.startsAt))
    : "—";

  return (
    <div className="grid gap-2 rounded-card border border-border-control bg-card px-3 py-3 sm:grid-cols-[9rem_minmax(0,1.6fr)_minmax(0,1.2fr)_7.5rem_8rem_auto] sm:items-center sm:gap-3">
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
      <Cell label={t("batchPay.classes.amount")}>
        <span className="grid gap-0.5">
          <span className="whitespace-nowrap">{amount === null ? t("batchPay.classes.noRate") : formatMoney(amount)}</span>
          {item.rateOverride != null ? <Pill tone="accent">{t("batchPay.classes.overridden")}</Pill> : null}
        </span>
      </Cell>
      <Cell label={t("batchPay.classes.filters.status")}>
        <span className="grid justify-items-start gap-1">
          <Pill tone={classStatusTone(item.status)}>{t(classStatusKey(item.status))}</Pill>
          {item.status === "PAID" && item.payment ? (
            <span className="text-meta text-muted">
              {t("batchPay.classes.paidInfo", { method: t(methodLabelKey(item.payment.method)), date: formatDate(item.payment.paidOn) })}
            </span>
          ) : null}
          {item.status === "CANCELLED" ? <span className="text-meta text-muted">{t("batchPay.classes.cancelledNote")}</span> : null}
        </span>
      </Cell>
      <div className="flex flex-wrap items-center justify-start gap-2 sm:justify-end">
        <Actions item={item} onAction={onAction} />
      </div>
    </div>
  );
}

function Actions({ item, onAction }: { item: BatchClass; onAction: (kind: OpenDialog["kind"]) => void }) {
  const { t } = useLocale();
  if (item.status === "SCHEDULED") {
    return (
      <Button size="sm" variant="secondary" onClick={() => onAction("edit")}>
        {t("batchPay.classes.actions.edit")}
      </Button>
    );
  }
  if (item.status === "HELD") {
    return (
      <>
        <Button size="sm" onClick={() => onAction("pay")}>
          {t("batchPay.classes.actions.recordPayment")}
        </Button>
        <Button size="sm" variant="secondary" onClick={() => onAction("notTaken")}>
          {t("batchPay.classes.actions.markNotTaken")}
        </Button>
        <Button size="sm" variant="ghost" onClick={() => onAction("edit")}>
          {t("batchPay.classes.actions.edit")}
        </Button>
      </>
    );
  }
  if (item.status === "PAID_PENDING" || item.status === "NOT_TAKEN_PENDING") {
    return <span className="text-meta text-muted">{t("batchPay.classes.actions.waiting")}</span>;
  }
  // Paid, not taken and cancelled classes are read only.
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

