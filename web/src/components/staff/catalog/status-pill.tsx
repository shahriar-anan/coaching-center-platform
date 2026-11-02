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

/** The message key of an exam's status. A withdrawn exam says "Withdrawn", not "Archived" (D1). Packages keep the shared key. */
export function examStatusKey(status: CatalogStatusValue): string {
  return status === "ARCHIVED" ? "staffCatalog.examStatus.ARCHIVED" : `staffCatalog.status.${status}`;
}

/** An exam's status as a word. */
export function ExamStatusPill({ status }: { status: CatalogStatusValue }) {
  const { t } = useLocale();
  return <Pill tone={tones[status]}>{t(examStatusKey(status))}</Pill>;
}
