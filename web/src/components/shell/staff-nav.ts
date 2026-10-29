import {
  Activity,
  ClipboardCheck,
  ClipboardList,
  CreditCard,
  LayoutDashboard,
  Megaphone,
  Package,
  Receipt,
  ScrollText,
  ShieldCheck,
  Tags,
  Trophy,
  UserCheck,
  UserCog,
  Users,
  type LucideIcon,
} from "lucide-react";
import type { Role } from "@/lib/api/exam/types";

export type StaffNavKey =
  | "dashboard"
  | "categories"
  | "modules"
  | "exams"
  | "participation"
  | "submissions"
  | "students"
  | "instructors"
  | "admins"
  | "payments"
  | "audit"
  | "account"
  | "orders"
  | "announcements"
  | "systemStatus";

/** The sidebar groups, in the order they show. Group names are in `common.staffNavGroup`. */
export type StaffNavGroup = "overview" | "catalog" | "exams" | "people" | "money" | "admin";

export const STAFF_NAV_GROUP_ORDER: StaffNavGroup[] = ["overview", "catalog", "exams", "people", "money", "admin"];

export type StaffNavAccess = Partial<Record<Exclude<Role, "STUDENT">, "full" | "read" | "assigned">>;

/** A sub-item under an accordion item. It takes its parent's access unless it sets its own. */
export type StaffNavChild = {
  key: "modulesAll" | "modulesCreate" | "examsAll" | "examsCreate";
  /** A path with no query. A child that opens a page with a query (the Exams create action) carries it in the href and is never shown as the current page. */
  href: string;
  access?: StaffNavAccess;
};

export type StaffNavItem = {
  key: StaffNavKey;
  group: StaffNavGroup;
  /** Route under `/staff`. For an accordion item this is the page its icon opens when the rail is collapsed. */
  href: string;
  /** The role may open the page. "read" means it is visible but the page is read-only for that role. */
  access: StaffNavAccess;
  icon: LucideIcon;
  /** Sub-items: they show in an accordion when the sidebar is open. */
  children?: StaffNavChild[];
};

/** The one table of staff navigation. Items are listed in the order they show within each group. */
export const STAFF_NAV: StaffNavItem[] = [
  {
    key: "dashboard",
    group: "overview",
    href: "/staff",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
    icon: LayoutDashboard,
  },
  {
    key: "categories",
    group: "catalog",
    href: "/staff/categories",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" },
    icon: Tags,
  },
  {
    key: "modules",
    group: "catalog",
    href: "/staff/modules",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "assigned" },
    icon: Package,
    children: [
      { key: "modulesAll", href: "/staff/modules" },
      { key: "modulesCreate", href: "/staff/modules/new", access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" } },
    ],
  },
  {
    key: "exams",
    group: "catalog",
    href: "/staff/exams",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "assigned" },
    icon: ClipboardList,
    children: [
      { key: "examsAll", href: "/staff/exams" },
      { key: "examsCreate", href: "/staff/exams?new=1", access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" } },
    ],
  },
  {
    key: "participation",
    group: "exams",
    href: "/staff/participation",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
    icon: Trophy,
  },
  {
    key: "submissions",
    group: "exams",
    href: "/staff/submissions",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read", INSTRUCTOR: "full" },
    icon: ClipboardCheck,
  },
  {
    key: "announcements",
    group: "exams",
    href: "/staff/announcements",
    access: { MASTER_ADMIN: "full" },
    icon: Megaphone,
  },
  {
    key: "students",
    group: "people",
    href: "/staff/students",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" },
    icon: Users,
  },
  { key: "instructors", group: "people", href: "/staff/instructors", access: { MASTER_ADMIN: "full" }, icon: UserCheck },
  { key: "admins", group: "people", href: "/staff/admins", access: { MASTER_ADMIN: "full" }, icon: ShieldCheck },
  {
    key: "payments",
    group: "money",
    href: "/staff/payments",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read" },
    icon: CreditCard,
  },
  {
    key: "orders",
    group: "money",
    href: "/staff/orders",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read" },
    icon: Receipt,
  },
  { key: "audit", group: "admin", href: "/staff/audit", access: { MASTER_ADMIN: "full" }, icon: ScrollText },
  {
    key: "systemStatus",
    group: "admin",
    href: "/staff/system-status",
    access: { MASTER_ADMIN: "full" },
    icon: Activity,
  },
  {
    key: "account",
    group: "admin",
    href: "/staff/account",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
    icon: UserCog,
  },
];

/** The items a role sees, in order. Items the role may not open are absent, not disabled. Sub-items the role may not open are removed too. */
export function staffNavFor(role: Role | null | undefined): StaffNavItem[] {
  if (!role || role === "STUDENT") {
    return [];
  }
  return STAFF_NAV.filter((item) => Boolean(item.access[role])).map((item) => {
    const children = item.children?.filter((child) => Boolean((child.access ?? item.access)[role]));
    return { ...item, children: children?.length ? children : undefined };
  });
}

/** The items grouped for the sidebar. A group the role cannot see at all is left out. */
export function groupStaffNav(items: StaffNavItem[]): { group: StaffNavGroup; items: StaffNavItem[] }[] {
  return STAFF_NAV_GROUP_ORDER.map((group) => ({
    group,
    items: items.filter((item) => item.group === group),
  })).filter((section) => section.items.length > 0);
}

/**
 * The item and sub-item for a path. A sub-item wins when its page is exactly this path. Otherwise the
 * item whose route is the longest match wins; the dashboard matches only the exact path. Returns
 * `null` when no item matches.
 */
export function activeNavFor(pathname: string, items: StaffNavItem[]): { key: StaffNavKey; child?: StaffNavChild["key"] } | null {
  for (const item of items) {
    const child = item.children?.find((candidate) => candidate.href === pathname);
    if (child) {
      return { key: item.key, child: child.key };
    }
  }
  let best: StaffNavItem | null = null;
  for (const item of items) {
    const matches = item.href === "/staff" ? pathname === "/staff" : pathname === item.href || pathname.startsWith(`${item.href}/`);
    if (matches && (!best || item.href.length > best.href.length)) {
      best = item;
    }
  }
  return best ? { key: best.key } : null;
}

/** The accordion item to open for a path: the active item when it has sub-items, otherwise none. */
export function openNavFor(pathname: string, items: StaffNavItem[]): StaffNavKey | null {
  const active = activeNavFor(pathname, items);
  const item = active ? items.find((candidate) => candidate.key === active.key) : undefined;
  return item?.children?.length ? item.key : null;
}

/** The key the sidebar remembers its collapsed state under, in this browser. */
export const NAV_COLLAPSED_KEY = "cc-staff-nav-collapsed";

function browserStorage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    // Some browsers throw on access when storage is blocked.
    return null;
  }
}

/** Whether the sidebar was left collapsed. Blocked or broken storage reads as expanded. */
export function readNavCollapsed(storage: Pick<Storage, "getItem"> | null = browserStorage()): boolean {
  try {
    return storage?.getItem(NAV_COLLAPSED_KEY) === "1";
  } catch {
    return false;
  }
}

/** Remembers the sidebar state. Blocked or full storage is ignored: the choice then lasts for this page only. */
export function writeNavCollapsed(collapsed: boolean, storage: Pick<Storage, "setItem"> | null = browserStorage()): void {
  try {
    storage?.setItem(NAV_COLLAPSED_KEY, collapsed ? "1" : "0");
  } catch {
    // Nothing to do: the sidebar still works for this visit.
  }
}

const navListeners = new Set<() => void>();
/** The choice made on this page. It wins over storage, so the toggle still works when storage is blocked. */
let navMemory: boolean | null = null;

/** For `useSyncExternalStore`: the sidebar re-reads its state when the person toggles it. */
export function subscribeNavCollapsed(listener: () => void): () => void {
  navListeners.add(listener);
  return () => {
    navListeners.delete(listener);
  };
}

/** The sidebar state now: the choice made on this page, else the one stored in this browser. */
export function currentNavCollapsed(): boolean {
  return navMemory ?? readNavCollapsed();
}

/** Sets the sidebar state, remembers it in this browser, and tells the sidebar to re-read it. */
export function setNavCollapsed(collapsed: boolean): void {
  navMemory = collapsed;
  writeNavCollapsed(collapsed);
  navListeners.forEach((listener) => listener());
}
