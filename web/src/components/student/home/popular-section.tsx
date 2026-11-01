"use client";

import Link from "next/link";
import { usePopularExams, usePopularPackages } from "@/lib/api/exam/catalog";
import { useLocale } from "@/i18n/locale-context";
import { Skeleton } from "@/components/ui/skeleton";
import { CatalogExamRow } from "@/components/student/catalog/exam-row";
import { PackageLinkCard } from "@/components/student/catalog/exam-packages";
import { modulePath } from "@/components/student/catalog/catalog-helpers";

/** How many of each the home shows; the Popular page lists them all. */
const HOME_EXAMS = 2;
const HOME_PACKAGES = 2;

/**
 * The popular exams and packages, on the student home (plan S3.1), as the right half of the row beside My orders. It shows the
 * first two exams and the first two packages and links to the full list. Only the empty state is spelled out: when nothing is
 * popular, the section says so instead of disappearing.
 */
export function PopularSection() {
  const { t } = useLocale();
  const exams = usePopularExams();
  const packages = usePopularPackages();
  const examRows = (exams.data ?? []).slice(0, HOME_EXAMS);
  const packageRows = (packages.data ?? []).slice(0, HOME_PACKAGES);

  return (
    <section aria-labelledby="popular-section">
      <div className="mb-2 flex items-center justify-between">
        <h2 id="popular-section" className="text-card-title font-bold">
          {t("catalog.popular.title")}
        </h2>
        <Link href="/packages" className="text-label font-semibold text-accent">
          {t("home.seeAll")}
        </Link>
      </div>
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
