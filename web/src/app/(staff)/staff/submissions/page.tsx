"use client";

import { Suspense } from "react";
import { SubmissionBrowser } from "@/components/staff/marking/submission-browser";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

/** The submitted papers are the Master Admin's and the teacher's work. The System Admin has no submissions page. */
export default function StaffSubmissionsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "INSTRUCTOR"]}>
        <SubmissionBrowser />
      </RequireStaff>
    </Suspense>
  );
}
