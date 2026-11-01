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
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { accessSourceKey } from "@/components/student/result/access-source";
import { RateLink, canRateSitting } from "@/components/student/reviews/rate-link";
import { reviewedTargets, useMyReviews } from "@/lib/api/exam/reviews";
import { useMyExamResults } from "@/lib/api/exam/sitting";
import { doorPath, examPath, modulePath } from "./catalog-helpers";
import { accessEnd, orderedRows, type AccessEnd } from "./my-exams-rows";
import { TypeFilter, filterByType, typeLabelKey, useTypeFilterParam } from "./type-filter";

type Tab = "exams" | "packages";

/** The access, as a word in the row's grey line: lifetime, the end date, or expired (plan S1.3). Coloured only where it matters. */
function AccessText({ end }: { end: AccessEnd }) {
  const { t, locale } = useLocale();
  if (end.kind === "expired") {
    return <span className="font-semibold text-warn">{t("catalog.myExams.expired")}</span>;
  }
  if (end.kind === "lifetime") {
    return <span className="font-semibold text-ok">{t("catalog.myExams.accessLifetime")}</span>;
  }
  return <span>{t("catalog.myExams.accessUntil", { date: formatDate(end.endsAt, locale, { year: "auto" }) })}</span>;
}

/** The grey line under a row's title: each fact in turn, with a dot between them. Empty facts are left out. */
function FactLine({ facts }: { facts: React.ReactNode[] }) {
  const shown = facts.filter((fact) => fact !== null && fact !== false && fact !== undefined);
  return (
    <p className="flex flex-wrap items-center gap-x-1.5 text-meta text-muted">
      {shown.map((fact, index) => (
        <span key={index} className="flex items-center gap-1.5">
          {index > 0 ? <span aria-hidden="true">·</span> : null}
          {fact}
        </span>
      ))}
    </p>
  );
}

/** A title, or what the row shows while its title loads or when the read fails. */
function TitleText({ title, pending, href }: { title: string; pending: boolean; href: string }) {
  const { t } = useLocale();
  if (!title) {
    return <span className="text-muted">{pending ? t("catalog.loading") : t("catalog.myExams.detailsMissing")}</span>;
  }
  return (
    <Link href={href} prefetch={false} className="font-semibold text-text hover:underline underline-offset-4">
      {title}
    </Link>
  );
}

/** "View payment" for a row bought with an order (Phase 4, S4.6). A roster or manual grant has no order, so no link. */
function PaymentLink({ orderId }: { orderId: string }) {
  const { t } = useLocale();
  return (
    <Link
      href={`/account/payments?order=${encodeURIComponent(orderId)}`}
      prefetch={false}
      className="inline-flex min-h-11 shrink-0 items-center rounded-control text-label font-semibold text-accent underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
    >
      {t("catalog.myExams.viewPayment")}
    </Link>
  );
}

function ExamRow({
  grant,
  exam,
  pending,
  rateable,
}: {
  grant: OwnExamGrant;
  exam: PublicExam | undefined;
  pending: boolean;
  /** The student sat this exam and has not reviewed it: show "Rate". */
  rateable: boolean;
}) {
  const { t, locale } = useLocale();
  const sourceKey = accessSourceKey(grant.via);
  return (
    <li>
      <Card padding="sm">
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
          <div className="grid min-w-0 flex-1 gap-1">
            <p className="text-body">
              <TitleText title={exam ? pickTitle(exam, locale) : ""} pending={pending} href={examPath(grant.examId)} />
            </p>
            <FactLine
              facts={[
                exam ? t(typeLabelKey(exam.examType)) : null,
                <AccessText key="access" end={accessEnd(grant)} />,
                sourceKey ? t(sourceKey) : null,
              ]}
            />
          </div>
          <div className="flex shrink-0 flex-wrap items-center gap-x-3">
            {grant.orderId ? <PaymentLink orderId={grant.orderId} /> : null}
            {rateable ? <RateLink examId={grant.examId} /> : null}
          </div>
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
      <Card padding="sm">
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
          <div className="grid min-w-0 flex-1 gap-1">
            <p className="text-body">
              <TitleText title={pkg ? pickTitle(pkg, locale) : ""} pending={pending} href={modulePath("mcq", grant.moduleId)} />
            </p>
            <FactLine facts={[<AccessText key="access" end={accessEnd(grant)} />, sourceKey ? t(sourceKey) : null]} />
          </div>
          {grant.orderId ? (
            <div className="flex shrink-0 items-center">
              <PaymentLink orderId={grant.orderId} />
            </div>
          ) : null}
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
  // The sittings the student has made (the first pages) give the status of each exam, and the reviews show which are rated.
  const sittings = useMyExamResults(null);
  const sittingStatus = new Map(
    (sittings.data?.pages.flatMap((page) => page.content) ?? []).map((row): [string, string] => [row.examId, row.status]),
  );
  const myReviews = useMyReviews(true);
  const reviewed = reviewedTargets(myReviews.data);
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
        <ErrorState error={access.error} onRetry={() => void access.refetch()} />
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
      <PageHeader
        title={t("catalog.myExams.title")}
        breadcrumbs={crumbs}
        actions={<Link href="/account/payments" prefetch={false} className="font-semibold text-accent underline underline-offset-4">{t("account.payments.link")}</Link>}
      />
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
            <ul className="grid gap-2">
              {typedExamRows.map((row) => (
                <ExamRow
                  key={row.examId}
                  grant={row}
                  exam={row.exam}
                  pending={row.pending}
                  rateable={canRateSitting(sittingStatus.get(row.examId), row.examId, reviewed)}
                />
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
          <ul className="grid gap-2">
            {packageRows.map((row) => (
              <PackageRow key={row.moduleId} grant={row} pkg={row.pkg} pending={row.pending} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
