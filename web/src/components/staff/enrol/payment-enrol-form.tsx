"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/errors";
import type { GrantQuote, GrantResponse } from "@/lib/api/exam/staff-admin";
import { useRemoveFromRoster } from "@/lib/api/exam/staff-catalog";
import { staffHref } from "@/lib/config";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { OverlapNotice } from "@/components/staff/enrol/overlap-notice";
import { directAlreadyActive, heldWarning, overlapItems, rosterBlocks } from "@/components/staff/enrol/overlap-model";
import { PaymentFields } from "@/components/staff/enrol/payment-fields";
import {
  EMPTY_DRAFT,
  paymentBody,
  quotePaisa,
  resolveDraft,
  todayInDhaka,
  type EnrolScope,
  type GrantPayment,
  type PaymentDraft,
} from "@/components/staff/enrol/payment-model";
import type { FoundStudent } from "@/components/staff/student-picker";

/** What the screen keeps after a student is enrolled, for the success notice. */
export type EnrolDone = { name: string; paymentPlanId: string | null; receiptNo: string | null };

/** The codes that mean the quote on screen is out of date, so it is read again. */
const STALE_QUOTE_CODES = new Set(["STUDENT_ON_ROSTER", "STUDENT_HAS_DIRECT_ENROLMENT"]);

/**
 * The payment form for one student and one target, under the student's name. It reads the quote for the price and the
 * warnings, takes the draft, and asks for a confirmation before anything is saved (plan C7). `enrol` posts the grant; a
 * failure stays on this form with its message, and a success hands the result to `onDone`.
 *
 * A package whose roster holds the student is blocked until the roster row is removed (plan D22). A student who already has a
 * direct enrolment here cannot be enrolled again. An overlap with other access warns and never blocks (plan D23).
 */
export function PaymentEnrolForm({
  scope,
  targetId,
  targetTitle,
  student,
  alreadyEnrolled,
  enrol,
  onDone,
  onCancel,
}: {
  scope: EnrolScope;
  targetId: string;
  targetTitle: string;
  student: FoundStudent;
  alreadyEnrolled: boolean;
  enrol: (studentId: string, reason: string, payment?: GrantPayment) => Promise<unknown>;
  onDone: (done: EnrolDone) => void;
  onCancel: () => void;
}) {
  const { t } = useLocale();
  const { formatNumber, formatMoney, formatDate } = useFormat();
  const { toast } = useToast();
  const [draft, setDraft] = useState<PaymentDraft>(EMPTY_DRAFT);
  const [showErrors, setShowErrors] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failures, setFailures] = useState<string[]>([]);
  const [removing, setRemoving] = useState(false);
  const removeRoster = useRemoveFromRoster(targetId);

  const quote = useQuery({
    queryKey: ["staffEnrol", "quote", scope, targetId, student.id],
    queryFn: ({ signal }) =>
      apiRequest<GrantQuote>(
        `/api/v1/live-exam-grants/quote?scope=${scope}&${scope === "MODULE" ? "moduleId" : "examId"}=${targetId}&studentId=${student.id}`,
        { signal },
      ),
  });
  const data = quote.data;
  const free = data?.free === true;
  const blocked = rosterBlocks(data, scope);
  const alreadyDirect = directAlreadyActive(data);
  const cannotSubmit = alreadyDirect || alreadyEnrolled;
  const warning = heldWarning(data, free);
  const overlaps = overlapItems(data);
  const standardPaisa = quotePaisa(data?.standardPrice ?? null);
  const today = todayInDhaka();
  const resolved = resolveDraft(draft, { targetId, standardPaisa, free, today });
  const check = resolved.check;
  const kind = t(`staffAdmin.enrol.payment.kind.${scope}`);

  function change(patch: Partial<PaymentDraft>) {
    setDraft((current) => ({ ...current, ...patch }));
  }

  function review(event: FormEvent) {
    event.preventDefault();
    setShowErrors(true);
    if (check.ok && !blocked && !cannotSubmit) {
      setFailures([]);
      setReviewing(true);
    }
  }

  async function confirm() {
    if (!check.ok) {
      return;
    }
    setBusy(true);
    setFailures([]);
    const payment = check.payment ? paymentBody(check.payment) : undefined;
    try {
      const result = (await enrol(student.id, check.payment ? "" : check.reason, payment)) as GrantResponse | undefined;
      setBusy(false);
      setReviewing(false);
      onDone({ name: student.fullName, paymentPlanId: result?.paymentPlanId ?? null, receiptNo: result?.receiptNo ?? null });
    } catch (error) {
      setBusy(false);
      setReviewing(false);
      const lines = [t(catalogErrorKey(error))];
      if (error instanceof ApiError && error.fieldErrors) {
        lines.push(...error.fieldErrors.map((item) => item.message));
      }
      setFailures(lines);
      if (error instanceof ApiError && STALE_QUOTE_CODES.has(error.code)) {
        void quote.refetch();
      }
    }
  }

  // The hook invalidates the staffEnrol keys, which include this quote, so the quote is read again and the payment fields open.
  async function removeFromRoster() {
    await removeRoster.mutateAsync(student.id);
    toast({ title: t("staffCatalog.roster.removed"), tone: "ok" });
  }

  const money = (paisa: number) => formatMoney(paisa / 100) ?? "";
  const summary: [string, string][] = [];
  if (check.ok) {
    summary.push([t("staffAdmin.enrol.payment.summary.student"), student.fullName + (student.studentCode ? ` · ${student.studentCode}` : "")]);
    summary.push([t("staffAdmin.enrol.payment.summary.item"), `${kind}: ${targetTitle}`]);
    const plan = check.payment;
    if (plan) {
      const typeText =
        plan.type === "FULL"
          ? t("staffAdmin.enrol.payment.types.FULL")
          : `${t("staffAdmin.enrol.payment.types.INSTALLMENTS")} · ${t("staffAdmin.enrol.payment.summary.installments", { count: formatNumber(plan.rows.length) })}`;
      summary.push([t("staffAdmin.enrol.payment.summary.type"), typeText]);
      summary.push([t("staffAdmin.enrol.payment.summary.total"), money(plan.totalPaisa)]);
      summary.push([
        t("staffAdmin.enrol.payment.summary.paidToday"),
        `${money(plan.paidPaisa)} · ${t(`staffAdmin.enrol.payment.methods.${plan.method}`)}`,
      ]);
      summary.push([
        t("staffAdmin.enrol.payment.summary.due"),
        plan.duePaisa > 0 ? money(plan.duePaisa) : t("staffAdmin.enrol.payment.summary.nothingDue"),
      ]);
      summary.push([
        t("staffAdmin.enrol.payment.summary.nextDate"),
        plan.nextRow
          ? `${formatDate(plan.nextRow.dueOn)} · ${money(plan.nextRow.paisa)}`
          : t("staffAdmin.enrol.payment.summary.none"),
      ]);
    } else {
      summary.push([t("staffAdmin.enrol.payment.summary.type"), t("staffAdmin.enrol.payment.types.NO_CHARGE")]);
      summary.push([t("staffAdmin.enrol.payment.summary.reason"), check.reason]);
    }
  }

  return (
    <>
      <form onSubmit={review} noValidate className="grid gap-4 rounded-control border border-border bg-bg p-4">
        <div className="grid gap-0.5">
          <span className="text-label font-semibold">{student.fullName}</span>
          <span className="text-meta text-muted" dir="ltr">
            {[student.studentCode, student.phone, student.email].filter(Boolean).join(" · ")}
          </span>
        </div>

        {alreadyDirect ? (
          <p role="status" className="rounded-control border border-warn/40 bg-warn/10 p-3 text-meta text-warn">
            {t("staffAdmin.enrol.payment.directActive")}
          </p>
        ) : alreadyEnrolled ? (
          <p className="text-meta text-muted">{t("staffCatalog.enrol.already")}</p>
        ) : null}

        {data?.unfinishedPlanId ? (
          <div role="status" className="grid gap-2 rounded-control border border-warn/40 bg-warn/10 p-3 text-meta text-warn">
            <p>{t("staffAdmin.enrol.payment.unfinishedWarning", { kind })}</p>
            <Link href={staffHref(`/staff/installments/${data.unfinishedPlanId}`)} className="w-fit font-semibold underline underline-offset-4">
              {t("staffAdmin.enrol.payment.openUnfinished")}
            </Link>
          </div>
        ) : null}
        {!blocked && warning === "OVERLAPS" ? <OverlapNotice items={overlaps} scope={scope} /> : null}
        {!blocked && warning === "ACCESS_HELD" ? (
          <p role="status" className="rounded-control border border-warn/40 bg-warn/10 p-3 text-meta text-warn">
            {t("staffAdmin.enrol.payment.accessHeldWarning", { kind })}
          </p>
        ) : null}

        {quote.isPending ? <p className="text-meta text-muted">{t("staffAdmin.enrol.payment.loadingPrice")}</p> : null}
        {quote.isError ? <p role="alert" className="text-meta text-danger">{t("staffAdmin.enrol.payment.quoteError")}</p> : null}

        {quote.isPending ? null : blocked ? (
          <div role="status" className="grid gap-3 rounded-control border border-warn/40 bg-warn/10 p-3 text-meta text-warn">
            <p>{t("staffAdmin.enrol.payment.rosterBlock")}</p>
            <div>
              <Button variant="danger" onClick={() => setRemoving(true)}>
                {t("staffAdmin.enrol.payment.rosterRemove")}
              </Button>
            </div>
          </div>
        ) : (
          <PaymentFields
            draft={draft}
            resolved={resolved}
            free={free}
            standardPaisa={standardPaisa}
            today={today}
            showErrors={showErrors}
            onChange={change}
          />
        )}

        {failures.length > 0 ? (
          <div role="alert" className="grid gap-1 text-meta text-danger">
            {failures.map((line, index) => (
              <p key={`${index}-${line}`}>{line}</p>
            ))}
          </div>
        ) : null}

        <div className="flex flex-wrap gap-2">
          {blocked ? null : (
            <Button type="submit" disabled={quote.isPending || busy || cannotSubmit}>
              {t("staffAdmin.enrol.payment.review")}
            </Button>
          )}
          <Button type="button" variant="secondary" onClick={onCancel} disabled={busy}>
            {t("common.actions.cancel")}
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={removing}
        onOpenChange={(open) => setRemoving(open)}
        title={t("staffCatalog.roster.removeTitle", { name: student.fullName })}
        body={data?.rosterStarted === true ? t("staffCatalog.roster.removeStartedBody") : t("staffCatalog.roster.removeBody")}
        confirmLabel={t("staffCatalog.roster.removeConfirm")}
        tone="danger"
        onConfirm={async () => {
          try {
            await removeFromRoster();
          } catch (caught) {
            throw new Error(t(catalogErrorKey(caught)));
          }
        }}
      />

      <Dialog
        open={reviewing && check.ok}
        onOpenChange={(open) => {
          if (!open && !busy) {
            setReviewing(false);
          }
        }}
        title={t("staffAdmin.enrol.payment.confirmTitle", { name: student.fullName })}
        description={t("staffAdmin.enrol.payment.confirmBody")}
        footer={
          <>
            <Button variant="secondary" disabled={busy} onClick={() => setReviewing(false)}>
              {t("staffAdmin.enrol.payment.back")}
            </Button>
            <Button loading={busy} disabled={busy} onClick={() => void confirm()}>
              {t("staffAdmin.enrol.payment.confirm")}
            </Button>
          </>
        }
      >
        <div className="grid gap-4">
          <dl className="grid gap-2">
            {summary.map(([label, value]) => (
              <div key={label} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-border pb-2 last:border-b-0">
                <dt className="text-meta text-muted">{label}</dt>
                <dd className="text-end font-medium tabular-nums text-text">{value}</dd>
              </div>
            ))}
          </dl>
          {warning === "OVERLAPS" ? <OverlapNotice items={overlaps} scope={scope} /> : null}
        </div>
      </Dialog>
    </>
  );
}

/** The success notice after an enrolment: the receipt and the plan for a payment, or a line for No charge (E12). */
export function EnrolDoneNotice({ done }: { done: EnrolDone }) {
  const { t } = useLocale();
  return (
    <div role="status" className="grid gap-2 rounded-control border border-ok/40 bg-ok/10 p-4">
      <p className="font-semibold text-text">{t("staffCatalog.enrol.done", { name: done.name })}</p>
      {done.paymentPlanId ? (
        <>
          {done.receiptNo ? (
            <p className="text-meta text-muted">{t("staffAdmin.enrol.payment.receiptNumber", { number: done.receiptNo })}</p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" href={staffHref(`/staff/installments/${done.paymentPlanId}`)}>
              {t("staffAdmin.enrol.payment.openReceipt")}
            </Button>
            <Button variant="secondary" href={staffHref(`/staff/installments/${done.paymentPlanId}`)}>
              {t("staffAdmin.enrol.payment.openPlan")}
            </Button>
          </div>
        </>
      ) : (
        <p className="text-meta text-muted">{t("staffAdmin.enrol.payment.noChargeDone")}</p>
      )}
    </div>
  );
}
