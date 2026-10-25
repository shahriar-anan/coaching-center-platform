"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { usePublishAllResults } from "@/lib/api/exam/staff-results";

/**
 * "সব খসড়া প্রকাশ করুন (N)". Master Admin only; the caller hides it for other roles and for
 * MCQ exams. The count is the server's draft count, and the confirm names the exam and the count.
 */
export function PublishResultsButton({
  examId,
  examTitle,
  drafts,
  loading,
}: {
  examId: string;
  examTitle: string;
  drafts: number | undefined;
  loading: boolean;
}) {
  const { t } = useLocale();
  const format = useFormat();
  const { toast } = useToast();
  const publish = usePublishAllResults(examId);
  const [open, setOpen] = useState(false);
  const count = drafts ?? 0;

  async function confirm() {
    try {
      const result = await publish.mutateAsync();
      toast({ title: t("staffResults.publish.done", { count: format.formatNumber(result.published) }), tone: "ok" });
    } catch {
      throw new Error(t("staffResults.publish.failed"));
    }
  }

  return (
    <>
      <Button
        variant="primary"
        disabled={loading || count === 0}
        title={count === 0 && !loading ? t("staffResults.publish.none") : undefined}
        onClick={() => setOpen(true)}
      >
        {loading ? t("common.states.loading") : count === 0 ? t("staffResults.publish.none") : t("staffResults.publish.button", { count: format.formatNumber(count) })}
      </Button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        title={t("staffResults.publish.confirmTitle", { exam: examTitle })}
        body={t("staffResults.publish.confirmBody", { count: format.formatNumber(count) })}
        confirmLabel={t("staffResults.publish.confirm")}
        onConfirm={confirm}
      />
    </>
  );
}
