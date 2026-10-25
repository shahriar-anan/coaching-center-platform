import { StudentShell } from "@/components/shell/student-shell";

export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return <StudentShell>{children}</StudentShell>;
}
