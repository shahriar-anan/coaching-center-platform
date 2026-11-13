"use client";

import { Suspense } from "react";
import { BatchList } from "@/components/staff/offline/batch-list";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineBatchesPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <BatchList />
      </RequireStaff>
    </Suspense>
  );
}
