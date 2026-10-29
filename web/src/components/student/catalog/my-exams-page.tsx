"use client";

import Link from "next/link";
import { useState } from "react";
import {
  useMyLiveExamAccess,
  usePublicExamsById,
  usePublicModulesById,
  type OwnExamGrant,
  type OwnModuleGrant,
} from "@/lib/api/exam/catalog";
import type { PublicExam, PublicModule } from "@/lib/api/exam/types";
import { formatDate, pickTitle } from "@/lib/format";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { accessSourceKey } from "@/components/student/result/access-source";
import { doorPath, examPath, modulePath } from "./catalog-helpers";
import { accessEnd, orderedRows, type AccessEnd } from "./my-exams-rows";
import { TypePill } from "./status-pill";
import { TypeFilter, filterByType, typeLabelKey, useTypeFilterParam } from "./type-filter";

type Tab = "exams" | "packages";

/** The access badge: lifetime, the end date, or expired (plan S1.3). */
function AccessBadge({ end }: { end: AccessEnd }) {
  const { t, locale } = useLocale();
  if (end.kind === "expired") {
    return <Pill tone="warn">{t("catalog.myExams.expired")}</Pill>;
  }
  if (end.kind === "lifetime") {
    return <Pill tone="ok">{t("catalog.myExams.accessLifetime")}</Pill>;
  }
  return (
    <Pill tone="accent">
      {t("catalog.myExams.accessUntil", { date: formatDate(end.endsAt, locale, { year: "auto" }) })}
    </Pill>
  );
}

/** A title, or what the row shows while its title loads or when the read fails. */
function TitleText({ title, pending, href }: { title: string; pending: boolean; href: string }) {
  const { t } = useLocale();
  if (!title) {
    return <span className="text-muted">{pending ? t("catalog.loading") : t("catalog.myExams.detailsMissing")}</span>;
  }
  return (
    <Link href={href} prefetch={false} className="font-bold text-accent">
      {title}
    </Link>
  );
}

function ExamRow({ grant, exam, pending }: { grant: OwnExamGrant; exam: PublicExam | undefined; pending: boolean }) {
  const { t, locale } = useLocale();
  const sourceKey = accessSourceKey(grant.via);
  return (
    <li>
      <Card padding="md">
        <p className="text-card-title">
          <TitleText title={exam ? pickTitle(exam, locale) : ""} pending={pending} href={examPath(grant.examId)} />
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-meta">
          {exam ? <TypePill type={exam.examType} /> : null}
          <AccessBadge end={accessEnd(grant)} />
          {sourceKey ? <Pill tone="muted">{t(sourceKey)}</Pill> : null}
        </div>
      </Card>
    </li>
  );
}

function PackageRow({ grant, pkg, pending }: { grant: OwnModuleGrant; pkg: PublicModule | undefined; pending: boolean }) {
  const { t, locale } = useLocale();
  const sourceKey = accessSourceKey(grant.via);
  return (
    <li>
      <Card padding="md">
        <p className="text-card-title">
          <TitleText title={pkg ? pickTitle(pkg, locale) : ""} pending={pending} href={modulePath("mcq", grant.moduleId)} />
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2 text-meta">
          <AccessBadge end={accessEnd(grant)} />
          {sourceKey ? <Pill tone="muted">{t(sourceKey)}</Pill> : null}
        </div>
      </Card>
    </li>
  );
}

/**
 * The student's own exams and packages, on two tabs (plan Q3, S1.3). Reads `GET /api/v1/me/live-exam-access` for the rows and
 * their access end, and the public reads for the titles, so an expired row still has a name.
 */
export function MyExamsPage() {
  const { t } = useLocale();
  const [tab, setTab] = useState<Tab>("exams");
  const [type, setType] = useTypeFilterParam();
  const access = useMyLiveExamAccess(true);
  const grants = access.data;
  const examGrants = grants?.exams ?? [];
  const packageGrants = grants?.modules ?? [];
  const examReads = usePublicExamsById(examGrants.map((grant) => grant.examId));
  const packageReads = usePublicModulesById(packageGrants.map((grant) => grant.moduleId));
  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: t("catalog.myExams.title") }];

  if (access.isPending) {
    return (
      <>
        <PageHeader title={t("catalog.myExams.title")} breadcrumbs={crumbs} />
        <Skeleton variant="card" count={3} />
      </>
    );
  }
  if (access.isError && !access.data) {
    return (
      <>
        <PageHeader title={t("catalog.myExams.title")} breadcrumbs={crumbs} />
        <ErrorState onRetry={() => void access.refetch()} />
      </>
    );
  }

  const examRows = orderedRows(
    examGrants.map((grant, index) => ({
      ...grant,
      exam: examReads[index]?.data as PublicExam | undefined,
      pending: examReads[index]?.isPending ?? false,
    })),
  );
  const packageRows = orderedRows(
    packageGrants.map((grant, index) => ({
      ...grant,
      pkg: packageReads[index]?.data as PublicModule | undefined,
      pending: packageReads[index]?.isPending ?? false,
    })),
  );

  // The type filter applies to the exam tab only: a package holds both types, so the control hides on the Packages tab.
  const typedExamRows = filterByType(examRows, type, (row) => row.exam?.examType);

  return (
    <>
      <PageHeader title={t("catalog.myExams.title")} breadcrumbs={crumbs} />
      <Tabs
        ariaLabel={t("catalog.myExams.title")}
        tabs={[
          { value: "exams", label: t("catalog.myExams.tabExams"), badge: typedExamRows.length },
          { value: "packages", label: t("catalog.myExams.tabPackages"), badge: packageGrants.length },
        ]}
        value={tab}
        onChange={(value) => setTab(value as Tab)}
      />
      {tab === "exams" ? (
        <div className="mt-3">
          <TypeFilter value={type} onChange={setType} />
        </div>
      ) : null}

      <div className="mt-4" role="tabpanel">
        {tab === "exams" ? (
          typedExamRows.length === 0 ? (
            <EmptyState
              title={
                type
                  ? t("catalog.myExams.emptyExamsOfType", { type: t(typeLabelKey(type)) })
                  : t("catalog.myExams.emptyExams")
              }
              body={type ? undefined : t("catalog.myExams.emptyExamsBody")}
              action={
                type ? (
                  <Button variant="secondary" onClick={() => setType(null)}>{t("catalog.typeFilter.showAll")}</Button>
                ) : (
                  <div className="flex flex-wrap justify-center gap-2">
                    <Button variant="secondary" href={doorPath("mcq")}>{t("catalog.myModules.openMcq")}</Button>
                    <Button variant="secondary" href={doorPath("written")}>{t("catalog.myModules.openWritten")}</Button>
                  </div>
                )
              }
            />
          ) : (
            <ul className="grid gap-3">
              {typedExamRows.map((row) => (
                <ExamRow key={row.examId} grant={row} exam={row.exam} pending={row.pending} />
              ))}
            </ul>
          )
        ) : packageRows.length === 0 ? (
          <EmptyState
            title={t("catalog.myExams.emptyPackages")}
            body={t("catalog.myExams.emptyPackagesBody")}
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <Button variant="secondary" href={doorPath("mcq")}>{t("catalog.myModules.openMcq")}</Button>
                <Button variant="secondary" href={doorPath("written")}>{t("catalog.myModules.openWritten")}</Button>
              </div>
            }
          />
        ) : (
          <ul className="grid gap-3">
            {packageRows.map((row) => (
              <PackageRow key={row.moduleId} grant={row} pkg={row.pkg} pending={row.pending} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
