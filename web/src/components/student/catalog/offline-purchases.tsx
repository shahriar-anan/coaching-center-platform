"use client";

import { FileText, ListChecks, Package } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { pickTitle } from "@/lib/format";
import { useOfflinePurchases, type OfflineExam, type OfflinePackage } from "@/lib/api/exam/account";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { examPath, modulePath } from "./catalog-helpers";
import { accessEnd } from "./my-exams-rows";
import { AccessPill, OwnedCard, TitleText } from "./owned-card";
import { typeLabelKey } from "./type-filter";

/**
 * The exams and packages a staff member enrolled the student in by hand. Each is the same card as on the Exams and Packages
 * tabs: an icon, the title with the access pill at its right, and a line of facts.
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
        <PackageCard key={`package-${row.moduleId}`} row={row} />
      ))}
      {exams.map((row) => (
        <ExamCard key={`exam-${row.examId}`} row={row} />
      ))}
    </ul>
  );
}

function ExamCard({ row }: { row: OfflineExam }) {
  const { t, locale } = useLocale();
  return (
    <OwnedCard
      icon={row.examType === "WRITTEN" ? FileText : ListChecks}
      title={<TitleText title={pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }, locale)} pending={false} href={examPath(row.examId)} />}
      access={<AccessPill end={accessEnd(row)} />}
      facts={[t(typeLabelKey(row.examType)), t("account.payments.offline.addedBy")]}
    />
  );
}

function PackageCard({ row }: { row: OfflinePackage }) {
  const { t, locale } = useLocale();
  return (
    <OwnedCard
      icon={Package}
      title={<TitleText title={pickTitle({ titleEn: row.titleEn, titleBn: row.titleBn }, locale)} pending={false} href={modulePath("mcq", row.moduleId)} />}
      access={<AccessPill end={accessEnd(row)} />}
      facts={[t("account.payments.offline.packageType"), t("account.payments.offline.addedBy")]}
    />
  );
}
