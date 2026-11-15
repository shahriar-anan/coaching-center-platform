"use client";

import { Suspense, use } from "react";
import { OfflineLeaderboardPage } from "@/components/student/my-admission/offline-leaderboard-page";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";

export default function OfflineResultPage({ params }: { params: Promise<{ admissionId: string; classId: string }> }) {
  const { admissionId, classId } = use(params);
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RequireStudent>
        <OfflineLeaderboardPage admissionId={admissionId} classId={classId} />
      </RequireStudent>
    </Suspense>
  );
}
