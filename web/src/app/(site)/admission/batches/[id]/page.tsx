"use client";

import { Suspense, use } from "react";
import { BatchPage } from "@/components/public-admission/batch-page";
import { Skeleton } from "@/components/ui/skeleton";

export default function Page({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <BatchRoute params={params} />
    </Suspense>
  );
}

function BatchRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <BatchPage id={id} />;
}
