"use client";

import { Suspense } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { AdminHome } from "@/components/staff/home/admin-home";
import { InstructorHome } from "@/components/staff/home/instructor-home";

function StaffHome() {
  const { t } = useLocale();
  const role = useRole();
  return (
    <>
      <PageHeader title={t("staffCatalog.home.title")} />
      {role.isInstructor ? <InstructorHome /> : <AdminHome isMasterAdmin={role.isMasterAdmin} />}
    </>
  );
}

export default function StaffHomePage() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <StaffHome />
    </Suspense>
  );
}
