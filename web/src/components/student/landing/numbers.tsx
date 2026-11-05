import type { LucideIcon } from "lucide-react";
import { BookOpen, Download, GraduationCap, Users } from "lucide-react";
import { toBengaliDigits } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { SITE_STATS_FALLBACK, useSiteStats } from "@/lib/api/exam/site-stats";

/**
 * The old site's statistics box, kept as it was: four figures in their own colours on a pale card. Students and mentors
 * are live counts from the API; until they load, or if they cannot be read, the fallback figures show, so the box never
 * jumps or goes blank. The app card has no number yet ("Coming soon"). Learning materials stays a fixed figure.
 */
interface Stat {
  icon: LucideIcon;
  colour: string;
  label: string;
  /** null shows the "Coming soon" text instead of a count. */
  figure: number | null;
}

export function Numbers() {
  const { t, locale } = useLocale();
  const { data } = useSiteStats();
  const counts = data ?? SITE_STATS_FALLBACK;
  const stats: Stat[] = [
    { icon: Users, colour: "text-[#FF6E76]", label: t("landing.statsStudents"), figure: counts.students },
    { icon: GraduationCap, colour: "text-[#3DAFFD]", label: t("landing.statsMentors"), figure: counts.mentors },
    { icon: Download, colour: "text-[#00B19E]", label: t("landing.statsApp"), figure: null },
    { icon: BookOpen, colour: "text-[#EAA819]", label: t("landing.statsMaterials"), figure: 1000 },
  ];
  return (
    <section aria-label={t("landing.statsLabel")} className="my-10 px-3 md:px-20">
      <ul className="mx-auto grid max-w-7xl grid-cols-2 gap-6 rounded-xl bg-gradient-to-br from-gray-100 via-yellow-50 to-gray-200 px-6 py-4 shadow-[0_20px_60px_-15px_rgba(1,15,139,.2)] md:grid-cols-4 dark:bg-none dark:bg-card dark:shadow-[0_20px_60px_-15px_rgba(128,128,128,0.25)]">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <li key={stat.label} className="flex flex-col items-center gap-2 text-center">
              <Icon aria-hidden="true" size={36} className={stat.colour} />
              <span className={`text-xl font-semibold ${stat.colour}`}>
                {/* No thousands separator, as on the old site: ১০০০+ */}
                {stat.figure === null
                  ? t("landing.statsComingSoon")
                  : `${locale === "bn" ? toBengaliDigits(String(stat.figure)) : stat.figure}+`}
              </span>
              <span className="text-sm text-gray-600 md:text-base lg:text-lg dark:text-muted">{stat.label}</span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
