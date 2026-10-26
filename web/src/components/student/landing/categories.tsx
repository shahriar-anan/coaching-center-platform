import Link from "next/link";
import { ClipboardList } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { pickTitle } from "@/lib/format";
import { useCategoryTree } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { categoryCards } from "@/components/student/landing/landing-data";

/**
 * The hero's way in: the old site's exam category cards, each with an explore
 * button per exam type it has (MCQ, written). While the catalog is empty or
 * cannot load, the mockup's two doors stand in, so the hero always has an entrance.
 */
export function HeroCategories() {
  const { t, locale } = useLocale();
  const { data, isPending } = useCategoryTree(null);

  if (isPending) return <Skeleton variant="card" count={1} />;
  const cards = categoryCards(data ?? []);
  if (cards.length === 0) return <Doors />;

  return (
    <div>
      <h2 className="text-label font-bold text-text-accent">{t("landing.categoriesTitle")}</h2>
      <ul className="mt-3 grid gap-3">
        {cards.map((card) => (
          <li key={card.id} className="rounded-card border border-border bg-card p-4 text-start shadow-sm">
            <div className="flex items-center gap-3">
              <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-action text-on-action">
                <ClipboardList aria-hidden="true" className="size-6" />
              </span>
              <span className="grid min-w-0">
                <span className="text-lg font-bold">{pickTitle(card, locale)}</span>
                <span className="text-meta text-muted">{t("landing.categoryModules", { count: card.moduleCount })}</span>
              </span>
            </div>
            <div className="mt-3 grid grid-cols-1 gap-2 min-[400px]:grid-cols-2">
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
    </div>
  );
}

/** The mockup's two doors into the MCQ and written lists. */
function Doors() {
  const { t } = useLocale();
  return (
    <div className="grid grid-cols-2 gap-3">
      <Link
        href="/mcq"
        className="block rounded-card border border-border bg-card p-5 text-center transition hover:-translate-y-0.5 hover:border-accent"
      >
        <span aria-hidden="true" className="block text-3xl">✅</span>
        <span className="mt-1 block text-title font-bold">{t("home.doorMcqTitle")}</span>
        <span className="block text-meta text-muted">{t("home.doorMcqSub")}</span>
      </Link>
      <Link
        href="/written"
        className="block rounded-card border border-border bg-card p-5 text-center transition hover:-translate-y-0.5 hover:border-accent"
      >
        <span aria-hidden="true" className="block text-3xl">✍️</span>
        <span className="mt-1 block text-title font-bold">{t("home.doorWrittenTitle")}</span>
        <span className="block text-meta text-muted">{t("home.doorWrittenSub")}</span>
      </Link>
    </div>
  );
}
