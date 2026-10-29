"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { RequireStaff } from "@/lib/auth/guards";
import { useModule } from "@/lib/api/exam/staff-catalog";
import { useStaffExam } from "@/lib/api/exam/staff-ops";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";

/** The package or exam is named in the card heading; the list under it is the same one the enrol screen shows. */
function ExamEnrolments({ examId }: { examId: string }) {
  const { pickTitle } = useFormat();
  const exam = useStaffExam(examId);
  return (
    <Card as="section" padding="md" className="grid gap-4">
      <h2 className="text-card-title font-semibold">{exam.data ? pickTitle(exam.data) : ""}</h2>
      <EnrolledList scope="EXAM" targetId={examId} />
    </Card>
  );
}

function PackageEnrolments({ packageId }: { packageId: string }) {
  const { pickTitle } = useFormat();
  const module = useModule(packageId);
  return (
    <Card as="section" padding="md" className="grid gap-4">
      <h2 className="text-card-title font-semibold">{module.data ? pickTitle(module.data) : ""}</h2>
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
