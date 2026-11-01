"use client";

import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { Languages, Moon, Sun } from "lucide-react";
import { useSyncExternalStore } from "react";
import { useLocale } from "@/i18n/locale-context";

function subscribeTheme(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observer.disconnect();
}

function isDark() {
  return document.documentElement.dataset.theme === "dark";
}

/** The language switch. Bangla is the default; the switch offers the other language. Shared by the pill and the menu item. */
function useLocaleSwitch() {
  const { locale, setLocale, t } = useLocale();
  return {
    short: locale === "bn" ? "EN" : "বাং",
    label: t("common.locale.switchLabel"),
    toggle: () => setLocale(locale === "bn" ? "en" : "bn"),
  };
}

/**
 * The light and dark switch. The server renders light; the real choice is read
 * after hydration (the inline script in the layout already set it before paint). Shared by the pill and the menu item.
 */
function useThemeSwitch() {
  const { t } = useLocale();
  const dark = useSyncExternalStore(subscribeTheme, isDark, () => false);

  function toggle() {
    const next = dark ? "light" : "dark";
    document.documentElement.dataset.theme = next;
    try {
      window.localStorage.setItem("theme", next);
    } catch {
      /* storage blocked: the choice lasts for this page only */
    }
  }

  return { dark, toggle, label: dark ? t("common.theme.toLight") : t("common.theme.toDark") };
}

/** The language switch. Bangla is the default; the pill offers the other language. */
export function LocalePill() {
  const { short, label, toggle } = useLocaleSwitch();
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      className="inline-flex h-11 min-w-11 shrink-0 items-center justify-center gap-1.5 rounded-full border border-border-control bg-card px-3 text-label font-semibold text-text hover:bg-bg max-[379px]:px-2.5"
    >
      <Languages aria-hidden="true" className="size-4 max-[379px]:hidden" />
      <span>{short}</span>
    </button>
  );
}

/** The light and dark switch. */
export function ThemePill() {
  const { dark, toggle, label } = useThemeSwitch();
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      title={label}
      className="inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-border-control bg-card text-text hover:bg-bg"
    >
      {dark ? <Sun aria-hidden="true" className="size-4" /> : <Moon aria-hidden="true" className="size-4" />}
    </button>
  );
}

const MENU_ROW = "flex min-h-11 cursor-pointer items-center gap-3 rounded-control px-3 outline-none data-[highlighted]:bg-bg";

/**
 * The language switch as a menu item. A Radix menu blocks Tab, so the pill cannot be reached inside a menu; this item is
 * reached with the arrow keys and Enter, and it uses the same logic as the pill.
 */
export function LocaleMenuItem() {
  const { label, toggle } = useLocaleSwitch();
  return (
    <DropdownMenu.Item onSelect={toggle} className={MENU_ROW}>
      <Languages aria-hidden="true" className="size-4" />
      {label}
    </DropdownMenu.Item>
  );
}

/** The light and dark switch as a menu item, for the same reason as LocaleMenuItem. */
export function ThemeMenuItem() {
  const { dark, toggle, label } = useThemeSwitch();
  return (
    <DropdownMenu.Item onSelect={toggle} className={MENU_ROW}>
      {dark ? <Sun aria-hidden="true" className="size-4" /> : <Moon aria-hidden="true" className="size-4" />}
      {label}
    </DropdownMenu.Item>
  );
}
