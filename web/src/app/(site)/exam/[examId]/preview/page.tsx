"use client";

import { use } from "react";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";
import { ExamPreview } from "@/components/student/catalog/exam-preview";

export default function ExamPreviewPage({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <ExamPreview examId={examId} />
      </RequireStudent>
    </Suspense>
  );
}
