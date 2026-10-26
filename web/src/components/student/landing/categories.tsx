import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";

const DOOR =
  "block rounded-card border-2 border-accent/60 bg-card p-6 text-center shadow-lg transition hover:-translate-y-1 hover:border-accent md:p-7";

/**
 * The hero's way in: the mockup's two doors, MCQ and written, under the old
 * site's "exam categories" heading. They are the largest thing in the hero.
 */
export function HeroCategories() {
  const { t } = useLocale();
  return (
    <section aria-labelledby="hero-categories">
      <h2 id="hero-categories" className="flex items-center justify-center gap-3 text-xl font-bold md:text-2xl lg:justify-start">
        <span aria-hidden="true" className="h-6 w-1.5 rounded-full bg-accent" />
        {t("landing.categoriesTitle")}
      </h2>
      <div className="mt-4 grid grid-cols-2 gap-4">
        <Link href="/mcq" className={DOOR}>
          <span aria-hidden="true" className="block text-4xl md:text-5xl">✅</span>
          <span className="mt-2 block text-xl font-bold md:text-2xl">{t("home.doorMcqTitle")}</span>
          <span className="mt-1 block text-body text-muted">{t("home.doorMcqSub")}</span>
        </Link>
        <Link href="/written" className={DOOR}>
          <span aria-hidden="true" className="block text-4xl md:text-5xl">✍️</span>
          <span className="mt-2 block text-xl font-bold md:text-2xl">{t("home.doorWrittenTitle")}</span>
          <span className="mt-1 block text-body text-muted">{t("home.doorWrittenSub")}</span>
        </Link>
      </div>
    </section>
  );
}
