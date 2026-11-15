"use client";

import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import type { AdmissionDetail } from "@/lib/api/exam/offline-admin";

/**
 * The setup at a glance on the admission detail page (plan 6.3). The values are read-only here. "Edit setup" opens the setup screen, and
 * is shown only while the setup can be changed.
 */
export function SetupSummary({ admission, editable }: { admission: AdmissionDetail; editable: boolean }) {
  const { t } = useLocale();
  const { pickTitle, formatDate, formatMoney } = useFormat();
  const setup = admission.setup;
  const batch = pickTitle({ titleEn: setup.batchNameEn, titleBn: setup.batchNameBn });

  const readyText =
    admission.state === "READY"
      ? t("staffOffline.setup.summary.readyMarked")
      : admission.state === "SUBMITTED"
        ? admission.readyProblems.length === 0
          ? t("staffOffline.setup.summary.readyTicked")
          : t("staffOffline.setup.summary.readyMissing", { count: admission.readyProblems.length })
        : t("staffOffline.setup.summary.readyNotYet");

  const rows: { label: string; value: string }[] = [
    { label: t("staffOffline.setup.batch"), value: batch || "—" },
    { label: t("staffOffline.setup.studentMonthlyFee"), value: formatMoney(setup.monthlyFee) ?? "—" },
    { label: t("staffOffline.setup.admittedOn"), value: setup.admittedOn ? formatDate(setup.admittedOn) : "—" },
    { label: t("staffOffline.setup.firstDue"), value: setup.firstDueOn ? formatDate(setup.firstDueOn) : "—" },
    { label: t("staffOffline.setup.courseEnds"), value: setup.courseEndsOn ? formatDate(setup.courseEndsOn) : "—" },
    { label: t("staffOffline.setup.summary.items"), value: String(admission.items.length) },
    { label: t("staffOffline.setup.summary.ready"), value: readyText },
  ];

  return (
    <section aria-labelledby="setup-summary-title" className="grid gap-4 rounded-card border border-border-control bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id="setup-summary-title" className="text-card-title text-text">
          {t("staffOffline.setup.title")}
        </h3>
        {editable ? (
          <Button variant="secondary" size="sm" href={`/staff/admissions/${admission.id}/setup`}>
            {t("staffOffline.setup.summary.edit")}
          </Button>
        ) : null}
      </div>

      <dl className="grid gap-3 sm:grid-cols-2">
        {rows.map((row) => (
          <div key={row.label} className="grid min-w-0 gap-0.5">
            <dt className="text-meta text-muted">{row.label}</dt>
            <dd className="break-words text-body text-text">{row.value}</dd>
          </div>
        ))}
      </dl>

      {admission.state === "READY" ? <p className="text-meta text-muted">{t("staffOffline.checklist.readyNote")}</p> : null}
    </section>
  );
}
