"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { offlineAdmissionEnabled } from "@/lib/config";
import { isPendingAdmission } from "@/lib/admission/gate";
import { useMyAdmission } from "@/lib/api/exam/offline-admission";
import { AdmissionFormView } from "./admission-form";
import { PrintSheet } from "./print-sheet";
import { UnderReview } from "./under-review";

function Loading() {
  const { t } = useLocale();
  return (
    <div className="py-6" aria-busy="true">
      <span className="sr-only">{t("common.states.loading")}</span>
      <Skeleton variant="card" count={2} />
    </div>
  );
}

/**
 * `/admission`: the form while the admission is open, the under-review view once it is submitted. A student with no
 * admission, or one already admitted, has nothing to do here, so they go home (the page is not theirs to show).
 */
export function AdmissionPage() {
  const router = useRouter();
  const admission = useMyAdmission({ enabled: offlineAdmissionEnabled });
  const data = admission.data;
  // While the offline admission switch is off this page has nothing to show, so it goes home.
  const leave = !offlineAdmissionEnabled || (admission.isSuccess && !isPendingAdmission(data?.state));

  useEffect(() => {
    if (leave) {
      router.replace("/dashboard");
    }
  }, [leave, router]);

  if (!offlineAdmissionEnabled) {
    return null;
  }
  if (admission.isPending) {
    return <Loading />;
  }
  if (admission.isError) {
    return <ErrorState onRetry={() => void admission.refetch()} error={admission.error} />;
  }
  if (!data || leave) {
    return null;
  }
  if (data.state === "SUBMITTED" || data.state === "READY") {
    return <UnderReview admission={data} />;
  }
  return <AdmissionFormView admission={data} />;
}

/** `/admission/print`: the registration form as an A4 sheet. A student with no admission is sent home. */
export function AdmissionPrintPage() {
  const router = useRouter();
  const admission = useMyAdmission();
  const data = admission.data;
  const missing = admission.isSuccess && !data;

  useEffect(() => {
    if (missing) {
      router.replace("/dashboard");
    }
  }, [missing, router]);

  if (admission.isPending) {
    return <Loading />;
  }
  if (admission.isError) {
    return <ErrorState onRetry={() => void admission.refetch()} error={admission.error} />;
  }
  if (!data) {
    return null;
  }
  return <PrintSheet admission={data} />;
}
