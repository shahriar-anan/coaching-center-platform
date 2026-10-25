"use client";

import { use } from "react";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";
import { LeaderboardPage } from "@/components/student/result/leaderboard-page";

export default function ExamLeaderboardPage({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <LeaderboardPage examId={examId} />
      </RequireStudent>
    </Suspense>
  );
}
