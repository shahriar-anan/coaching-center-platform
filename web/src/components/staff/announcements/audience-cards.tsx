"use client";

import { useId } from "react";
import { BookOpen, Package, UserCheck, Users, type LucideIcon } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import type { AnnouncementAudience } from "@/lib/api/exam/staff-admin";

const AUDIENCES: { value: AnnouncementAudience; icon: LucideIcon; name: string; hint: string }[] = [
  { value: "ALL", icon: Users, name: "staffAdmin.announce.audienceAll", hint: "staffAdmin.announce.audienceAllHint" },
  { value: "MODULE", icon: Package, name: "staffAdmin.announce.audienceModule", hint: "staffAdmin.announce.audienceModuleHint" },
  { value: "EXAM", icon: BookOpen, name: "staffAdmin.announce.audienceExam", hint: "staffAdmin.announce.audienceExamHint" },
  { value: "SELECTED", icon: UserCheck, name: "staffAdmin.announce.audienceSelected", hint: "staffAdmin.announce.audienceSelectedHint" },
];

/**
 * Who gets the announcement, as four option cards in one radio group. Each card is at least 44 px tall and shows an icon,
 * the name and one line. The name is the radio's accessible name; the line is its description. Arrow keys move between
 * the cards, as for any radio group.
 */
export function AudienceCards({
  value,
  onChange,
}: {
  value: AnnouncementAudience;
  onChange: (next: AnnouncementAudience) => void;
}) {
  const { t } = useLocale();
  const groupId = useId();

  return (
    <fieldset className="grid gap-3">
      <legend className="mb-1 text-label font-medium text-text">{t("staffAdmin.announce.audience")}</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        {AUDIENCES.map((option) => {
          const Icon = option.icon;
          const id = `${groupId}-${option.value}`;
          return (
            <label key={option.value} className="block cursor-pointer">
              <input
                type="radio"
                name={`${groupId}-audience`}
                value={option.value}
                checked={value === option.value}
                onChange={() => onChange(option.value)}
                aria-labelledby={`${id}-name`}
                aria-describedby={`${id}-hint`}
                className="peer sr-only"
              />
              <span className="flex h-full min-h-11 items-start gap-3 rounded-card border border-border-control bg-card p-4 transition-colors hover:bg-bg peer-checked:border-accent peer-checked:bg-accent/10 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent">
                <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-accent-text" />
                <span className="grid min-w-0 gap-0.5">
                  <span id={`${id}-name`} className="font-bold text-text">
                    {t(option.name)}
                  </span>
                  <span id={`${id}-hint`} className="text-meta text-muted">
                    {t(option.hint)}
                  </span>
                </span>
              </span>
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
