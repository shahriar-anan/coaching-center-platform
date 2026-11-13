"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useApprovePayment, useRejectPayment, type FeePayment } from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";

/** Rejecting asks for a reason, which the person who recorded the payment reads (plan 7). */
export function RejectPaymentDialog({
  payment,
  open,
  onOpenChange,
}: {
  payment: FeePayment;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const reject = useRejectPayment();
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!reason.trim()) {
      setError(t("staffOffline.reject.required"));
      return;
    }
    try {
      await reject.mutateAsync({ id: payment.id, reason: reason.trim() });
      toast({ title: t("staffOffline.reject.done"), tone: "ok" });
      setReason("");
      setError(null);
      onOpenChange(false);
    } catch (caught) {
      setError(offlineErrorText(caught, t));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setError(null);
        onOpenChange(next);
      }}
      title={t("staffOffline.reject.title")}
      description={t("staffOffline.reject.body")}
      footer={
        <>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button variant="danger" onClick={() => void submit()} loading={reject.isPending}>
            {t("staffOffline.reject.submit")}
          </Button>
        </>
      }
    >
      <Field label={t("staffOffline.reject.reasonLabel")} error={error ?? undefined} required>
        <Textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} invalid={Boolean(error)} />
      </Field>
    </Dialog>
  );
}

/** Approve (at once) and Reject for one pending payment. Only the Master Admin sees them; the parent decides. */
export function PaymentDecisionButtons({ payment }: { payment: FeePayment }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const approve = useApprovePayment();
  const [rejecting, setRejecting] = useState(false);
  return (
    <div className="flex flex-wrap gap-2">
      <Button
        size="sm"
        loading={approve.isPending}
        onClick={() =>
          approve.mutate(payment.id, {
            onSuccess: () => toast({ title: t("staffOffline.approve.done"), tone: "ok" }),
            onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
          })
        }
      >
        {t("staffOffline.approve.one")}
      </Button>
      <Button size="sm" variant="secondary" onClick={() => setRejecting(true)}>
        {t("staffOffline.reject.one")}
      </Button>
      <RejectPaymentDialog payment={payment} open={rejecting} onOpenChange={setRejecting} />
    </div>
  );
}
