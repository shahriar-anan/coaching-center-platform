"use client";

import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { ReviewsPage } from "@/components/staff/reviews/reviews-page";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffReviewsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN", "INSTRUCTOR"]}>
        <ReviewsPage />
      </RequireStaff>
    </Suspense>
  );
}
