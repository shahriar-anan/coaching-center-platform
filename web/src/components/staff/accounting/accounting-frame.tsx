"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";
import type { Crumb } from "@/components/ui/breadcrumbs";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { RequireStaff } from "@/lib/auth/guards";
import { useGroups } from "@/lib/api/accounting/groups";
import { pickName } from "./accounting-model";

export const ACCOUNTING_HOME = "/staff/accounting";

/**
 * The frame of every accounting page (accounting-plan 4.6): the Master Admin gate, the title row and the sub-navigation (Home, Entries,
 * Reports, Settings, then one link per live group in order). Anyone else sees the "for the Master Admin" message, as the instructor pay
 * pages did. The print pages (voucher, statement) do not use the frame: they print without the app's chrome.
 */
export function AccountingFrame({
  title,
  breadcrumbs = [],
  actions,
  children,
}: {
  title: string;
  breadcrumbs?: Crumb[];
  actions?: ReactNode;
  children: ReactNode;
}) {
  const { t } = useLocale();
  return (
    <RequireStaff
      roles={["MASTER_ADMIN"]}
      notFound={<EmptyState title={t("accounting.forMaster.title")} body={t("accounting.forMaster.body")} />}
    >
      <AccountingBody title={title} breadcrumbs={breadcrumbs} actions={actions}>
        {children}
      </AccountingBody>
    </RequireStaff>
  );
}

function AccountingBody({
  title,
  breadcrumbs,
  actions,
  children,
}: {
  title: string;
  breadcrumbs: Crumb[];
  actions?: ReactNode;
  children: ReactNode;
}) {
  const { t } = useLocale();
  const trail: Crumb[] = [
    { label: t("common.staffNav.dashboard"), href: "/staff" },
    { label: t("accounting.title"), href: ACCOUNTING_HOME },
    ...breadcrumbs,
  ];
  return (
    <div className="grid gap-5">
      <PageHeader title={title} breadcrumbs={trail} actions={actions} />
      <AccountingSubNav />
      {children}
    </div>
  );
}

/** The sub-navigation: the fixed sections, then each live group in its order. Only the Master Admin's session gets here, so the groups load. */
export function AccountingSubNav() {
  const { t, locale } = useLocale();
  const pathname = usePathname();
  const groups = useGroups(false);

  const fixed = [
    { href: ACCOUNTING_HOME, label: t("accounting.nav.home") },
    { href: `${ACCOUNTING_HOME}/entries`, label: t("accounting.nav.entries") },
    { href: `${ACCOUNTING_HOME}/reports`, label: t("accounting.nav.reports") },
    { href: `${ACCOUNTING_HOME}/settings`, label: t("accounting.nav.settings") },
  ];
  const groupLinks = (groups.data ?? []).map((group) => ({
    href: `${ACCOUNTING_HOME}/groups/${group.id}`,
    label: pickName(group, locale),
  }));

  return (
    <nav aria-label={t("accounting.title")} className="-mx-1 overflow-x-auto">
      <ul className="flex min-w-max items-center gap-1 px-1 pb-1">
        {[...fixed, ...groupLinks].map((item) => {
          const active = item.href === ACCOUNTING_HOME ? pathname === ACCOUNTING_HOME : pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "inline-flex min-h-10 items-center rounded-control px-3 text-label transition-colors",
                  active ? "bg-accent/15 text-accent-text" : "text-muted hover:bg-bg hover:text-text",
                )}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/** A plain loading or failure state for a page's own data, so each page reads the same. */
export function AccountingLoading() {
  return <Skeleton variant="rows" count={4} />;
}
