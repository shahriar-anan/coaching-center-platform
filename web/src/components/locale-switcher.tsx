"use client";

import { useLocale, type Locale } from "@/i18n/locale-context";

export function LocaleSwitcher() {
  const { locale, setLocale, t } = useLocale();

  return (
    <label className="flex items-center gap-2 text-meta text-muted">
      <span className="sr-only">{t("locale.switch")}</span>
      <select
        aria-label={t("locale.switch")}
        className="rounded-control border border-border bg-card px-2 py-1 text-text"
        value={locale}
        onChange={(e) => setLocale(e.target.value as Locale)}
      >
        <option value="en">{t("locale.en")}</option>
        <option value="bn">{t("locale.bn")}</option>
      </select>
    </label>
  );
}
