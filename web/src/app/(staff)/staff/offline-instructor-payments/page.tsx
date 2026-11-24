"use client";

import { Suspense } from "react";
import { InstructorPayments } from "@/components/staff/instructor-pay/instructor-payments-page";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineInstructorPaymentsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <InstructorPayments />
      </RequireStaff>
    </Suspense>
  );
}
