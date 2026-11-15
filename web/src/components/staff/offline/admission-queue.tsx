"use client";

import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { SearchInput } from "@/components/ui/search-input";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { useRole } from "@/lib/auth/guards";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import {
  useAdmissionQueue,
  useBatches,
  useOfflineCounts,
  type AdmissionQueueRow,
  type OfflineCounts,
  type QueueMoneyFilter,
} from "@/lib/api/exam/offline-admin";
import type { AdmissionState } from "@/lib/api/exam/offline-admission";
import type { AdmissionSource } from "@/lib/api/exam/offline-admin";
import type { Role } from "@/lib/api/exam/types";
import { AdmissionPhoto } from "./admission-photo";
import { DeskActivationDialog, type ShownCode } from "./desk-activation-dialog";
import { DeskStartDialog } from "./desk-start-dialog";
import { QueueMoneyPills } from "./money-pills";
import {
  QUEUE_MONEY_FILTERS,
  QUEUE_TABS,
  parseMoneyFilter,
  parseQueueTab,
  waitingFor,
  type QueueTab,
} from "./offline-model";
import { stateLabelKey } from "./labels";

/** The count each queue tab shows, from the counts endpoint. */
const TAB_COUNT: Record<QueueTab, keyof OfflineCounts> = {
  AWAITING_FORM: "awaitingForm",
  SUBMITTED: "submitted",
  CHANGES_REQUESTED: "changesRequested",
  READY: "ready",
  ONBOARDED: "onboarded",
  CLOSED: "closed",
};

const STATE_TONE: Record<AdmissionState, PillTone> = {
  AWAITING_FORM: "muted",
  SUBMITTED: "accent",
  CHANGES_REQUESTED: "warn",
  READY: "warn",
  ONBOARDED: "ok",
  CLOSED: "muted",
};

/** The copy for an empty tab: what the state means and what to do next. */
function emptyCopy(tab: QueueTab): { title: string; body: string } {
  switch (tab) {
    case "AWAITING_FORM":
      return { title: "staffOffline.queue.empty.AWAITING_FORM.title", body: "staffOffline.queue.empty.AWAITING_FORM.body" };
    case "SUBMITTED":
      return { title: "staffOffline.queue.empty.SUBMITTED.title", body: "staffOffline.queue.empty.SUBMITTED.body" };
    case "CHANGES_REQUESTED":
      return { title: "staffOffline.queue.empty.CHANGES_REQUESTED.title", body: "staffOffline.queue.empty.CHANGES_REQUESTED.body" };
    case "READY":
      return { title: "staffOffline.queue.empty.READY.title", body: "staffOffline.queue.empty.READY.body" };
    case "ONBOARDED":
      return { title: "staffOffline.queue.empty.ONBOARDED.title", body: "staffOffline.queue.empty.ONBOARDED.body" };
    case "CLOSED":
      return { title: "staffOffline.queue.empty.CLOSED.title", body: "staffOffline.queue.empty.CLOSED.body" };
  }
}

/**
 * The admissions queue (plan 6.3): tabs with counts, batch, money and search filters, and one row per admission with its photo, state,
 * time in state, money pills and the one next action. The tab and filters live in the URL.
 */
export function AdmissionQueue() {
  const { t } = useLocale();
  const { formatRelative, pickTitle } = useFormat();
  const role = useRole();
  const [params, setParams] = useListParams({ tab: "", batch: "", source: "", flag: "", q: "", page: 0, size: 20 });
  const tab = parseQueueTab(params.tab, role.role);
  const isMaster = role.role === "MASTER_ADMIN";
  const reviewer = isMaster || role.role === "SYSTEM_ADMIN";
  // A new student made here is always a desk admission: the phone, then the code to read out.
  const [creating, setCreating] = useState(false);
  const [shown, setShown] = useState<ShownCode | null>(null);

  const counts = useOfflineCounts(reviewer);
  const batches = useBatches("");
  const queue = useAdmissionQueue({
    status: tab,
    batchId: params.batch || undefined,
    source: params.source || undefined,
    q: params.q || undefined,
    flag: parseMoneyFilter(params.flag),
    page: params.page,
    size: 20,
  });

  const tabs = QUEUE_TABS.map((value) => ({
    value,
    label: t(stateLabelKey(value)),
    badge: counts.data?.[TAB_COUNT[value]] ?? 0,
  }));

  const columns: Column<AdmissionQueueRow>[] = [
    {
      key: "name",
      header: t("staffOffline.queue.columns.name"),
      cell: (row) => {
        // Until the form arrives the name is the phone number.
        const name = row.status === "AWAITING_FORM" ? row.mobile : row.fullName || row.mobile;
        return (
          <div className="flex min-w-0 items-center gap-3">
            <AdmissionPhoto admissionId={row.id} hasPhoto={row.hasPhoto} name={name} />
            <div className="min-w-0">
              <Link href={`/staff/admissions/${row.id}`} className="font-medium text-text underline-offset-4 hover:underline">
                {name}
              </Link>
            </div>
          </div>
        );
      },
    },
    {
      key: "mobile",
      header: t("staffOffline.queue.columns.mobile"),
      mobileLabel: t("staffOffline.queue.columns.mobile"),
      cell: (row) => <span className="font-latin">{row.mobile}</span>,
    },
    {
      key: "batch",
      header: t("staffOffline.queue.columns.batch"),
      mobileLabel: t("staffOffline.queue.columns.batch"),
      cell: (row) => pickTitle({ titleEn: row.batchNameEn, titleBn: row.batchNameBn }) || "—",
    },
    {
      key: "source",
      header: t("staffOffline.queue.sourceLabel"),
      mobileLabel: t("staffOffline.queue.sourceLabel"),
      cell: (row) => t(`staffOffline.queue.sources.${row.source}`),
    },
    {
      key: "state",
      header: t("staffOffline.queue.columns.state"),
      mobileLabel: t("staffOffline.queue.columns.state"),
      cell: (row) => (
        <div className="grid gap-1">
          <span>
            <Pill tone={STATE_TONE[row.status]}>{t(stateLabelKey(row.status))}</Pill>
          </span>
          {row.status === "AWAITING_FORM" ? (
            <span className="text-meta text-muted">
              {t(row.activated ? "staffOffline.activation.activated" : "staffOffline.activation.notActivated")}
            </span>
          ) : null}
          <span className="text-meta text-muted">
            {t("staffOffline.queue.sinceAgo", { time: formatRelative(Date.now() - Date.parse(row.submittedAt ?? row.createdAt)) })}
          </span>
        </div>
      ),
    },
    {
      key: "money",
      header: t("staffOffline.queue.columns.money"),
      mobileLabel: t("staffOffline.queue.columns.money"),
      cell: (row) => <QueueMoneyPills row={row} />,
    },
    {
      key: "action",
      header: "",
      align: "end",
      cell: (row) => <RowAction row={row} role={role.role} onShowCode={(next) => setShown({ admissionId: next.id, phone: next.mobile })} />,
    },
  ];

  const empty = emptyCopy(tab);
  const batchName = (id: string) => {
    const batch = batches.data?.find((candidate) => candidate.id === id);
    return batch ? pickTitle({ titleEn: batch.nameEn, titleBn: batch.nameBn }) : "";
  };

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffOffline.queue.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffOffline.queue.title") }]}
        actions={<Button onClick={() => setCreating(true)}>{t("staffOffline.queue.addStudent")}</Button>}
      />
      {reviewer ? <SummaryCards counts={counts.data} isMaster={isMaster} /> : null}
      <Tabs
        tabs={tabs}
        value={tab}
        onChange={(value) => setParams({ tab: value, page: 0 })}
        ariaLabel={t("staffOffline.queue.tabsLabel")}
        wrap
      />
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(12rem,auto)_minmax(10rem,auto)_minmax(12rem,auto)]">
        <SearchInput
          className="self-end"
          id="admissions-q"
          value={params.q}
          onChange={(q) => setParams({ q })}
          placeholder={t("staffOffline.queue.searchPlaceholder")}
        />
        <Field label={t("staffOffline.queue.batch")}>
          <Select value={params.batch} onChange={(event) => setParams({ batch: event.target.value })}>
            <option value="">{t("staffOffline.queue.allBatches")}</option>
            {(batches.data ?? []).map((batch) => (
              <option key={batch.id} value={batch.id}>
                {batchName(batch.id)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffOffline.queue.sourceLabel")}>
          <Select value={params.source} onChange={(event) => setParams({ source: event.target.value, page: 0 })}>
            <option value="">{t("staffOffline.queue.sourceAll")}</option>
            {SOURCES.map((source) => (
              <option key={source} value={source}>
                {t(`staffOffline.queue.sources.${source}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("staffOffline.queue.money")}>
          <Select value={parseMoneyFilter(params.flag) ?? ""} onChange={(event) => setParams({ flag: event.target.value, page: 0 })}>
            <option value="">{t("staffOffline.queue.flag.all")}</option>
            {QUEUE_MONEY_FILTERS.map((flag) => (
              <option key={flag} value={flag}>
                {t(`staffOffline.queue.flag.${FLAG_KEY[flag]}`)}
              </option>
            ))}
          </Select>
        </Field>
      </div>

      {queue.isError ? (
        <ErrorState onRetry={() => queue.refetch()} error={queue.error} />
      ) : (
        <>
          <Table
            columns={columns}
            rows={queue.data?.content ?? []}
            rowKey={(row) => row.id}
            loading={queue.isPending}
            empty={
              <EmptyState
                title={t(empty.title)}
                body={t(empty.body)}
              />
            }
          />
          {queue.data ? (
            <Pagination
              page={queue.data.page}
              totalPages={queue.data.totalPages}
              onPage={(page) => setParams({ page })}
            />
          ) : null}
        </>
      )}
      <DeskStartDialog
        open={creating}
        onOpenChange={setCreating}
        onCreated={(desk) =>
          setShown({ admissionId: desk.admissionId, phone: desk.phone, initial: { code: desk.code, expiresAt: desk.expiresAt } })
        }
      />
      {shown ? (
        <DeskActivationDialog
          key={`${shown.admissionId}-${shown.initial?.code ?? "live"}`}
          admissionId={shown.admissionId}
          phone={shown.phone}
          initial={shown.initial}
          onClose={() => setShown(null)}
        />
      ) : null}
    </div>
  );
}

/** The sources of an application, in the order the filter lists them. */
const SOURCES: AdmissionSource[] = ["PUBLIC", "ACCOUNT", "DESK"];

/** The copy key of each money filter. */
const FLAG_KEY: Record<QueueMoneyFilter, "unpaid" | "overdue" | "pendingApproval"> = {
  unpaid: "unpaid",
  overdue: "overdue",
  "pending-approval": "pendingApproval",
};

/**
 * The dashboard cards of the offline admissions (plan 5): what waits for review, and for the Master Admin what is ready to onboard,
 * what payments wait for approval, and what fees are overdue. Each card opens the list it counts.
 */
function SummaryCards({ counts, isMaster }: { counts: OfflineCounts | undefined; isMaster: boolean }) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const cards = [
    { key: "review", label: t("staffOffline.dashboard.formsToReview"), value: counts?.submitted, href: "/staff/admissions?tab=SUBMITTED", master: false },
    { key: "ready", label: t("staffOffline.dashboard.readyToOnboard"), value: counts?.ready, href: "/staff/admissions?tab=READY", master: true },
    { key: "payments", label: t("staffOffline.dashboard.paymentsToApprove"), value: counts?.paymentsPending, href: "/staff/offline-fees?tab=pending", master: true },
    { key: "overdue", label: t("staffOffline.dashboard.overdueFees"), value: counts?.overdue, href: "/staff/offline-fees?tab=overdue", master: false },
  ].filter((card) => isMaster || !card.master);
  return (
    <ul className={`grid gap-3 ${isMaster ? "sm:grid-cols-2 xl:grid-cols-4" : "sm:grid-cols-2"}`}>
      {cards.map((card) => (
        <li key={card.key}>
          <Card href={card.href} className="h-full">
            <p className="text-meta font-bold text-muted">{card.label}</p>
            <p className="mt-1 text-section font-semibold tabular-nums">{card.value === undefined ? "—" : formatNumber(card.value)}</p>
          </Card>
        </li>
      ))}
    </ul>
  );
}

/** The one next action on a row for this role, or what the row waits for. Staff never see Onboard. */
function RowAction({
  row,
  role,
  onShowCode,
}: {
  row: AdmissionQueueRow;
  role: Role | null;
  onShowCode: (row: AdmissionQueueRow) => void;
}) {
  const { t } = useLocale();
  const wait = waitingFor(row.status, role);
  if (row.status === "AWAITING_FORM" && !row.activated) {
    return (
      <Button size="sm" variant="secondary" onClick={() => onShowCode(row)}>
        {t("staffOffline.activation.showCode")}
      </Button>
    );
  }
  if (row.status === "SUBMITTED") {
    return <Button href={`/staff/admissions/${row.id}`} size="sm">{t("staffOffline.actions.review")}</Button>;
  }
  if (row.status === "READY" && role === "MASTER_ADMIN") {
    return <Button href={`/staff/admissions/${row.id}`} size="sm">{t("staffOffline.actions.onboard")}</Button>;
  }
  if (row.status === "ONBOARDED") {
    return (
      <Button href={`/staff/admissions/${row.id}?tab=fees&pay=1`} size="sm" variant="secondary">
        {t("staffOffline.actions.recordPayment")}
      </Button>
    );
  }
  if (row.status === "CLOSED") {
    return <Button href={`/staff/admissions/${row.id}`} size="sm" variant="secondary">{t("staffOffline.actions.open")}</Button>;
  }
  if (wait === "student") {
    return <span className="text-meta text-muted">{t("staffOffline.waiting.student")}</span>;
  }
  if (wait === "masterAdmin") {
    return <span className="text-meta text-muted">{t("staffOffline.waiting.masterAdmin")}</span>;
  }
  return null;
}
