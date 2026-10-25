"use client";
// Stand-in for @/i18n/locale-context with the split catalogs (WS0). Delete after WS0 merges.
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import bnSitting from "@/i18n/messages/bn/sitting";
import enSitting from "@/i18n/messages/en/sitting";
import bnResult from "@/i18n/messages/bn/result";
import enResult from "@/i18n/messages/en/result";

export type Locale = "en" | "bn";

const catalogs: Record<Locale, Record<string, unknown>> = {
  bn: {
    sitting: bnSitting,
    result: bnResult,
    common: { retry: "আবার চেষ্টা করুন", cancel: "ফিরে যান", close: "বন্ধ করুন" },
  },
  en: {
    sitting: enSitting,
    result: enResult,
    common: { retry: "Try again", cancel: "Go back", close: "Close" },
  },
};

type Value = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
};

const Context = createContext<Value | null>(null);

function resolve(tree: unknown, key: string): string | undefined {
  let current = tree;
  for (const part of key.split(".")) {
    if (current === null || typeof current !== "object") {
      return undefined;
    }
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === "string" ? current : undefined;
}

export function LocaleProvider({ children, initial = "bn" }: { children: ReactNode; initial?: Locale }) {
  const [locale, setLocale] = useState<Locale>(initial);
  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const template = resolve(catalogs[locale], key) ?? key;
      if (!params) {
        return template;
      }
      return Object.entries(params).reduce(
        (text, [name, value]) => text.replaceAll(`{${name}}`, String(value)),
        template,
      );
    },
    [locale],
  );
  const value = useMemo(() => ({ locale, setLocale, t }), [locale, t]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useLocale(): Value {
  const value = useContext(Context);
  if (!value) {
    throw new Error("useLocale must be used within LocaleProvider");
  }
  return value;
}
