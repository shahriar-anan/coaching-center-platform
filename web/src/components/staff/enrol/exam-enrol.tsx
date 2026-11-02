"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { apiRequest } from "@/lib/api/client";
import { staffCatalogKeys } from "@/lib/api/exam/staff-catalog";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { OneStudentEnrol } from "@/components/staff/enrol/one-student-enrol";

/**
 * Master Admin only: enrols one student directly in this exam. The grant needs a reason, which is kept for the audit
 * record. Only a published exam takes new students; the ones already enrolled keep their access if it is unpublished.
 */
export function ExamEnrol({ examId, published }: { examId: string; published: boolean }) {
  const { t } = useLocale();
  const queryClient = useQueryClient();
  return (
    <div className="grid gap-6">
    <OneStudentEnrol
      headingId="exam-enrol-heading"
      enabled={published}
      disabledHint={t("staffCatalog.enrol.unpublishedExam")}
      requireReason
      enrolledIds={new Set()}
      enrol={async (studentId, reason) => {
        await apiRequest("/api/v1/live-exam-grants", { method: "POST", body: { scope: "EXAM", examId, studentId, reason } });
        await queryClient.invalidateQueries({ queryKey: staffCatalogKeys.exam(examId) });
        await queryClient.invalidateQueries({ queryKey: ["staffEnrol"] });
      }}
    />
    <div className="grid gap-3">
      <h3 className="text-label font-semibold">{t("staffAdmin.enrolments.title")}</h3>
      <EnrolledList scope="EXAM" targetId={examId} />
    </div>
    </div>
  );
}
