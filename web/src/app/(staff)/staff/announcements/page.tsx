"use client";

import { Suspense } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { AnnouncementForm } from "@/components/staff/announcements/announcement-form";
import { AnnouncementHistory } from "@/components/staff/announcements/announcement-history";
import { RequireStaff, useRole } from "@/lib/auth/guards";
import { useLocale } from "@/i18n/locale-context";

export default function StaffAnnouncementsPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <AnnouncementsPage />
      </RequireStaff>
    </Suspense>
  );
}

function AnnouncementsPage() {
  const { t } = useLocale();
  const { isMasterAdmin } = useRole();

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffAdmin.announce.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("staffAdmin.announce.title") }]}
      />
      {isMasterAdmin ? (
        <>
          <p className="text-meta text-muted">{t("staffAdmin.announce.intro")}</p>
          <AnnouncementForm />
          <AnnouncementHistory />
        </>
      ) : (
        <EmptyState title={t("staffAdmin.announce.title")} body={t("staffAdmin.announce.readOnly")} />
      )}
    </div>
  );
}
