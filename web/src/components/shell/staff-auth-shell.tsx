"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Logo } from "@/components/shell/logo";
import { LocalePill, ThemePill } from "@/components/shell/pills";
import { siteHref } from "@/lib/config";

/**
 * Sign-in and recovery pages on the admin host: a plain frame for administrators, staff and instructors.
 * No student navigation, no sign-up call to action.
 */
export function StaffAuthShell({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  return (
    <div data-surface="staff" className="flex min-h-dvh flex-col bg-bg text-text">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-50 focus:rounded-control focus:bg-card focus:px-4 focus:py-2"
      >
        {t("common.nav.skipToContent")}
      </a>
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex min-h-16 w-full max-w-6xl items-center justify-between gap-3 px-4">
          <Logo href="/login" compact />
          <div className="flex items-center gap-2">
            <LocalePill />
            <ThemePill />
          </div>
        </div>
      </header>
      <main id="main" className="mx-auto flex w-full max-w-[440px] flex-1 flex-col justify-center px-4 py-10">
        {children}
      </main>
      <footer className="border-t border-border px-4 py-5 text-center text-meta text-muted">
        <p>{t("auth.staffLogin.footer")}</p>
        <Link href={siteHref("/")} className="font-semibold text-text-accent underline underline-offset-4">
          {t("auth.staffLogin.studentSite")}
        </Link>
      </footer>
    </div>
  );
}
