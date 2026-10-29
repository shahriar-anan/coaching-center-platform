"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { apiRequest } from "@/lib/api/client";
import { RequireStaff } from "@/lib/auth/guards";
import { DirectEnrolment, type FoundStudent } from "@/components/staff/enrol/direct-enrolment";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";

/** Opened from a student's page with `?student=<id>`, the list starts with that student already on it. */
function EnrolRoute() {
  const params = useSearchParams();
  const studentId = params?.get("student") ?? null;
  const student = useQuery({
    queryKey: ["staffEnrol", "student", studentId],
    queryFn: ({ signal }) => apiRequest<FoundStudent>(`/api/v1/students/${studentId}`, { signal }),
    enabled: studentId !== null,
    staleTime: 60_000,
  });

  if (studentId !== null && student.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  const examId = params?.get("exam") ?? undefined;
  return (
    <DirectEnrolment
      key={`${studentId ?? "none"}-${examId ?? "any"}`}
      initialStudents={student.data ? [student.data] : []}
      initialExamId={examId}
    />
  );
}

export default function StaffEnrolPage() {
  const { t } = useLocale();
  return (
    <Suspense fallback={<Skeleton variant="card" count={2} />}>
      <RequireStaff roles={["MASTER_ADMIN"]}>
        <PageHeader
          title={t("staffAdmin.enrol.title")}
          breadcrumbs={[{ label: t("common.nav.home"), href: "/staff" }, { label: t("staffAdmin.enrol.title") }]}
        />
        <EnrolRoute />
      </RequireStaff>
    </Suspense>
  );
}
