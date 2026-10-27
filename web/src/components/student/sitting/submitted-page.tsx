"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { useStudentExam } from "@/lib/api/exam/sitting";
import { useFormat } from "@/lib/format/use-format";
import { studentHome } from "@/lib/config";

/**
 * Where a student lands after confirming a written submission: a plain statement that the
 * script was received and is final, and what happens next.
 * Someone who opens the address without a stored script is sent back to the exam.
 */
export function SubmittedPage({ examId }: { examId: string }) {
  const { t } = useLocale();
  const format = useFormat();
  const router = useRouter();
  const exam = useStudentExam(examId);
  const data = exam.data;
  const stored = Boolean(data?.answerStored);

  useEffect(() => {
    if (data && !stored) {
      router.replace(`/exam/${examId}/sit`);
    }
  }, [data, stored, router, examId]);

  if (exam.isPending || (data && !stored)) {
    return <Skeleton variant="card" />;
  }
  if (exam.isError || !data) {
    return <ErrorState onRetry={() => exam.refetch()} />;
  }

  const title = format.pickTitle({ titleEn: data.titleEn, titleBn: data.titleBn ?? data.titleEn });

  return (
    <div className="mx-auto grid max-w-xl gap-4 py-6">
      <Card>
        <div className="grid justify-items-center gap-3 py-4 text-center">
          <CheckCircle2 aria-hidden="true" className="size-14 text-ok" />
          <h1 className="text-title tracking-tight text-text">{t("sitting.submitted.title")}</h1>
          <p className="text-body text-text">{t("sitting.submitted.body", { exam: title })}</p>
          <p className="text-body text-muted">{t("sitting.submitted.final")}</p>
          <p className="text-body text-muted">{t("sitting.submitted.next")}</p>
          <div className="mt-2 flex flex-wrap justify-center gap-2">
            <Button href={studentHome}>{t("sitting.submitted.home")}</Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
