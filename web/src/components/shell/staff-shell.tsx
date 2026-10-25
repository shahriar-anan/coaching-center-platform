"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { Menu, LogOut, Search } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { Logo } from "@/components/shell/logo";
import { UnsavedChangesDialog } from "@/components/shell/unsaved-changes-dialog";
import { LocalePill, ThemePill } from "@/components/shell/pills";
import { staffNavFor, type StaffNavItem } from "@/components/shell/staff-nav";
import { Button } from "@/components/ui/button";
import { StaffQuickSearch } from "@/components/shell/staff-quick-search";
import { Dialog } from "@/components/ui/dialog";
import { IconButton } from "@/components/ui/icon-button";
import { cn } from "@/components/ui/cn";
import type { Role } from "@/lib/api/exam/types";
import { useRole } from "@/lib/auth/guards";

function isActive(pathname: string, href: string): boolean {
  return href === "/staff" ? pathname === "/staff" : pathname === href || pathname.startsWith(`${href}/`);
}

function RoleChip({ role }: { role: Role }) {
  const { t } = useLocale();
  if (role === "STUDENT") {
    return null;
  }
  return (
    <span className="inline-flex rounded-control bg-action/10 px-2.5 py-1 text-pill text-action">
      {t(`common.roles.${role}`)}
    </span>
  );
}

function NavList({ items, onNavigate }: { items: StaffNavItem[]; onNavigate?: () => void }) {
  const { t } = useLocale();
  const pathname = usePathname() ?? "/staff";
  return (
    <ul className="grid gap-1">
      {items.map((item) => {
        const active = isActive(pathname, item.href);
        return (
          <li key={item.key}>
            <Link
              href={item.href}
              prefetch
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex min-h-10 items-center rounded-control px-3 text-label font-medium",
                active ? "bg-action/10 text-action" : "text-text hover:bg-bg",
              )}
            >
              {t(`common.staffNav.${item.key}`)}
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * The admin surface: a 232 px sidebar with the nav for the signed-in role, and
 * a top bar with the page title, language, theme and the person's name. Under
 * 768 px the sidebar becomes a drawer behind a menu button.
 */
export function StaffShell({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  const { session, logout } = useSession();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const { canAuthor, canSeeStudents } = useRole();
  // The phone search is offered only where the search itself works.
  const canSearch = canAuthor || canSeeStudents;
  const role = session.status === "authenticated" ? session.user.role : null;
  const items = staffNavFor(role);

  return (
    <div data-surface="staff" className="min-h-dvh bg-bg text-text md:grid md:grid-cols-[232px_1fr]">
      <aside className="hidden border-e border-border bg-card md:sticky md:top-0 md:flex md:h-dvh md:flex-col md:gap-5 md:p-4">
        <Logo href="/staff" className="px-2 text-section" />
        {role ? <RoleChip role={role} /> : null}
        <nav aria-label={t("common.nav.main")} className="min-h-0 flex-1 overflow-y-auto">
          <NavList items={items} />
        </nav>
      </aside>

      <Dialog open={drawerOpen} onOpenChange={setDrawerOpen} title={t("common.nav.menu")}>
        {role ? <RoleChip role={role} /> : null}
        <NavList items={items} onNavigate={() => setDrawerOpen(false)} />
      </Dialog>

      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-20 border-b border-border bg-card">
          <div className="flex min-h-14 items-center gap-2 px-4 md:px-6">
            <IconButton
              label={t("common.nav.openMenu")}
              icon={<Menu aria-hidden="true" className="size-5" />}
              onClick={() => setDrawerOpen(true)}
              className="md:hidden"
            />
            <div className="flex min-w-0 flex-1 items-center gap-3">
              <Logo href="/staff" className="text-base md:hidden" />
              <div className="hidden min-w-0 flex-1 md:block">
                <StaffQuickSearch />
              </div>
            </div>
            <div className="flex items-center gap-2">
              {canSearch ? (
                <IconButton
                  label={t("common.quickSearch.open")}
                  icon={<Search aria-hidden="true" className="size-5" />}
                  onClick={() => setSearchOpen(true)}
                  className="md:hidden"
                />
              ) : null}
              <LocalePill />
              <ThemePill />
              {session.status === "authenticated" ? (
                <span className="hidden text-meta text-muted sm:inline">{session.user.fullName}</span>
              ) : null}
              <Button variant="secondary" onClick={() => void logout()} className="min-h-10 px-3">
                <LogOut aria-hidden="true" className="size-4" />
                <span className="hidden sm:inline">{t("common.nav.logout")}</span>
              </Button>
            </div>
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1120px] flex-1 px-4 py-6 md:px-6">{children}</main>
      </div>
      <UnsavedChangesDialog />
      {canSearch ? (
        <Dialog open={searchOpen} onOpenChange={setSearchOpen} title={t("common.quickSearch.label")}>
          <StaffQuickSearch />
        </Dialog>
      ) : null}
    </div>
  );
}
