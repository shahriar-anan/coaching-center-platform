"use client";

import { Suspense } from "react";
import { PaymentRecordsPage } from "@/components/staff/payment-records/payment-records-page";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffPaymentRecordsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <PaymentRecordsPage />
      </RequireStaff>
    </Suspense>
  );
}
