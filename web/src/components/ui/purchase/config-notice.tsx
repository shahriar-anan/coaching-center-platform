"use client";

import { useLocale } from "@/i18n/locale-context";

/**
 * Shown when checkout is off. Renders nothing when `show` is false; when true it
 * is a status banner, so screen readers announce it.
 */
export function ConfigNotice({ show }: { show: boolean }) {
  const { t } = useLocale();
  if (!show) return null;
  return (
    <p role="status" className="rounded-control border border-warn bg-warn/10 px-4 py-3 text-body text-warn">
      {t("common.purchase.shared.configOff")}
    </p>
  );
}
