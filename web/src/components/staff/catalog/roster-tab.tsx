"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { apiRequest } from "@/lib/api/client";
import { useModule } from "@/lib/api/exam/staff-catalog";
import type { GrantResponse } from "@/lib/api/exam/staff-admin";
import { staffHref } from "@/lib/config";
import { OneStudentEnrol } from "@/components/staff/enrol/one-student-enrol";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { grantBody, type GrantPayment } from "@/components/staff/enrol/payment-model";
import { Button } from "@/components/ui/button";

/**
 * Master Admin only: who is enrolled in this package, enrolled the same way as an exam. A student is enrolled one at a time
 * with the payment taken now (full price or installments), or as No charge with a reason. The list below shows everyone who
 * holds access: direct enrolments with their payment, and roster students, who can be removed from it.
 * A package that is not published takes no new students; the students already on it keep their access.
 */
export function RosterTab({ moduleId, published }: { moduleId: string; published: boolean }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const { isMasterAdmin } = useRole();
  const queryClient = useQueryClient();
  const module = useModule(moduleId);

  async function enrol(studentId: string, reason: string, payment?: GrantPayment) {
    const result = await apiRequest<GrantResponse>("/api/v1/live-exam-grants", {
      method: "POST",
      body: grantBody("MODULE", moduleId, studentId, reason, payment),
    });
    void queryClient.invalidateQueries({ queryKey: ["staffEnrol"] });
    return result;
  }

  return (
    <div className="grid gap-6">
      {isMasterAdmin ? (
        <div className="flex justify-end">
          <Button variant="secondary" href={staffHref(`/staff/installments?moduleId=${moduleId}`)}>
            {t("staffAdmin.enrol.manageInstallments")}
          </Button>
        </div>
      ) : null}

      <OneStudentEnrol
        headingId="roster-enrol-heading"
        enabled={published}
        disabledHint={t("staffCatalog.enrol.unpublishedPackage")}
        requireReason={false}
        enrolledIds={new Set()}
        enrol={enrol}
        payment={{ scope: "MODULE", targetId: moduleId, targetTitle: pickTitle(module.data) }}
      />

      <section aria-labelledby="roster-list-heading" className="grid gap-3">
        <h3 id="roster-list-heading" className="text-card-title font-semibold">
          {t("staffCatalog.roster.listHeading")}
        </h3>
        <EnrolledList scope="MODULE" targetId={moduleId} />
      </section>
    </div>
  );
}
