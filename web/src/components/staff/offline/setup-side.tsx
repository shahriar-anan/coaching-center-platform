"use client";

import { useState } from "react";
import { cn } from "@/components/ui/cn";
import { Button } from "@/components/ui/button";
import { Pill } from "@/components/ui/pill";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useBackToReview, useMarkReady, type AdmissionDetail } from "@/lib/api/exam/offline-admin";
import { AdmissionPhoto } from "./admission-photo";
import { offlineErrorText } from "./offline-error";
import { canMarkReady, isAfterOnboarding } from "./offline-model";
import { ReadyChecklist } from "./ready-checklist";
import { stateLabelKey } from "./labels";
import type { SaveStatus, setupTotals } from "./setup-draft";

/**
 * The summary beside the setup: the student, the totals of the dues, the counts, the ready checklist, the primary action and the save
 * status. On a phone it is a bar at the bottom of the screen: the totals, the action and a Details toggle that opens the rest.
 * Mark ready stays off until the checklist is ticked and nothing is still saving, and says why.
 */
export function SetupSide({
  admission,
  totals,
  status,
  pending,
  canReview,
}: {
  admission: AdmissionDetail;
  totals: ReturnType<typeof setupTotals>;
  status: SaveStatus;
  /** True while a change is still on its way to the server. */
  pending: boolean;
  canReview: boolean;
}) {
  const { t } = useLocale();
  const { formatDate, formatMoney, formatNumber } = useFormat();
  const [open, setOpen] = useState(false);
  const state = admission.state;
  const name = admission.form.fullName?.trim() || "—";
  const packages = admission.items.filter((item) => item.scope === "MODULE").length;
  const exams = admission.items.length - packages;

  const saveText = pending || status === "saving"
    ? t("staffOffline.setup.saving")
    : status === "saved"
      ? t("staffOffline.setupScreen.saved")
      : status === "error"
        ? t("staffOffline.setup.notSaved")
        : "";

  const reason = canMarkReady(admission.readyProblems, state) && !pending
    ? null
    : pending
      ? t("staffOffline.setupScreen.summary.savingFirst")
      : t("staffOffline.setupScreen.summary.missing", { count: admission.readyProblems.length });

  const compact = totals.months === 0 ? "—" : t("staffOffline.setupScreen.totals.compact", { total: formatMoney(totals.total) ?? "", count: formatNumber(totals.months) });

  return (
    <aside
      aria-label={t("staffOffline.setupScreen.summary.title")}
      className={cn(
        "grid content-start gap-4 border-border-control bg-card",
        // A bar along the bottom on a phone; a card beside the setup, kept in view while the form scrolls, from the lg breakpoint.
        "max-lg:fixed max-lg:inset-x-0 max-lg:bottom-0 max-lg:z-20 max-lg:max-h-[78dvh] max-lg:overflow-y-auto max-lg:border-t max-lg:p-3 max-lg:shadow-[0_-8px_24px_rgb(0_0_0/0.08)]",
        "lg:sticky lg:top-6 lg:rounded-card lg:border lg:p-5",
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="grid min-w-0 gap-0.5">
          <span className="font-bold text-text">{compact}</span>
          <span role="status" aria-live="polite" className="min-h-5 text-meta text-muted">
            {saveText}
          </span>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button variant="ghost" size="sm" className="lg:hidden" onClick={() => setOpen((value) => !value)} aria-expanded={open}>
            {t(open ? "staffOffline.setupScreen.summary.hide" : "staffOffline.setupScreen.summary.show")}
          </Button>
          <SideAction admission={admission} canReview={canReview} allowed={reason === null} reason={reason} />
        </div>
      </div>

      <div className={cn("grid gap-4", !open && "hidden lg:grid")}>
        <div className="flex items-center gap-3">
          <AdmissionPhoto admissionId={admission.id} hasPhoto={admission.hasPhoto} name={name} />
          <div className="grid min-w-0 gap-0.5">
            <span className="break-words font-bold text-text">{name}</span>
            <span className="font-latin text-meta text-muted">{admission.form.mobile}</span>
            <span>
              <Pill tone="muted">{t(stateLabelKey(state))}</Pill>
            </span>
          </div>
        </div>

        <dl className="grid gap-3 text-body">
          <div className="grid gap-0.5">
            <dt className="text-meta text-muted">{t("staffOffline.setupScreen.totals.label")}</dt>
            <dd className="font-latin text-text">
              {totals.months === 0
                ? t("staffOffline.setup.duesEmpty")
                : t("staffOffline.setupScreen.totals.line", { count: formatNumber(totals.months), total: formatMoney(totals.total) ?? "" })}
            </dd>
          </div>
          {totals.firstDue && totals.lastDue ? (
            <div className="grid gap-0.5">
              <dt className="text-meta text-muted">{t("staffOffline.setupScreen.totals.rangeLabel")}</dt>
              <dd className="text-text">{t("staffOffline.setupScreen.totals.range", { first: formatDate(totals.firstDue), last: formatDate(totals.lastDue) })}</dd>
            </div>
          ) : null}
          <div className="grid gap-0.5">
            <dt className="text-meta text-muted">{t("staffOffline.setup.summary.items")}</dt>
            <dd className="text-text">
              {t("staffOffline.setupScreen.counts", { packages: formatNumber(packages), exams: formatNumber(exams) })}
            </dd>
          </div>
        </dl>

        {state === "SUBMITTED" ? <ReadyChecklist problems={admission.readyProblems} /> : null}
        {state === "READY" ? <p className="text-meta text-muted">{t("staffOffline.checklist.readyNote")}</p> : null}
        {isAfterOnboarding(state) ? <p className="text-meta text-muted">{t("staffOffline.setup.lockedDone")}</p> : null}
      </div>
    </aside>
  );
}

/** The one primary action: Mark ready while the review waits, Back to review once it is ready, nothing after that. */
function SideAction({
  admission,
  canReview,
  allowed,
  reason,
}: {
  admission: AdmissionDetail;
  canReview: boolean;
  allowed: boolean;
  reason: string | null;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const ready = useMarkReady(admission.id);
  const back = useBackToReview(admission.id);

  if (!canReview) {
    return null;
  }
  if (admission.state === "SUBMITTED") {
    return (
      <div className="grid justify-items-end gap-1">
        <Button
          disabled={!allowed}
          loading={ready.isPending}
          aria-describedby={reason ? "setup-ready-reason" : undefined}
          onClick={() =>
            ready.mutate(undefined, {
              onSuccess: () => toast({ title: t("staffOffline.actions.readyDone"), tone: "ok" }),
              onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
            })
          }
        >
          {t("staffOffline.setupScreen.summary.markReady")}
        </Button>
        {reason ? (
          <span id="setup-ready-reason" className="text-end text-meta text-muted">
            {reason}
          </span>
        ) : null}
      </div>
    );
  }
  if (admission.state === "READY") {
    return (
      <Button
        variant="secondary"
        loading={back.isPending}
        onClick={() =>
          back.mutate(undefined, {
            onSuccess: () => toast({ title: t("staffOffline.actions.backDone"), tone: "ok" }),
            onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
          })
        }
      >
        {t("staffOffline.actions.backToReview")}
      </Button>
    );
  }
  return null;
}
