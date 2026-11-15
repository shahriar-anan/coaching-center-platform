"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Pill } from "@/components/ui/pill";
import { TextInput } from "@/components/ui/text-input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { ApiError } from "@/lib/api/errors";
import { useFormat } from "@/lib/format/use-format";
import {
  useCloseAdmission,
  useNextRoll,
  useOnboard,
  useSaveTerms,
  type AdmissionDetail,
  type AdmissionItem,
} from "@/lib/api/exam/offline-admin";
import { useAllModuleOptions } from "@/lib/api/exam/staff-catalog";
import { offlineErrorText } from "./offline-error";
import { draftOf, setupTotals } from "./setup-draft";

/** How many exams and packages the onboarding list shows before "Show all". */
const ITEMS_SHOWN = 6;

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
  const { toast } = useToast();
  const onboard = useOnboard(admission.id);
  const suggested = useNextRoll(admission.id, open);
  const [typed, setTyped] = useState<string | null>(null);
  const suggestion = suggested.data?.suggestion ?? null;
  // The suggestion fills the box until the person types their own roll number.
  const roll = (typed ?? suggestion ?? "").trim();
  const valid = /^\d{4,12}$/.test(roll);
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setTyped(null);
        onOpenChange(next);
      }}
      title={t("staffOffline.onboard.title", { name: studentName(admission) })}
      body={t("staffOffline.onboard.body")}
      confirmLabel={t("staffOffline.onboard.confirm")}
      confirmDisabled={!valid}
      onConfirm={async () => {
        try {
          await onboard.mutateAsync({ rollNo: roll });
          toast({ title: t("staffOffline.onboard.done"), tone: "ok" });
        } catch (error) {
          // A roll the batch already uses is named under its field; anything else gets the localised sentence.
          const field = error instanceof ApiError ? error.fieldErrors?.find((problem) => problem.field === "rollNo") : undefined;
          throw new Error(field ? field.message : offlineErrorText(error, t));
        }
      }}
    >
      <OnboardSummary admission={admission} />
      <Field
        label={t("staffOffline.onboard.roll")}
        hint={suggested.isPending ? undefined : suggestion ? t("staffOffline.onboard.rollSuggested") : t("staffOffline.onboard.rollNone")}
        error={roll && !valid ? t("staffOffline.onboard.rollInvalid") : undefined}
        required
      >
        <TextInput
          inputMode="numeric"
          value={roll}
          onChange={(event) => setTyped(event.target.value)}
          invalid={Boolean(roll) && !valid}
        />
      </Field>
    </ConfirmDialog>
  );
}

/**
 * What onboarding will do: the setup as a list of facts, then the exams and packages. It is mounted only while the dialog is open, so
 * the package list is read only then. Long lists scroll inside their own box and show the first few until "Show all".
 */
function OnboardSummary({ admission }: { admission: AdmissionDetail }) {
  const { t } = useLocale();
  const { formatDate, formatMoney, pickTitle } = useFormat();
  const [showAll, setShowAll] = useState(false);
  const modules = useAllModuleOptions();
  const totals = setupTotals(draftOf(admission.setup));
  const packages = admission.items.filter((item) => item.scope === "MODULE").length;
  const exams = admission.items.length - packages;
  // A package's exam count is in the module list (the exams of each type). Until that list is read, no count is shown.
  const examCounts = new Map<string, number>(
    (modules.data ?? []).map((pkg): [string, number] => [pkg.id, (pkg.mcqCount ?? 0) + (pkg.writtenCount ?? 0)]),
  );
  const shown = showAll ? admission.items : admission.items.slice(0, ITEMS_SHOWN);
  const countHeading = [
    packages > 0 ? (packages === 1 ? t("staffOffline.onboard.packagesOne", { count: 1 }) : t("staffOffline.onboard.packagesMany", { count: packages })) : null,
    exams > 0 ? (exams === 1 ? t("staffOffline.onboard.examsOne", { count: 1 }) : t("staffOffline.onboard.examsMany", { count: exams })) : null,
  ]
    .filter(Boolean)
    .join(", ");

  const existing = admission.alreadyAccessTo.map((batch) => pickTitle({ titleEn: batch.nameEn, titleBn: batch.nameBn })).filter(Boolean);
  return (
    <div className="grid min-w-0 gap-4">
      {existing.length > 0 ? (
        <p role="status" className="rounded-control border border-accent/40 bg-accent/5 px-3 py-2 text-body text-text">
          {t("staffOffline.onboard.alreadyAccess", { student: studentName(admission), batches: existing.join(", ") })}
        </p>
      ) : null}
      <dl className="grid gap-2 text-body">
        <Row label={t("staffOffline.onboard.student")} value={studentName(admission)} />
        <Row
          label={t("staffOffline.setup.batch")}
          value={pickTitle({ titleEn: admission.setup.batchNameEn, titleBn: admission.setup.batchNameBn }) || "—"}
        />
        <Row label={t("staffOffline.onboard.fee")} value={formatMoney(admission.setup.monthlyFee) ?? "—"} />
        <Row label={t("staffOffline.onboard.firstDue")} value={admission.setup.firstDueOn ? formatDate(admission.setup.firstDueOn) : "—"} />
        <Row label={t("staffOffline.onboard.courseEnds")} value={admission.setup.courseEndsOn ? formatDate(admission.setup.courseEndsOn) : "—"} />
        <Row label={t("staffOffline.onboard.payments")} value={String(totals.months)} />
        <Row label={t("staffOffline.onboard.total")} value={formatMoney(totals.total) ?? "—"} />
        <Row label={t("staffOffline.onboard.emailed")} value={t("staffOffline.onboard.emailedValue")} />
        <Row label={t("staffOffline.onboard.codeTo")} value={codeLine(admission, t)} />
      </dl>

      <section aria-labelledby="onboard-items" className="grid min-w-0 gap-2">
        <h3 id="onboard-items" className="text-meta font-semibold text-text">
          {countHeading || t("staffOffline.onboard.items")}
        </h3>
        {admission.items.length === 0 ? (
          <p className="text-meta text-muted">{t("staffOffline.onboard.none")}</p>
        ) : (
          <>
            <ul
              aria-label={t("staffOffline.onboard.items")}
              className="grid divide-y divide-border rounded-control border border-border-control"
            >
              {shown.map((item) => (
                <OnboardItemRow key={item.id} item={item} examCount={item.moduleId ? examCounts.get(item.moduleId) : undefined} />
              ))}
            </ul>
            {admission.items.length > ITEMS_SHOWN ? (
              <Button variant="ghost" size="sm" className="justify-self-start" onClick={() => setShowAll((current) => !current)}>
                {showAll ? t("staffOffline.onboard.showFewer") : t("staffOffline.onboard.showAll", { count: admission.items.length })}
              </Button>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}

/**
 * Which code goes to which email (plan Q3, P8). A student who already has an account, or whose application matched one, gets the join
 * code; a new applicant gets the activation code that makes the account. The code lasts 7 days.
 */
function codeLine(admission: AdmissionDetail, t: (key: string, params?: Record<string, string | number>) => string): string {
  if (admission.source === "DESK") {
    return t("staffOffline.onboard.codeDesk");
  }
  const joining = admission.source !== "PUBLIC" || Boolean(admission.matchedName);
  const email = admission.matchedEmail ?? admission.form.email ?? admission.form.mobile ?? "—";
  const message = joining ? t("staffOffline.onboard.codeJoin", { email }) : t("staffOffline.onboard.codeActivation", { email });
  return `${message} ${t("staffOffline.onboard.codeLasts")}`;
}

/** One exam or package of the onboarding list: its type, its title and, for a package, how many exams it holds. */
function OnboardItemRow({ item, examCount }: { item: AdmissionItem; examCount: number | undefined }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const isPackage = item.scope === "MODULE";
  return (
    <li className="flex flex-wrap items-center gap-x-3 gap-y-1 px-3 py-2">
      <Pill tone={isPackage ? "accent" : "muted"}>{t(isPackage ? "staffOffline.items.package" : "staffOffline.items.exam")}</Pill>
      <span className="min-w-0 flex-1 break-words text-text">{pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn }) || "—"}</span>
      {isPackage && examCount !== undefined ? (
        <span className="text-meta text-muted">
          {examCount === 1 ? t("staffOffline.onboard.examsOne", { count: 1 }) : t("staffOffline.onboard.examsMany", { count: examCount })}
        </span>
      ) : null}
    </li>
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
      body={t(admission.state === "ONBOARDED" ? "staffOffline.close.body" : "staffOffline.close.bodyBefore")}
      confirmLabel={t("staffOffline.close.confirm")}
      tone="danger"
      confirmDisabled={reason.trim().length === 0}
      onConfirm={async () => {
        try {
          await close.mutateAsync(reason.trim());
          toast({ title: t("staffOffline.close.done"), tone: "ok" });
        } catch (error) {
          // The dialog shows the message it is given, so it gets the localised sentence, not the API text.
          throw new Error(offlineErrorText(error, t));
        }
      }}
    >
      <Field label={t("staffOffline.close.reasonLabel")} hint={t("staffOffline.close.reasonHint")} required>
        <Textarea rows={2} value={reason} onChange={(event) => setReason(event.target.value)} />
      </Field>
    </ConfirmDialog>
  );
}
