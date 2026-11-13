"use client";

import Link from "next/link";
import { Pill } from "@/components/ui/pill";
import { useLocale } from "@/i18n/locale-context";
import type { AdmissionState } from "@/lib/api/exam/offline-admission";
import { stateLabelKey } from "./labels";

/** The students list's badge for an offline admission: its state, linking to the admission. Nothing for a student without one. */
export function OfflineStudentBadge({ admission }: { admission: { admissionId: string; status: AdmissionState } | undefined }) {
  const { t } = useLocale();
  if (!admission) {
    return null;
  }
  return (
    <Link href={`/staff/admissions/${admission.admissionId}`} className="inline-flex max-w-full">
      <Pill tone="accent">
        {t("staffOffline.students.badge")} · {t(stateLabelKey(admission.status))}
      </Pill>
    </Link>
  );
}
