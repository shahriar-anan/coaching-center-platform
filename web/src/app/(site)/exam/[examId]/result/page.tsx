"use client";

import { use } from "react";
import { RequireStudent } from "@/lib/auth/guards";
import { ResultPage } from "@/components/student/result/result-page";

export default function ExamResultPage({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <RequireStudent>
      <ResultPage examId={examId} />
    </RequireStudent>
  );
}
