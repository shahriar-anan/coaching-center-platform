"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { ExamsPage } from "@/components/staff/exams/exams-page";

/** The staff Exams page. The list reads the URL, so the page is inside Suspense. */
export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <ExamsPage />
    </Suspense>
  );
}
