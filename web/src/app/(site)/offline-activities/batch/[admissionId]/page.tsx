"use client";

import { Suspense, use } from "react";
import { MyBatchPage } from "@/components/student/my-admission/my-admission-page";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStudent } from "@/lib/auth/guards";

/** One of the student's offline batches (16 Nov): opened from a card on My batches. */
export default function Page({ params }: { params: Promise<{ admissionId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <BatchRoute params={params} />
    </Suspense>
  );
}

function BatchRoute({ params }: { params: Promise<{ admissionId: string }> }) {
  const { admissionId } = use(params);
  return (
    <RequireStudent>
      <MyBatchPage admissionId={admissionId} />
    </RequireStudent>
  );
}
