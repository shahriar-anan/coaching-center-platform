"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { NoticeBoardPage } from "@/components/staff/notice-board/notice-board-page";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffNoticeBoardPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
        <NoticeBoardPage />
      </RequireStaff>
    </Suspense>
  );
}
