"use client";

import { useLocale } from "@/i18n/locale-context";
import { Pill } from "@/components/ui/pill";

/** "অ্যাক্সেস আছে" with a small label for how access was given: a module, this exam, the roster or an admin. */
export function AccessBadge({ via }: { via: "MODULE" | "EXAM" | "ROSTER" | "MANUAL" | "FREE" | "BUNDLE" }) {
  const { t } = useLocale();
  return (
    <Pill tone="ok">
      <span>{t("common.purchase.shared.accessGranted")}</span>
      <span className="text-muted">· {t(`common.purchase.shared.via.${via}`)}</span>
    </Pill>
  );
}
