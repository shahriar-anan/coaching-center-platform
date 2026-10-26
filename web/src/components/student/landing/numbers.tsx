import { useCategoryTree } from "@/lib/api/exam/catalog";
import { formatNumber } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { totals } from "@/components/student/landing/landing-data";

/** Counts from the exam catalog only. The old site's hard-coded figures are not shown. */
export function Numbers() {
  const { t, locale } = useLocale();
  const { data } = useCategoryTree(null);
  if (!data) return null;
  const counts = totals(data);
  if (counts.modules === 0) return null;

  const stats = [
    { value: counts.modules, label: t("landing.numbersModules") },
    { value: counts.mcq, label: t("landing.numbersMcq") },
    { value: counts.written, label: t("landing.numbersWritten") },
  ].filter((stat) => stat.value > 0);

  return (
    <section aria-label={t("landing.numbersModules")} className="bg-band px-4 py-10 text-on-band">
      <dl className="mx-auto flex max-w-6xl flex-wrap justify-center gap-x-12 gap-y-6 text-center">
        {stats.map((stat) => (
          <div key={stat.label}>
            <dd className="text-3xl font-extrabold text-accent md:text-5xl">{formatNumber(stat.value, locale)}</dd>
            <dt className="mt-1 text-body text-on-band/80">{stat.label}</dt>
          </div>
        ))}
      </dl>
    </section>
  );
}
