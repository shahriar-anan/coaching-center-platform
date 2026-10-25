"use client";

import { Pill, type PillTone } from "@/components/ui/pill";
import { useLocale } from "@/i18n/locale-context";

export type CatalogStatusValue = "DRAFT" | "PUBLISHED" | "ARCHIVED";

const tones: Record<CatalogStatusValue, PillTone> = {
  DRAFT: "muted",
  PUBLISHED: "ok",
  ARCHIVED: "warn",
};

/** A status as a word. The tone only reinforces it. */
export function StatusPill({ status }: { status: CatalogStatusValue }) {
  const { t } = useLocale();
  return <Pill tone={tones[status]}>{t(`staffCatalog.status.${status}`)}</Pill>;
}
