"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { RequireStaff } from "@/lib/auth/guards";
import { Skeleton } from "@/components/ui/skeleton";
import { ExamEditor } from "@/components/staff/catalog/exam-editor";

function ExamPage() {
  const { examId } = useParams<{ examId: string }>();
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <ExamEditor examId={examId} />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <ExamPage />
    </Suspense>
  );
}
