"use client";

import { Suspense, use } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";
import { SubmittedPage } from "@/components/student/sitting/submitted-page";

export default function ExamSubmittedPage({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <SubmittedPage examId={examId} />
      </RequireStudent>
    </Suspense>
  );
}
