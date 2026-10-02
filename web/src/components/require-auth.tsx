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
    }
  }, [session.status, router]);

  if (session.status === "loading") {
    return (
      <p className="p-8 text-center text-zinc-600">{t("auth.restoring")}</p>
    );
  }

  if (session.status !== "authenticated") {
    return null;
  }

  return <>{children}</>;
}
