"use client";

import { Suspense, use } from "react";
import { InstallmentPlanScreen } from "@/components/staff/installments/installment-plan-detail";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

/** One installment plan: its schedule, payments and actions. Master Admin only. */
export default function StaffInstallmentPlanPage({ params }: { params: Promise<{ planId: string }> }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <PlanRoute params={params} />
    </Suspense>
  );
}

function PlanRoute({ params }: { params: Promise<{ planId: string }> }) {
  const { planId } = use(params);
  return (
    <RequireStaff roles={["MASTER_ADMIN"]}>
      <InstallmentPlanScreen planId={planId} />
    </RequireStaff>
  );
}
