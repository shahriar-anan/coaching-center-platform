"use client";

import { useLocale } from "@/i18n/locale-context";
import type { PendingChangeList } from "@/lib/api/exam/staff-catalog";

/** The sections of the exam editor that a change can sit in. */
export type ChangedSection = "details" | "text" | "schedule" | "content" | "price" | "instructor";

/** Which section each changed field is shown in. A field not listed here is not shown on any section. */
const FIELD_SECTION: Record<string, ChangedSection> = {
  titleEn: "details", titleBn: "details", categoryId: "details", paperLanguage: "details", previewEnabled: "details",
  instructor: "instructor",
  descriptionEn: "text", descriptionBn: "text", rulesEn: "text", rulesBn: "text",
  mode: "schedule", liveAt: "schedule", endAt: "schedule", durationMinutes: "schedule", passPercent: "schedule",
  maximum: "schedule", negativeMarks: "schedule", answersChangeable: "schedule",
  price: "price", free: "price", discountPercent: "price", discountEndsAt: "price", accessDays: "price",
};

/** The sections that hold a staged change: a field's section, and the paper when its questions or paper changed. */
export function changedSections(changes: PendingChangeList | null | undefined): Set<ChangedSection> {
  const sections = new Set<ChangedSection>();
  for (const change of changes?.fields ?? []) {
    const section = FIELD_SECTION[change.field];
    if (section) sections.add(section);
  }
  if ((changes?.questions.length ?? 0) > 0 || changes?.paper) sections.add("content");
  return sections;
}

/**
 * Shown once at the top of a published exam or package that has staged changes. The Master Admin sees the way to publish
 * them; a System Admin sees that the Master Admin decides.
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

/** A small badge on each section that holds a staged change. */
export function PendingSectionBadge() {
  const { t } = useLocale();
  return (
    <span role="status" className="inline-flex w-fit items-center rounded-full border border-warn/40 bg-warn/10 px-2.5 py-0.5 text-meta font-medium text-warn">
      {t("staffCatalog.pending.sectionBadge")}
    </span>
  );
}
