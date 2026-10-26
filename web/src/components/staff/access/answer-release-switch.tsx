"use client";

import { useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { useExamAnswerRelease, useSetAnswersReleased } from "@/lib/api/exam/staff-admin";
import { adminErrorText } from "./admin-error";

/**
 * Shows and changes whether the MCQ correct answers of one exam are released to
 * students. Each change asks first and names the exam. Written papers have no
 * answers to release, so the switch is not shown for them. Only Master Admin and
 * System Admin see it; any other role gets nothing, and no request is made.
 * `SLOT: answer release` on the exam page.
 */
export function AnswerReleaseSwitch({ examId }: { examId: string }) {
  const { isMasterAdmin, isSystemAdmin } = useRole();
  if (!isMasterAdmin && !isSystemAdmin) {
    return null;
  }
  return <ReleaseSwitch examId={examId} />;
}

function ReleaseSwitch({ examId }: { examId: string }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const { toast } = useToast();
  const exam = useExamAnswerRelease(examId);
  const release = useSetAnswersReleased(examId);
  const [askingFor, setAskingFor] = useState<boolean | null>(null);

  if (exam.isError) {
    return <ErrorState message={t("staffAdmin.release.loadError")} onRetry={() => void exam.refetch()} />;
  }
  if (exam.isPending) {
    return <Skeleton variant="text" count={1} />;
  }
  if (!exam.data || exam.data.examType !== "MCQ") {
    return null;
  }

  const released = exam.data.answersReleasedAt !== null;
  const name = pickTitle(exam.data);

  return (
    <section aria-labelledby="answer-release-title" className="mt-6 flex flex-wrap items-center gap-4">
      <h2 id="answer-release-title" className="text-card font-semibold text-text">
        {t("staffAdmin.release.label")}
      </h2>
      <div className="flex items-center gap-3">
        <Switch
          checked={released}
          label={t("staffAdmin.release.label")}
          disabled={release.isPending}
          onCheckedChange={(next) => setAskingFor(next)}
        />
        <span className="text-label text-text">
          {released ? t("staffAdmin.release.shown") : t("staffAdmin.release.withheld")}
        </span>
      </div>

      <ConfirmDialog
        open={askingFor !== null}
        onOpenChange={(open) => {
          if (!open) {
            setAskingFor(null);
          }
        }}
        title={
          askingFor
            ? t("staffAdmin.release.turnOnTitle", { exam: name })
            : t("staffAdmin.release.turnOffTitle", { exam: name })
        }
        body={askingFor ? t("staffAdmin.release.turnOnBody") : t("staffAdmin.release.turnOffBody")}
        confirmLabel={t("staffAdmin.release.confirm")}
        onConfirm={async () => {
          if (askingFor === null) {
            return;
          }
          try {
            await release.mutateAsync(askingFor);
            toast({ title: t("staffAdmin.release.done"), tone: "ok" });
          } catch (e) {
            throw new Error(adminErrorText(e, t));
          }
        }}
      />
    </section>
  );
}
