"use client";

import { Suspense, use } from "react";
import { BatchDetail } from "@/components/staff/offline/batch-detail";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineBatchPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <BatchRoute params={params} />
    </Suspense>
  );
}

function BatchRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <BatchDetail id={id} />
    </RequireStaff>
  );
}
