"use client";

import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { ErrorState } from "@/components/ui/error-state";
import { useAdmissionFees, useAdmissionHistory, type AdmissionDetail, type FeeSchedule } from "@/lib/api/exam/offline-admin";
import { CloseDialog, TermsDialog } from "./admission-dialogs";
import { FeesTab } from "./fees-tab";
import { FormTab } from "./form-tab";
import { ItemsPanel } from "./items-panel";
import { historyLabelKey } from "./history";
import { moneyPills, nextOwedDue, toNumber } from "./offline-model";
import { stateLabelKey } from "./labels";
import { MoneyPills } from "./money-pills";

export const AFTER_TABS = ["overview", "fees", "items", "form", "history"] as const;
export type AfterTab = (typeof AFTER_TABS)[number];

/** The tab from the URL, or the overview when the URL has none or an unknown one. */
export function parseAfterTab(raw: string | null | undefined): AfterTab {
  return (AFTER_TABS as readonly string[]).includes(raw ?? "") ? (raw as AfterTab) : "overview";
}

/**
 * The page once an admission is onboarded (plan 6.3): Overview, Fees, Exams and packages, Form and History. The Master Admin alone changes
 * the terms, the exams and packages, and closes the admission.
 */
export function AfterOnboarding({
  admission,
  tab,
  onTab,
  canReview,
  canOnboard,
  openPayment,
}: {
  admission: AdmissionDetail;
  tab: AfterTab;
  onTab: (tab: AfterTab) => void;
  canReview: boolean;
  canOnboard: boolean;
  openPayment: boolean;
}) {
  const { t } = useLocale();
  const active = admission.state === "ONBOARDED";
  const tabs = [
    { value: "overview", label: t("staffOffline.tabs.overview") },
    { value: "fees", label: t("staffOffline.tabs.fees") },
    { value: "items", label: t("staffOffline.tabs.items") },
    { value: "form", label: t("staffOffline.tabs.form") },
    { value: "history", label: t("staffOffline.tabs.history") },
  ];
  return (
    <div className="grid gap-5">
      <Tabs tabs={tabs} value={tab} onChange={(value) => onTab(parseAfterTab(value))} ariaLabel={t("staffOffline.tabs.label")} wrap />
      {tab === "overview" ? <OverviewPanel admission={admission} canOnboard={canOnboard && active} /> : null}
      {tab === "fees" ? (
        <FeesTab admission={admission} canRecord={canReview} canApprove={canOnboard} openOnLoad={openPayment} />
      ) : null}
      {tab === "items" ? (
        <ItemsPanel admission={admission} canEdit={canOnboard && active} accessUntil={admission.setup.courseEndsOn} />
      ) : null}
      {tab === "form" ? <FormTab admission={admission} canReview={canReview} /> : null}
      {tab === "history" ? <HistoryPanel admission={admission} /> : null}
    </div>
  );
}

function OverviewPanel({ admission, canOnboard }: { admission: AdmissionDetail; canOnboard: boolean }) {
  const { t } = useLocale();
  const { formatDate, formatMoney, formatDateTime, pickTitle } = useFormat();
  const fees = useAdmissionFees(admission.id);
  const [terms, setTerms] = useState(false);
  const [closing, setClosing] = useState(false);
  const schedule: FeeSchedule | undefined = fees.data;
  const next = schedule ? nextOwedDue(schedule.dues) : null;
  const batch = pickTitle({ titleEn: admission.setup.batchNameEn, titleBn: admission.setup.batchNameBn });
  const closed = admission.state === "CLOSED";
  return (
    <div className="grid gap-5">
      {closed ? (
        <p className="rounded-control bg-bg px-3 py-2 text-body text-muted">
          {t("staffOffline.overview.closedOn", { date: admission.closedAt ? formatDateTime(admission.closedAt) : "" })}
          {admission.closedReason ? ` · ${admission.closedReason}` : ""}
        </p>
      ) : null}
      <dl className="grid gap-3 rounded-card border border-border-control bg-card p-4 sm:grid-cols-2">
        <Item label={t("staffOffline.overview.batch")} value={batch || "—"} />
        <Item label={t("staffOffline.overview.state")} value={<Pill tone={closed ? "muted" : "ok"}>{t(stateLabelKey(admission.state))}</Pill>} />
        <Item label={t("staffOffline.overview.admittedOn")} value={admission.setup.admittedOn ? formatDate(admission.setup.admittedOn) : "—"} />
        <Item label={t("staffOffline.overview.courseEnds")} value={admission.setup.courseEndsOn ? formatDate(admission.setup.courseEndsOn) : "—"} />
        <Item label={t("staffOffline.overview.fee")} value={formatMoney(toNumber(admission.setup.monthlyFee)) ?? "—"} />
        <Item
          label={t("staffOffline.overview.nextDue")}
          value={next ? `${formatMoney(toNumber(next.owed))} · ${formatDate(next.dueOn)}` : t("staffOffline.overview.noneDue")}
        />
        <Item label={t("staffOffline.overview.onboardedOn")} value={admission.onboardedAt ? formatDateTime(admission.onboardedAt) : "—"} />
        <Item label={t("staffOffline.overview.items")} value={String(admission.items.length)} />
      </dl>
      {schedule ? <MoneyPills pills={moneyPills(schedule)} /> : null}
      {canOnboard && !closed ? (
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={() => setTerms(true)}>
            {t("staffOffline.overview.changeTerms")}
          </Button>
          <Button variant="danger" onClick={() => setClosing(true)}>
            {t("staffOffline.overview.close")}
          </Button>
        </div>
      ) : null}
      {canOnboard ? (
        <>
          <TermsDialog admission={admission} open={terms} onOpenChange={setTerms} />
          <CloseDialog admission={admission} open={closing} onOpenChange={setClosing} />
        </>
      ) : null}
    </div>
  );
}

function Item({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-meta text-muted">{label}</dt>
      <dd className="break-words text-body text-text">{value}</dd>
    </div>
  );
}

/** The admission's history, as the API builds it: newest first, with who did each step. */
function HistoryPanel({ admission }: { admission: AdmissionDetail }) {
  const { t } = useLocale();
  const { formatDateTime } = useFormat();
  const timeline = useAdmissionHistory(admission.id);
  if (timeline.isPending) {
    return <Skeleton variant="rows" count={3} />;
  }
  if (timeline.isError) {
    return <ErrorState onRetry={() => timeline.refetch()} error={timeline.error} />;
  }
  const rows = timeline.data ?? [];
  if (rows.length === 0) {
    return <p className="text-meta text-muted">{t("staffOffline.history.empty")}</p>;
  }
  return (
    <ol className="grid gap-2" aria-label={t("staffOffline.history.title")}>
      {rows.map((row, index) => (
        <li key={`${row.action}-${row.at}-${index}`} className="grid gap-0.5 rounded-control border border-border-control bg-card px-3 py-2">
          <span className="text-body text-text">{t(historyLabelKey(row.action))}</span>
          <span className="text-meta text-muted">
            {row.actorName ? t("staffOffline.history.by", { name: row.actorName, time: formatDateTime(row.at) }) : formatDateTime(row.at)}
          </span>
          {row.detail ? <span className="break-words text-meta text-muted">{row.detail}</span> : null}
        </li>
      ))}
    </ol>
  );
}
