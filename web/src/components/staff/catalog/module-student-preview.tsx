"use client";

import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useModuleExams, useStaffModuleCover, type StaffExam, type StaffModule } from "@/lib/api/exam/staff-catalog";
import { PackageCover } from "@/components/ui/package/package-cover";
import { ruleLines } from "@/components/student/catalog/lms-blocks";

/** Published exams first, in date order, undated last. */
function byDate(a: StaffExam, b: StaffExam): number {
  if (a.liveAt === b.liveAt) {
    return 0;
  }
  if (a.liveAt === null) {
    return 1;
  }
  if (b.liveAt === null) {
    return -1;
  }
  return new Date(a.liveAt).getTime() - new Date(b.liveAt).getTime();
}

/** The text in the reader's language, falling back to the other language when that one is empty. */
function textFor(en: string | null | undefined, bn: string | null | undefined, locale: "en" | "bn"): string {
  const primary = locale === "bn" ? bn : en;
  const fallback = locale === "bn" ? en : bn;
  return (primary ?? fallback ?? "").trim();
}

/**
 * The module as a student sees it: cover, title, description, price, and each published exam with its
 * date, duration, pass mark, full marks, description and numbered rules. It reads the staff data, so a
 * draft can be previewed before it is published. Nothing is sold or granted here.
 */
export function ModuleStudentPreview({ module }: { module: StaffModule }) {
  const { t, locale } = useLocale();
  const { pickTitle, formatDateTime, formatMoney, formatNumber } = useFormat();
  const exams = useModuleExams(module.id);
  const cover = useStaffModuleCover(module.id, module.coverUrl);

  if (exams.isPending) {
    return <p className="text-meta text-muted">{t("common.states.loading")}</p>;
  }

  const all = exams.data?.content ?? [];
  const published = all.filter((exam) => exam.status === "PUBLISHED");
  const hidden = all.length - published.length;
  const moduleDescription = textFor(module.descriptionEn, module.descriptionBn, locale);

  return (
    <div className="grid gap-6">
      <div className="grid gap-4 sm:grid-cols-[minmax(0,260px)_1fr] sm:items-start">
        <PackageCover src={cover.data ?? null} alt={pickTitle(module)} type={null} title={module.titleEn} className="rounded-control" />
        <div className="grid gap-2">
          <h3 className="text-card-title font-semibold">{pickTitle(module)}</h3>
          {moduleDescription ? <p className="text-body whitespace-pre-line">{moduleDescription}</p> : null}
          <p className="text-meta text-muted">
            {module.price == null ? t("catalog.priceNotSet") : t("catalog.modulePrice", { price: formatMoney(module.price) ?? "" })}
          </p>
        </div>
      </div>

      {(["MCQ", "WRITTEN"] as const).map((type) => {
        const rows = published.filter((exam) => exam.examType === type).sort(byDate);
        return (
          <section key={type} className="grid gap-3" aria-label={t(`staffCatalog.examType.${type}`)}>
            <h4 className="text-label font-semibold">{t(`staffCatalog.examType.${type}`)}</h4>
            {rows.length === 0 ? (
              <p className="text-meta text-muted">{t(type === "MCQ" ? "catalog.examListEmptyMcq" : "catalog.examListEmptyWritten")}</p>
            ) : (
              <ul className="grid gap-3">
                {rows.map((exam) => (
                  <ExamCard
                    key={exam.id}
                    exam={exam}
                    locale={locale}
                    title={locale === "bn" ? exam.titleBn : exam.titleEn}
                    when={exam.liveAt ? formatDateTime(exam.liveAt) : t("catalog.dateTbd")}
                    duration={t("staffCatalog.moduleExams.minutes", { n: formatNumber(exam.durationMinutes) })}
                    pass={exam.passPercent === null ? null : t("catalog.exam.passMarkValue", { p: formatNumber(exam.passPercent) })}
                    fullMarks={type === "WRITTEN" ? formatNumber(exam.maximum) : null}
                    price={exam.price == null ? null : formatMoney(exam.price)}
                    labels={{
                      date: t("catalog.exam.start"),
                      duration: t("catalog.exam.duration"),
                      pass: t("catalog.exam.passMark"),
                      fullMarks: t("catalog.exam.fullMarks"),
                      price: t("catalog.exam.price"),
                      rules: t("catalog.exam.rules"),
                    }}
                  />
                ))}
              </ul>
            )}
          </section>
        );
      })}

      {hidden > 0 ? <p className="text-meta text-muted">{t("staffCatalog.moduleForm.studentViewDrafts", { count: formatNumber(hidden) })}</p> : null}
    </div>
  );
}

type CardLabels = { date: string; duration: string; pass: string; fullMarks: string; price: string; rules: string };

/** One exam as a student reads it: the facts, the description, and the rules as a numbered list. */
function ExamCard({
  exam,
  locale,
  title,
  when,
  duration,
  pass,
  fullMarks,
  price,
  labels,
}: {
  exam: StaffExam;
  locale: "en" | "bn";
  title: string;
  when: string;
  duration: string;
  pass: string | null;
  fullMarks: string | null;
  price: string | null;
  labels: CardLabels;
}) {
  const description = textFor(exam.descriptionEn, exam.descriptionBn, locale);
  const rules = ruleLines(textFor(exam.rulesEn, exam.rulesBn, locale));
  const facts: [string, string][] = [
    [labels.date, when],
    [labels.duration, duration],
    ...(pass ? [[labels.pass, pass] as [string, string]] : []),
    ...(fullMarks ? [[labels.fullMarks, fullMarks] as [string, string]] : []),
    ...(price ? [[labels.price, price] as [string, string]] : []),
  ];
  return (
    <li className="grid gap-3 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-4">
      <p className="font-semibold">{title}</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-meta">
        {facts.map(([term, value]) => (
          <div key={term} className="contents">
            <dt className="text-muted">{term}</dt>
            <dd className="tabular-nums">{value}</dd>
          </div>
        ))}
      </dl>
      {description ? <p className="text-body whitespace-pre-line">{description}</p> : null}
      {rules.length > 0 ? (
        <div className="grid gap-2">
          <p className="text-meta font-medium text-muted">{labels.rules}</p>
          <ol lang={locale} className="grid list-decimal gap-1.5 ps-6 text-body">
            {rules.map((rule, index) => (
              <li key={`${index}-${rule}`}>{rule}</li>
            ))}
          </ol>
        </div>
      ) : null}
    </li>
  );
}
