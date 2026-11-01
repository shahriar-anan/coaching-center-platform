import type { Metadata } from "next";
import { Suspense } from "react";
import { PackagesPage } from "@/components/student/catalog/packages-page";
import { Skeleton } from "@/components/ui/skeleton";

export const metadata: Metadata = { title: "Packages" };

/** The filters read the URL (?category=&type=&q=), so the page sits inside a Suspense boundary for the static build. */
export default function Packages() {
  return (
    <Suspense fallback={<Skeleton variant="card" count={3} />}>
      <PackagesPage />
    </Suspense>
  );
}
