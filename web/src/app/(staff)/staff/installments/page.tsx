"use client";

import { Suspense } from "react";
import { InstallmentsList } from "@/components/staff/installments/installments-list";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

/** The Master Admin's installment plans. The URL may carry studentId, examId or moduleId, from the links on other screens. */
export default function StaffInstallmentsPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <InstallmentsList />
      </RequireStaff>
    </Suspense>
  );
}
