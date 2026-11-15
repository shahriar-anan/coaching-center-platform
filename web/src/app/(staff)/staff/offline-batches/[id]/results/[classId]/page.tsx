"use client";

import { Suspense, use } from "react";
import { ExamResultSheet } from "@/components/staff/offline/result-sheet-view";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffOfflineExamResultPage({ params }: { params: Promise<{ id: string; classId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <ResultRoute params={params} />
    </Suspense>
  );
}

function ResultRoute({ params }: { params: Promise<{ id: string; classId: string }> }) {
  const { id, classId } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <ExamResultSheet batchId={id} classId={classId} />
    </RequireStaff>
  );
}
