"use client";

import { Suspense } from "react";
import { useParams } from "next/navigation";
import { PayPersonPage } from "@/components/staff/pay/pay-person-page";
import { Skeleton } from "@/components/ui/skeleton";
import { RequireStaff } from "@/lib/auth/guards";

function PersonPay() {
  const { userId } = useParams<{ userId: string }>();
  return (
    <RequireStaff roles={["MASTER_ADMIN"]}>
      <PayPersonPage userId={userId} />
    </RequireStaff>
  );
}

export default function Page() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <PersonPay />
    </Suspense>
  );
}
