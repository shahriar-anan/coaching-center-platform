"use client";

import { useState, type ReactNode } from "react";
import { FileText, ListChecks, Package, type LucideIcon } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { formatDate, formatMoney, pickTitle } from "@/lib/format";
import {
  useOfflinePurchases,
  type OfflineExam,
  type OfflinePackage,
  type OfflinePayment,
} from "@/lib/api/exam/account";
import { isMissing, optionalDate } from "@/lib/api/missing";
import { Pill } from "@/components/ui/pill";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { examPath, modulePath } from "./catalog-helpers";
import { accessEnd } from "./my-exams-rows";
import { AccessPill, OwnedCard, TitleText } from "./owned-card";
import { typeLabelKey } from "./type-filter";
import { canOpenOfflineItem, isSettled, offlinePills, offlineRowKey } from "./offline-payment";
import { OfflinePaymentPanel } from "./offline-payment-panel";

/** The fields every Offline purchases row has, exam or package. */
type OfflineRow = {
  grantedAt: string;
  endsAt?: string | null;
  expired: boolean;
  accessRemoved?: boolean;
  payment?: OfflinePayment | null;
};

/**
 * The exams and packages a staff member enrolled the student in by hand. Each is the same card as on the Exams and Packages
 * tabs: an icon, the title with the access pill at its right, and a line of facts. A card with a payment plan also shows what
 * has been paid, what is due, the next date, and its payments (plan section 8).
 */
export function OfflinePurchases() {
  const { t } = useLocale();
  const list = useOfflinePurchases();
  if (list.isPending) {
    return <Skeleton variant="rows" count={3} />;
  }
  if (list.isError) {
    return <ErrorState error={list.error} onRetry={() => void list.refetch()} />;
  }
  const exams = list.data?.exams ?? [];
  const packages = list.data?.packages ?? [];
  if (exams.length === 0 && packages.length === 0) {
    return <EmptyState title={t("account.payments.offline.empty")} body={t("account.payments.offline.emptyBody")} />;
  }
  return (
    <ul className="grid gap-2">
      {packages.map((row) => (
        <PackageCard key={offlineRowKey("package", row.moduleId, row)} row={row} />
      ))}
      {exams.map((row) => (
        <ExamCard key={offlineRowKey("exam", row.examId, row)} row={row} />
      ))}
    </ul>
  );
}

function ExamCard({ row }: { row: OfflineExam }) {
  const { t, locale } = useLocale();
  const title = pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }, locale);
  return (
    <OfflineCard
      icon={row.examType === "WRITTEN" ? FileText : ListChecks}
      title={
        <TitleText title={title} pending={false} href={canOpenOfflineItem(row) ? examPath(row.examId) : null} />
      }
      row={row}
      facts={[t(typeLabelKey(row.examType)), t("account.payments.offline.addedBy")]}
      itemTitle={title}
    />
  );
}

function PackageCard({ row }: { row: OfflinePackage }) {
  const { t, locale } = useLocale();
  const title = pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }, locale);
  // The package opens on the door the API names. Without one (an older API), the MCQ door as before.
  const door = row.door === "written" ? "written" : "mcq";
  return (
    <OfflineCard
      icon={Package}
      title={
        <TitleText title={title} pending={false} href={canOpenOfflineItem(row) ? modulePath(door, row.moduleId) : null} />
      }
      row={row}
      facts={[t("account.payments.offline.packageType"), t("account.payments.offline.addedBy")]}
      itemTitle={title}
    />
  );
}

function OfflineCard({
  icon,
  title,
  row,
  facts,
  itemTitle,
}: {
  icon: LucideIcon;
  title: ReactNode;
  row: OfflineRow;
  facts: string[];
  itemTitle: string;
}) {
  return (
    <OwnedCard
      icon={icon}
      title={title}
      access={<OfflinePills row={row} />}
      facts={facts}
      footer={isMissing(row.payment) ? null : <PaymentSummary payment={row.payment} itemTitle={itemTitle} />}
    />
  );
}

/** The access end and, for a card with a plan, its status: Active, Due soon, Overdue, Completed or Access ended. */
function OfflinePills({ row }: { row: OfflineRow }) {
  const { t } = useLocale();
  const pills = offlinePills(row);
  return (
    <span className="flex flex-wrap items-center gap-2">
      {pills.showAccessEnd ? <AccessPill end={accessEnd(row)} /> : null}
      {pills.status ? <Pill tone={pills.status.tone}>{t(pills.status.key)}</Pill> : null}
    </span>
  );
}

/** What is paid and due on the plan, the next date, and the button that opens the payments. */
function PaymentSummary({ payment, itemTitle }: { payment: OfflinePayment; itemTitle: string }) {
  const { t, locale } = useLocale();
  const [open, setOpen] = useState(false);
  const paid = formatMoney(payment.paid, locale);
  const due = formatMoney(payment.due, locale);
  const nextDate = optionalDate(payment.nextDueOn);
  return (
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-meta text-muted">
        {paid ? <span className="tabular-nums">{t("account.payments.offline.paidLine", { amount: paid })}</span> : null}
        {due ? <span className="tabular-nums">{t("account.payments.offline.dueLine", { amount: due })}</span> : null}
        {nextDate ? (
          <span>{t("account.payments.offline.nextLine", { date: formatDate(nextDate, locale) })}</span>
        ) : null}
        {isSettled(payment) ? <span>{t("account.payments.offline.settled")}</span> : null}
      </div>
      {payment.planId ? (
        <>
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="inline-flex min-h-9 w-fit items-center rounded-control border border-border px-3 text-meta font-semibold text-text transition-colors hover:border-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          >
            {t("account.payments.offline.viewPayments")}
          </button>
          <OfflinePaymentPanel open={open} onOpenChange={setOpen} planId={payment.planId} itemTitle={itemTitle} />
        </>
      ) : null}
    </div>
  );
}
