"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { apiRequest } from "@/lib/api/client";
import { staffCatalogKeys } from "@/lib/api/exam/staff-catalog";
import { OneStudentEnrol } from "@/components/staff/enrol/one-student-enrol";

/**
 * Master Admin only: enrols one student directly in this exam. The grant needs a reason, which is kept for the audit
 * record. Only a published exam takes new students; the ones already enrolled keep their access if it is unpublished.
 */
export function ExamEnrol({ examId, published }: { examId: string; published: boolean }) {
  const { t } = useLocale();
  const queryClient = useQueryClient();
  return (
    <OneStudentEnrol
      headingId="exam-enrol-heading"
      enabled={published}
      disabledHint={t("staffCatalog.enrol.unpublishedExam")}
      requireReason
      enrolledIds={new Set()}
      enrol={async (studentId, reason) => {
        await apiRequest("/api/v1/live-exam-grants", { method: "POST", body: { scope: "EXAM", examId, studentId, reason } });
        await queryClient.invalidateQueries({ queryKey: staffCatalogKeys.exam(examId) });
      }}
    />
  );
}
