"use client";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useLocale } from "@/i18n/locale-context";
import type { AdmissionView } from "@/lib/api/exam/offline-admission";
import { useMyAdmissions } from "@/lib/api/exam/my-admission";
import { admissionStep, isLockedOut } from "@/lib/admission/gate";
import { formatDate, pickTitle } from "@/lib/format";
import { useSession } from "@/lib/auth/session-context";
import { VerifyEmailCard } from "./verify-email-card";

/**
 * What a student sees after the form is submitted, until the office asks for changes or admits them (plan 6.2). Once the Master Admin
 * has onboarded an applicant with an account, a join code is out: the page says so and leads to the join form. A walk-in student, who is
 * held on these pages, can log out from here; everyone else goes back to the rest of the site.
 */
export function UnderReview({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  const { logout } = useSession();
  const admissions = useMyAdmissions();
  const join = admissionStep(admission) === "JOIN";
  const locked = isLockedOut(admissions.data ?? [admission]);
  const batch = pickTitle({ titleEn: admission.batchNameEn ?? null, titleBn: admission.batchNameBn ?? null }, locale);
  return (
    <div className="grid gap-6">
      <header className="grid gap-2">
        <h1 className="text-title tracking-tight text-text">{t(join ? "admission.review.approvedTitle" : "admission.review.title")}</h1>
        {batch ? <p className="text-body text-muted">{t("admission.review.batch", { batch })}</p> : null}
      </header>
      <VerifyEmailCard email={admission.form?.email ?? null} />
      <Card padding="lg" className="grid gap-3">
        <p className="text-body text-text">{t(join ? "admission.review.joinBody" : "admission.review.body")}</p>
        {admission.submittedAt ? (
          <p className="text-meta text-muted">
            {t("admission.review.submittedOn", { date: formatDate(admission.submittedAt, locale) })}
          </p>
        ) : null}
        {join ? (
          <div>
            <Button href="/admission/join" size="lg">
              {t("admission.review.enterJoinCode")}
            </Button>
          </div>
        ) : null}
      </Card>
      <div className="flex flex-wrap gap-3">
        <Button href="/admission/print" variant="secondary" size="lg">
          {t("admission.review.viewPrint")}
        </Button>
        {locked ? (
          <Button type="button" variant="ghost" size="lg" onClick={() => void logout()}>
            {t("admission.review.logout")}
          </Button>
        ) : (
          <Button href="/" variant="ghost" size="lg">
            {t("admission.review.goHome")}
          </Button>
        )}
      </div>
    </div>
  );
}
