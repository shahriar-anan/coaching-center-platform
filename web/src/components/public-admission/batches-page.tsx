"use client";

import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { offlineAdmissionEnabled } from "@/lib/config";
import { usePublicBatches } from "@/lib/api/exam/public-admission";
import { BatchCard } from "./batch-card";

/**
 * `/admission`: the offline batches as cards (plan 8, P2). Open batches come first, as the API orders them. With the offline switch off
 * the page is not there; the landing menu sends visitors to the register page instead.
 */
export function AdmissionBatchesPage() {
  const { t } = useLocale();
  const batches = usePublicBatches(offlineAdmissionEnabled);
  if (!offlineAdmissionEnabled) {
    return <NotFoundState title={t("common.notFound.title")} body={t("common.notFound.body")} backHref="/" backLabel={t("common.guard.goHome")} />;
  }
  return (
    <div className="grid gap-5">
      <PageHeader title={t("publicAdmission.list.title")} breadcrumbs={[{ label: t("catalog.home"), href: "/" }, { label: t("publicAdmission.list.title") }]} />
      <p className="text-body text-muted">{t("publicAdmission.list.lead")}</p>
      {batches.isPending ? (
        <Skeleton variant="card" count={3} />
      ) : batches.isError ? (
        <ErrorState error={batches.error} onRetry={() => void batches.refetch()} />
      ) : (batches.data ?? []).length === 0 ? (
        <EmptyState title={t("publicAdmission.list.empty")} body={t("publicAdmission.list.emptyBody")} />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {(batches.data ?? []).map((batch) => (
            <li key={batch.id} className="min-w-0">
              <BatchCard batch={batch} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
