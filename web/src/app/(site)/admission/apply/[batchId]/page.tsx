"use client";

import { Suspense, use } from "react";
import { ApplyPage } from "@/components/public-admission/apply-page";
import { Skeleton } from "@/components/ui/skeleton";

export default function Page({ params }: { params: Promise<{ batchId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <ApplyRoute params={params} />
    </Suspense>
  );
}

function ApplyRoute({ params }: { params: Promise<{ batchId: string }> }) {
  const { batchId } = use(params);
  return <ApplyPage batchId={batchId} />;
}
