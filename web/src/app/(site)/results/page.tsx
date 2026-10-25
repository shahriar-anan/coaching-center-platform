"use client";

import { RequireStudent } from "@/lib/auth/guards";
import { ResultsHub } from "@/components/student/result/results-hub";

export default function ResultsPage() {
  return (
    <RequireStudent>
      <ResultsHub />
    </RequireStudent>
  );
}
