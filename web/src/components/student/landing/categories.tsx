import { ClipboardList } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { pickTitle } from "@/lib/format";
import { useCategoryTree } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { categoryCards } from "@/components/student/landing/landing-data";
import { Section } from "@/components/student/landing/section";

/** The old site's exam category cards: an icon, the title, and an explore button for each exam type the category has. */
export function Categories() {
  const { t, locale } = useLocale();
  const { data, isPending } = useCategoryTree(null);
  const title = t("landing.categoriesTitle");
  const subtitle = t("landing.categoriesSubtitle");

  if (isPending) {
    return (
      <Section id="landing-categories" title={title} subtitle={subtitle} center tinted>
        <Skeleton variant="card" count={3} />
      </Section>
    );
  }
  const cards = categoryCards(data ?? []);
  if (cards.length === 0) return null;

  return (
    <Section id="landing-categories" title={title} subtitle={subtitle} center tinted>
      <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {cards.map((card) => (
          <li key={card.id} className="flex flex-col items-center gap-4 rounded-card bg-card p-6 text-center shadow-lg md:p-8">
            <span className="flex size-20 items-center justify-center rounded-2xl bg-action text-on-action">
              <ClipboardList aria-hidden="true" className="size-10" />
            </span>
            <h3 className="text-xl font-semibold">{pickTitle(card, locale)}</h3>
            <p className="text-body text-muted">{t("landing.categoryModules", { count: card.moduleCount })}</p>
            <div className="mt-auto grid w-full gap-2 pt-2">
              {card.mcqHref ? (
                <Button href={card.mcqHref} fullWidth>
                  {t("landing.categoryExploreMcq")}
                </Button>
              ) : null}
              {card.writtenHref ? (
                <Button href={card.writtenHref} variant={card.mcqHref ? "secondary" : "primary"} fullWidth>
                  {t("landing.categoryExploreWritten")}
                </Button>
              ) : null}
              {!card.mcqHref && !card.writtenHref ? (
                <Button href={card.href} fullWidth>
                  {t("landing.categoryExplore")}
                </Button>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
    </Section>
  );
}
