"use client";

import { Suspense, use, useEffect } from "react";
import { useRouter } from "next/navigation";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * The old per-module address. It stays as a deep link: it opens the submissions browser
 * with that module already chosen.
 */
export default function StaffModuleSubmissionsPage({ params }: { params: Promise<{ moduleId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={3} />}>
      <ModuleRedirect params={params} />
    </Suspense>
  );
}

function ModuleRedirect({ params }: { params: Promise<{ moduleId: string }> }) {
  const { moduleId } = use(params);
  const router = useRouter();

  useEffect(() => {
    router.replace(`/staff/submissions?module=${encodeURIComponent(moduleId)}`);
  }, [moduleId, router]);

  return <Skeleton variant="rows" count={3} />;
}
