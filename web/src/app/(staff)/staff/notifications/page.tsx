"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { NotificationsPage } from "@/components/staff/notifications/notifications-page";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffNotificationsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff>
        <NotificationsPage />
      </RequireStaff>
    </Suspense>
  );
}
