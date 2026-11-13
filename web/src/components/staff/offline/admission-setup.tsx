"use client";

import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useAdmission, type AdmissionDetail } from "@/lib/api/exam/offline-admin";
import { isAfterOnboarding } from "./offline-model";
import { ItemsPanel } from "./items-panel";
import { SetupForm } from "./setup-form";
import { SetupSide } from "./setup-side";
import { setupTotals, useSetupDraft } from "./setup-draft";

/**
 * The setup of one admission on its own screen (plan 6.3). Card 1 is the batch and the fees, card 2 the exams and packages; the side
 * summary holds the student, the totals, the ready checklist and the primary action. Staff and the Master Admin can change it while the
 * form is under review. Once the admission is ready (Back to review unlocks it) or past review, every card is read-only.
 */
export function AdmissionSetupView({ id }: { id: string }) {
  const admission = useAdmission(id);

  if (admission.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (admission.isError) {
    return <ErrorState onRetry={() => admission.refetch()} error={admission.error} />;
  }
  return <SetupScreen admission={admission.data} />;
}

function SetupScreen({ admission: a }: { admission: AdmissionDetail }) {
  const { t } = useLocale();
  const role = useRole();
  const after = isAfterOnboarding(a.state);
  const canReview = role.canSeeAdmissions;
  const editable = a.state === "SUBMITTED" && canReview;
  const canEditItems = canReview && !after && a.state !== "READY";
  const name = a.form.fullName?.trim() || "—";
  const setup = useSetupDraft(a, editable);
  const totals = setupTotals(setup.draft);

  const note = after
    ? t("staffOffline.setup.lockedDone")
    : a.state === "READY"
      ? t("staffOffline.setup.lockedReady")
      : a.state !== "SUBMITTED"
        ? t("staffOffline.setup.lockedForm")
        : null;

  return (
    <div className="grid gap-6 max-lg:pb-40">
      <PageHeader
        title={t("staffOffline.setup.title")}
        breadcrumbs={[
          { label: t("common.staffNav.admissions"), href: "/staff/admissions" },
          { label: name, href: `/staff/admissions/${a.id}` },
          { label: t("staffOffline.setup.title") },
        ]}
        actions={
          <Button variant="secondary" size="sm" href={`/staff/admissions/${a.id}`}>
            {t("staffOffline.setup.backToAdmission")}
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(22rem,26rem)] lg:items-start">
        <div className="grid min-w-0 content-start gap-6">
          {note ? <p className="rounded-control bg-bg px-3 py-2 text-body text-text">{note}</p> : null}
          <SetupForm
            id="setup-batch-title"
            step={1}
            draft={setup.draft}
            onEdit={setup.edit}
            errors={setup.errors}
            editable={editable}
            savedBatch={a.setup}
          />
          <ItemsPanel
            id="setup-items-title"
            step={2}
            admission={a}
            canEdit={canEditItems}
            accessUntil={setup.draft.courseEndsOn || null}
          />
        </div>

        <SetupSide admission={a} totals={totals} status={setup.status} pending={setup.pending} canReview={canReview} />
      </div>
    </div>
  );
}
