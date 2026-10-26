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

export type StaffNavItem = {
  key: StaffNavKey;
  /** Route under `/staff`. Pages link to it; the workstream that owns it builds it. */
  href: string;
  /** The role may open the page. "read" means it is visible but the page is read-only for that role. */
  access: Partial<Record<Exclude<Role, "STUDENT">, "full" | "read" | "assigned">>;
};

/** The one table of staff navigation. Order is the order on screen. */
export const STAFF_NAV: StaffNavItem[] = [
  {
    key: "dashboard",
    href: "/staff",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
  },
  { key: "categories", href: "/staff/categories", access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" } },
  {
    key: "modules",
    href: "/staff/modules",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "assigned" },
  },
  {
    key: "participation",
    href: "/staff/participation",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full", INSTRUCTOR: "full" },
  },
  {
    key: "submissions",
    href: "/staff/submissions",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read", INSTRUCTOR: "full" },
  },
  { key: "students", href: "/staff/students", access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "full" } },
  { key: "instructors", href: "/staff/instructors", access: { MASTER_ADMIN: "full" } },
  { key: "admins", href: "/staff/admins", access: { MASTER_ADMIN: "full" } },
  {
    key: "payments",
    href: "/staff/payments",
    access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read" },
  },
  { key: "audit", href: "/staff/audit", access: { MASTER_ADMIN: "full" } },
  { key: "orders", href: "/staff/orders", access: { MASTER_ADMIN: "full", SYSTEM_ADMIN: "read" } },
  { key: "announcements", href: "/staff/announcements", access: { MASTER_ADMIN: "full" } },
  { key: "systemStatus", href: "/staff/system-status", access: { MASTER_ADMIN: "full" } },
  {
    key: "account",
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
