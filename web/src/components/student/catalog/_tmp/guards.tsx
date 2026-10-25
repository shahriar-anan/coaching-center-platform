// Stand-in for @/lib/auth/guards (WS0). Delete after the WS0 merge.
"use client";

import { useEffect, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useSession } from "@/lib/auth/session-context";
import { staffHref } from "./config";
import { useLocale } from "./locale";
import { Button, Skeleton } from "./ui";

export function useRole() {
  const { session } = useSession();
  const role = session.status === "authenticated" ? (session.user.role as string) : null;
  return { role, isStudent: role === "STUDENT" };
}

export function RequireStudent({ children }: { children: ReactNode }) {
  const { session } = useSession();
  const { t } = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    if (session.status === "anonymous") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [session.status, router, pathname]);
  if (session.status !== "authenticated") return <Skeleton variant="card" count={3} />;
  if (session.user.role !== "STUDENT") {
    return (
      <div className="rounded-card border border-border bg-card p-6 text-center">
        <p className="font-semibold">{t("catalog.guard.studentOnly")}</p>
        <Button variant="secondary" className="mt-3" href={staffHref("/staff")}>
          {t("catalog.guard.staffHome")}
        </Button>
      </div>
    );
  }
  return <>{children}</>;
}
