"use client";

import Link from "next/link";
import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { formatDate } from "@/lib/format";
import { useMyLiveExamAccess, type MyLiveExamAccess } from "@/lib/api/exam/catalog";
import { useOpenOrder, type MyOrder } from "@/lib/api/exam/account";
import { useCancelOrder, useResumeOrder } from "@/lib/api/exam/purchase";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Pill } from "@/components/ui/pill";
import { useToast } from "@/components/ui/toast";

/** The grant that a purchase made, found in the student's access list: one with an order id (S4.6). */
export interface BoughtGrant {
  orderId: string;
  grantedAt?: string;
}

/**
 * The purchase of one package or exam, from the student's own grants. A grant with no order id (a roster or an admin grant) is
 * not a purchase, so it gets no payment line.
 */
export function boughtGrant(
  access: MyLiveExamAccess | undefined,
  target: { moduleId: string } | { examId: string },
): BoughtGrant | null {
  const grant =
    "moduleId" in target
      ? access?.modules.find((row) => row.moduleId === target.moduleId && row.orderId)
      : access?.exams.find((row) => row.examId === target.examId && row.orderId);
  return grant?.orderId ? { orderId: grant.orderId, grantedAt: grant.grantedAt } : null;
}

/** The access list, read once for a signed-in student (cached with the rest of the catalog). */
export function useBoughtGrant(target: { moduleId: string } | { examId: string }, enabled: boolean): BoughtGrant | null {
  const access = useMyLiveExamAccess(enabled);
  return boughtGrant(access.data, target);
}

/**
 * The line under a bought package or exam: its purchase date, and a link to its payment in the payment history (S4.6). The
 * link opens that row's panel (`?order=`).
 */
export function PurchasedLine({ grant }: { grant: BoughtGrant }) {
  const { t, locale } = useLocale();
  const link = (
    <Link
      href={`/account/payments?order=${encodeURIComponent(grant.orderId)}`}
      className="font-semibold text-accent underline underline-offset-4"
    >
      {t("purchase.lines.viewPayment")}
    </Link>
  );
  return (
    <p className="text-body text-muted">
      {grant.grantedAt
        ? t("purchase.lines.purchased", { date: formatDate(grant.grantedAt, locale, { year: "auto" }) })
        : t("purchase.lines.paid")}
      {" · "}
      {link}
    </p>
  );
}

/**
 * The line for a payment that is not finished: a "Payment waiting" note with Continue payment and Cancel (S4.6). It shows nothing
 * when the student has no open order for this package or exam, and asks for nothing when `enabled` is false.
 */
export function OpenOrderLine({ filter, enabled }: { filter: { moduleId?: string; examId?: string }; enabled: boolean }) {
  const open = useOpenOrder(filter, enabled);
  const order = enabled ? open.data?.content[0] : undefined;
  return order ? <PendingOrder order={order} /> : null;
}

/** The open order's line and its two actions. Its own component, so the toast and the mutations run only when there is an order. */
function PendingOrder({ order }: { order: MyOrder }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const resume = useResumeOrder();
  const cancel = useCancelOrder();
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function continuePayment() {
    setBusy(true);
    try {
      await resume(order);
    } catch {
      setBusy(false);
      toast({ title: t("common.states.errorBody"), tone: "danger" });
    }
  }

  async function cancelOrder() {
    try {
      await cancel.mutateAsync(order.orderId);
      toast({ title: t("account.payments.detail.cancelled"), tone: "ok" });
    } catch {
      toast({ title: t("common.states.errorBody"), tone: "danger" });
    }
  }

  return (
    <div className="grid gap-3 rounded-control border border-border bg-bg p-3">
      <p className="flex flex-wrap items-center gap-2">
        <Pill tone="warn">{t("purchase.lines.waiting")}</Pill>
      </p>
      <div className="flex flex-wrap gap-2">
        <Button loading={busy} onClick={() => void continuePayment()}>
          {t("account.payments.detail.continue")}
        </Button>
        <Button variant="secondary" onClick={() => setConfirming(true)}>
          {t("purchase.lines.cancel")}
        </Button>
      </div>
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("account.payments.detail.cancelTitle")}
        body={t("account.payments.detail.cancelBody")}
        confirmLabel={t("account.payments.detail.cancelConfirm")}
        tone="danger"
        onConfirm={cancelOrder}
      />
    </div>
  );
}
