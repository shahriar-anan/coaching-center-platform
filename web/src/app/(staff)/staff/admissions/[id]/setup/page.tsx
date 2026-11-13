"use client";

import { Suspense, use } from "react";
import { AdmissionSetupView } from "@/components/staff/offline/admission-setup";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffAdmissionSetupPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <SetupRoute params={params} />
    </Suspense>
  );
}

function SetupRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <AdmissionSetupView id={id} />
    </RequireStaff>
  );
}
