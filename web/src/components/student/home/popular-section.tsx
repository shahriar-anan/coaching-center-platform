"use client";

import { usePopularExams, usePopularPackages } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { Skeleton } from "@/components/ui/skeleton";
import { CatalogExamRow } from "@/components/student/catalog/exam-row";
import { PackageLinkCard } from "@/components/student/catalog/exam-packages";
import { modulePath } from "@/components/student/catalog/catalog-helpers";

/**
 * The popular exams and packages, on the student home (plan S3.1). Only the empty state is spelled out: when nothing is popular,
 * the section says so instead of disappearing.
 */
export function PopularSection() {
  const { t } = useLocale();
  const exams = usePopularExams();
  const packages = usePopularPackages();
  const examRows = exams.data ?? [];
  const packageRows = packages.data ?? [];

  return (
    <section className="mt-6" aria-labelledby="popular-section">
      <h2 id="popular-section" className="mb-2 text-card-title font-bold">
        {t("catalog.popular.title")}
      </h2>
      {exams.isPending || packages.isPending ? (
        <Skeleton variant="card" count={2} />
      ) : examRows.length === 0 && packageRows.length === 0 ? (
        <p className="text-muted">{t("catalog.popular.empty")}</p>
      ) : (
        <div className="grid gap-2.5">
          {examRows.map((exam) => (
            <CatalogExamRow key={exam.id} exam={exam} />
          ))}
          {packageRows.map((pkg) => (
            <PackageLinkCard
              key={pkg.id}
              href={modulePath("mcq", pkg.id)}
              pkg={pkg}
              fallback={{ id: pkg.id, titleEn: pkg.titleEn, titleBn: pkg.titleBn }}
              popular
            />
          ))}
        </div>
      )}
    </section>
  );
}
