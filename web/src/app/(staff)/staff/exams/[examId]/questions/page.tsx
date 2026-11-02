"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { RequireStaff } from "@/lib/auth/guards";
import { Skeleton } from "@/components/ui/skeleton";
import { QuestionPage } from "@/components/staff/questions/question-page";

function QuestionsRoute() {
  const { examId } = useParams<{ examId: string }>();
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <QuestionPage examId={examId} />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <QuestionsRoute />
    </Suspense>
  );
}
