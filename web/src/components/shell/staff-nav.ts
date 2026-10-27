import type { Role } from "@/lib/api/exam/types";

export type StaffNavKey =
  | "dashboard"
  | "categories"
  | "modules"
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

export type StaffNavItem = {
  key: StaffNavKey;
  group: StaffNavGroup;
  /** Route under `/staff`. Pages link to it; the workstream that owns it builds it. */
  href: string;
  /** The role may open the page. "read" means it is visible but the page is read-only for that role. */
  access: Partial<Record<Exclude<Role, "STUDENT">, "full" | "read" | "assigned">>;
};

/** The one table of staff navigation. Items are listed in the order they show within each group. */
export const STAFF_NAV: StaffNavItem[] = [
  {
    key: "dashboard",
    group: "overview",
    href: "/staff",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
  },
  {
    key: "categories",
    group: "catalog",
    href: "/staff/categories",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" },
  },
  {
    key: "modules",
    group: "catalog",
    href: "/staff/modules",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "assigned" },
  },
  {
    key: "participation",
    group: "exams",
    href: "/staff/participation",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
  },
  {
    key: "submissions",
    group: "exams",
    href: "/staff/submissions",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read", INSTRUCTOR: "full" },
  },
  {
    key: "announcements",
    group: "exams",
    href: "/staff/announcements",
    access: { MASTER_ADMIN: "full" },
  },
  {
    key: "students",
    group: "people",
    href: "/staff/students",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" },
  },
  { key: "instructors", group: "people", href: "/staff/instructors", access: { MASTER_ADMIN: "full" } },
  { key: "admins", group: "people", href: "/staff/admins", access: { MASTER_ADMIN: "full" } },
  {
    key: "payments",
    group: "money",
    href: "/staff/payments",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read" },
  },
  {
    key: "orders",
    group: "money",
    href: "/staff/orders",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read" },
  },
  { key: "audit", group: "admin", href: "/staff/audit", access: { MASTER_ADMIN: "full" } },
  { key: "systemStatus", group: "admin", href: "/staff/system-status", access: { MASTER_ADMIN: "full" } },
  {
    key: "account",
    group: "admin",
    href: "/staff/account",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
  },
];

/** The items a role sees, in order. Items the role may not open are absent, not disabled. */
export function staffNavFor(role: Role | null | undefined): StaffNavItem[] {
  if (!role || role === "STUDENT") {
    return [];
  }
  return STAFF_NAV.filter((item) => Boolean(item.access[role]));
}

/** The items grouped for the sidebar. A group the role cannot see at all is left out. */
export function groupStaffNav(items: StaffNavItem[]): { group: StaffNavGroup; items: StaffNavItem[] }[] {
  return STAFF_NAV_GROUP_ORDER.map((group) => ({
    group,
    items: items.filter((item) => item.group === group),
  })).filter((section) => section.items.length > 0);
}
