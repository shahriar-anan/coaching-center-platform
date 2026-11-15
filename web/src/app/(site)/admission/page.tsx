"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { AdmissionBatchesPage } from "@/components/public-admission/batches-page";

// The page reads the address (sign-in guard, filters): a production build needs a Suspense boundary around it.
export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <AdmissionBatchesPage />
    </Suspense>
  );
}
