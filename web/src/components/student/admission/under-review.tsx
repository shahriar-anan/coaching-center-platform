"use client";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useLocale } from "@/i18n/locale-context";
import type { AdmissionView } from "@/lib/api/exam/offline-admission";
import { formatDate } from "@/lib/format";
import { useSession } from "@/lib/auth/session-context";

/** What a student sees after the form is submitted, until the office asks for changes or admits them (plan 6.2). */
export function UnderReview({ admission }: { admission: AdmissionView }) {
  const { t, locale } = useLocale();
  const { logout } = useSession();
  return (
    <div className="grid gap-6">
      <header className="grid gap-2">
        <h1 className="text-title tracking-tight text-text">{t("admission.review.title")}</h1>
      </header>
      <Card padding="lg" className="grid gap-3">
        <p className="text-body text-text">{t("admission.review.body")}</p>
        {admission.submittedAt ? (
          <p className="text-meta text-muted">
            {t("admission.review.submittedOn", { date: formatDate(admission.submittedAt, locale) })}
          </p>
        ) : null}
      </Card>
      <div className="flex flex-wrap gap-3">
        <Button href="/admission/print" variant="secondary" size="lg">
          {t("admission.review.viewPrint")}
        </Button>
        <Button type="button" variant="ghost" size="lg" onClick={() => void logout()}>
          {t("admission.review.logout")}
        </Button>
      </div>
    </div>
  );
}
