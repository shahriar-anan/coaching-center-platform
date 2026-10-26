import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { examPath } from "@/components/student/catalog/catalog-helpers";
import { TypePill } from "@/components/student/catalog/status-pill";
import { useUpcomingExams } from "@/lib/api/exam/catalog";
import { formatDate, formatTime, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { nextExams } from "@/components/student/landing/landing-data";
import { Section } from "@/components/student/landing/section";

/** The next few live exams across both types. Hidden when there are none. */
export function Upcoming() {
  const { t, locale } = useLocale();
  const mcq = useUpcomingExams("MCQ");
  const written = useUpcomingExams("WRITTEN");
  const title = t("landing.upcomingTitle");
  const subtitle = t("landing.upcomingSubtitle");

  if (mcq.isPending || written.isPending) {
    return (
      <Section id="landing-upcoming" title={title} subtitle={subtitle}>
        <Skeleton variant="card" count={2} />
      </Section>
    );
  }
  const exams = nextExams([mcq.data ?? [], written.data ?? []], 5);
  if (exams.length === 0) return null;

  return (
    <Section id="landing-upcoming" title={title} subtitle={subtitle}>
      <ul className="grid gap-3 md:grid-cols-2">
        {exams.map((exam) => (
          <li key={`${exam.examType}-${exam.id}`}>
            <Card href={examPath(exam.id)}>
              {exam.liveAt ? (
                <p className="text-body text-muted">
                  {formatDate(exam.liveAt, locale, { year: "auto" })} · {formatTime(exam.liveAt, locale)}
                </p>
              ) : null}
              <p className="font-bold">{pickTitle(exam, locale)}</p>
              <p className="mt-1 flex flex-wrap items-center gap-2 text-body text-muted">
                <TypePill type={exam.examType} />
                <span>{pickTitle({ titleEn: exam.moduleTitleEn ?? "", titleBn: exam.moduleTitleBn ?? "" }, locale)}</span>
              </p>
            </Card>
          </li>
        ))}
      </ul>
    </Section>
  );
}
