"use client";

import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { offlineAdmissionEnabled } from "@/lib/config";
import { usePublicBatches } from "@/lib/api/exam/public-admission";
import { BatchCard } from "./batch-card";

/**
 * The published batches as cards, the same cards as the Admission page (P15). A batch in `excludeBatchIds` is left out: the batches the
 * student is already in are shown above, as their My admission sections.
 */
export function BatchOffers({ excludeBatchIds }: { excludeBatchIds: string[] }) {
  const { t } = useLocale();
  const batches = usePublicBatches(offlineAdmissionEnabled);
  const shown = (batches.data ?? []).filter((batch) => !excludeBatchIds.includes(batch.id));

  return (
    <section aria-labelledby="batch-offers" className="mt-8 grid gap-4">
      <h2 id="batch-offers" className="text-card-title font-bold text-text">
        {t("publicAdmission.activities.offers")}
      </h2>
      {batches.isPending ? (
        <Skeleton variant="card" count={2} />
      ) : batches.isError ? (
        <ErrorState error={batches.error} onRetry={() => void batches.refetch()} />
      ) : shown.length === 0 ? (
        <EmptyState title={t("publicAdmission.list.empty")} body={t("publicAdmission.list.emptyBody")} />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {shown.map((batch) => (
            <li key={batch.id} className="min-w-0">
              <BatchCard batch={batch} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
