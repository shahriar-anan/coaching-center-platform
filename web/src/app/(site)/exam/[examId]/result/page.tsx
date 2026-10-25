"use client";

import { use } from "react";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";
import { ResultPage } from "@/components/student/result/result-page";

export default function ExamResultPage({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <ResultPage examId={examId} />
      </RequireStudent>
    </Suspense>
  );
}
