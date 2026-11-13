"use client";

import { Suspense } from "react";
import { FeeApprovals } from "@/components/staff/offline/fee-approvals";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineFeesPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <FeeApprovals />
      </RequireStaff>
    </Suspense>
  );
}
