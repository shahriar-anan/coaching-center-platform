"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { OfflineActivitiesPage } from "@/components/public-admission/offline-activities-page";

// The page reads the address (sign-in guard, filters): a production build needs a Suspense boundary around it.
export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <OfflineActivitiesPage />
    </Suspense>
  );
}
