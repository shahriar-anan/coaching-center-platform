"use client";

import { use } from "react";
import { RequireStudent } from "@/lib/auth/guards";
import { LeaderboardPage } from "@/components/student/result/leaderboard-page";

export default function ExamLeaderboardPage({ params }: { params: Promise<{ examId: string }> }) {
  const { examId } = use(params);
  return (
    <RequireStudent>
      <LeaderboardPage examId={examId} />
    </RequireStudent>
  );
}
