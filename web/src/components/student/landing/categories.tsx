import Link from "next/link";
import { useState } from "react";
import { ArrowRight } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, formatNumber, pickTitle } from "@/lib/format";
import { useCategoryTree, type ExamType } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { categoryCards, type CategoryCard } from "@/components/student/landing/landing-data";
import { DoorIcon } from "@/components/student/door-icon";
import { Section } from "@/components/student/landing/section";

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
          <DoorIcon kind="mcq" />
          <span className="mt-1 block text-title font-bold">{t("home.doorMcqTitle")}</span>
          <span className="block text-meta text-muted">{t("home.doorMcqSub")}</span>
        </Link>
        <Link
          href="/written"
          className="block rounded-card border border-border bg-card p-5 text-center transition hover:-translate-y-0.5 hover:border-accent"
        >
          <DoorIcon kind="written" />
          <span className="mt-1 block text-title font-bold">{t("home.doorWrittenTitle")}</span>
          <span className="block text-meta text-muted">{t("home.doorWrittenSub")}</span>
        </Link>
      </div>
    </section>
  );
}

/** The switch's label for each type, in the landing messages (the Bangla page's other type labels are English). */
const landingTypeKey = (type: ExamType) => (type === "MCQ" ? "landing.typeMcq" : "landing.typeWritten");

/**
 * The MCQ / Written switch under the section's title. Two options only (no "All"), so the cards always belong to one type.
 * Pressed state is announced with aria-pressed, and the buttons are keyboard reachable.
 */
function TypeSwitch({ value, onChange }: { value: ExamType; onChange: (next: ExamType) => void }) {
  const { t } = useLocale();
  const options: ExamType[] = ["MCQ", "WRITTEN"];
  return (
    <div
      role="group"
      aria-label={t("landing.typeSwitchLabel")}
      className="inline-flex gap-1 rounded-full border border-border bg-card p-1"
    >
      {options.map((option) => {
        const active = option === value;
        return (
          <button
            key={option}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option)}
            className={cn(
              "min-h-11 rounded-full px-6 text-label font-bold transition-colors duration-150",
              active ? "bg-accent text-on-accent" : "text-muted hover:text-text",
            )}
          >
            {t(landingTypeKey(option))}
          </button>
        );
      })}
    </div>
  );
}

/**
 * One category card: an exam icon in a gold-tinted circle, the title, the counts as chips, the price when there is one, and a full-width
 * Explore button. The whole card is one link, and the button only shows it, so no links are nested. The price line is left out when no
 * package of the type has a price.
 */
function ExamCategoryCard({ card }: { card: CategoryCard }) {
  const { t, locale } = useLocale();
  const price = formatMoney(card.fromPrice, locale);
  // One chip per count that is not zero. A card only exists when at least one of the two is.
  const chips = [
    card.examCount > 0 ? t("catalog.countExams", { n: formatNumber(card.examCount, locale) }) : null,
    card.moduleCount > 0 ? t("catalog.countModules", { n: formatNumber(card.moduleCount, locale) }) : null,
  ].filter((part): part is string => part !== null);
  return (
    <Link
      href={card.href}
      prefetch
      className="group flex h-full w-full flex-col items-center gap-3 rounded-card border border-border bg-bg p-5 text-center shadow-sm transition duration-300 motion-safe:hover:-translate-y-1 hover:border-accent hover:shadow-md motion-reduce:transition-none"
    >
      {/* The old site's exam icon. It is drawn in the accent text colour, so it reads on the tinted circle in both themes. */}
      <span className="grid size-11 shrink-0 place-items-center rounded-full bg-accent/15">
        <DoorIcon kind="mcq" className="size-6" />
      </span>
      {/* The middle takes the free height, so the Explore buttons line up at the bottom of equal-height cards. */}
      <span className="flex flex-1 flex-col items-center gap-2">
        <span className="line-clamp-2 text-base font-bold leading-snug">{pickTitle(card, locale)}</span>
        <span className="flex flex-wrap justify-center gap-2">
          {chips.map((chip) => (
            <span key={chip} className="rounded-full border border-border px-3 py-1 text-meta text-muted">
              {chip}
            </span>
          ))}
        </span>
        {price ? <span className="text-label font-bold text-text-accent">{t("landing.categoryFrom", { price })}</span> : null}
      </span>
      <span className="inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-control bg-accent px-4 text-label font-bold text-on-accent transition group-hover:opacity-90 motion-reduce:transition-none">
        {t("landing.categoryExplore")}
        <ArrowRight aria-hidden="true" className="size-4" />
      </span>
    </Link>
  );
}

/**
 * The exam categories straight after the hero. A switch picks the type first (MCQ by default), then the cards are the top-level
 * categories of that type. Both trees are read, so the section can hide itself when neither type has a category.
 */
export function ExamCategories() {
  const { t } = useLocale();
  const [type, setType] = useState<ExamType>("MCQ");
  const mcq = useCategoryTree("MCQ");
  const written = useCategoryTree("WRITTEN");
  const chosen = type === "MCQ" ? mcq : written;
  const other = type === "MCQ" ? written : mcq;
  const otherType: ExamType = type === "MCQ" ? "WRITTEN" : "MCQ";
  const cards = categoryCards(chosen.data ?? [], type);
  const title = t("landing.examsTitle");
  const subtitle = t("landing.categoriesSubtitle");

  // Wait for the other type too when the chosen one is empty, so the section does not flash the empty line before it hides.
  if (chosen.isPending || (cards.length === 0 && other.isPending)) {
    return (
      <Section id="landing-exams" title={title} subtitle={subtitle} center tone="surface">
        <div className="flex justify-center">
          <TypeSwitch value={type} onChange={setType} />
        </div>
        <div className="mt-6">
          <Skeleton variant="card" count={3} />
        </div>
      </Section>
    );
  }
  if (cards.length === 0 && categoryCards(other.data ?? [], otherType).length === 0) return null;

  return (
    <Section id="landing-exams" title={title} subtitle={subtitle} center tone="surface">
      <div className="flex justify-center">
        <TypeSwitch value={type} onChange={setType} />
      </div>
      {cards.length === 0 ? (
        <p className="mt-6 text-center text-body text-muted">
          {t("landing.categoriesEmpty", { type: t(landingTypeKey(type)) })}
        </p>
      ) : (
        <ul className="mx-auto mt-6 grid max-w-5xl grid-cols-[repeat(auto-fit,minmax(16rem,22rem))] justify-center gap-5">
          {cards.map((card) => (
            <li key={card.id} className="flex">
              <ExamCategoryCard card={card} />
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}
