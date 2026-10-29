"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { PaymentsList } from "@/components/staff/ops/payments-list";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffPaymentsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <PaymentsList />
      </RequireStaff>
    </Suspense>
  );
}
