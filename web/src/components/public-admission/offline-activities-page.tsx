"use client";

import { MyAdmissionPage } from "@/components/student/my-admission/my-admission-page";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { BatchOffers } from "./batch-offers";

/**
 * `/offline-activities` (plan P15): the one place for a student's offline life. A student sees My admission first, one section per batch
 * they are in, then the published batches they are not in. Anyone else (a visitor, or staff) sees the published batches as cards.
 */
export function OfflineActivitiesPage() {
  const { t } = useLocale();
  const { session } = useSession();
  const title = t("publicAdmission.activities.title");

  if (session.status === "loading") {
    return <Skeleton variant="card" count={2} />;
  }
  if (session.status === "authenticated" && session.user.role === "STUDENT") {
    return <MyAdmissionPage title={title} offers />;
  }
  return (
    <>
      <PageHeader title={title} breadcrumbs={[{ label: t("catalog.home"), href: "/" }, { label: title }]} />
      <BatchOffers excludeBatchIds={[]} />
    </>
  );
}
