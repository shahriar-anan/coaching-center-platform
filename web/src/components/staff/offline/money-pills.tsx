"use client";

import { Pill } from "@/components/ui/pill";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useAdmissionFees, type AdmissionQueueRow } from "@/lib/api/exam/offline-admin";
import { moneyPills, queueMoneyPills, type MoneyPill } from "./offline-model";

/** The money pills of one admission, in the fixed colour code of offline-model.ts. */
export function MoneyPills({ pills }: { pills: MoneyPill[] }) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();
  if (pills.length === 0) {
    return null;
  }
  return (
    <div className="flex flex-wrap gap-1.5">
      {pills.map((pill) => (
        <Pill key={pill.key} tone={pill.tone}>
          {t(`staffOffline.money.${pill.key}`, pill.amount === null ? {} : { amount: formatMoney(pill.amount) ?? "" })}
        </Pill>
      ))}
    </div>
  );
}

/**
 * The money of a queue row: its pills from the server's flags, and the next due that is still owed. No schedule is read for a row.
 */
export function QueueMoneyPills({ row }: { row: AdmissionQueueRow }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  return (
    <div className="grid gap-1">
      <MoneyPills pills={queueMoneyPills(row)} />
      {row.nextDueOn ? (
        <span className="text-meta text-muted">{t("staffOffline.queue.nextDue", { date: formatDate(row.nextDueOn) })}</span>
      ) : null}
    </div>
  );
}

/**
 * The money pills of one admission in a list where the row carries no money fields (the batch's students), read from its schedule.
 * Only onboarded admissions have dues, so the query runs only for them and a pending admission shows nothing.
 */
export function AdmissionMoneyPills({ admissionId, enabled }: { admissionId: string; enabled: boolean }) {
  const fees = useAdmissionFees(admissionId, enabled);
  if (!enabled || !fees.data) {
    return null;
  }
  return <MoneyPills pills={moneyPills(fees.data)} />;
}
