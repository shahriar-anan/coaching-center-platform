"use client";

import { Suspense, useEffect } from "react";
import { useRouter } from "next/navigation";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { SystemStatusList } from "@/components/staff/status/system-status-list";
import { RequireStaff } from "@/lib/auth/guards";
import { useLocale } from "@/i18n/locale-context";

export default function StaffSystemStatusPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]} notFound={<BackToStaffHome />}>
        <SystemStatus />
      </RequireStaff>
    </Suspense>
  );
}

function SystemStatus() {
  const { t } = useLocale();

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffAdmin.status.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("staffAdmin.status.title") }]}
      />
      <p className="text-meta text-muted">{t("staffAdmin.status.intro")}</p>
      <SystemStatusList />
    </div>
  );
}

/** System Admin and the other staff roles are sent back to the staff home. */
function BackToStaffHome() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/staff");
  }, [router]);

  return null;
}
