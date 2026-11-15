"use client";

import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";

/**
 * The confirmation after an application is sent (plan 2A, step 3): the batch, and the email the office will reply to. Nothing is
 * sent to the email at this point (P10); the office's review is what emails the student.
 */
export function SentPanel({ batchName, email, signedIn }: { batchName: string; email: string; signedIn: boolean }) {
  const { t } = useLocale();
  return (
    <section aria-labelledby="sent-title" className="grid gap-4 rounded-card border border-border bg-card p-5 sm:p-6">
      <h2 id="sent-title" className="text-title tracking-tight text-text">
        {t("publicAdmission.confirm.title")}
      </h2>
      <p role="status" className="text-body leading-relaxed text-text">
        {t("publicAdmission.confirm.body", { batch: batchName, email })}
      </p>
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" href="/admission">
          {t("publicAdmission.confirm.back")}
        </Button>
        {signedIn ? <Button href="/offline-activities">{t("publicAdmission.confirm.myAdmission")}</Button> : null}
      </div>
    </section>
  );
}
