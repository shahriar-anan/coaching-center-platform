import type { Metadata } from "next";
import { StaffShell } from "@/components/shell/staff-shell";
import { RequireStaff } from "@/lib/auth/guards";

/** The admin panel is never indexed. The student site stays indexable. */
export const metadata: Metadata = {
  robots: { index: false, follow: false },
};

export default function StaffLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireStaff>
      <StaffShell>{children}</StaffShell>
    </RequireStaff>
  );
}
