"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { RequireStaff } from "@/lib/auth/guards";
import { useModule } from "@/lib/api/exam/staff-catalog";
import { useStaffExam } from "@/lib/api/exam/staff-ops";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { staffHref } from "@/lib/config";

/**
 * The package or exam is named in the card heading, with a link to its installments. The list under it is the same one the
 * enrol screen shows.
 */
function ExamEnrolments({ examId }: { examId: string }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const exam = useStaffExam(examId);
  return (
    <Card as="section" padding="md" className="grid gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 className="text-card-title font-semibold">{exam.data ? pickTitle(exam.data) : ""}</h2>
        <Button variant="secondary" href={staffHref(`/staff/installments?examId=${examId}`)}>
          {t("staffAdmin.enrol.manageInstallments")}
        </Button>
      </div>
      <EnrolledList scope="EXAM" targetId={examId} />
    </Card>
  );
}

function PackageEnrolments({ packageId }: { packageId: string }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const module = useModule(packageId);
  return (
    <Card as="section" padding="md" className="grid gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 className="text-card-title font-semibold">{module.data ? pickTitle(module.data) : ""}</h2>
        <Button variant="secondary" href={staffHref(`/staff/installments?moduleId=${packageId}`)}>
          {t("staffAdmin.enrol.manageInstallments")}
        </Button>
      </div>
      <EnrolledList scope="MODULE" targetId={packageId} />
    </Card>
  );
}

function EnrolmentsRoute() {
  const { t } = useLocale();
  const params = useSearchParams();
  const examId = params?.get("exam") ?? null;
  const packageId = params?.get("package") ?? null;
  if (examId) {
    return <ExamEnrolments examId={examId} />;
  }
  if (packageId) {
    return <PackageEnrolments packageId={packageId} />;
  }
  return <p className="text-meta text-muted">{t("staffAdmin.enrolments.noTarget")}</p>;
}

export default function StaffEnrolmentsPage() {
  const { t } = useLocale();
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <PageHeader
          title={t("staffAdmin.enrolments.title")}
          breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("staffAdmin.enrolments.title") }]}
        />
        <EnrolmentsRoute />
      </RequireStaff>
    </Suspense>
  );
}
