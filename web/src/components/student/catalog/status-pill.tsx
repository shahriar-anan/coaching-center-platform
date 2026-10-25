"use client";

import type { ExamType, MyStatus } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { Pill } from "@/components/ui/pill";
import { statusPill, typeLabelKey } from "./catalog-helpers";

export function ExamStatusPill({
  status,
  examType,
  retakeAvailable,
}: {
  status: MyStatus;
  examType: ExamType;
  retakeAvailable?: boolean | null;
}) {
  const { t } = useLocale();
  const pill = statusPill(status, { examType, retakeAvailable });
  return (
    <Pill tone={pill.tone} pulse={pill.pulse}>
      {t(pill.key)}
    </Pill>
  );
}

export function TypePill({ type }: { type: ExamType }) {
  const { t } = useLocale();
  return <Pill tone="accent">{t(typeLabelKey(type))}</Pill>;
}
