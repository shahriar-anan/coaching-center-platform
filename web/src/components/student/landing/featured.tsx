import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { featuredModuleIds } from "@/content/site-info";
import { useCategoryTree } from "@/lib/api/exam/catalog";
import { formatMoney, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { featuredModules } from "@/components/student/landing/landing-data";
import { Section } from "@/components/student/landing/section";

/** Modules with their price and exam counts, as the old site's package cards did. */
export function Featured() {
  const { t, locale } = useLocale();
  const { data, isPending } = useCategoryTree(null);
  const title = t("landing.packagesTitle");
  const subtitle = t("landing.packagesSubtitle");
  const action = (
    <Link href="/mcq" className="text-label font-semibold text-text-accent underline underline-offset-4">
      {t("landing.seeAll")}
    </Link>
  );

  if (isPending) {
    return (
      <Section id="landing-packages" title={title} subtitle={subtitle} action={action}>
        <Skeleton variant="card" count={3} />
      </Section>
    );
  }
  const cards = featuredModules(data ?? [], featuredModuleIds, 3);
  if (cards.length === 0) return null;

  return (
    <Section id="landing-packages" title={title} subtitle={subtitle} action={action}>
      <ul className="grid gap-5 md:grid-cols-3">
        {cards.map((card) => {
          const price = formatMoney(card.module.price, locale);
          const description = locale === "bn" ? card.module.descriptionBn : card.module.descriptionEn;
          return (
            <li key={card.module.id} className="flex flex-col overflow-hidden rounded-card border border-border bg-card">
              <div className="flex flex-wrap gap-2 bg-band p-5 text-on-band">
                {card.mcq ? <span className="rounded-full bg-accent px-3 py-1 text-meta font-bold text-on-accent">{t("landing.packageMcq", { count: card.mcq })}</span> : null}
                {card.written ? <span className="rounded-full border border-on-band/40 px-3 py-1 text-meta font-semibold">{t("landing.packageWritten", { count: card.written })}</span> : null}
              </div>
              <div className="flex flex-1 flex-col gap-3 p-5">
                <h3 className="text-lg font-bold">{pickTitle(card.module, locale)}</h3>
                {description ? <p className="line-clamp-3 text-body text-muted">{description}</p> : null}
                <p className="mt-auto pt-2 text-title font-bold text-text-accent">
                  {price ?? t("landing.categoryNoPrice")}
                </p>
              </div>
              <div className="border-t border-border p-4">
                <Button href={card.href} variant="secondary" fullWidth>
                  {t("landing.packageDetails")}
                </Button>
              </div>
            </li>
          );
        })}
      </ul>
    </Section>
  );
}
