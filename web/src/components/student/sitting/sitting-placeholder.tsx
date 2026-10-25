"use client";

import { useLocale } from "./_tmp/locale";

/**
 * Written sitting is not built yet (see the Report in the brief). Shown instead of a broken paper
 * so a written exam never falls through to the MCQ paper.
 */
export function SittingPlaceholder() {
  const { t } = useLocale();
  return (
    <div role="status" className="rounded-card border border-border bg-card p-6 text-center text-text">
      {t("sitting.written.comingSoon")}
    </div>
  );
}
