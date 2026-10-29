"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { CSSProperties, ReactNode } from "react";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { Bell, BookOpen, CalendarDays, CircleUserRound, Home, ListChecks, LogOut, Menu as MenuIcon, Receipt, Star, Trophy } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import { staffHome } from "@/lib/config";
import { Logo } from "@/components/shell/logo";
import { SiteFooter, type FooterVariant } from "@/components/shell/site-footer";
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

/**
 * The student's top bar (S1.1, plan Q2). The bottom bar takes over below the large breakpoint. MCQ and Written are reached
 * from Home, so they are not in the bar (Phase 4, A2). Their routes stay.
 */
const TOP_LINKS: { href: string; key: "home" | "myExams" | "results" | "routine" }[] = [
  { href: "/", key: "home" },
  { href: "/my-modules", key: "myExams" },
  { href: "/results", key: "results" },
  { href: "/routine", key: "routine" },
];

/** Routes where the paper is on screen: no tab bar, so it never covers the questions. */
const SITTING_PATH = /^\/exam\/[^/]+\/sit(\/|$)/;

/** Sign-in and sign-up pages on the student host: copyright and legal links only. */
const SIGN_IN_PATH = /^\/(login|register|set-phone|activate|code|forgot-password|reset-password)(\/|$)/;

/** The exam and package detail pages keep their own wide frame (detail-layout.tsx); every other page uses the full width. */
const DETAIL_PATH = /^\/(exam\/[^/]+|mcq\/m\/[^/]+|written\/m\/[^/]+)\/?$/;

function hidesTabBar(pathname: string): boolean {
  return SITTING_PATH.test(pathname);
}

/**
 * Which footer a route shows: none on the exam sitting page (a timed paper, no exits), minimal on
 * sign-in pages, full everywhere else on the student host. The staff and admin hosts have no footer.
 */
export function footerVariant(pathname: string): FooterVariant {
  if (SITTING_PATH.test(pathname)) return "none";
  if (SIGN_IN_PATH.test(pathname)) return "minimal";
  return "full";
}

/** The tab bar is 3.5 rem plus the safe area. A sticky action bar sits above it (WS9 FN-2). */
const tabBarVars = {
  "--tabbar-h": "calc(3.5rem + env(safe-area-inset-bottom))",
  "--sticky-pb": "0.75rem",
} as CSSProperties;

function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

/** Below 400 px the language and theme switches move into one menu, so the header fits at large text sizes. */
function SettingsMenu() {
  const { t } = useLocale();
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger
        aria-label={t("common.nav.openMenu")}
        className="hidden size-11 items-center justify-center rounded-full border border-border bg-card text-text hover:bg-bg max-[399px]:inline-flex"
      >
        <MenuIcon aria-hidden="true" className="size-5" />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          className="z-50 grid gap-2 rounded-card border border-border bg-card p-2 shadow-lg"
        >
          <LocalePill />
          <ThemePill />
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

function UserMenu() {
  const { t } = useLocale();
  const { session, logout } = useSession();
  if (session.status !== "authenticated") {
    // The old site's single pill: log in, a divider, sign up.
    const half = "inline-flex h-full items-center px-3 hover:bg-black/10 sm:px-4";
    return (
      <span className="inline-flex h-11 shrink-0 items-center overflow-hidden rounded-full bg-action text-label font-semibold text-on-action">
        <Link href="/login" prefetch={false} className={half}>
          {t("common.nav.login")}
        </Link>
        <span aria-hidden="true" className="h-5 w-px bg-on-action/40" />
        <Link href="/register" prefetch={false} className={half}>
          {t("common.nav.signUp")}
        </Link>
      </span>
    );
  }
  const user = session.user;
  // The API leaves the name out when the account has none, so read it as possibly missing.
  const fullName = user.fullName ?? "";
  const firstName = fullName.trim().split(/\s+/)[0] || fullName;
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger
        aria-label={t("common.nav.userMenu", { name: fullName })}
        className="inline-flex h-11 max-w-36 items-center gap-2 rounded-control px-3 text-label font-semibold text-text hover:bg-bg max-[379px]:max-w-11 max-[379px]:px-2"
      >
        <span className="truncate max-[379px]:hidden">{firstName}</span>
        <CircleUserRound aria-hidden="true" className="hidden size-5 max-[379px]:block" />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          className="z-50 grid min-w-48 gap-0.5 rounded-card border border-border bg-card p-1.5 text-body text-text shadow-lg"
        >
          <MenuLink href="/" icon={Home} label={t("common.nav.home")} />
          <MenuLink href="/account" icon={CircleUserRound} label={t("common.nav.myAccount")} />
          <MenuLink href="/account/payments" icon={Receipt} label={t("account.payments.link")} />
          <MenuLink href="/wishlist" icon={Star} label={t("common.nav.favourites")} />
          <MenuLink href="/notices" icon={Bell} label={t("common.nav.notices")} />
          <MenuLink href="/routine" icon={CalendarDays} label={t("common.nav.routine")} />
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
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-card pb-[env(safe-area-inset-bottom)] lg:hidden"
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
                      className="absolute -end-2 -top-1.5 flex min-w-4 items-center justify-center rounded-full bg-warn-fill px-1 text-pill text-on-warn tabular-nums"
                    >
                      {formatNumber(unreadCount, locale)}
                    </span>
                  ) : null}
                </span>
                {t(`common.nav.${tab.key}`)}
                {tab.key === "notices" && unreadCount > 0 ? (
                  <>{" "}<span className="sr-only">{t("common.unreadNotices", { count: formatNumber(unreadCount, locale) })}</span></>
                ) : null}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** The student's top bar, from desktop width up (S1.1). Visitors and staff never see it. */
function StudentLinks() {
  const { t } = useLocale();
  const pathname = usePathname() ?? "/";
  return (
    <nav aria-label={t("common.nav.studentMain")} className="hidden min-w-0 flex-1 justify-center lg:flex">
      <ul className="flex items-center gap-1 text-label font-semibold">
        {TOP_LINKS.map((link) => {
          const active = isActive(pathname, link.href);
          return (
            <li key={link.href}>
              <Link
                href={link.href}
                prefetch
                aria-current={active ? "page" : undefined}
                className={cn(
                  "inline-flex min-h-11 items-center whitespace-nowrap rounded-control px-3 hover:bg-bg hover:text-text",
                  active ? "text-text" : "text-muted",
                )}
              >
                {t(`common.nav.${link.key}`)}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** Landing-page links, from tablet width up. */
function WideNav() {
  const { t } = useLocale();
  // The old site's main menu. "Features" is now "Exams". Items without an exam-site page
  // show a "coming soon" page until the client decides what they become.
  const links = [
    { href: "/register", label: t("landing.navAdmission") },
    { href: "/mcq", label: t("landing.navExam") },
    { href: "/mcq", label: t("landing.navPackages") },
    { href: "/notices", label: t("landing.navNotices") },
    { href: "/publications", label: t("landing.navPublications") },
    { href: "/blog", label: t("landing.navBlog") },
    { href: "/media", label: t("landing.navMedia") },
    { href: "/resources", label: t("landing.navResources") },
  ];
  return (
    <nav aria-label={t("landing.navLabel")} className="hidden flex-1 justify-center md:flex">
      <ul className="flex items-center gap-6 text-label font-semibold text-muted">
        {links.map((link) => (
          <li key={link.label}>
            <Link href={link.href} prefetch={false} className="hover:text-text">
              {link.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/**
 * The student surface: a header, a footer and, on phones, a bottom tab bar for signed-in students.
 * Most pages use a 1024 px frame. The exam and package detail pages keep their own
 * wide frame, and the home page for everyone who is not a signed-in student is drawn wide too.
 * Pages render their content only.
 */
export function StudentShell({ children }: { children: ReactNode }) {
  const { t } = useLocale();
  const { session } = useSession();
  const pathname = usePathname() ?? "/";
  const signedIn = session.status === "authenticated";
  const showTabs = signedIn && session.user.role === "STUDENT" && !hidesTabBar(pathname);
  const student = signedIn && session.user.role === "STUDENT";
  const wide = pathname === "/" && !student;
  const roomy = !wide && !DETAIL_PATH.test(pathname);
  const column = wide ? "max-w-6xl" : roomy ? "max-w-5xl" : "max-w-[760px]";
  // The top bar needs the wider header from large width up; the content column stays 760 px on detail pages.
  const headerColumn = student ? "max-w-[760px] lg:max-w-5xl" : column;
  const footer = footerVariant(pathname);

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
        <div className={cn("mx-auto flex h-16 w-full items-center justify-between gap-2 px-4 max-[379px]:px-3", headerColumn)}>
          <Logo compact />
          {wide ? <WideNav /> : null}
          {student ? <StudentLinks /> : null}
          <div className="flex shrink-0 items-center gap-1.5 sm:gap-2">
            {signedIn && session.user.role === "STUDENT" ? <NoticeBell /> : null}
            <span className="flex items-center gap-1.5 max-[399px]:hidden sm:gap-2">
              <LocalePill />
              <ThemePill />
            </span>
            <SettingsMenu />
            <UserMenu />
          </div>
        </div>
      </header>
      <main
        id="main"
        className={cn(
          "mx-auto w-full flex-1",
          // The wide landing page sets its own gutters and column per section.
          !wide && (roomy ? "max-w-5xl px-4 pt-6 pb-10" : "max-w-[760px] px-4 pt-6 pb-10"),
          showTabs && "pb-[calc(5rem+env(safe-area-inset-bottom))] lg:pb-10",
        )}
      >
        {/* Lists and cards sit in a centred 768 px column; the sitting paper keeps the full frame. */}
        {roomy && !SITTING_PATH.test(pathname) ? <div className="mx-auto w-full max-w-3xl">{children}</div> : children}
      </main>
      {footer !== "none" ? <SiteFooter variant={footer} width={wide ? "max-w-6xl" : "max-w-5xl"} /> : null}
      {showTabs ? <TabBar /> : null}
      <UnsavedChangesDialog />
    </div>
  );
}
