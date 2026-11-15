"use client";

import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney } from "@/lib/format";
import { nextDueOf, showsMyAdmissionLinks } from "@/lib/admission/my-admission";
import { useMyAdmission } from "@/lib/api/exam/offline-admission";
import { useMyAdmissionFees } from "@/lib/api/exam/my-admission";
import { Card } from "@/components/ui/card";

export const MY_ADMISSION_HREF = "/offline-activities";

/**
 * The home card: "My admission: next payment ৳X on <date>". It shows only for an onboarded admission, and only for a signed-in
 * student, so every other student makes no fee request. The admission query is the one the shell already holds.
 */
export function HomeAdmissionCard() {
  const { t, locale } = useLocale();
  const admission = useMyAdmission();
  const active = showsMyAdmissionLinks(admission.data?.state);
  const fees = useMyAdmissionFees({ enabled: active });
  if (!active || fees.isPending) {
    return null;
  }
  const next = fees.data ? nextDueOf(fees.data.dues) : null;
  const text =
    fees.isError || !fees.data
      ? t("myAdmission.title")
      : next
        ? t("myAdmission.homeCard", {
            amount: formatMoney(next.owed, locale) ?? "",
            date: formatDate(next.dueOn, locale, { year: "auto" }),
          })
        : t("myAdmission.homeCardNone");
  return (
    <section aria-label={t("myAdmission.title")} className="mb-4">
      <Card href={MY_ADMISSION_HREF} padding="sm">
        <p className="font-bold">{text}</p>
      </Card>
    </section>
  );
}

/** The account page's row to My admission. Same rule as the home card and the menu: onboarded admissions only. */
export function AccountAdmissionRow() {
  const { t } = useLocale();
  const admission = useMyAdmission();
  if (!showsMyAdmissionLinks(admission.data?.state)) {
    return null;
  }
  return (
    <div className="mt-3">
      <Card href={MY_ADMISSION_HREF}>
        <span className="font-bold">{t("myAdmission.menu")}</span>
      </Card>
    </div>
  );
}
