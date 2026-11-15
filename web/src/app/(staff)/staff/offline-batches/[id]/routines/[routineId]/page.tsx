"use client";

import { Suspense, use } from "react";
import { RoutinePreview } from "@/components/staff/offline/routine-preview";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineRoutinePage({ params }: { params: Promise<{ id: string; routineId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RoutineRoute params={params} />
    </Suspense>
  );
}

function RoutineRoute({ params }: { params: Promise<{ id: string; routineId: string }> }) {
  const { id, routineId } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <RoutinePreview batchId={id} routineId={routineId} />
    </RequireStaff>
  );
}
