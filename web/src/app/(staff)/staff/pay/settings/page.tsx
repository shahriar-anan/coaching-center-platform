"use client";

import { Suspense } from "react";
import { PayDefaultsPage } from "@/components/staff/pay/pay-defaults-form";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffPaySettingsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={3} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <PayDefaultsPage />
      </RequireStaff>
    </Suspense>
  );
}
