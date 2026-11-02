"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RetakeRequestsPage } from "@/components/staff/retake-requests/retake-requests-page";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffRetakeRequestsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={4} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <RetakeRequestsPage />
      </RequireStaff>
    </Suspense>
  );
}
