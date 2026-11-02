import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { cn } from "@/components/ui/cn";
import { Skeleton } from "@/components/ui/skeleton";
import { examPath } from "@/components/student/catalog/catalog-helpers";
import { priceLabel, pricingOf } from "@/components/student/catalog/price-view";
import { TypePill } from "@/components/student/catalog/status-pill";
import { useUpcomingExams, type UpcomingExam } from "@/lib/api/exam/catalog";
import { formatDate, formatMoney, formatNumber, formatTime, pickTitle } from "@/lib/format";
import { useServerNow } from "@/lib/time/clock";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { daysUntil, nextExams } from "@/components/student/landing/landing-data";
import { Section } from "@/components/student/landing/section";

/** The day, month and year of an exam's date, split out of the full date so each can be set on its own. */
function dateParts(iso: string, locale: Locale) {
  const words = formatDate(iso, locale, { year: "always" }).split(" ");
  const year = words.pop() ?? "";
  const [day = "", ...month] = words;
  return { day, month: month.join(" "), year };
}

/** The Bangla month names and their three-letter English form, which a phone shows in both languages. */
const SHORT_MONTHS: Record<string, string> = {
  জানুয়ারি: "Jan",
  ফেব্রুয়ারি: "Feb",
  মার্চ: "Mar",
  এপ্রিল: "Apr",
  মে: "May",
  জুন: "Jun",
  জুলাই: "Jul",
  আগস্ট: "Aug",
  সেপ্টেম্বর: "Sep",
  অক্টোবর: "Oct",
  নভেম্বর: "Nov",
  ডিসেম্বর: "Dec",
};

/** The month in full from md up, and as "Nov" on a phone, in English and Bangla alike, where the full name does not fit its block. */
function MonthText({ month }: { month: string }) {
  const { locale } = useLocale();
  const short = locale === "bn" ? (SHORT_MONTHS[month] ?? month) : month.slice(0, 3);
  return (
    <>
      <span className="md:hidden">{short}</span>
      <span className="hidden md:inline">{month}</span>
    </>
  );
}

/** The price the order charges, or "Free"; a sentence when the exam has no price of its own. */
function ExamPrice({ exam }: { exam: UpcomingExam }) {
  const { t, locale } = useLocale();
  const label = priceLabel(pricingOf(exam.pricing, exam.price));
  if (label.kind === "free") {
    return <span className="text-label font-bold">{t("catalog.price.free")}</span>;
  }
  if (label.kind === "moduleOnly") {
    return <span className="text-meta text-on-band/80">{t("catalog.price.moduleOnly")}</span>;
  }
  const amount = label.kind === "price" ? label.amount : label.kind === "discounted" ? label.effective : null;
  return (
    <span className="text-card-title font-extrabold tabular-nums">
      {formatMoney(amount, locale) ?? t("catalog.priceNotSet")}
    </span>
  );
}

/**
 * The soonest exam as a navy card: a date block, the relative day, the time, the type, the title, the package, the price and a
 * button to the exam. On a phone it is compact, with the date block beside the text; from md the date block is large and the card
 * roomier. The button is gold, because the site's button colour is the band colour itself.
 */
function Spotlight({ exam, liveAt, now }: { exam: UpcomingExam; liveAt: string; now: number }) {
  const { t, locale } = useLocale();
  const date = dateParts(liveAt, locale);
  const packageName = pickTitle({ titleEn: exam.moduleTitleEn ?? "", titleBn: exam.moduleTitleBn ?? "" }, locale);
  const days = daysUntil(liveAt, now);
  let relative: string | null = null;
  if (days === 0) {
    relative = t("catalog.routine.today");
  } else if (days === 1) {
    relative = t("catalog.routine.tomorrow");
  } else if (days > 1) {
    relative = t("landing.upcomingInDays", { count: formatNumber(days, locale) });
  }

  return (
    <div className="flex gap-4 rounded-card bg-band p-4 text-on-band md:items-center md:gap-6 md:p-8">
      <div className="grid h-fit w-16 shrink-0 place-items-center rounded-control bg-accent py-2 text-center text-band md:w-28 md:rounded-card md:py-4">
        <span className="text-2xl font-extrabold leading-none tabular-nums md:text-4xl">{date.day}</span>
        <span className="mt-1 text-pill font-bold md:mt-2 md:text-label">
          <MonthText month={date.month} />
        </span>
        <span className="hidden text-meta tabular-nums md:block">{date.year}</span>
      </div>
      <div className="grid min-w-0 flex-1 gap-1.5 md:gap-3">
        <div className="flex flex-wrap items-center gap-2">
          {/* The pill keeps its own colours, so it sits on a card-coloured chip that reads in both themes. */}
          <span className="inline-flex rounded-full bg-card">
            <TypePill type={exam.examType} />
          </span>
          {relative ? <span className="text-label font-bold text-accent">{relative}</span> : null}
        </div>
        <h3 className="text-card-title font-bold leading-snug md:text-title">{pickTitle(exam, locale)}</h3>
        {packageName ? <p className="line-clamp-1 text-meta text-on-band/80 md:text-body">{packageName}</p> : null}
        <p className="text-meta tabular-nums text-on-band/80 md:text-body">{formatTime(liveAt, locale)}</p>
        <div className="flex flex-wrap items-center justify-between gap-2 pt-1 md:gap-3 md:pt-2">
          <ExamPrice exam={exam} />
          <Link
            href={examPath(exam.id)}
            prefetch
            className="inline-flex min-h-11 items-center rounded-control bg-accent px-4 text-label font-bold text-band md:px-5 transition-opacity hover:opacity-90"
          >
            {t("landing.upcomingView")}
          </Link>
        </div>
      </div>
    </div>
  );
}

/** The day over the month, in a small tinted chip at the start of a list row. */
function DateChip({ iso }: { iso: string }) {
  const { locale } = useLocale();
  const { day, month } = dateParts(iso, locale);
  return (
    <span className="grid w-14 shrink-0 place-items-center rounded-control bg-accent/15 py-1.5 text-accent-text md:w-16">
      <span className="text-card-title font-extrabold leading-none tabular-nums">{day}</span>
      <span className="mt-1 block max-w-full truncate text-pill font-bold">
        <MonthText month={month} />
      </span>
    </span>
  );
}

/** One of the next exams in the list: the date chip, the type and time, the title, and a chevron, all in one link. */
function NextRow({ exam }: { exam: UpcomingExam }) {
  const { locale } = useLocale();
  return (
    <Link
      href={examPath(exam.id)}
      prefetch
      className="group flex min-h-11 items-center gap-3 rounded-card border border-border bg-card p-3 transition-colors hover:border-accent md:gap-4 md:p-4"
    >
      {exam.liveAt ? <DateChip iso={exam.liveAt} /> : null}
      <span className="min-w-0 flex-1">
        <span className="mb-1 flex flex-wrap items-center gap-2 text-meta text-muted">
          <TypePill type={exam.examType} />
          {exam.liveAt ? <span className="tabular-nums">{formatTime(exam.liveAt, locale)}</span> : null}
        </span>
        <span className="line-clamp-1 block font-bold group-hover:text-text-accent">{pickTitle(exam, locale)}</span>
      </span>
      <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-muted" />
    </Link>
  );
}

/**
 * The next live exams across both types, three in all: the soonest as a spotlight, and the next two beside it as a list (stacked on
 * phones).
 * Hidden when there are none.
 */
export function Upcoming() {
  const { t } = useLocale();
  const now = useServerNow(60_000);
  const mcq = useUpcomingExams("MCQ");
  const written = useUpcomingExams("WRITTEN");
  const title = t("landing.upcomingTitle");
  const subtitle = t("landing.upcomingSubtitle");

  if (mcq.isPending || written.isPending) {
    return (
      <Section id="landing-upcoming" title={title} subtitle={subtitle} center tone="surface">
        <Skeleton variant="card" count={2} />
      </Section>
    );
  }
  const [spotlight, ...rest] = nextExams([mcq.data ?? [], written.data ?? []], 3);
  if (!spotlight || !spotlight.liveAt) return null;

  return (
    <Section id="landing-upcoming" title={title} subtitle={subtitle} center tone="surface">
      <div className={cn("grid gap-6", rest.length > 0 && "lg:grid-cols-2 lg:items-start")}>
        <Spotlight exam={spotlight} liveAt={spotlight.liveAt} now={now} />
        {rest.length > 0 ? (
          <ul className="grid content-start gap-3">
            {rest.map((exam) => (
              <li key={`${exam.examType}-${exam.id}`}>
                <NextRow exam={exam} />
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    </Section>
  );
}
