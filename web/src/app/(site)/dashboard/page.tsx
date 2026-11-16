import type { Metadata } from "next";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { DashboardPage } from "@/components/student/home/home-page";

export const metadata: Metadata = { title: "Dashboard" };

/**
 * The student's dashboard (16 Nov). Login lands here; "/" is the landing page. The page reads the address (sign-in guard), so a
 * production build needs a Suspense boundary around it.
 */
export default function Dashboard() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <DashboardPage />
    </Suspense>
  );
}
