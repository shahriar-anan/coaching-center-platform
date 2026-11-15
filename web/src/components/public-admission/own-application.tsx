"use client";

import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import type { AdmissionView } from "@/lib/api/exam/offline-admission";
import { admissionStep, admissionStepHref } from "@/lib/admission/gate";

/**
 * "Your application" (admission-access-plan.md section 3): where the student's application for a batch stands, with the one action that
 * moves it on. The batch page shows it in place of Apply, and the apply page shows it when the application is already past the form.
 */
export function OwnApplication({ admission }: { admission: AdmissionView }) {
  const { t } = useLocale();
  const step = admissionStep(admission);
  const href = admissionStepHref(step);
  return (
    <section aria-labelledby="own-application" className="grid gap-3 rounded-card border border-accent/40 bg-card p-4 sm:p-5" data-step={step}>
      <h2 id="own-application" className="text-card-title font-bold text-text">
        {t("publicAdmission.mine.title")}
      </h2>
      <p className="text-body text-text">{t(`publicAdmission.mine.step.${step}`)}</p>
      {href ? (
        <div>
          <Button href={href} size="lg" variant={step === "REVIEW" ? "secondary" : "primary"}>
            {t(`publicAdmission.mine.action.${step}`)}
          </Button>
        </div>
      ) : null}
    </section>
  );
}
