"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { session } = useSession();
  const router = useRouter();
  const { t } = useLocale();

  useEffect(() => {
    if (session.status === "anonymous") {
      router.replace("/login");
    } else if (session.status === "authenticated" && session.user.role === "STUDENT") {
      router.replace("/purchase/result");
    }
  }, [session, router]);

  if (session.status === "loading") {
    return (
      <p className="p-8 text-center text-muted">{t("auth.restoring")}</p>
    );
  }

  if (session.status !== "authenticated" || session.user.role === "STUDENT") {
    return null;
  }

  return <>{children}</>;
}
