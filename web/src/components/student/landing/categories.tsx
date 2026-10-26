import Link from "next/link";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, pickTitle } from "@/lib/format";
import { useCategoryTree } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { categoryCards } from "@/components/student/landing/landing-data";

/**
 * The hero's way in: the mockup's two doors, MCQ and written, under the old
 * site's "exam categories" heading.
 */
export function HeroCategories() {
  const { t } = useLocale();
  return (
    <section aria-labelledby="hero-categories">
      <h2 id="hero-categories" className="text-label font-bold text-text-accent">
        {t("landing.categoriesTitle")}
      </h2>
      <div className="mt-3 grid grid-cols-2 gap-3">
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
    </section>
  );
}

/** The section's frame: a tinted band with a navy heading pill, as on the old site. */
function ExamSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section aria-labelledby="landing-exams" className="bg-accent/10 px-4 py-14 md:py-20">
      <div className="mx-auto max-w-6xl">
        <h2
          id="landing-exams"
          className="mx-auto w-fit rounded-full bg-band px-8 py-2.5 text-center text-2xl font-bold text-on-band shadow-lg md:text-3xl"
        >
          {title}
        </h2>
        {children}
      </div>
    </section>
  );
}

/**
 * The exam categories straight after the hero. Each card follows the pattern
 * used for course and exam cards on education sites: a coloured cover with the
 * exam types it offers, the title, a count and price, and one full-width action.
 */
export function ExamCategories() {
  const { t, locale } = useLocale();
  const { data, isPending } = useCategoryTree(null);
  const title = t("landing.examsTitle");

  if (isPending) {
    return (
      <ExamSection title={title}>
        <div className="mt-10">
          <Skeleton variant="card" count={3} />
        </div>
      </ExamSection>
    );
  }
  const cards = categoryCards(data ?? []);
  if (cards.length === 0) return null;

  return (
    <ExamSection title={title}>
      <ul className="mx-auto mt-10 flex max-w-5xl flex-wrap justify-center gap-6">
        {cards.map((card) => {
          const price = formatMoney(card.fromPrice, locale);
          return (
            <li key={card.id} className="flex w-full sm:w-[calc(50%-0.75rem)] lg:w-[calc(33.333%-1rem)]">
              <Link
                href={card.href}
                prefetch
                className="group flex w-full flex-col overflow-hidden rounded-card border border-border bg-card shadow-md transition duration-300 hover:-translate-y-1 hover:shadow-xl"
              >
                <span className="relative flex h-28 items-end gap-2 overflow-hidden bg-band p-4 text-on-band">
                  <span aria-hidden="true" className="absolute -end-8 -top-8 size-32 rounded-full bg-accent/20" />
                  {card.mcqHref ? (
                    <span className="relative rounded-full bg-accent px-3 py-1 text-meta font-bold text-on-accent">
                      {t("home.doorMcqTitle")}
                    </span>
                  ) : null}
                  {card.writtenHref ? (
                    <span className="relative rounded-full border border-on-band/40 px-3 py-1 text-meta font-semibold">
                      {t("home.doorWrittenTitle")}
                    </span>
                  ) : null}
                </span>

                <span className="flex flex-1 flex-col gap-2 p-5">
                  <span className="line-clamp-2 min-h-12 text-lg font-bold leading-snug">{pickTitle(card, locale)}</span>
                  <span className="text-body text-muted">{t("landing.categoryModules", { count: card.moduleCount })}</span>
                  <span className="mt-auto pt-3 text-label font-semibold text-text-accent">
                    {price ? t("landing.categoryFrom", { price }) : t("landing.categoryNoPrice")}
                  </span>
                </span>

                <span className="border-t border-border p-4">
                  <span className="block rounded-control bg-action py-2.5 text-center text-label font-bold text-on-action transition group-hover:opacity-90">
                    {t("landing.categoryExplore")}
                  </span>
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </ExamSection>
  );
}
