"use client";

import { Suspense, use } from "react";
import { ReopenPage } from "@/components/public-admission/reopen-page";
import { Skeleton } from "@/components/ui/skeleton";

export default function Page({ params }: { params: Promise<{ linkToken: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <ReopenRoute params={params} />
    </Suspense>
  );
}

function ReopenRoute({ params }: { params: Promise<{ linkToken: string }> }) {
  const { linkToken } = use(params);
  return <ReopenPage linkToken={linkToken} />;
}
