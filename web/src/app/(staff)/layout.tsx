import type { Metadata } from "next";
import { StaffShell } from "@/components/shell/staff-shell";
import { RequireStaff } from "@/lib/auth/guards";
import { Suspense } from "react";
import { Skeleton } from "@/components/ui/skeleton";

/** The admin panel is never indexed. The student site stays indexable. */
export const metadata: Metadata = {
  robots: { index: false, follow: false },
};

export default function StaffLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStaff>
        <StaffShell>{children}</StaffShell>
      </RequireStaff>
    </Suspense>
  );
}
