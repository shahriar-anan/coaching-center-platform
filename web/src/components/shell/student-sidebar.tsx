"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, CalendarDays, CircleUserRound, ClipboardList, GraduationCap, Home, LayoutDashboard, Star, Trophy, type LucideIcon } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { Logo } from "@/components/shell/logo";
import { cn } from "@/components/ui/cn";
import { useUnreadCount } from "@/lib/api/exam/notices";
import { formatNumber } from "@/lib/format";
import { offlineAdmissionEnabled } from "@/lib/config";

type StudentNavKey = "dashboard" | "myBatches" | "home" | "myExams" | "results" | "routine" | "notices" | "favourites" | "account";
// `match` is the path that marks the item current, for a link that carries a query.
type StudentNavItem = { href: string; key: StudentNavKey; icon: LucideIcon; match?: string };
type StudentNavGroup = "study" | "personal";

/**
 * The student's pages, grouped as the staff sidebar groups its own. Every item links to a live page; exam pages
 * (the paper, the detail pages, MCQ and Written) are reached from Home and My exams, so they are not listed here.
 */
const GROUPS: { group: StudentNavGroup; items: StudentNavItem[] }[] = [
  {
    group: "study",
    items: [
      // 16 Nov: Dashboard is the student's own page (login lands there); Home is the site's landing page.
      { href: "/dashboard", key: "dashboard", icon: LayoutDashboard },
      // 17 Nov: the student's offline batches, while offline admission is on.
      ...(offlineAdmissionEnabled
        ? [{ href: "/offline-activities?tab=mine", key: "myBatches", icon: GraduationCap, match: "/offline-activities" } satisfies StudentNavItem]
        : []),
      { href: "/", key: "home", icon: Home },
      { href: "/my-modules", key: "myExams", icon: ClipboardList },
      { href: "/results", key: "results", icon: Trophy },
      { href: "/routine", key: "routine", icon: CalendarDays },
    ],
  },
  {
    group: "personal",
    items: [
      { href: "/notices", key: "notices", icon: Bell },
      { href: "/wishlist", key: "favourites", icon: Star },
      { href: "/account", key: "account", icon: CircleUserRound },
    ],
  },
];

const NAV_KEY = "student-nav-collapsed";

// The choice is read once from storage and then kept in memory, as the staff menu keeps its own choice.
let collapsedMemory: boolean | null = null;
const collapsedListeners = new Set<() => void>();

function readStoredCollapsed(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  try {
    return window.localStorage.getItem(NAV_KEY) === "1";
  }
  catch {
    // Private windows can block storage; the menu then starts expanded.
    return false;
  }
}

export function currentStudentNavCollapsed(): boolean {
  collapsedMemory ??= readStoredCollapsed();
  return collapsedMemory;
}

export function setStudentNavCollapsed(collapsed: boolean): void {
  collapsedMemory = collapsed;
  try {
    window.localStorage.setItem(NAV_KEY, collapsed ? "1" : "0");
  }
  catch {
    // The choice still applies for this visit.
  }
  collapsedListeners.forEach((listener) => listener());
}

export function subscribeStudentNavCollapsed(listener: () => void): () => void {
  collapsedListeners.add(listener);
  return () => collapsedListeners.delete(listener);
}

function isActive(pathname: string, href: string): boolean {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}

/**
 * The student's menu items, grouped. The side menu shows them from desktop width up; below that the header's menu button
 * opens them in a drawer (17 Nov), and `onNavigate` closes it.
 */
export function StudentNavList({ collapsed = false, onNavigate }: { collapsed?: boolean; onNavigate?: () => void }) {
  const { t, locale } = useLocale();
  const pathname = usePathname() ?? "/";
  const { data: unread } = useUnreadCount();
  const idSuffix = onNavigate ? "-drawer" : "";
  // Results counts the unread results; Notices counts every unread notice. Other items have no count.
  const countFor = (key: StudentNavKey): number => {
    if (key === "notices") return unread?.count ?? 0;
    if (key === "results") return unread?.byGroup?.RESULTS ?? 0;
    return 0;
  };
  return (
    <div className="grid gap-4">
      {GROUPS.map(({ group, items }) => (
        <section key={group} aria-labelledby={`student-nav-${group}${idSuffix}`} className="grid gap-1">
          <h2
            id={`student-nav-${group}${idSuffix}`}
            className={cn("px-3 pt-1 text-pill font-semibold uppercase tracking-wide text-muted", collapsed && "sr-only")}
          >
            {t(`common.studentNavGroup.${group}`)}
          </h2>
          {collapsed ? <hr aria-hidden="true" className="mx-2 border-border" /> : null}
          <ul className="grid gap-1">
            {items.map((item) => {
              const active = isActive(pathname, item.match ?? item.href);
              const Icon = item.icon;
              const label = t(`common.nav.${item.key}`);
              const count = countFor(item.key);
              return (
                <li key={item.href} className="relative">
                  <Link
                    href={item.href}
                    prefetch
                    onClick={onNavigate}
                    aria-current={active ? "page" : undefined}
                    title={collapsed ? label : undefined}
                    className={cn(
                      "flex min-h-11 w-full items-center gap-3 rounded-control px-3 text-label transition-colors hover:bg-bg hover:text-text",
                      collapsed && "justify-center px-0",
                      active ? "bg-bg font-semibold text-text" : "text-muted",
                    )}
                  >
                    {/* The bar on the left edge marks the current page, so colour is not the only cue. */}
                    {active ? <span aria-hidden="true" className="absolute inset-y-2 start-0 w-1 rounded-full bg-accent" /> : null}
                    <Icon aria-hidden="true" className={cn("size-5 shrink-0", active && "text-text-accent")} />
                    <span className={cn("min-w-0 flex-1 truncate text-start", collapsed && "sr-only")}>{label}</span>
                    {count > 0 && !collapsed ? (
                      <span className="rounded-full bg-warn-fill px-2 py-0.5 text-pill font-semibold text-on-warn tabular-nums">
                        {formatNumber(count, locale)}
                        <span className="sr-only">{t("common.unreadNotices", { count: formatNumber(count, locale) })}</span>
                      </span>
                    ) : null}
                    {count > 0 && collapsed ? (
                      <span aria-hidden="true" className="absolute end-2 top-2 size-2.5 rounded-full bg-warn-fill" />
                    ) : null}
                  </Link>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

/**
 * The student's side menu, from desktop width up. It is full height and holds the logo, as the staff sidebar does.
 * It collapses to an 80 px icon rail; the shell keeps the choice. Below desktop width the header's menu button opens the
 * same items in a drawer.
 */
export function StudentSidebar({ collapsed }: { collapsed: boolean }) {
  const { t } = useLocale();
  return (
    <aside
      data-collapsed={collapsed || undefined}
      className={cn(
        "sticky top-0 hidden h-dvh shrink-0 flex-col gap-4 border-e border-border bg-card py-4 transition-[width] duration-200 motion-reduce:transition-none lg:flex",
        collapsed ? "w-20" : "w-60",
      )}
    >
      <div className={cn("flex min-h-11 items-center", collapsed ? "justify-center px-2" : "px-4")}>
        <Logo wordmark={!collapsed} className="min-w-0" />
      </div>
      <nav
        aria-label={t("common.nav.main")}
        className={cn("min-h-0 flex-1 overflow-x-hidden overflow-y-auto", collapsed ? "px-2" : "px-3")}
      >
        <StudentNavList collapsed={collapsed} />
      </nav>
    </aside>
  );
}
