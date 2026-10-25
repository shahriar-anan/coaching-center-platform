import { StudentShell } from "@/components/shell/student-shell";

/** Sign-in and sign-up pages: the student shell without the dashboard tabs. */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return <StudentShell>{children}</StudentShell>;
}
