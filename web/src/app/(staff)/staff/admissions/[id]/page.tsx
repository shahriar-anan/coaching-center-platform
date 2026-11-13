"use client";

import { Suspense, use } from "react";
import { AdmissionDetailView } from "@/components/staff/offline/admission-detail";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffAdmissionPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <AdmissionRoute params={params} />
    </Suspense>
  );
}

function AdmissionRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <AdmissionDetailView id={id} />
    </RequireStaff>
  );
}
