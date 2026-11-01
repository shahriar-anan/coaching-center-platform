import type { LucideIcon } from "lucide-react";
import { BookOpen, Download, GraduationCap, Users } from "lucide-react";
import { toBengaliDigits } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";

/**
 * The old site's statistics box, copied as it was: four figures in their own colours on a pale card. The figures are the
 * old site's: students and mentors came from its settings on 1 November 2026, the other two were fixed in its code.
 * Update them here when the client gives new ones.
 */
const STATS: { icon: LucideIcon; value: number; colour: string; key: string }[] = [
  { icon: Users, value: 874, colour: "text-[#FF6E76]", key: "landing.statsStudents" },
  { icon: GraduationCap, value: 2, colour: "text-[#3DAFFD]", key: "landing.statsMentors" },
  { icon: Download, value: 200, colour: "text-[#00B19E]", key: "landing.statsDownloads" },
  { icon: BookOpen, value: 1000, colour: "text-[#EAA819]", key: "landing.statsMaterials" },
];

export function Numbers() {
  const { t, locale } = useLocale();
  return (
    <section aria-label={t("landing.statsLabel")} className="my-10 px-3 md:px-20">
      <ul className="mx-auto grid max-w-7xl grid-cols-2 gap-6 rounded-xl bg-gradient-to-br from-gray-100 via-yellow-50 to-gray-200 px-6 py-4 shadow-[0_20px_60px_-15px_rgba(1,15,139,.2)] md:grid-cols-4 dark:bg-none dark:bg-card dark:shadow-[0_20px_60px_-15px_rgba(128,128,128,0.25)]">
        {STATS.map((stat) => {
          const Icon = stat.icon;
          return (
            <li key={stat.key} className="flex flex-col items-center gap-2 text-center">
              <Icon aria-hidden="true" size={36} className={stat.colour} />
              <span className={`text-xl font-semibold ${stat.colour}`}>{/* No thousands separator, as on the old site: ১০০০+ */}
                {locale === "bn" ? toBengaliDigits(String(stat.value)) : stat.value}+</span>
              <span className="text-sm text-gray-600 md:text-base lg:text-lg dark:text-muted">{t(stat.key)}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
