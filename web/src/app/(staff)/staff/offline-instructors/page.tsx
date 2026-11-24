"use client";

import { Suspense } from "react";
import { InstructorList } from "@/components/staff/instructor-pay/instructor-list";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineInstructorsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <InstructorList />
      </RequireStaff>
    </Suspense>
  );
}
