"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useBackToReview, useMarkReady, useRequestChanges, type AdmissionDetail } from "@/lib/api/exam/offline-admin";
import { canMarkReady } from "./offline-model";
import { offlineErrorText } from "./offline-error";
import { SendBackDialog } from "./send-back-dialog";
import { CloseDialog, OnboardDialog } from "./admission-dialogs";

/**
 * For an application from the website with no account yet: whether the onboarding will join an existing account (matched by email, then
 * phone, plan Q3) or make a new one. Nothing is shown for an application from a signed-in student or from the desk.
 */
function MatchLine({ admission }: { admission: AdmissionDetail }) {
  const { t } = useLocale();
  if (admission.source !== "PUBLIC") {
    return null;
  }
  // 13 Nov D3: a phone that belongs to another account is a warning, never a match. Onboarding is refused until the form is fixed.
  if (admission.phoneOwnerName) {
    return (
      <p role="alert" className="rounded-control border border-warn/50 bg-warn/5 px-3 py-2 text-body text-text">
        {t("staffOffline.match.phoneOwner", { name: admission.phoneOwnerName })}
      </p>
    );
  }
  if (!admission.matchedName) {
    return <p className="text-meta text-muted">{t("staffOffline.match.newAccount")}</p>;
  }
  return (
    <>
      <p className="rounded-control bg-bg px-3 py-2 text-body text-text">
        {admission.matchedEmail
          ? t("staffOffline.match.matches", { name: admission.matchedName, email: admission.matchedEmail })
          : t("staffOffline.match.matchesNoEmail", { name: admission.matchedName })}
      </p>
      {/* Shown from the review on, so staff learn it before the setup, not only when onboarding is refused. */}
      {admission.matchedHasBatch ? (
        <p role="alert" className="rounded-control border border-warn/50 bg-warn/5 px-3 py-2 text-body text-text">
          {t("staffOffline.match.hasBatch", { name: admission.matchedName })}
        </p>
      ) : null}
    </>
  );
}

/**
 * The one step the person can take now (plan 6.3, principle 1). Actions the role cannot take are not shown: staff never see Onboard, and
 * a line says who acts next instead. Ready for approval stays off until the checklist is all ticked.
 */
export function PrimaryActions({
  admission,
  canReview,
  canOnboard,
}: {
  admission: AdmissionDetail;
  canReview: boolean;
  canOnboard: boolean;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const ready = useMarkReady(admission.id);
  const requestChanges = useRequestChanges(admission.id);
  const backToReview = useBackToReview(admission.id);
  const [sendingBack, setSendingBack] = useState(false);
  const [onboarding, setOnboarding] = useState(false);
  const [closing, setClosing] = useState(false);
  const state = admission.state;
  const readyAllowed = canMarkReady(admission.readyProblems, state);

  function fail(error: unknown) {
    toast({ title: offlineErrorText(error, t), tone: "danger" });
  }

  return (
    <section aria-labelledby="actions-title" className="grid gap-3">
      <h3 id="actions-title" className="text-card-title text-text">
        {t("staffOffline.actions.title")}
      </h3>

      {state === "SUBMITTED" && canReview ? (
        <>
          <MatchLine admission={admission} />
          <Button
            fullWidth
            disabled={!readyAllowed}
            loading={ready.isPending}
            onClick={() =>
              ready.mutate(undefined, {
                onSuccess: () => toast({ title: t("staffOffline.actions.readyDone"), tone: "ok" }),
                onError: fail,
              })
            }
          >
            {t("staffOffline.actions.ready")}
          </Button>
          {!readyAllowed ? <p className="text-meta text-muted">{t("staffOffline.actions.readyOff")}</p> : null}
          <Button fullWidth variant="secondary" onClick={() => setSendingBack(true)}>
            {t("staffOffline.actions.sendBack")}
          </Button>
        </>
      ) : null}

      {state === "READY" && canReview ? (
        <>
          <MatchLine admission={admission} />
          {admission.joinPending ? (
            <p className="text-meta text-muted">{t("staffOffline.onboard.waitingStudent")}</p>
          ) : canOnboard ? (
            <Button
              fullWidth
              disabled={admission.matchedHasBatch || Boolean(admission.phoneOwnerName)}
              onClick={() => setOnboarding(true)}
            >
              {t("staffOffline.actions.onboard")}
            </Button>
          ) : (
            <p className="text-meta text-muted">{t("staffOffline.actions.waitMasterOnboard")}</p>
          )}
          <Button
            fullWidth
            variant="secondary"
            loading={backToReview.isPending}
            onClick={() =>
              backToReview.mutate(undefined, {
                onSuccess: () => toast({ title: t("staffOffline.actions.backDone"), tone: "ok" }),
                onError: fail,
              })
            }
          >
            {t("staffOffline.actions.backToReview")}
          </Button>
          <Button fullWidth variant="secondary" onClick={() => setSendingBack(true)}>
            {t("staffOffline.actions.sendBack")}
          </Button>
        </>
      ) : null}

      {state === "CHANGES_REQUESTED" ? <p className="text-meta text-muted">{t("staffOffline.waiting.student")}</p> : null}
      {state === "AWAITING_FORM" ? <p className="text-meta text-muted">{t("staffOffline.waiting.notSubmitted")}</p> : null}

      {canOnboard && state !== "ONBOARDED" && state !== "CLOSED" ? (
        <Button fullWidth variant="secondary" onClick={() => setClosing(true)}>
          {t("staffOffline.actions.close")}
        </Button>
      ) : null}

      <Button fullWidth variant="secondary" onClick={() => window.open(`/staff/admissions/${admission.id}/print`, "_blank", "noopener")}>
        {t("staffOffline.actions.print")}
      </Button>

      <SendBackDialog
        open={sendingBack}
        onOpenChange={setSendingBack}
        onSubmit={async (reason) => {
          try {
            await requestChanges.mutateAsync(reason);
            toast({ title: t("staffOffline.actions.sentBack"), tone: "ok" });
          } catch (error) {
            fail(error);
            throw error;
          }
        }}
      />

      {canOnboard ? <OnboardDialog admission={admission} open={onboarding} onOpenChange={setOnboarding} /> : null}
      {canOnboard ? <CloseDialog admission={admission} open={closing} onOpenChange={setClosing} /> : null}
    </section>
  );
}
