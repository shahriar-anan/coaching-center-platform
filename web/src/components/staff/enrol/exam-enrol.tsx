"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { apiRequest } from "@/lib/api/client";
import { staffCatalogKeys } from "@/lib/api/exam/staff-catalog";
import { useStaffExam } from "@/lib/api/exam/staff-ops";
import type { GrantResponse } from "@/lib/api/exam/staff-admin";
import { staffHref } from "@/lib/config";
import { Button } from "@/components/ui/button";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { OneStudentEnrol } from "@/components/staff/enrol/one-student-enrol";
import { grantBody } from "@/components/staff/enrol/payment-model";

/**
 * Master Admin only: enrols one student directly in this exam. The Master Admin records the payment here, or chooses No charge
 * with a reason. Only a published exam takes new students; the ones already enrolled keep their access if it is unpublished.
 */
export function ExamEnrol({ examId, published }: { examId: string; published: boolean }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const { isMasterAdmin } = useRole();
  const queryClient = useQueryClient();
  const exam = useStaffExam(examId);
  return (
    <div className="grid gap-6">
      {isMasterAdmin ? (
        <div className="flex justify-end">
          <Button variant="secondary" href={staffHref(`/staff/installments?examId=${examId}`)}>
            {t("staffAdmin.enrol.manageInstallments")}
          </Button>
        </div>
      ) : null}
      <OneStudentEnrol
        headingId="exam-enrol-heading"
        enabled={published}
        disabledHint={t("staffCatalog.enrol.unpublishedExam")}
        requireReason
        enrolledIds={new Set()}
        payment={{ scope: "EXAM", targetId: examId, targetTitle: pickTitle(exam.data) }}
        enrol={async (studentId, reason, payment) => {
          const result = await apiRequest<GrantResponse>("/api/v1/live-exam-grants", {
            method: "POST",
            body: grantBody("EXAM", examId, studentId, reason, payment),
          });
          await queryClient.invalidateQueries({ queryKey: staffCatalogKeys.exam(examId) });
          await queryClient.invalidateQueries({ queryKey: ["staffEnrol"] });
          return result;
        }}
      />
      <div className="grid gap-3">
        <h3 className="text-label font-semibold">{t("staffAdmin.enrolments.title")}</h3>
        <EnrolledList scope="EXAM" targetId={examId} />
      </div>
    </div>
  );
}
