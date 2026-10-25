"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { CSSProperties, ReactNode } from "react";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { Bell, BookOpen, CircleUserRound, Home, ListChecks, LogOut, Trophy } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { staffHome } from "@/lib/config";
import { Logo } from "@/components/shell/logo";
import { LocalePill, ThemePill } from "@/components/shell/pills";
import { NoticeBell } from "@/components/student/notice-bell";
import { formatNumber } from "@/lib/format";
import { useUnreadCount } from "@/lib/api/exam/notices";
import { OfflineBanner } from "@/components/ui/offline-banner";
import { UnsavedChangesDialog } from "@/components/shell/unsaved-changes-dialog";
import { cn } from "@/components/ui/cn";

type TabItem = { href: string; key: "home" | "myExams" | "results" | "notices" | "account"; icon: typeof Home };

const TABS: TabItem[] = [
  { href: "/", key: "home", icon: Home },
  { href: "/my-modules", key: "myExams", icon: BookOpen },
  { href: "/results", key: "results", icon: Trophy },
  { href: "/notices", key: "notices", icon: Bell },
  { href: "/account", key: "account", icon: CircleUserRound },
];

/** Routes where the paper is on screen: no tab bar, so it never covers the questions. */
function hidesTabBar(pathname: string): boolean {
  return /^\/exam\/[^/]+\/sit(\/|$)/.test(pathname);
}

/** The tab bar is 3.5 rem plus the safe area. A sticky action bar sits above it (WS9 FN-2). */
const tabBarVars = {
  "--tabbar-h": "calc(3.5rem + env(safe-area-inset-bottom))",
  "--sticky-pb": "0.75rem",
} as CSSProperties;

function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

function UserMenu() {
  const { t } = useLocale();
  const { session, logout } = useSession();
  if (session.status !== "authenticated") {
    return (
      <Link
        href="/login"
        prefetch={false}
        className="inline-flex h-11 items-center rounded-control px-4 text-label font-semibold text-text hover:bg-bg"
      >
        {t("common.nav.login")}
      </Link>
    );
  }
  const user = session.user;
  const firstName = user.fullName.trim().split(/\s+/)[0] || user.fullName;
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger
        aria-label={t("common.nav.userMenu", { name: user.fullName })}
        className="inline-flex h-11 max-w-36 items-center gap-2 rounded-control px-3 text-label font-semibold text-text hover:bg-bg"
      >
        <span className="truncate">{firstName}</span>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          className="z-50 grid min-w-48 gap-0.5 rounded-card border border-border bg-card p-1.5 text-body text-text shadow-lg"
        >
          <MenuLink href="/" icon={Home} label={t("common.nav.home")} />
          <MenuLink href="/account" icon={CircleUserRound} label={t("common.nav.myAccount")} />
          <MenuLink href="/notices" icon={Bell} label={t("common.nav.notices")} />
          {user.role !== "STUDENT" ? (
            <MenuLink href={staffHome} icon={ListChecks} label={t("common.nav.adminPanel")} external />
          ) : null}
          <DropdownMenu.Separator className="my-1 h-px bg-border" />
          <DropdownMenu.Item
            onSelect={() => void logout()}
            className="flex min-h-11 cursor-pointer items-center gap-3 rounded-control px-3 outline-none data-[highlighted]:bg-bg"
          >
            <LogOut aria-hidden="true" className="size-4" />
            {t("common.nav.logout")}
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

function MenuLink({
  href,
  icon: Icon,
  label,
  external,
}: {
  href: string;
  icon: typeof Home;
  label: string;
  external?: boolean;
}) {
  const className =
    "flex min-h-11 items-center gap-3 rounded-control px-3 outline-none data-[highlighted]:bg-bg";
  return (
    <DropdownMenu.Item asChild>
      {external ? (
        <a href={href} className={className}>
          <Icon aria-hidden="true" className="size-4" />
          {label}
        </a>
      ) : (
        <Link href={href} prefetch className={className}>
          <Icon aria-hidden="true" className="size-4" />
          {label}
        </Link>
      )}
    </DropdownMenu.Item>
  );
}

function TabBar() {
  const { t, locale } = useLocale();
  const pathname = usePathname() ?? "/";
  // Same query as the header bell, so the badge costs no extra request.
  const { data: unread } = useUnreadCount();
  const unreadCount = unread?.count ?? 0;
  return (
    <nav
      aria-label={t("common.nav.tabs")}
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <ul className="mx-auto grid h-14 max-w-[760px] grid-cols-5">
        {TABS.map((tab) => {
          const active = isActive(pathname, tab.href);
          const Icon = tab.icon;
          return (
            <li key={tab.href}>
              <Link
                href={tab.href}
                prefetch
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex h-full flex-col items-center justify-center gap-0.5 text-pill font-medium",
                  active ? "text-text-accent" : "text-muted",
                )}
              >
                <span className="relative inline-flex">
                  <Icon aria-hidden="true" className={cn("size-5", active && "text-accent")} />
                  {tab.key === "notices" && unreadCount > 0 ? (
                    <span
                      aria-hidden="true"
                      className="absolute -end-2 -top-1.5 flex min-w-4 items-center justify-center rounded-full bg-warn-fill px-1 text-pill text-white tabular-nums"
                    >
                      {formatNumber(unreadCount, locale)}
                    </span>
                  ) : null}
                </span>
                {t(`common.nav.${tab.key}`)}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/**
 * The student surface: a 760 px column with a header, a footer and, on phones,
 * a bottom tab bar for signed-in students. Pages render their content only.
 */
export function StudentShell({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  const { session } = useSession();
  const pathname = usePathname() ?? "/";
  const signedIn = session.status === "authenticated";
  const showTabs = signedIn && session.user.role === "STUDENT" && !hidesTabBar(pathname);

  return (
    <div
      data-surface="site"
      data-tabbar={showTabs ? "true" : undefined}
      style={showTabs ? tabBarVars : undefined}
      className="flex min-h-dvh flex-col bg-bg text-text"
    >
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:start-3 focus:top-3 focus:z-50 focus:rounded-control focus:bg-card focus:px-4 focus:py-2"
      >
        {t("common.nav.skipToContent")}
      </a>
      <OfflineBanner />
      <header className="sticky top-0 z-20 border-b border-border bg-card/95 backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-[760px] items-center justify-between gap-2 px-4">
          <Logo />
          <div className="flex items-center gap-1.5 sm:gap-2">
            {signedIn && session.user.role === "STUDENT" ? <NoticeBell /> : null}
            <LocalePill />
            <ThemePill />
            <UserMenu />
          </div>
        </div>
      </header>
      <main
        id="main"
        className={cn(
          "mx-auto w-full max-w-[760px] flex-1 px-4 pt-6 pb-10",
          showTabs && "pb-[calc(5rem+env(safe-area-inset-bottom))] md:pb-10",
        )}
      >
        {children}
      </main>
      <footer className="border-t border-border bg-card">
        <nav aria-label={t("common.footer.label")} className="mx-auto grid w-full max-w-[760px] gap-3 px-4 py-6">
          <ul className="flex flex-wrap gap-x-5 gap-y-2 text-meta text-muted">
            <li>
              <Link href="/help" prefetch={false} className="hover:text-text hover:underline">
                {t("common.footer.help")}
              </Link>
            </li>
            <li>
              <Link href="/about" prefetch={false} className="hover:text-text hover:underline">
                {t("common.footer.about")}
              </Link>
            </li>
            <li>
              <Link href="/contact" prefetch={false} className="hover:text-text hover:underline">
                {t("common.footer.contact")}
              </Link>
            </li>
            <li>
              <Link href="/terms" prefetch={false} className="hover:text-text hover:underline">
                {t("common.footer.terms")}
              </Link>
            </li>
            <li>
              <Link href="/privacy" prefetch={false} className="hover:text-text hover:underline">
                {t("common.footer.privacy")}
              </Link>
            </li>
            <li>
              <Link href="/refund" prefetch={false} className="hover:text-text hover:underline">
                {t("common.footer.refund")}
              </Link>
            </li>
          </ul>
          <p className="text-meta text-muted">{t("common.footer.copyright")}</p>
        </nav>
      </footer>
      {showTabs ? <TabBar /> : null}
      <UnsavedChangesDialog />
    </div>
  );
}
