"use client";

import { RequireStudent } from "@/lib/auth/guards";
import { JoinPage } from "@/components/public-admission/join-page";

export default function Page() {
  return (
    <RequireStudent>
      <JoinPage />
    </RequireStudent>
  );
}
