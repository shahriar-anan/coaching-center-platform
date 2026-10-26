"use client";

import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useLocale } from "@/i18n/locale-context";
import { useSystemStatus, type SystemCheck } from "@/lib/api/exam/staff-admin";

/**
 * One row per configuration check: its name in plain words, a Yes or No pill,
 * and a note. A failed check adds one sentence on what to change. The page writes
 * every word itself, from the check's name. The API's `note` is never shown, so a
 * value in it cannot reach the screen, and neither can an unknown field.
 */
export function SystemStatusList() {
  const { t } = useLocale();
  const status = useSystemStatus();

  const columns: Column<SystemCheck>[] = [
    {
      key: "check",
      header: t("staffAdmin.status.columns.check"),
      cell: (row) => (
        <span className="grid gap-1">
          <span className="text-body text-text">{plainName(row.name, t)}</span>
          {row.ok ? null : <span className="text-meta text-danger">{fixSentence(row.name, t)}</span>}
        </span>
      ),
    },
    {
      key: "result",
      header: t("staffAdmin.status.columns.result"),
      cell: (row) =>
        row.ok ? (
          <Pill tone="ok">{t("staffAdmin.status.yes")}</Pill>
        ) : (
          <Pill tone="danger">{t("staffAdmin.status.no")}</Pill>
        ),
    },
    {
      key: "note",
      header: t("staffAdmin.status.columns.note"),
      cell: (row) => <span className="text-meta text-muted">{plainNote(row.name, t)}</span>,
    },
  ];

  if (status.isError) {
    return <ErrorState message={t("staffAdmin.status.loadError")} onRetry={() => void status.refetch()} />;
  }
  if (status.isPending) {
    return <Skeleton variant="rows" count={5} />;
  }
  const checks = status.data?.checks ?? [];
  if (checks.length === 0) {
    return <EmptyState title={t("staffAdmin.status.empty")} />;
  }
  return <Table columns={columns} rows={checks} rowKey={(row) => row.name} loading={status.isFetching} />;
}

type Translate = (key: string) => string;

/** The copy key for each check the API sends. A check not listed here gets the neutral label. */
const CHECK_KEYS: Record<string, string> = {
  "checkout.configured": "checkoutConfigured",
  "checkout.profile": "checkoutProfile",
  "mail.configured": "mailConfigured",
  "google.configured": "googleConfigured",
  "mail.hour-before.off": "mailHourBeforeOff",
  "auth.dev-codes.off": "authDevCodesOff",
  "seed.off": "seedOff",
};

function plainName(name: string, t: Translate): string {
  const key = CHECK_KEYS[name];
  return key ? t(`staffAdmin.status.names.${key}`) : t("staffAdmin.status.unknownName");
}

/** What the check means, whichever way it came out. */
function plainNote(name: string, t: Translate): string {
  const key = CHECK_KEYS[name];
  return key ? t(`staffAdmin.status.notes.${key}`) : t("staffAdmin.status.unknownNote");
}

function fixSentence(name: string, t: Translate): string {
  const key = CHECK_KEYS[name];
  return key ? t(`staffAdmin.status.fixes.${key}`) : t("staffAdmin.status.unknownFix");
}
