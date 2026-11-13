"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import {
  useCloseAdmission,
  useOnboard,
  useSaveTerms,
  type AdmissionDetail,
} from "@/lib/api/exam/offline-admin";
import { offlineErrorText } from "./offline-error";

/** The name the dialogs show for the student: the form's full name. */
function studentName(admission: AdmissionDetail): string {
  return admission.form.fullName ?? "";
}

/**
 * Onboarding (plan 6.3, principle 5): the Master Admin only. It restates what happens: the student, the items, the fee, the first due
 * date, the end date, and that the student is emailed.
 */
export function OnboardDialog({ admission, open, onOpenChange }: { admission: AdmissionDetail; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const { formatDate, formatMoney, pickTitle } = useFormat();
  const { toast } = useToast();
  const onboard = useOnboard(admission.id);
  const items = admission.items.map((item) => pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn })).filter(Boolean);
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffOffline.onboard.title", { name: studentName(admission) })}
      body={t("staffOffline.onboard.body")}
      confirmLabel={t("staffOffline.onboard.confirm")}
      onConfirm={async () => {
        try {
          await onboard.mutateAsync();
          toast({ title: t("staffOffline.onboard.done"), tone: "ok" });
        } catch (error) {
          // The dialog shows the message it is given, so it gets the localised sentence, not the API text.
          throw new Error(offlineErrorText(error, t));
        }
      }}
    >
      <dl className="grid gap-2 text-body">
        <Row label={t("staffOffline.onboard.student")} value={studentName(admission)} />
        <Row label={t("staffOffline.onboard.items")} value={items.length > 0 ? items.join(", ") : t("staffOffline.onboard.none")} />
        <Row label={t("staffOffline.onboard.fee")} value={formatMoney(admission.setup.monthlyFee) ?? "—"} />
        <Row label={t("staffOffline.onboard.firstDue")} value={admission.setup.firstDueOn ? formatDate(admission.setup.firstDueOn) : "—"} />
        <Row label={t("staffOffline.onboard.courseEnds")} value={admission.setup.courseEndsOn ? formatDate(admission.setup.courseEndsOn) : "—"} />
        <Row label={t("staffOffline.onboard.emailed")} value={t("staffOffline.onboard.emailedValue")} />
      </dl>
    </ConfirmDialog>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-0.5 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-3">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="break-words text-text">{value}</dd>
    </div>
  );
}

/**
 * Change the terms after onboarding (plan D16): the fee and the end date. Future unpaid dues change; a paid due is never cancelled. The
 * second step restates the change before it is saved, and the student is emailed the old and new terms.
 */
export function TermsDialog({ admission, open, onOpenChange }: { admission: AdmissionDetail; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const { formatDate, formatMoney } = useFormat();
  const { toast } = useToast();
  const terms = useSaveTerms(admission.id);
  const [fee, setFee] = useState(admission.setup.monthlyFee === null ? "" : String(admission.setup.monthlyFee));
  const [end, setEnd] = useState(admission.setup.courseEndsOn ?? "");
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const feeChanged = fee.trim() !== "" && Number(fee) !== Number(admission.setup.monthlyFee);
  const endChanged = end !== "" && end !== admission.setup.courseEndsOn;
  const nothing = !feeChanged && !endChanged;

  async function save() {
    const body: { monthlyFee?: string; courseEndsOn?: string } = {};
    if (feeChanged) body.monthlyFee = fee.trim();
    if (endChanged) body.courseEndsOn = end;
    try {
      await terms.mutateAsync(body);
      toast({ title: t("staffOffline.terms.saved"), tone: "ok" });
      setConfirming(false);
      onOpenChange(false);
    } catch (caught) {
      setError(offlineErrorText(caught, t));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setConfirming(false);
        setError(null);
        onOpenChange(next);
      }}
      title={t("staffOffline.terms.title")}
      description={t("staffOffline.terms.body")}
      footer={
        confirming ? (
          <>
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              {t("staffOffline.terms.back")}
            </Button>
            <Button onClick={() => void save()} loading={terms.isPending}>
              {t("staffOffline.terms.confirm")}
            </Button>
          </>
        ) : (
          <>
            <Button variant="ghost" onClick={() => onOpenChange(false)}>
              {t("common.actions.cancel")}
            </Button>
            <Button disabled={nothing} onClick={() => setConfirming(true)}>
              {t("staffOffline.terms.review")}
            </Button>
          </>
        )
      }
    >
      {confirming ? (
        <dl className="grid gap-2 text-body">
          {feeChanged ? (
            <Row label={t("staffOffline.terms.feeLabel")} value={`${formatMoney(admission.setup.monthlyFee) ?? "—"} → ${formatMoney(fee) ?? "—"}`} />
          ) : null}
          {endChanged ? (
            <Row
              label={t("staffOffline.terms.endLabel")}
              value={`${admission.setup.courseEndsOn ? formatDate(admission.setup.courseEndsOn) : "—"} → ${formatDate(end)}`}
            />
          ) : null}
          <Row label={t("staffOffline.onboard.emailed")} value={t("staffOffline.terms.emailed")} />
        </dl>
      ) : (
        <div className="grid gap-4">
          <Field label={t("staffOffline.setup.monthlyFee")} error={error ?? undefined}>
            <TextInput inputMode="decimal" value={fee} onChange={(event) => setFee(event.target.value)} />
          </Field>
          <Field label={t("staffOffline.setup.courseEnds")}>
            <TextInput type="date" value={end} onChange={(event) => setEnd(event.target.value)} />
          </Field>
        </div>
      )}
    </Dialog>
  );
}

/** Close the admission (plan 6.3): the Master Admin only. Access stops now and the future unpaid dues are cancelled. */
export function CloseDialog({ admission, open, onOpenChange }: { admission: AdmissionDetail; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const close = useCloseAdmission(admission.id);
  const [reason, setReason] = useState("");
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffOffline.close.title", { name: studentName(admission) })}
      body={t("staffOffline.close.body")}
      confirmLabel={t("staffOffline.close.confirm")}
      tone="danger"
      onConfirm={async () => {
        try {
          await close.mutateAsync(reason.trim() || null);
          toast({ title: t("staffOffline.close.done"), tone: "ok" });
        } catch (error) {
          // The dialog shows the message it is given, so it gets the localised sentence, not the API text.
          throw new Error(offlineErrorText(error, t));
        }
      }}
    >
      <Field label={t("staffOffline.close.reasonLabel")}>
        <Textarea rows={2} value={reason} onChange={(event) => setReason(event.target.value)} />
      </Field>
    </ConfirmDialog>
  );
}
