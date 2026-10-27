"use client";

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

/** The language switch. Bangla is the default; the pill offers the other language. */
export function LocalePill() {
  const { locale, setLocale, t } = useLocale();
  const next = locale === "bn" ? "en" : "bn";
  return (
    <button
      type="button"
      onClick={() => setLocale(next)}
      aria-label={t("common.locale.switchLabel")}
      className="inline-flex h-11 min-w-11 shrink-0 items-center justify-center gap-1.5 rounded-full border border-border bg-card px-3 text-label font-semibold text-text hover:bg-bg max-[379px]:px-2.5"
    >
      <Languages aria-hidden="true" className="size-4 max-[379px]:hidden" />
      <span>{locale === "bn" ? "EN" : "বাং"}</span>
    </button>
  );
}

/**
 * The light and dark switch. The server renders light; the real choice is read
 * after hydration (the inline script in the layout already set it before paint).
 */
export function ThemePill() {
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

  const label = dark ? t("common.theme.toLight") : t("common.theme.toDark");
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      title={label}
      className="inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-border bg-card text-text hover:bg-bg"
    >
      {dark ? <Sun aria-hidden="true" className="size-4" /> : <Moon aria-hidden="true" className="size-4" />}
    </button>
  );
}
