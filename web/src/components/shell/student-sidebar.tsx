"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, CalendarDays, CircleUserRound, ClipboardList, Home, Star, Trophy, type LucideIcon } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { Logo } from "@/components/shell/logo";
import { cn } from "@/components/ui/cn";

type StudentNavKey = "home" | "myExams" | "results" | "routine" | "notices" | "favourites" | "account";
type StudentNavItem = { href: string; key: StudentNavKey; icon: LucideIcon };
type StudentNavGroup = "study" | "personal";

/**
 * The student's pages, grouped as the staff sidebar groups its own. Every item links to a live page; exam pages
 * (the paper, the detail pages, MCQ and Written) are reached from Home and My exams, so they are not listed here.
 */
const GROUPS: { group: StudentNavGroup; items: StudentNavItem[] }[] = [
  {
    group: "study",
    items: [
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
 * The student's side menu, from desktop width up. It is full height and holds the logo, as the staff sidebar does.
 * It collapses to an 80 px icon rail; the shell keeps the choice. Below desktop width the bottom tab bar takes over.
 */
export function StudentSidebar({ collapsed }: { collapsed: boolean }) {
  const { t } = useLocale();
  const pathname = usePathname() ?? "/";
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
        <div className="grid gap-4">
          {GROUPS.map(({ group, items }) => (
            <section key={group} aria-labelledby={`student-nav-${group}`} className="grid gap-1">
              <h2
                id={`student-nav-${group}`}
                className={cn("px-3 pt-1 text-pill font-semibold uppercase tracking-wide text-muted", collapsed && "sr-only")}
              >
                {t(`common.studentNavGroup.${group}`)}
              </h2>
              {collapsed ? <hr aria-hidden="true" className="mx-2 border-border" /> : null}
              <ul className="grid gap-1">
                {items.map((item) => {
                  const active = isActive(pathname, item.href);
                  const Icon = item.icon;
                  const label = t(`common.nav.${item.key}`);
                  return (
                    <li key={item.href} className="relative">
                      <Link
                        href={item.href}
                        prefetch
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
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
        </div>
      </nav>
    </aside>
  );
}
