"use client";

import Link from "next/link";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { ChevronDown } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { LocaleMenuItem, ThemeMenuItem } from "@/components/shell/pills";

type NavLink = { label: string; href: string };

/** One entry of the main menu. Exam is not a link: it opens the two exam types, so no entry goes straight to one type. */
export type SiteNavItem = NavLink | { label: string; types: NavLink[] };

/**
 * The old site's main menu, shared by the desktop bar and the phone menu. "Features" is now "Exams". Items without an
 * exam-site page show a "coming soon" page until the client decides what they become.
 */
export function useSiteNav(): SiteNavItem[] {
  const { t } = useLocale();
  return [
    { label: t("landing.navAdmission"), href: "/register" },
    {
      label: t("landing.navExam"),
      types: [
        { label: t("landing.navMcq"), href: "/mcq" },
        { label: t("landing.navWritten"), href: "/written" },
      ],
    },
    { label: t("landing.navPackages"), href: "/packages" },
    { label: t("landing.navNotices"), href: "/notices" },
    { label: t("landing.navPublications"), href: "/publications" },
    { label: t("landing.navBlog"), href: "/blog" },
    { label: t("landing.navMedia"), href: "/media" },
    { label: t("landing.navResources"), href: "/resources" },
  ];
}

const MENU_PANEL =
  "z-50 grid max-h-[80dvh] min-w-56 gap-0.5 overflow-y-auto rounded-card border border-border bg-card p-1.5 text-body text-text shadow-lg";

function SiteMenuLink({ label, href }: NavLink) {
  return (
    <DropdownMenu.Item asChild>
      <Link href={href} prefetch={false} className="flex min-h-11 items-center rounded-control px-3 outline-none data-[highlighted]:bg-bg">
        {label}
      </Link>
    </DropdownMenu.Item>
  );
}

/** The Exam entry in the desktop bar: a button that opens the two exam types. It looks like the other links. */
export function SiteNavDropdown({
  label,
  types,
  onOpenChange,
}: {
  label: string;
  types: NavLink[];
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <DropdownMenu.Root onOpenChange={onOpenChange}>
      <DropdownMenu.Trigger className="inline-flex items-center gap-1 hover:text-text data-[state=open]:text-text">
        {label}
        <ChevronDown aria-hidden="true" className="size-4" />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content align="start" sideOffset={6} className={MENU_PANEL}>
          {types.map((type) => (
            <SiteMenuLink key={type.href} {...type} />
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

/**
 * Below 1280 px, where the bar has no links: one text button, "Menu", with the same entries and the language and theme
 * switches as menu items. Visitors get it on every page; the landing page also shows it for signed-in staff, as before.
 */
export function SiteMenu({ onOpenChange }: { onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const items = useSiteNav();
  return (
    <DropdownMenu.Root onOpenChange={onOpenChange}>
      <DropdownMenu.Trigger className="inline-flex h-11 items-center rounded-control px-3 text-label font-semibold text-text hover:bg-bg max-[379px]:px-2 xl:hidden">
        {t("common.nav.menu")}
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content align="end" sideOffset={6} aria-label={t("landing.navLabel")} className={MENU_PANEL}>
          {items.map((item) =>
            "types" in item ? (
              <DropdownMenu.Group key={item.label}>
                <DropdownMenu.Label className="px-3 pt-2 pb-1 text-pill font-semibold text-muted">{item.label}</DropdownMenu.Label>
                {item.types.map((type) => (
                  <SiteMenuLink key={type.href} {...type} />
                ))}
              </DropdownMenu.Group>
            ) : (
              <SiteMenuLink key={item.href} {...item} />
            ),
          )}
          <DropdownMenu.Separator className="my-1 h-px bg-border" />
          <LocaleMenuItem />
          <ThemeMenuItem />
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
