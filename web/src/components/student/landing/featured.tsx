import Link from "next/link";
import { Skeleton } from "@/components/ui/skeleton";
import { PackageCard } from "@/components/ui/package/package-card";
import { coverSrc } from "@/components/ui/package/package-cover";
import { featuredModuleIds } from "@/content/site-info";
import { useCategoryTree } from "@/lib/api/exam/catalog";
import { pickText, pickTitle } from "@/lib/format";
import { firstLine } from "@/components/student/catalog/catalog-helpers";
import { useLocale } from "@/i18n/locale-context";
import { featuredModules } from "@/components/student/landing/landing-data";
import { Section } from "@/components/student/landing/section";

/** The featured packages (modules) as the old site's package cards, each with its cover, summary and price. */
export function Featured() {
  const { t, locale } = useLocale();
  const { data, isPending } = useCategoryTree(null);
  const title = t("landing.packagesTitle");
  const subtitle = t("landing.packagesSubtitle");
  const action = (
    <Link href="/packages" className="text-label font-semibold text-text-accent underline underline-offset-4">
      {t("landing.seeAll")}
    </Link>
  );

  if (isPending) {
    return (
      <Section id="landing-packages" title={title} subtitle={subtitle} action={action} center>
        <Skeleton variant="card" count={3} />
      </Section>
    );
  }
  const cards = featuredModules(data ?? [], featuredModuleIds, 4);
  if (cards.length === 0) return null;

  // On phones the cards stack down the page, one under another, so nothing scrolls sideways. From md the cards form a grid.
  return (
    <Section id="landing-packages" title={title} subtitle={subtitle} action={action} center>
      <ul className="grid gap-4 md:grid-cols-[repeat(auto-fit,minmax(15rem,21rem))] md:justify-center md:gap-5">
        {cards.map((card) => {
          const type = card.mcq && card.mcq > 0 ? "MCQ" : "WRITTEN";
          const examCount = (card.mcq ?? 0) + (card.written ?? 0);
          const summary = firstLine(pickText({ ...card.module }, "description", locale));
          return (
            <li key={card.module.id} className="min-w-0">
              <PackageCard
                href={card.href}
                cover={coverSrc(card.module.coverUrl)}
                type={type}
                title={pickTitle(card.module, locale)}
                summary={summary || null}
                examCount={examCount}
                price={card.module.price ?? null}
              />
            </li>
          );
        })}
      </ul>
    </Section>
  );
}
