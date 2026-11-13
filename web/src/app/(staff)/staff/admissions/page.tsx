"use client";

import { Suspense } from "react";
import { AdmissionQueue } from "@/components/staff/offline/admission-queue";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffAdmissionsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <AdmissionQueue />
      </RequireStaff>
    </Suspense>
  );
}
