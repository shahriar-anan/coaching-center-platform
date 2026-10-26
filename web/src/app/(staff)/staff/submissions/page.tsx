"use client";

import { Suspense } from "react";
import { SubmissionBrowser } from "@/components/staff/marking/submission-browser";
import { Skeleton } from "@/components/ui/skeleton";
import { ALL_STAFF, RequireStaff } from "@/lib/auth/guards";

export default function StaffSubmissionsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={ALL_STAFF}>
        <SubmissionBrowser />
      </RequireStaff>
    </Suspense>
  );
}
