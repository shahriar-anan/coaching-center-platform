"use client";

import Link from "next/link";
import { methodLabelKey } from "@/components/staff/offline/labels";
import { useId, useMemo, useState, type ReactNode } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Pill } from "@/components/ui/pill";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { MoneyPills } from "@/components/staff/offline/money-pills";
import {
  useApproveNotTakenMany,
  useApprovePayments,
  useRejectNotTaken,
  useRejectPayment,
  type BulkResult,
  type ClassRow,
} from "@/lib/api/exam/instructor-pay";
import { ClassPaymentDialog } from "./class-payment-dialog";
import { NotTakenDialog } from "./not-taken-dialog";
import { RejectDialog } from "./reject-dialog";
import {
  approvalIds,
  classEndIso,
  classStartIso,
  dueOf,
  groupByInstructor,
  hasAmount,
  waitingDecisionId,
  waitingPaymentId,
  type InstructorGroup,
} from "./pay-model";

export type ClassListMode = "settle" | "waiting";

type Dialogs =
  | { kind: "pay"; row: ClassRow }
  | { kind: "notTaken"; row: ClassRow }
  | { kind: "reject"; row: ClassRow }
  | { kind: "approve"; rows: ClassRow[] }
  | null;

/**
 * The classes grouped by instructor (plan 8.2). "To settle" shows held classes with Record payment and Mark not taken. "Waiting for
 * approval" shows the entries staff recorded; the Master Admin ticks rows and approves them after one confirmation that lists them,
 * or rejects one with a reason. Each group opens to its classes.
 */
export function GroupedClasses({
  rows,
  mode,
  batchNames,
  canSettle,
  canApprove,
}: {
  rows: ClassRow[];
  mode: ClassListMode;
  batchNames: Map<string, string>;
  canSettle: boolean;
  canApprove: boolean;
}) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();
  const { toast } = useToast();
  const approvePayments = useApprovePayments();
  const approveNotTaken = useApproveNotTakenMany();
  const rejectPayment = useRejectPayment();
  const rejectNotTaken = useRejectNotTaken();
  const groups = useMemo(() => groupByInstructor(rows), [rows]);
  // Settle groups start closed; waiting groups start open, since an approval needs to see each row.
  const [toggled, setToggled] = useState<Set<string>>(() => new Set());
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [dialog, setDialog] = useState<Dialogs>(null);
  const selectable = mode === "waiting" && canApprove;

  if (rows.length === 0) {
    return (
      <EmptyState
        title={t(mode === "settle" ? "instructorPay.empty.settleTitle" : "instructorPay.empty.waitingTitle")}
        body={t(mode === "settle" ? "instructorPay.empty.settleBody" : "instructorPay.empty.waitingBody")}
      />
    );
  }

  const chosen = rows.filter((row) => selected.has(row.id));
  const confirmCount = dialog?.kind === "approve" ? dialog.rows.length : 0;

  function groupKey(group: InstructorGroup): string {
    return group.instructorId ?? "none";
  }

  function isOpen(group: InstructorGroup): boolean {
    const key = groupKey(group);
    return mode === "waiting" ? !toggled.has(key) : toggled.has(key);
  }

  function toggleGroup(group: InstructorGroup) {
    const key = groupKey(group);
    setToggled((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function toggleRow(id: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  /**
   * Approves the picked rows after one confirmation: payments and not-taken marks go in one call each. The rows that failed stay
   * selected, and the dialog says how many went through.
   */
  async function approveRows(picked: ClassRow[]) {
    const { paymentIds, decisionIds } = approvalIds(picked);
    const idToRow = new Map<string, string>();
    for (const row of picked) {
      const key = waitingPaymentId(row) ?? waitingDecisionId(row);
      if (key) {
        idToRow.set(key, row.id);
      }
    }
    const results: BulkResult[] = [];
    if (paymentIds.length > 0) {
      results.push(...(await approvePayments.mutateAsync(paymentIds)));
    }
    if (decisionIds.length > 0) {
      results.push(...(await approveNotTaken.mutateAsync(decisionIds)));
    }
    const failed = results.filter((result) => !result.ok);
    const stillSelected = failed.map((result) => idToRow.get(result.id)).filter((id): id is string => Boolean(id));
    setSelected(new Set(stillSelected));
    if (failed.length > 0) {
      throw new Error(t("instructorPay.approve.partial", { done: results.length - failed.length, failed: failed.length }));
    }
    toast({ title: t("instructorPay.approve.done", { count: results.length }), tone: "ok" });
  }

  return (
    <div className="grid gap-3">
      {selectable && chosen.length > 0 ? (
        <div className="flex flex-wrap items-center gap-3 rounded-control border border-border-control bg-card px-3 py-2">
          <span className="text-body text-text">{t("instructorPay.approve.selected", { count: chosen.length })}</span>
          <Button size="sm" onClick={() => setDialog({ kind: "approve", rows: chosen })}>
            {t("instructorPay.approve.button", { count: chosen.length })}
          </Button>
        </div>
      ) : null}

      {groups.map((group) => {
        const open = isOpen(group);
        return (
          <GroupCard key={groupKey(group)} group={group} mode={mode} open={open} onToggle={() => toggleGroup(group)}>
            <ul>
              {group.classes.map((row) => (
                <ClassItem
                  key={row.id}
                  row={row}
                  mode={mode}
                  batchName={batchNames.get(row.batchId) ?? ""}
                  canSettle={canSettle}
                  canApprove={canApprove}
                  selectable={selectable}
                  selected={selected.has(row.id)}
                  onToggle={() => toggleRow(row.id)}
                  onPay={() => setDialog({ kind: "pay", row })}
                  onNotTaken={() => setDialog({ kind: "notTaken", row })}
                  onApprove={() => setDialog({ kind: "approve", rows: [row] })}
                  onReject={() => setDialog({ kind: "reject", row })}
                />
              ))}
            </ul>
          </GroupCard>
        );
      })}

      {dialog?.kind === "pay" ? (
        <ClassPaymentDialog
          key={dialog.row.id}
          row={dialog.row}
          batchName={batchNames.get(dialog.row.batchId) ?? ""}
          open
          onOpenChange={(open) => !open && setDialog(null)}
          canApprove={canApprove}
        />
      ) : null}
      {dialog?.kind === "notTaken" ? (
        <NotTakenDialog
          key={dialog.row.id}
          row={dialog.row}
          batchName={batchNames.get(dialog.row.batchId) ?? ""}
          open
          onOpenChange={(open) => !open && setDialog(null)}
          canApprove={canApprove}
        />
      ) : null}
      {dialog?.kind === "reject" ? (
        <RejectDialog
          key={dialog.row.id}
          open
          onOpenChange={(open) => !open && setDialog(null)}
          summary={`${dialog.row.instructorName ?? dialog.row.instructorText ?? ""} · ${dialog.row.subject} · ${formatMoney(dueOf(dialog.row)) ?? ""}`}
          onReject={async (reason) => {
            const paymentId = waitingPaymentId(dialog.row);
            const decisionId = waitingDecisionId(dialog.row);
            if (paymentId) {
              await rejectPayment.mutateAsync({ id: paymentId, reason });
            } else if (decisionId) {
              await rejectNotTaken.mutateAsync({ id: decisionId, reason });
            }
          }}
        />
      ) : null}
      <ConfirmDialog
        open={dialog?.kind === "approve"}
        onOpenChange={(open) => !open && setDialog(null)}
        title={t("instructorPay.approve.confirmTitle", { count: confirmCount })}
        body={t("instructorPay.approve.confirmBody")}
        confirmLabel={t("instructorPay.approve.confirmButton", { count: confirmCount })}
        onConfirm={async () => {
          if (dialog?.kind === "approve") {
            await approveRows(dialog.rows);
          }
        }}
      >
        {dialog?.kind === "approve" ? (
          <ul className="grid gap-1 text-body text-text">
            {dialog.rows.map((row) => (
              <li key={row.id}>
                {row.instructorName ?? row.instructorText ?? ""} · {row.heldOn} · {row.subject} ·{" "}
                {formatMoney(row.payment && row.status === "PAID_PENDING" ? row.payment.amount : dueOf(row)) ?? ""} ·{" "}
                {row.payment && row.status === "PAID_PENDING" && hasAmount(row)
                  ? `${t("instructorPay.row.ratePerClass", { amount: formatMoney(dueOf(row)) ?? "" })} · `
                  : ""}
                {row.status === "PAID_PENDING" ? t("instructorPay.row.paidBy") : t("instructorPay.row.notTakenBy")}
              </li>
            ))}
          </ul>
        ) : null}
      </ConfirmDialog>
    </div>
  );
}

/** One instructor's group: a header that opens to its classes. */
function GroupCard({
  group,
  mode,
  open,
  onToggle,
  children,
}: {
  group: InstructorGroup;
  mode: ClassListMode;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  const { t } = useLocale();
  const { formatNumber, formatMoney } = useFormat();
  const listId = useId();
  const name = group.name || t("instructorPay.group.noInstructor");
  const count = group.classes.length;
  const countText = count === 1 ? t("instructorPay.group.classOne") : t("instructorPay.group.classMany", { count: formatNumber(count) });
  const amountText =
    mode === "settle"
      ? t("instructorPay.group.due", { amount: formatMoney(group.due) ?? "" })
      : t("instructorPay.group.toApprove", { amount: formatMoney(group.due) ?? "" });

  // 17 Nov: on To settle, an instructor's card opens their own page, where every class of theirs is settled and others are added.
  if (mode === "settle" && group.instructorId) {
    return (
      <section className="overflow-hidden rounded-card border border-border bg-card">
        <Link
          href={`/staff/offline-instructors/${group.instructorId}`}
          className="flex min-h-11 w-full flex-wrap items-center justify-between gap-x-3 gap-y-1 px-4 py-3 text-start hover:bg-bg"
        >
          <span className="grid min-w-0 gap-0.5">
            <span className="truncate font-semibold text-text">{name}</span>
            <span className="text-meta text-muted">
              {countText} · {amountText}
            </span>
          </span>
          <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-muted" />
        </Link>
      </section>
    );
  }

  return (
    <section className="overflow-hidden rounded-card border border-border bg-card">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={listId}
        onClick={onToggle}
        className="flex min-h-11 w-full flex-wrap items-center justify-between gap-x-3 gap-y-1 px-4 py-3 text-start hover:bg-bg"
      >
        <span className="grid min-w-0 gap-0.5">
          <span className="truncate font-semibold text-text">{name}</span>
          <span className="text-meta text-muted">
            {countText} · {amountText}
          </span>
        </span>
        <ChevronDown aria-hidden="true" className={cn("size-5 shrink-0 text-muted transition-transform", open && "rotate-180")} />
      </button>
      {open ? (
        <div id={listId} className="border-t border-border">
          {children}
        </div>
      ) : null}
    </section>
  );
}

/** One class in a group: its time, batch, subject, amount and state, with the actions that state allows. */
function ClassItem({
  row,
  mode,
  batchName,
  canSettle,
  canApprove,
  selectable,
  selected,
  onToggle,
  onPay,
  onNotTaken,
  onApprove,
  onReject,
}: {
  row: ClassRow;
  mode: ClassListMode;
  batchName: string;
  canSettle: boolean;
  canApprove: boolean;
  selectable: boolean;
  selected: boolean;
  onToggle: () => void;
  onPay: () => void;
  onNotTaken: () => void;
  onApprove: () => void;
  onReject: () => void;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney, formatTimeRange, formatTime } = useFormat();
  const due = dueOf(row);
  const when = row.startsAt
    ? row.endsAt
      ? formatTimeRange(classStartIso(row), classEndIso(row))
      : formatTime(classStartIso(row))
    : "";
  // A settled or waiting payment shows what was paid; a held class shows what is due.
  const amount = row.payment && (row.status === "PAID_PENDING" || row.status === "PAID") ? row.payment.amount : row.amount;
  const noInstructor = !row.instructorId;
  const noAmount = !hasAmount(row);
  // 17 Nov: a recorded payment shows the class's rate beside it, and how far it is from the rate, so the approver can check it.
  const payment = row.payment && (row.status === "PAID_PENDING" || row.status === "PAID") ? row.payment : null;
  const difference = payment && !noAmount ? Number(payment.amount) - due : 0;

  return (
    <li className="grid gap-3 border-t border-border px-4 py-3 first:border-t-0 sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-start">
      {selectable && (row.status === "PAID_PENDING" || row.status === "NOT_TAKEN_PENDING") ? (
        <input
          type="checkbox"
          aria-label={t("instructorPay.approve.select", { name: row.instructorName ?? row.instructorText ?? "", subject: row.subject })}
          className="mt-1 size-5 accent-accent"
          checked={selected}
          onChange={onToggle}
        />
      ) : null}
      <div className="grid min-w-0 gap-1.5">
        <p className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-body">
          <span className="font-medium text-text">{formatDate(row.heldOn)}</span>
          {when ? <span className="text-text">{when}</span> : null}
          {batchName ? <span className="text-meta text-muted">{batchName}</span> : null}
        </p>
        <p className="break-words text-body text-text">
          {row.subject}
          {row.topic ? <span className="text-muted"> · {row.topic}</span> : null}
        </p>
        <div className="flex flex-wrap items-center gap-1.5">
          {amount !== null && amount !== undefined && row.status !== "NOT_TAKEN_PENDING" ? (
            <span className="font-latin text-body font-semibold tabular-nums text-text">{formatMoney(amount)}</span>
          ) : null}
          {row.status === "HELD" && !noAmount ? <MoneyPills pills={[{ key: "owed", tone: "muted", amount: due }]} /> : null}
          {row.status === "HELD" && noAmount ? <Pill tone="warn">{t("instructorPay.row.noAmount")}</Pill> : null}
          {row.status === "PAID_PENDING" ? <MoneyPills pills={[{ key: "pending", tone: "accent", amount: null }]} /> : null}
          {row.status === "NOT_TAKEN_PENDING" ? <Pill tone="accent">{t("instructorPay.row.notTakenPending")}</Pill> : null}
          {noInstructor && row.status === "HELD" ? <Pill tone="warn">{t("instructorPay.row.noInstructor")}</Pill> : null}
        </div>
        {payment ? (
          <div className="grid gap-0.5 text-meta text-muted">
            <span>
              {noAmount ? t("instructorPay.row.noRateSet") : t("instructorPay.row.ratePerClass", { amount: formatMoney(due) ?? "" })}
              {" · "}
              {t(methodLabelKey(payment.method))} · {formatDate(payment.paidOn)}
              {payment.reference ? ` · ${payment.reference}` : ""}
            </span>
            {Math.abs(difference) >= 0.005 ? (
              <span className="font-medium text-warn">
                {t(difference > 0 ? "instructorPay.row.paidMore" : "instructorPay.row.paidLess", {
                  difference: formatMoney(Math.abs(difference)) ?? "",
                })}
              </span>
            ) : null}
            {payment.note ? <span className="break-words text-text">{t("instructorPay.row.paymentNote", { note: payment.note })}</span> : null}
          </div>
        ) : null}
        {noInstructor && row.status === "HELD" && canSettle ? (
          <Link href={`/staff/offline-batches/${row.batchId}`} className="text-meta text-accent-text underline-offset-4 hover:underline">
            {t("instructorPay.row.setInstructor")}
          </Link>
        ) : null}
      </div>
      <div className="flex flex-wrap gap-2 sm:justify-end">
        {mode === "settle" && canSettle && row.status === "HELD" ? (
          <>
            <Button size="sm" onClick={onPay} disabled={noInstructor || noAmount} title={noInstructor || noAmount ? t("instructorPay.row.payHint") : undefined}>
              {t("instructorPay.row.record")}
            </Button>
            <Button size="sm" variant="secondary" onClick={onNotTaken}>
              {t("instructorPay.row.notTaken")}
            </Button>
          </>
        ) : null}
        {mode === "waiting" && canApprove && (row.status === "PAID_PENDING" || row.status === "NOT_TAKEN_PENDING") ? (
          <>
            <Button size="sm" onClick={onApprove}>
              {t("instructorPay.row.approve")}
            </Button>
            <Button size="sm" variant="secondary" onClick={onReject}>
              {t("instructorPay.row.reject")}
            </Button>
          </>
        ) : null}
      </div>
    </li>
  );
}
