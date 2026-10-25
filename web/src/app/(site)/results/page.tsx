"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";
import { ResultsHub } from "@/components/student/result/results-hub";

export default function ResultsPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <ResultsHub />
      </RequireStudent>
    </Suspense>
  );
}
