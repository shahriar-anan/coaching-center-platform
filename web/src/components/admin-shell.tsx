"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/i18n/locale-context";
import { useSession, useUser } from "@/lib/auth/session-context";

export function AdminShell({ children }: { children: React.ReactNode }) {
  const { t } = useLocale();
  const { logout } = useSession();
  const user = useUser();
  const pathname = usePathname();
  const isMaster = user?.role === "MASTER_ADMIN";

  return (
    <div className="min-h-full flex flex-col bg-zinc-50 text-zinc-900">
      <header className="border-b border-zinc-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-6">
            <span className="font-semibold">{t("app.title")}</span>
            <nav className="flex gap-4 text-sm">
              <Link
                href="/students"
                className={
                  pathname.startsWith("/students")
                    ? "font-medium text-zinc-900"
                    : "text-zinc-600 hover:text-zinc-900"
                }
              >
                {t("nav.students")}
              </Link>
              {isMaster ? (
                <Link
                  href="/admins"
                  className={
                    pathname.startsWith("/admins")
                      ? "font-medium text-zinc-900"
                      : "text-zinc-600 hover:text-zinc-900"
                  }
                >
                  {t("nav.admins")}
                </Link>
              ) : null}
            </nav>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <LocaleSwitcher />
            {user ? (
              <span className="hidden text-zinc-600 sm:inline">{user.fullName}</span>
            ) : null}
            <button
              type="button"
              onClick={() => logout()}
              className="rounded-md border border-zinc-300 px-3 py-1.5 hover:bg-zinc-100"
            >
              {t("nav.logout")}
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">{children}</main>
    </div>
  );
}
