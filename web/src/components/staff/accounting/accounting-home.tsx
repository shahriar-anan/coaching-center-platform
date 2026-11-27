"use client";

import Link from "next/link";
import { ListCard } from "@/components/ui/list-card";
import { useLocale } from "@/i18n/locale-context";
import { useGroups } from "@/lib/api/accounting/groups";
import { ACCOUNTING_HOME, AccountingFrame } from "./accounting-frame";
import { pickName } from "./accounting-model";

/**
 * The accounting home. W1 shows the live groups only; W3 adds the cards, the owed list, the latest entries and the chart (plan 4.6).
 */
export function AccountingHome() {
  const { t, locale } = useLocale();
  const groups = useGroups(false);
  return (
    <AccountingFrame title={t("accounting.title")}>
      <ListCard>
        {(groups.data ?? []).map((group) => (
          <li key={group.id}>
            <Link href={`${ACCOUNTING_HOME}/groups/${group.id}`} className="flex min-h-12 items-center justify-between gap-3 p-4 text-text hover:bg-bg">
              <span className="font-medium">{pickName(group, locale)}</span>
              <span className="text-meta text-muted">{t(`accounting.groupMode.${group.mode}`)}</span>
            </Link>
          </li>
        ))}
      </ListCard>
    </AccountingFrame>
  );
}
