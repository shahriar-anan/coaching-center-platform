"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { Logo } from "@/components/shell/logo";
import { activeNavFor, groupStaffNav, type StaffNavChild, type StaffNavItem, type StaffNavKey } from "@/components/shell/staff-nav";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";

const ROW =
  "relative flex min-h-11 w-full items-center gap-3 rounded-control px-3 text-label text-sidebar-text transition-colors hover:bg-sidebar-hover";
const CURRENT = "bg-sidebar-active font-semibold text-sidebar-on-active hover:bg-sidebar-active";

/** The bar on the left edge of the current item. Colour alone does not mark it, so screen readers also get `aria-current`. */
function CurrentBar() {
  return <span aria-hidden="true" className="absolute inset-y-2 start-0 w-1 rounded-full bg-sidebar-accent" />;
}

/** One sidebar item. With sub-items it is an accordion button; on the collapsed rail it is an icon link to its page. */
function NavEntry({
  item,
  current,
  currentChild,
  open,
  collapsed,
  badge,
  onToggle,
  onNavigate,
}: {
  item: StaffNavItem;
  current: boolean;
  currentChild?: StaffNavChild["key"];
  open: boolean;
  collapsed: boolean;
  badge: ReactNode;
  onToggle: () => void;
  onNavigate: () => void;
}) {
  const { t } = useLocale();
  const Icon = item.icon;
  const label = t(`common.staffNav.${item.key}`);
  const body = (
    <>
      {current ? <CurrentBar /> : null}
      <Icon aria-hidden="true" className="size-5 shrink-0" />
      <span className={cn("min-w-0 flex-1 truncate text-start", collapsed && "sr-only")}>{label}</span>
      {badge}
    </>
  );

  if (item.children && !collapsed) {
    const listId = `staff-nav-sub-${item.key}`;
    return (
      <>
        <button
          type="button"
          aria-expanded={open}
          aria-controls={listId}
          onClick={onToggle}
          className={cn(ROW, current && (currentChild ? "font-semibold text-sidebar-on-active" : CURRENT))}
        >
          {body}
          <ChevronDown aria-hidden="true" className={cn("size-4 shrink-0 transition-transform", open && "rotate-180")} />
        </button>
        {open ? (
          <ul id={listId} className="mt-1 grid gap-1">
            {item.children.map((child) => {
              const selected = currentChild === child.key;
              return (
                <li key={child.key}>
                  <Link
                    href={child.href}
                    prefetch
                    onClick={onNavigate}
                    aria-current={selected ? "page" : undefined}
                    className={cn(
                      "flex min-h-11 items-center rounded-control ps-11 pe-3 text-label text-sidebar-text transition-colors hover:bg-sidebar-hover",
                      selected && "bg-sidebar-active font-semibold text-sidebar-on-active hover:bg-sidebar-active",
                    )}
                  >
                    {t(`common.staffNav.${child.key}`)}
                  </Link>
                </li>
              );
            })}
          </ul>
        ) : null}
      </>
    );
  }

  return (
    <Link
      href={item.href}
      prefetch
      onClick={onNavigate}
      aria-current={current ? "page" : undefined}
      title={collapsed ? label : undefined}
      className={cn(ROW, collapsed && "justify-center px-0", current && CURRENT)}
    >
      {body}
    </Link>
  );
}

/**
 * The sidebar's pages: sections with uppercase labels, an icon on every item, and an accordion for
 * items with sub-items. One accordion is open at a time; the page's own item opens when the page
 * changes. On the collapsed rail the labels are hidden for screen readers only.
 */
export function StaffNavList({
  items,
  collapsed = false,
  pendingCount = 0,
  onNavigate = () => undefined,
}: {
  items: StaffNavItem[];
  collapsed?: boolean;
  /** Changes waiting to be published, shown as a count on the Exams item. */
  pendingCount?: number;
  onNavigate?: () => void;
}) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const pathname = usePathname() ?? "/staff";
  const active = activeNavFor(pathname, items);
  const activeKey = active?.key ?? null;
  const [openKey, setOpenKey] = useState<StaffNavKey | null>(activeKey);
  const [seenKey, setSeenKey] = useState<StaffNavKey | null>(activeKey);
  // A page with a different item opens that item. The state is set during render, so there is no flash.
  if (seenKey !== activeKey) {
    setSeenKey(activeKey);
    setOpenKey(activeKey);
  }

  return (
    <div className="grid gap-4">
      {groupStaffNav(items).map(({ group, items: groupItems }) => (
        <section key={group} aria-labelledby={`staff-nav-${group}`} className="grid gap-1">
          <h2
            id={`staff-nav-${group}`}
            className={cn("px-3 pt-1 text-pill font-semibold uppercase tracking-wide text-sidebar-muted", collapsed && "sr-only")}
          >
            {t(`common.staffNavGroup.${group}`)}
          </h2>
          {collapsed ? <hr aria-hidden="true" className="mx-2 border-sidebar-line" /> : null}
          <ul className="grid gap-1">
            {groupItems.map((item) => {
              const badge =
                item.key === "exams" && pendingCount > 0 ? (
                  collapsed ? (
                    <>
                      <span aria-hidden="true" className="absolute end-2 top-2 size-2.5 rounded-full bg-warn-fill" />
                      <span className="sr-only">{t("common.staffNav.pendingChanges", { count: formatNumber(pendingCount) })}</span>
                    </>
                  ) : (
                    <span className="rounded-full bg-warn-fill px-2 text-pill font-semibold tabular-nums text-on-warn">
                      {formatNumber(pendingCount)}
                      <span className="sr-only">{t("common.staffNav.pendingChanges", { count: formatNumber(pendingCount) })}</span>
                    </span>
                  )
                ) : null;
              return (
                <li key={item.key} className="relative">
                  <NavEntry
                    item={item}
                    current={activeKey === item.key}
                    currentChild={activeKey === item.key ? active?.child : undefined}
                    open={openKey === item.key}
                    collapsed={collapsed}
                    badge={badge}
                    onToggle={() => setOpenKey(openKey === item.key ? null : item.key)}
                    onNavigate={onNavigate}
                  />
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** The desktop sidebar: 240 px, or an 80 px icon rail when collapsed. The shell sets the column width. */
export function StaffSidebar({
  items,
  collapsed,
  pendingCount,
}: {
  items: StaffNavItem[];
  collapsed: boolean;
  pendingCount: number;
}) {
  const { t } = useLocale();
  return (
    <aside
      data-collapsed={collapsed || undefined}
      className="hidden border-e border-sidebar-line bg-sidebar text-sidebar-text md:sticky md:top-0 md:flex md:h-dvh md:flex-col md:gap-4 md:py-4"
    >
      <div className={cn("flex min-h-11 items-center", collapsed ? "justify-center px-2" : "px-4")}>
        <Logo href="/staff" tone="rail" wordmark={!collapsed} className="min-w-0" />
      </div>
      <nav
        aria-label={t("common.nav.main")}
        className={cn(
          "min-h-0 flex-1 overflow-x-hidden overflow-y-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden",
          collapsed ? "px-2" : "px-3",
        )}
      >
        <StaffNavList items={items} collapsed={collapsed} pendingCount={pendingCount} />
      </nav>
    </aside>
  );
}
