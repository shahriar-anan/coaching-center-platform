"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";
import { MyAdmissionPage } from "@/components/student/my-admission/my-admission-page";

/** The guard reads the URL (?next=), so it sits inside a Suspense boundary for the static build. */
export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <MyAdmissionPage />
      </RequireStudent>
    </Suspense>
  );
}
