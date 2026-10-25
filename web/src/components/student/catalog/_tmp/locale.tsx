// Stand-in for the namespaced useLocale (WS0). Delete after the WS0 merge and import
// useLocale from @/i18n/locale-context.
"use client";

import { useCallback } from "react";
import { useLocale as useBaseLocale, type Locale } from "@/i18n/locale-context";
import bnCatalog from "@/i18n/messages/bn/catalog";
import bnHome from "@/i18n/messages/bn/home";
import bnPurchase from "@/i18n/messages/bn/purchase";
import enCatalog from "@/i18n/messages/en/catalog";
import enHome from "@/i18n/messages/en/home";
import enPurchase from "@/i18n/messages/en/purchase";

const own: Record<Locale, Record<string, unknown>> = {
  bn: { catalog: bnCatalog, home: bnHome, purchase: bnPurchase },
  en: { catalog: enCatalog, home: enHome, purchase: enPurchase },
};

function resolve(tree: Record<string, unknown>, key: string): string | undefined {
  let current: unknown = tree;
  for (const part of key.split(".")) {
    if (current === null || typeof current !== "object") return undefined;
    current = (current as Record<string, unknown>)[part];
  }
  return typeof current === "string" ? current : undefined;
}

export function useLocale() {
  const base = useBaseLocale();
  const { locale } = base;
  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const template = resolve(own[locale], key);
      if (template === undefined) return base.t(key, params);
      if (!params) return template;
      return Object.entries(params).reduce(
        (text, [name, value]) => text.split(`{${name}}`).join(String(value)),
        template,
      );
    },
    [base, locale],
  );
  return { locale, setLocale: base.setLocale, t };
}

export type { Locale };
