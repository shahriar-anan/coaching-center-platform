"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { adminErrorText } from "@/components/staff/access/admin-error";
import { useLocale } from "@/i18n/locale-context";
import { useRecordRefund, type StaffOrderRow } from "@/lib/api/exam/staff-ops";
import { useFormat } from "@/lib/format/use-format";

/** Up to two decimal places, no sign, no thousands separator. */
const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;

/**
 * Records a refund already made in the SSLCommerz merchant panel (plan 5.3). Master Admin only. The first step checks the
 * form; the second step asks for confirmation, since a recorded refund cannot be undone from this page. The parent mounts
 * this dialog only while it is open, so every open starts with a fresh form.
 */
export function RefundDialog({ row, onClose }: { row: StaffOrderRow; onClose: () => void }) {
  const { t } = useLocale();
  const format = useFormat();
  const { toast } = useToast();
  const record = useRecordRefund();
  const [amount, setAmount] = useState(() => row.amount.toFixed(2));
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const [revokeAccess, setRevokeAccess] = useState(true);
  const [amountMissing, setAmountMissing] = useState(false);
  const [reasonMissing, setReasonMissing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const paid = format.formatMoney(row.amount) ?? String(row.amount);
  const amountValue = Number(amount.trim());
  const amountOk = AMOUNT_PATTERN.test(amount.trim()) && amountValue >= 0.01 && amountValue <= row.amount;

  function next() {
    setError(null);
    setAmountMissing(!amountOk);
    setReasonMissing(reason.trim().length === 0);
    if (!amountOk || reason.trim().length === 0) {
      return;
    }
    setConfirming(true);
  }

  async function save() {
    setError(null);
    try {
      await record.mutateAsync({
        orderId: row.orderId,
        amount: amountValue,
        reason,
        reference,
        revokeAccess,
      });
      toast({ title: t("staffOps.paymentDetail.refund.saved"), tone: "ok" });
      onClose();
    } catch (e) {
      setConfirming(false);
      setError(adminErrorText(e, t));
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !record.isPending) {
          onClose();
        }
      }}
      title={t("staffOps.paymentDetail.refund.dialogTitle")}
      description={t("staffOps.paymentDetail.refund.dialogIntro")}
      footer={
        confirming ? (
          <>
            <Button variant="ghost" onClick={() => setConfirming(false)} disabled={record.isPending}>
              {t("staffOps.paymentDetail.refund.back")}
            </Button>
            <Button variant="danger" loading={record.isPending} onClick={save}>
              {t("staffOps.paymentDetail.refund.confirm")}
            </Button>
          </>
        ) : (
          <>
            <Button variant="ghost" onClick={onClose}>
              {t("common.actions.cancel")}
            </Button>
            <Button onClick={next}>{t("staffOps.paymentDetail.refund.next")}</Button>
          </>
        )
      }
    >
      {confirming ? (
        <div className="grid gap-3">
          <p className="text-body text-text">
            {t("staffOps.paymentDetail.refund.confirmBody", {
              amount: format.formatMoney(amountValue) ?? amount,
              paid,
            })}
          </p>
          <p className="text-meta text-muted">
            {revokeAccess
              ? t("staffOps.paymentDetail.refund.confirmAccessRemoved")
              : t("staffOps.paymentDetail.refund.confirmAccessKept")}
          </p>
        </div>
      ) : (
        <form
          className="grid gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            next();
          }}
        >
          <Field
            label={t("staffOps.paymentDetail.refund.amountLabel")}
            hint={t("staffOps.paymentDetail.refund.amountHint", { paid })}
            error={amountMissing ? t("staffOps.paymentDetail.refund.amountInvalid", { paid }) : undefined}
            required
          >
            <TextInput
              inputMode="decimal"
              value={amount}
              invalid={amountMissing}
              onChange={(event) => {
                setAmount(event.target.value);
                setAmountMissing(false);
              }}
            />
          </Field>
          <Field label={t("staffOps.paymentDetail.refund.referenceLabel")}>
            <TextInput value={reference} maxLength={128} onChange={(event) => setReference(event.target.value)} />
          </Field>
          <Field
            label={t("staffOps.paymentDetail.refund.reasonLabel")}
            hint={t("staffOps.paymentDetail.refund.reasonHint")}
            error={reasonMissing ? t("staffOps.paymentDetail.refund.reasonRequired") : undefined}
            required
          >
            <Textarea
              rows={3}
              maxLength={500}
              value={reason}
              invalid={reasonMissing}
              onChange={(event) => {
                setReason(event.target.value);
                setReasonMissing(false);
              }}
            />
          </Field>
          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              className="mt-1 size-5"
              checked={revokeAccess}
              onChange={(event) => setRevokeAccess(event.target.checked)}
            />
            <span className="grid gap-0.5">
              <span className="font-semibold text-text">{t("staffOps.paymentDetail.refund.revokeLabel")}</span>
              <span className="text-meta text-muted">{t("staffOps.paymentDetail.refund.revokeHint")}</span>
            </span>
          </label>
        </form>
      )}
      {error ? (
        <p role="alert" className="mt-3 text-meta text-danger">
          {error}
        </p>
      ) : null}
    </Dialog>
  );
}
