import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";

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
