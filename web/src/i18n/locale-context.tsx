"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { catalogs, type MessageTree } from "@/i18n/messages";

export type Locale = "en" | "bn";

export const LOCALE_STORAGE_KEY = "locale";

type LocaleContextValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
};

const LocaleContext = createContext<LocaleContextValue | null>(null);

function resolve(messages: MessageTree, key: string): string | undefined {
  const parts = key.split(".");
  let current: unknown = messages;
  for (const part of parts) {
    if (current === null || typeof current !== "object") {
      return undefined;
    }
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === "string" ? current : undefined;
}

const BN_DIGITS = ["০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"];

function localizeParam(value: string | number, locale: Locale): string {
  if (typeof value === "number" && locale === "bn") {
    return String(value).replace(/\d/g, (d) => BN_DIGITS[Number(d)]);
  }
  return String(value);
}

export function translate(
  locale: Locale,
  key: string,
  params?: Record<string, string | number>,
): string {
  const other: Locale = locale === "bn" ? "en" : "bn";
  // A missing key shows the key itself outside production, so gaps are visible.
  const template =
    resolve(catalogs[locale], key) ??
    (process.env.NODE_ENV === "production" ? resolve(catalogs[other], key) : undefined) ??
    key;
  if (!params) {
    return template;
  }
  return Object.entries(params).reduce(
    (text, [name, value]) => text.split(`{${name}}`).join(localizeParam(value, locale)),
    template,
  );
}

const DEFAULT_LOCALE: Locale = "bn";

/** The choice made in this tab; read back from storage on a fresh load. */
let chosen: Locale | null = null;
const listeners = new Set<() => void>();

function readStoredLocale(): Locale | null {
  try {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    return stored === "en" || stored === "bn" ? stored : null;
  } catch {
    return null;
  }
}

function subscribeLocale(onChange: () => void) {
  listeners.add(onChange);
  return () => {
    listeners.delete(onChange);
  };
}

export function LocaleProvider({
  children,
  initialLocale,
}: {
  children: ReactNode;
  /** Fixes the locale (tests, previews). Otherwise Bangla, or the stored choice. */
  initialLocale?: Locale;
}) {
  // The server and the first client render both use the default, so hydration matches;
  // the stored choice applies right after, without an effect.
  const locale = useSyncExternalStore(
    subscribeLocale,
    () => initialLocale ?? chosen ?? readStoredLocale() ?? DEFAULT_LOCALE,
    () => initialLocale ?? DEFAULT_LOCALE,
  );

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const setLocale = useCallback((next: Locale) => {
    chosen = next;
    try {
      window.localStorage.setItem(LOCALE_STORAGE_KEY, next);
    } catch {
      /* storage blocked: the choice lasts for this page only */
    }
    listeners.forEach((notify) => notify());
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => translate(locale, key, params),
    [locale],
  );

  const value = useMemo(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useLocale(): LocaleContextValue {
  const ctx = useContext(LocaleContext);
  if (!ctx) {
    throw new Error("useLocale must be used within LocaleProvider");
  }
  return ctx;
}
