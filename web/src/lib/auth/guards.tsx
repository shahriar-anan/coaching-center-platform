"use client";

import { useEffect, useMemo, type ReactNode } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";
import type { Role, StaffRole } from "@/lib/api/exam/types";
import { staffHome, studentHome } from "@/lib/config";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Skeleton } from "@/components/ui/skeleton";

export type RoleInfo = {
  role: Role | null;
  isStudent: boolean;
  isStaff: boolean;
  isMasterAdmin: boolean;
  isSystemAdmin: boolean;
  isInstructor: boolean;
  canEditPrice: boolean;
  /** The module cover image: uploaded and removed by the Master Admin only. */
  canManageCover: boolean;
  canDelete: boolean;
  canManageRoster: boolean;
  canAssignInstructor: boolean;
  canEvaluate: boolean;
  canPublishResult: boolean;
  canSeePayments: boolean;
  canSeeAudit: boolean;
  canManageStaff: boolean;
  /** Category, module and exam authoring. */
  canAuthor: boolean;
  canSeeStudents: boolean;
};

type Capability = Exclude<keyof RoleInfo, "role" | `is${string}`>;

/** The one table of what each role may do. Pages never compare role strings. */
const CAPABILITIES: Record<Role, Capability[]> = {
  MASTER_ADMIN: [
    "canEditPrice",
    "canManageCover",
    "canDelete",
    "canManageRoster",
    "canAssignInstructor",
    "canEvaluate",
    "canPublishResult",
    "canSeePayments",
    "canSeeAudit",
    "canManageStaff",
    "canAuthor",
    "canSeeStudents",
  ],
  SYSTEM_ADMIN: ["canSeePayments", "canAuthor", "canSeeStudents"],
  INSTRUCTOR: ["canEvaluate"],
  STUDENT: [],
};

export function roleInfo(role: Role | null | undefined): RoleInfo {
  const caps = new Set<Capability>(role ? CAPABILITIES[role] : []);
  const has = (c: Capability) => caps.has(c);
  return {
    role: role ?? null,
    isStudent: role === "STUDENT",
    isStaff: Boolean(role && role !== "STUDENT"),
    isMasterAdmin: role === "MASTER_ADMIN",
    isSystemAdmin: role === "SYSTEM_ADMIN",
    isInstructor: role === "INSTRUCTOR",
    canEditPrice: has("canEditPrice"),
    canManageCover: has("canManageCover"),
    canDelete: has("canDelete"),
    canManageRoster: has("canManageRoster"),
    canAssignInstructor: has("canAssignInstructor"),
    canEvaluate: has("canEvaluate"),
    canPublishResult: has("canPublishResult"),
    canSeePayments: has("canSeePayments"),
    canSeeAudit: has("canSeeAudit"),
    canManageStaff: has("canManageStaff"),
    canAuthor: has("canAuthor"),
    canSeeStudents: has("canSeeStudents"),
  };
}

export function useRole(): RoleInfo {
  const { session } = useSession();
  const role = session.status === "authenticated" ? session.user.role : null;
  return useMemo(() => roleInfo(role), [role]);
}

/** `/login?next=<current path>`, with the expired reason when the API dropped the session. */
export function loginRedirect(pathname: string, search: string, expired: boolean): string {
  const current = `${pathname}${search ? `?${search}` : ""}`;
  const params = new URLSearchParams({ next: current });
  if (expired) {
    params.set("reason", "expired");
  }
  return `/login?${params}`;
}

function useLoginRedirect(active: boolean) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { sessionExpired } = useSession();
  useEffect(() => {
    if (active) {
      router.replace(loginRedirect(pathname ?? "/", searchParams?.toString() ?? "", sessionExpired));
    }
  }, [active, pathname, router, searchParams, sessionExpired]);
}

function GuardLoading() {
  const { t } = useLocale();
  return (
    <div className="py-6" aria-busy="true">
      <span className="sr-only">{t("common.guard.restoring")}</span>
      <Skeleton variant="card" count={2} />
    </div>
  );
}

function CrossLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Button href={href} variant="primary">
      {children}
    </Button>
  );
}

/** Sitting, notices, account, payment history, purchase result. */
export function RequireStudent({ children }: { children: ReactNode }) {
  const { session } = useSession();
  const { t } = useLocale();
  useLoginRedirect(session.status === "anonymous");

  if (session.status !== "authenticated") {
    return <GuardLoading />;
  }
  if (session.user.role !== "STUDENT") {
    return (
      <EmptyState
        title={t("common.guard.studentOnly")}
        body={t("common.guard.studentOnlyBody")}
        action={<CrossLink href={staffHome}>{t("common.guard.goStaffHome")}</CrossLink>}
      />
    );
  }
  return <>{children}</>;
}

export const ALL_STAFF: StaffRole[] = ["MASTER_ADMIN", "SYSTEM_ADMIN", "INSTRUCTOR"];

/**
 * Staff pages. A student sees the staff-only message; a staff role not in
 * `roles` sees a not-found page (`notFound` lets the caller supply one).
 */
export function RequireStaff({
  roles = ALL_STAFF,
  children,
  notFound,
}: {
  roles?: StaffRole[];
  children: ReactNode;
  notFound?: ReactNode;
}) {
  const { session } = useSession();
  const { t } = useLocale();
  useLoginRedirect(session.status === "anonymous");

  if (session.status !== "authenticated") {
    return <GuardLoading />;
  }
  const role = session.user.role;
  if (role === "STUDENT") {
    return (
      <EmptyState
        title={t("common.guard.staffOnly")}
        body={t("common.guard.staffOnlyBody")}
        action={<CrossLink href={studentHome}>{t("common.guard.goHome")}</CrossLink>}
      />
    );
  }
  if (!roles.includes(role)) {
    return (
      <>
        {notFound ?? (
          <EmptyState
            title={t("common.notFound.title")}
            body={t("common.notFound.body")}
            action={<CrossLink href={staffHome}>{t("common.guard.goStaffHome")}</CrossLink>}
          />
        )}
      </>
    );
  }
  return <>{children}</>;
}
