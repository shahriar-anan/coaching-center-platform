import type { Metadata } from "next";
import { StaffAuthShell } from "@/components/shell/staff-auth-shell";
import { StudentShell } from "@/components/shell/student-shell";
import { requestSurface } from "@/lib/url/request-surface";
import { SurfaceProvider } from "@/lib/url/surface-context";

/** The admin host's sign-in pages are never indexed. */
export async function generateMetadata(): Promise<Metadata> {
  return (await requestSurface()) === "staff" ? { robots: { index: false, follow: false } } : {};
}

/**
 * Sign-in and sign-up pages. On the student host: the student shell without the dashboard tabs.
 * On the admin host: a plain frame for administrators, staff and instructors.
 */
export default async function AuthLayout({ children }: { children: React.ReactNode }) {
  const surface = await requestSurface();
  return (
    <SurfaceProvider surface={surface}>
      {surface === "staff" ? <StaffAuthShell>{children}</StaffAuthShell> : <StudentShell>{children}</StudentShell>}
    </SurfaceProvider>
  );
}
