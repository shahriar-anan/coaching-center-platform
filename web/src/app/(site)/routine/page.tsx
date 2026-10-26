"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";
import { RoutinePage } from "@/components/student/catalog/routine-page";

/** The guard reads the URL (?next=), so it sits inside a Suspense boundary for the static build. */
export default function Routine() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <RoutinePage />
      </RequireStudent>
    </Suspense>
  );
}
