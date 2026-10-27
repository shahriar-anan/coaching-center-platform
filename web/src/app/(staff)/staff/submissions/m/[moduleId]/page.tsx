"use client";

import { Suspense, use, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
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
  const search = useSearchParams();

  useEffect(() => {
    // Keep the other filters on the link, such as `exam`, so a count lands on that exam's list.
    const next = new URLSearchParams(search.toString());
    next.set("module", moduleId);
    router.replace(`/staff/submissions?${next.toString()}`);
  }, [moduleId, router, search]);

  return <Skeleton variant="rows" count={3} />;
}
