import Link from "next/link";
import { BookOpen } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, pickTitle } from "@/lib/format";
import { useCategoryTree } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { categoryCards } from "@/components/student/landing/landing-data";
import { Section } from "@/components/student/landing/section";

export function Categories() {
  const { t, locale } = useLocale();
  const { data, isPending } = useCategoryTree(null);
  const title = t("landing.categoriesTitle");
  const subtitle = t("landing.categoriesSubtitle");

  if (isPending) {
    return (
      <Section id="landing-categories" title={title} subtitle={subtitle}>
        <Skeleton variant="card" count={3} />
      </Section>
    );
  }
  const cards = categoryCards(data ?? []);
  if (cards.length === 0) return null;

  return (
    <Section id="landing-categories" title={title} subtitle={subtitle}>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {cards.map((card) => {
          const price = formatMoney(card.fromPrice, locale);
          return (
            <li key={card.id}>
              <Link
                href={card.href}
                prefetch
                className="flex h-full flex-col gap-4 rounded-card border border-border bg-card p-5 transition hover:-translate-y-0.5 hover:border-accent"
              >
                <span className="flex size-11 items-center justify-center rounded-full bg-accent/15 text-text-accent">
                  <BookOpen aria-hidden="true" className="size-5" />
                </span>
                <span className="grid gap-1">
                  <span className="text-lg font-bold">{pickTitle(card, locale)}</span>
                  <span className="text-body text-muted">{t("landing.categoryModules", { count: card.moduleCount })}</span>
                </span>
                <span className="mt-auto text-label font-semibold text-text-accent">
                  {price ? t("landing.categoryFrom", { price }) : t("landing.categoryNoPrice")}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </Section>
  );
}
