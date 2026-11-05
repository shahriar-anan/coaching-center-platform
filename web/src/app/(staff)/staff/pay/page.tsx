"use client";

import { Suspense } from "react";
import { PayPeoplePage } from "@/components/staff/pay/pay-people-page";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffPayPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <PayPeoplePage />
      </RequireStaff>
    </Suspense>
  );
}
