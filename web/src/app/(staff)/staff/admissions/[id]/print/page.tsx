"use client";

import { Suspense, use } from "react";
import { AdmissionPrint } from "@/components/staff/offline/admission-print";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

export default function StaffAdmissionPrintPage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <PrintRoute params={params} />
    </Suspense>
  );
}

function PrintRoute({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN", "SYSTEM_ADMIN"]}>
      <AdmissionPrint id={id} />
    </RequireStaff>
  );
}
