"use client";

import { Suspense } from "react";
import { MyPayPage } from "@/components/staff/pay/my-pay-page";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffMyPayPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["SYSTEM_ADMIN", "INSTRUCTOR"]}>
        <MyPayPage />
      </RequireStaff>
    </Suspense>
  );
}
