"use client";

import { useLocale, type Locale } from "@/i18n/locale-context";

export function LocaleSwitcher() {
  const { locale, setLocale, t } = useLocale();

  return (
    <label className="flex items-center gap-2 text-sm text-zinc-600">
      <span className="sr-only">{t("locale.switch")}</span>
      <select
        aria-label={t("locale.switch")}
        className="rounded-md border border-zinc-300 bg-white px-2 py-1 text-zinc-900"
        value={locale}
        onChange={(e) => setLocale(e.target.value as Locale)}
      >
        <option value="en">{t("locale.en")}</option>
        <option value="bn">{t("locale.bn")}</option>
      </select>
    </label>
  );
}
