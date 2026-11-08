"use client";

import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import type { OverlapItem } from "@/lib/api/exam/staff-admin";
import { staffHref } from "@/lib/config";
import { isMovedFromExam, moneyLineIndexes, overlapHow } from "@/components/staff/enrol/overlap-model";
import type { EnrolScope } from "@/components/staff/enrol/payment-model";

/**
 * The warning about what the student already holds (plan 14.5, 14.11): one line for each exam or package, how it is held, and for
 * a payment plan what is paid and still due, with a link to the plan. When a package enrolment comes while an exam plan is still
 * owed, the line says plainly that the plan stays open (D26). It is shown above the payment fields and again in the confirmation.
 * It never blocks the save, and the total is adjusted by hand.
 */
export function OverlapNotice({ items, scope }: { items: OverlapItem[]; scope: EnrolScope }) {
  const { t } = useLocale();
  const { formatMoney, pickTitle } = useFormat();
  const moneyOnce = moneyLineIndexes(items);
  return (
    <div role="status" className="grid gap-3 rounded-control border border-warn/40 bg-warn/10 p-3 text-meta text-warn">
      <p className="font-semibold">{t("staffAdmin.enrol.payment.overlap.heading")}</p>
      <ul className="grid gap-3">
        {items.map((item, index) => {
          const title = pickTitle({ titleEn: item.titleEn, titleBn: item.titleBn });
          const packageTitle =
            pickTitle({ titleEn: item.packageTitleEn, titleBn: item.packageTitleBn }) || t("staffAdmin.enrol.payment.overlap.packageWord");
          const how = t(`staffAdmin.enrol.payment.overlap.how.${overlapHow(item)}`, { package: packageTitle });
          const line = t(
            item.kind === "PACKAGE" ? "staffAdmin.enrol.payment.overlap.package" : "staffAdmin.enrol.payment.overlap.exam",
            { title, how },
          );
          const paid = formatMoney(item.paid ?? null);
          const due = formatMoney(item.due ?? null);
          const hasAmounts = Boolean(paid && due);
          const showMoney = Boolean(item.planId) && moneyOnce.has(index);

          // The moved-from-exam case: the plan stays open, and the line says what that means (plan 14.11, D26).
          if (item.planId && isMovedFromExam(item, scope)) {
            return (
              <li key={`${item.kind}-${item.id}`} className="grid gap-1">
                <span>{line}</span>
                <span className="font-semibold">
                  {hasAmounts
                    ? t("staffAdmin.enrol.payment.overlap.movedFromExam", { title, paid: paid ?? "", due: due ?? "" })
                    : t("staffAdmin.enrol.payment.overlap.movedFromExamNoAmounts", { title })}
                </span>
                <Link href={staffHref(`/staff/installments/${item.planId}`)} className="w-fit font-semibold underline underline-offset-4">
                  {t("staffAdmin.enrol.payment.overlap.openPlan")}
                </Link>
              </li>
            );
          }

          return (
            <li key={`${item.kind}-${item.id}`} className="grid gap-1">
              <span>{line}</span>
              {showMoney ? (
                <>
                  <span className="tabular-nums">
                    {hasAmounts
                      ? t("staffAdmin.enrol.payment.overlap.plan", { paid: paid ?? "", due: due ?? "" })
                      : t("staffAdmin.enrol.payment.overlap.planNoAmounts")}
                  </span>
                  <Link href={staffHref(`/staff/installments/${item.planId}`)} className="w-fit font-semibold underline underline-offset-4">
                    {t("staffAdmin.enrol.payment.overlap.openPlan")}
                  </Link>
                </>
              ) : null}
            </li>
          );
        })}
      </ul>
      <p>{t("staffAdmin.enrol.payment.overlap.closing")}</p>
    </div>
  );
}
