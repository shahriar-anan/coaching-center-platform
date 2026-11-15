"use client";

import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { useToast } from "@/components/ui/toast";
import { offlineErrorText } from "./offline-error";
import { useAdmission, useResendCode, type AdmissionDetail } from "@/lib/api/exam/offline-admin";
import type { AdmissionState } from "@/lib/api/exam/offline-admission";
import { useListParams } from "@/lib/url/list-params";
import { AdmissionPhoto } from "./admission-photo";
import { AfterOnboarding, parseAfterTab } from "./after-onboarding";
import { FormReadOnly } from "./form-readonly";
import { AdmissionMoneyPills } from "./money-pills";
import { PrimaryActions } from "./primary-actions";
import { SetupSummary } from "./setup-summary";
import { isAfterOnboarding, progressIndex, waitingFor } from "./offline-model";
import { stateLabelKey } from "./labels";

const STATE_TONE: Record<AdmissionState, PillTone> = {
  AWAITING_FORM: "muted",
  SUBMITTED: "accent",
  CHANGES_REQUESTED: "warn",
  READY: "warn",
  ONBOARDED: "ok",
  CLOSED: "muted",
};

const PROGRESS_KEYS = [
  "staffOffline.progress.form",
  "staffOffline.progress.review",
  "staffOffline.progress.ready",
  "staffOffline.progress.onboarded",
] as const;

/**
 * One admission (plan 6.3). Before onboarding: the form on the left and, on the right, a summary of the setup (edited on its own screen), and the actions.
 * After onboarding: the tabs for the fees, the exams and packages, the form and the history.
 */
export function AdmissionDetailView({ id }: { id: string }) {
  const { t } = useLocale();
  const role = useRole();
  const admission = useAdmission(id);
  const [params, setParams] = useListParams({ tab: "", pay: "" });

  if (admission.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (admission.isError) {
    return <ErrorState onRetry={() => admission.refetch()} error={admission.error} />;
  }
  const a = admission.data;
  const name = a.form.fullName?.trim() || "—";
  const after = isAfterOnboarding(a.state);
  const canReview = role.canSeeAdmissions;
  const canOnboard = role.canOnboardAdmission;

  return (
    <div className="grid gap-6">
      <PageHeader
        title={name}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("common.staffNav.admissions"), href: "/staff/admissions" },
          { label: name },
        ]}
      />

      <Header admission={a} />
      {canOnboard && (a.state === "ONBOARDED" || a.joinPending) ? <CodeStatus admission={a} /> : null}
      <Progress state={a.state} />
      <WaitingLine state={a.state} role={role.role} admission={a} />

      {after ? (
        <AfterOnboarding
          admission={a}
          tab={parseAfterTab(params.tab)}
          onTab={(tab) => setParams({ tab })}
          canReview={canReview}
          canOnboard={canOnboard}
          openPayment={params.pay === "1"}
        />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(22rem,26rem)] lg:items-start">
          <div className="grid min-w-0 gap-5">
            {a.changesReason && a.state === "CHANGES_REQUESTED" ? (
              <p role="status" className="rounded-control border border-warn/40 bg-warn/10 px-3 py-2 text-body text-warn">
                {t("staffOffline.detail.currentReturn", { reason: a.changesReason })}
              </p>
            ) : null}
            <AdmissionPhoto admissionId={a.id} hasPhoto={a.hasPhoto} name={name} size="lg" />
            <FormReadOnly form={a.form} />
            <SubmissionHistory admission={a} />
          </div>

          <aside className="grid min-w-0 gap-6 lg:sticky lg:top-4 lg:max-h-[calc(100dvh-2rem)] lg:overflow-y-auto lg:pe-1">
            <SetupSummary admission={a} editable={a.state === "SUBMITTED" && canReview} />
            <PrimaryActions admission={a} canReview={canReview} canOnboard={canOnboard} />
          </aside>
        </div>
      )}
    </div>
  );
}

/**
 * After onboarding: when the join or activation code was sent, and a button for a new one (plan P8). The new code replaces the old one,
 * which stops working at once. Only the Master Admin sees it, as only the Master Admin onboards.
 */
function CodeStatus({ admission }: { admission: AdmissionDetail }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const { toast } = useToast();
  const resend = useResendCode(admission.id);
  if (!admission.joinCodeSentAt) {
    return null;
  }
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-border-control bg-card px-3 py-2">
      <p className="text-body text-text">
        {admission.joinPending
          ? t("staffOffline.onboard.waiting", { date: formatDate(admission.joinCodeSentAt) })
          : t("staffOffline.onboard.codeSent", { date: formatDate(admission.joinCodeSentAt) })}
      </p>
      {admission.canResendCode ? (
      <Button
        size="sm"
        variant="secondary"
        loading={resend.isPending}
        onClick={() =>
          resend.mutate(undefined, {
            onSuccess: () => toast({ title: t("staffOffline.onboard.resent"), tone: "ok" }),
            onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
          })
        }
      >
        {t("staffOffline.onboard.resend")}
      </Button>
      ) : null}
    </div>
  );
}

function Header({ admission }: { admission: AdmissionDetail }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const batch = pickTitle({ titleEn: admission.setup.batchNameEn, titleBn: admission.setup.batchNameBn });
  const name = admission.form.fullName?.trim() || "—";
  return (
    <header className="flex flex-wrap items-start gap-4">
      <AdmissionPhoto admissionId={admission.id} hasPhoto={admission.hasPhoto} name={name} />
      <div className="grid min-w-0 flex-1 gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-latin text-body text-text">{admission.form.mobile ?? "—"}</span>
          <Pill tone={STATE_TONE[admission.state]}>{t(stateLabelKey(admission.state))}</Pill>
          {batch ? <Pill tone="muted">{batch}</Pill> : null}
          {admission.rollNo ? <Pill tone="muted">{t("staffOffline.detail.roll", { roll: admission.rollNo })}</Pill> : null}
        </div>
        <AdmissionMoneyPills admissionId={admission.id} enabled={isAfterOnboarding(admission.state)} />
      </div>
    </header>
  );
}

/** Form → Review → Ready → Onboarded, with the stage the admission is in marked. */
function Progress({ state }: { state: AdmissionState }) {
  const { t } = useLocale();
  const index = progressIndex(state);
  return (
    <ol aria-label={t("staffOffline.progress.label")} className="grid grid-cols-4 gap-2">
      {PROGRESS_KEYS.map((key, stage) => (
        <li
          key={key}
          aria-current={stage === index ? "step" : undefined}
          className={cn(
            "rounded-control border px-2 py-2 text-center text-meta",
            stage < index && "border-ok/40 bg-ok/10 text-ok-text",
            stage === index && "border-accent bg-accent/15 font-semibold text-accent-text",
            stage > index && "border-border-control text-muted",
          )}
        >
          {t(key)}
        </li>
      ))}
    </ol>
  );
}

/** Who the admission waits for, in words. Shown only where someone has to act. */
function WaitingLine({
  state,
  role,
  admission,
}: {
  state: AdmissionState;
  role: Parameters<typeof waitingFor>[1];
  admission: AdmissionDetail;
}) {
  const { t } = useLocale();
  const wait = waitingFor(state, role);
  if (wait === "none") {
    return null;
  }
  // "You can onboard" is true only when nothing stops it: a code already out, or a clash shown under Next step, changes the line.
  if (wait === "onboard" && admission.joinPending) {
    return <p className="rounded-control bg-bg px-3 py-2 text-body text-text">{t("staffOffline.onboard.waitingStudent")}</p>;
  }
  if (wait === "onboard" && (admission.phoneOwnerName || admission.matchedHasBatch)) {
    return <p className="rounded-control bg-bg px-3 py-2 text-body text-text">{t("staffOffline.waiting.blocked")}</p>;
  }
  const key =
    wait === "student"
      ? "staffOffline.waiting.student"
      : wait === "review"
        ? "staffOffline.waiting.review"
        : wait === "onboard"
          ? "staffOffline.waiting.onboard"
          : "staffOffline.waiting.masterAdmin";
  return <p className="rounded-control bg-bg px-3 py-2 text-body text-text">{t(key)}</p>;
}

/** Each submission of the form, and each time it was sent back and why (plan D5). */
function SubmissionHistory({ admission }: { admission: AdmissionDetail }) {
  const { t } = useLocale();
  const { formatDateTime } = useFormat();
  return (
    <section aria-labelledby="submissions-title" className="grid gap-3">
      <h3 id="submissions-title" className="text-card-title text-text">
        {t("staffOffline.detail.submissions")}
      </h3>
      {admission.submissions.length === 0 ? (
        <p className="text-meta text-muted">{t("staffOffline.detail.noSubmissions")}</p>
      ) : (
        <ol className="grid gap-2">
          {admission.submissions.map((submission, index) => (
            <li key={submission.id} className="grid gap-1 rounded-control border border-border-control bg-card px-3 py-2">
              <span className="text-body text-text">
                {index === 0 ? t("staffOffline.detail.submittedOn") : t("staffOffline.detail.resubmittedOn")}{" "}
                <span className="text-muted">{formatDateTime(submission.submittedAt)}</span>
              </span>
              {submission.returnedReason ? (
                <span className="text-meta text-warn">
                  {t("staffOffline.detail.returnedOn", { date: submission.returnedAt ? formatDateTime(submission.returnedAt) : "" })}
                  {" · "}
                  {submission.returnedReason}
                </span>
              ) : null}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
