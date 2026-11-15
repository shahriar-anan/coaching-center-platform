"use client";

import { Suspense, use } from "react";
import { InstructorDetail } from "@/components/staff/instructor-pay/instructor-detail";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineInstructorPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <InstructorRoute params={params} />
    </Suspense>
  );
}

function InstructorRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <InstructorDetail id={id} />
    </RequireStaff>
  );
}
