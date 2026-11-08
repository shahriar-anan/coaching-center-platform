"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiRequest } from "@/lib/api/client";
import type { GrantResponse } from "@/lib/api/exam/staff-admin";
import { useExam, useModule } from "@/lib/api/exam/staff-catalog";
import { staffHref } from "@/lib/config";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { OneStudentEnrol, type FoundStudent } from "@/components/staff/enrol/one-student-enrol";
import { grantBody, type GrantPayment } from "@/components/staff/enrol/payment-model";
import { TargetPicker, type PickedTarget } from "@/components/staff/target-picker";

export type { FoundStudent };

/**
 * Master Admin only: enrols one student at a time into a published package or exam, with the payment taken now (plan D12).
 * A package or an exam passed in from its menu is chosen already. A student passed in from a student's page (`?student=`)
 * is chosen too, and the payment fields show for them straight away.
 */
export function DirectEnrolment({
  initialStudent,
  initialPackageId,
  initialExamId,
}: {
  initialStudent: FoundStudent | null;
  initialPackageId?: string;
  initialExamId?: string;
}) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const queryClient = useQueryClient();

  // The target named in the URL. Its title comes from the single read; a failed read shows the id instead.
  const presetExam = useExam(initialExamId);
  const presetPackage = useModule(initialPackageId);
  const preset: PickedTarget | null = initialExamId
    ? { kind: "exam", id: initialExamId, title: pickTitle(presetExam.data) || (presetExam.isError ? initialExamId : "") }
    : initialPackageId
      ? { kind: "package", id: initialPackageId, title: pickTitle(presetPackage.data) || (presetPackage.isError ? initialPackageId : "") }
      : null;
  // Undefined until the user chooses or clears a target. After that their choice replaces the preset.
  const [picked, setPicked] = useState<PickedTarget | null | undefined>(undefined);
  const target = picked === undefined ? preset : picked;
  const targetId = target?.id ?? "";
  const scope = target?.kind === "exam" ? "EXAM" : "MODULE";
  const manageHref = staffHref(
    target ? (scope === "EXAM" ? `/staff/installments?examId=${targetId}` : `/staff/installments?moduleId=${targetId}`) : "/staff/installments",
  );

  async function enrol(studentId: string, reason: string, payment?: GrantPayment) {
    const result = await apiRequest<GrantResponse>("/api/v1/live-exam-grants", {
      method: "POST",
      body: grantBody(scope, targetId, studentId, reason, payment),
    });
    void queryClient.invalidateQueries({ queryKey: ["staffEnrol"] });
    return result;
  }

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap justify-end gap-3">
        <Button variant="secondary" href={manageHref}>
          {t("staffAdmin.enrol.manageInstallments")}
        </Button>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
        {/* Left: the package or exam. */}
        <Card as="section" padding="md" className="grid gap-4">
          <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.targetHeading")}</h2>
          <TargetPicker
            kind="either"
            status="PUBLISHED"
            label={t("staffAdmin.enrol.scope")}
            value={target}
            onChange={(next) => setPicked(next)}
          />
          <p className="text-meta text-muted">{t("staffAdmin.enrol.publishedOnly")}</p>
        </Card>

        {/* Right: one student, found and chosen, then the payment. */}
        <OneStudentEnrol
          headingId="direct-enrol-heading"
          enabled={Boolean(target)}
          disabledHint={t("staffAdmin.enrol.chooseTargetFirst")}
          requireReason={false}
          enrolledIds={new Set()}
          enrol={enrol}
          payment={target ? { scope, targetId, targetTitle: target.title } : undefined}
          initialStudent={initialStudent}
        />
      </div>

      {/* Below both columns: who is already on the chosen package or exam, newest first, so a student just enrolled is at the top. */}
      {targetId ? (
        <Card as="section" padding="md" className="grid gap-4">
          <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.enrolledHeading")}</h2>
          <EnrolledList scope={scope} targetId={targetId} />
        </Card>
      ) : null}
    </div>
  );
}
