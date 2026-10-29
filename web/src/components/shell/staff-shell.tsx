"use client";

import { useState, useSyncExternalStore, type ReactNode } from "react";
import { Menu, LogOut, PanelLeftClose, PanelLeftOpen, Search } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { Logo } from "@/components/shell/logo";
import { UnsavedChangesDialog } from "@/components/shell/unsaved-changes-dialog";
import { LocalePill, ThemePill } from "@/components/shell/pills";
import { currentNavCollapsed, setNavCollapsed, staffNavFor, subscribeNavCollapsed } from "@/components/shell/staff-nav";
import { StaffNavList, StaffSidebar } from "@/components/shell/staff-sidebar";
import { RoleChip, StaffProfileMenu } from "@/components/shell/staff-profile-menu";
import { Button } from "@/components/ui/button";
import { StaffQuickSearch } from "@/components/shell/staff-quick-search";
import { Dialog } from "@/components/ui/dialog";
import { IconButton } from "@/components/ui/icon-button";
import { cn } from "@/components/ui/cn";
import { usePendingChanges } from "@/lib/api/exam/staff-exams";
import { useRole } from "@/lib/auth/guards";

/**
 * The admin surface: a sidebar (240 px, or an 80 px icon rail the person can collapse; the choice is
 * remembered in this browser), and a header with the menu, search, language, theme and the profile
 * menu. Under 768 px the sidebar becomes a drawer behind the menu button.
 */
export function StaffShell({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  const { session, logout } = useSession();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  // The server renders the sidebar expanded; the browser reads the stored choice after the first render.
  const collapsed = useSyncExternalStore(subscribeNavCollapsed, currentNavCollapsed, () => false);
  const { canAuthor, canSeeStudents } = useRole();
  // The phone search is offered only where the search itself works.
  const canSearch = canAuthor || canSeeStudents;
  const role = session.status === "authenticated" ? session.user.role : null;
  const items = staffNavFor(role);
  // The pending count is for the Master Admin and the System Admin only; other roles never call it.
  const canSeePending = role === "MASTER_ADMIN" || role === "SYSTEM_ADMIN";
  const pending = usePendingChanges(canSeePending);
  const pendingCount = pending.data?.items.length ?? 0;

  function toggleCollapsed() {
    setNavCollapsed(!collapsed);
  }

  return (
    <div
      data-surface="staff"
      className={cn(
        "min-h-dvh bg-bg text-text transition-[grid-template-columns] duration-200 motion-reduce:transition-none md:grid",
        collapsed ? "md:grid-cols-[80px_1fr]" : "md:grid-cols-[240px_1fr]",
      )}
    >
      <StaffSidebar items={items} collapsed={collapsed} pendingCount={pendingCount} />

      <Dialog open={drawerOpen} onOpenChange={setDrawerOpen} title={t("common.nav.menu")}>
        <div className="grid gap-4 rounded-card bg-sidebar p-3 text-sidebar-text">
          {role ? <RoleChip role={role} /> : null}
          <nav aria-label={t("common.nav.main")}>
            <StaffNavList items={items} pendingCount={pendingCount} onNavigate={() => setDrawerOpen(false)} />
          </nav>
        </div>
        <div className="mt-5 grid gap-3 border-t border-border pt-4 min-[480px]:hidden">
          <div className="flex flex-wrap items-center gap-2">
            <LocalePill />
            <ThemePill />
          </div>
          <Button variant="secondary" onClick={() => void logout()} className="min-h-11 justify-start px-3">
            <LogOut aria-hidden="true" className="size-4" />
            {t("common.nav.logout")}
          </Button>
        </div>
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
            <IconButton
              label={collapsed ? t("common.staffNav.expand") : t("common.staffNav.collapse")}
              icon={collapsed ? <PanelLeftOpen aria-hidden="true" className="size-5" /> : <PanelLeftClose aria-hidden="true" className="size-5" />}
              aria-expanded={!collapsed}
              onClick={toggleCollapsed}
              className="hidden md:inline-flex"
            />
            <Logo href="/staff" compact className="min-h-11 shrink-0 text-card-title md:hidden" />
            <div className="hidden min-w-0 flex-1 md:block">
              <StaffQuickSearch />
            </div>
            <div className="ms-auto flex items-center gap-2">
              {canSearch ? (
                <IconButton
                  label={t("common.quickSearch.open")}
                  icon={<Search aria-hidden="true" className="size-5" />}
                  onClick={() => setSearchOpen(true)}
                  className="shrink-0 md:hidden"
                />
              ) : null}
              {/* Under 480 px the language and theme controls move into the menu, so the header fits at large text sizes. */}
              <div className="hidden items-center gap-2 min-[480px]:flex">
                <LocalePill />
                <ThemePill />
              </div>
              <StaffProfileMenu />
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
