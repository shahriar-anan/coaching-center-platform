"use client";

import { useLocale } from "@/i18n/locale-context";

/**
 * Shown on every section of a published exam or package that has staged changes. The Master Admin sees the way to
 * publish them; a System Admin sees that the Master Admin decides.
 */
export function PendingChangesBanner({ masterAdmin }: { masterAdmin: boolean }) {
  const { t } = useLocale();
  return (
    <p role="status" className="rounded-control border border-warn/40 bg-warn/10 px-4 py-3 text-meta text-warn">
      <span className="font-medium">{t("staffCatalog.pending.bannerTitle")}</span>{" "}
      {t(masterAdmin ? "staffCatalog.pending.bannerMaster" : "staffCatalog.pending.bannerWaiting")}
    </p>
  );
}
