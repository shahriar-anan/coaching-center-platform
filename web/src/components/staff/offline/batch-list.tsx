"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { Table, type Column } from "@/components/ui/table";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useFormat } from "@/lib/format/use-format";
import { useBatches, type BatchView } from "@/lib/api/exam/offline-admin";
import { useListParams } from "@/lib/url/list-params";
import { BatchFormDialog } from "./batch-form-dialog";
import { useState } from "react";

/** The batches (plan 6.3): name, end date, default fee, students, and whether a routine is uploaded. Only the Master Admin creates one. */
export function BatchList() {
  const { t } = useLocale();
  const role = useRole();
  const { formatDate, formatMoney, pickTitle } = useFormat();
  const [params, setParams] = useListParams({ status: "ACTIVE" });
  const batches = useBatches(params.status);
  const [creating, setCreating] = useState(false);

  const columns: Column<BatchView>[] = [
    {
      key: "name",
      header: t("staffOffline.batches.columns.name"),
      cell: (batch) => (
        <Link href={`/staff/offline-batches/${batch.id}`} className="font-medium text-text underline-offset-4 hover:underline">
          {pickTitle({ titleEn: batch.nameEn, titleBn: batch.nameBn })}
        </Link>
      ),
    },
    {
      key: "endsOn",
      header: t("staffOffline.batches.columns.endsOn"),
      mobileLabel: t("staffOffline.batches.columns.endsOn"),
      cell: (batch) => formatDate(batch.endsOn),
    },
    {
      key: "fee",
      header: t("staffOffline.batches.columns.fee"),
      mobileLabel: t("staffOffline.batches.columns.fee"),
      cell: (batch) => (
        <span className="font-latin">
          {formatMoney(Number(batch.courseFee))} · {t("staffOffline.batches.installmentsCount", { count: batch.defaultInstallments })}
        </span>
      ),
    },
    {
      key: "students",
      header: t("staffOffline.batches.columns.students"),
      mobileLabel: t("staffOffline.batches.columns.students"),
      cell: (batch) => <span className="font-latin">{batch.studentCount}</span>,
    },
    {
      key: "routine",
      header: t("staffOffline.batches.columns.routine"),
      mobileLabel: t("staffOffline.batches.columns.routine"),
      cell: (batch) =>
        batch.routineUploaded ? (
          <Pill tone="ok">{t("staffOffline.batches.routineUploaded")}</Pill>
        ) : (
          <Pill tone="warn">{t("staffOffline.batches.noRoutine")}</Pill>
        ),
    },
    {
      key: "status",
      header: t("staffOffline.batches.columns.status"),
      mobileLabel: t("staffOffline.batches.columns.status"),
      cell: (batch) =>
        batch.status === "ARCHIVED" ? (
          <Pill tone="muted">{t("staffOffline.batches.archived")}</Pill>
        ) : batch.published ? (
          <Pill tone="ok">{t("staffOffline.batches.publish.publishedLabel")}</Pill>
        ) : (
          <Pill tone="warn">{t("staffOffline.batches.publish.draftLabel")}</Pill>
        ),
    },
  ];

  return (
    <div className="grid gap-5">
      <PageHeader
        title={t("staffOffline.batches.title")}
        breadcrumbs={[{ label: t("common.staffNav.dashboard"), href: "/staff" }, { label: t("staffOffline.batches.title") }]}
        actions={role.canManageOfflineBatches ? <Button onClick={() => setCreating(true)}>{t("staffOffline.batches.new")}</Button> : undefined}
      />
      <div className="grid gap-3 sm:max-w-xs">
        <Field label={t("staffOffline.batches.statusFilter")}>
          <Select value={params.status} onChange={(event) => setParams({ status: event.target.value })}>
            <option value="ACTIVE">{t("staffOffline.batches.active")}</option>
            <option value="ARCHIVED">{t("staffOffline.batches.archived")}</option>
            <option value="">{t("staffOffline.batches.all")}</option>
          </Select>
        </Field>
      </div>
      {batches.isError ? (
        <ErrorState onRetry={() => batches.refetch()} error={batches.error} />
      ) : (
        <Table
          columns={columns}
          rows={batches.data ?? []}
          rowKey={(batch) => batch.id}
          loading={batches.isPending}
          empty={<EmptyState title={t("staffOffline.batches.empty")} body={t("staffOffline.batches.emptyBody")} />}
        />
      )}
      {role.canManageOfflineBatches ? <BatchFormDialog batch={null} open={creating} onOpenChange={setCreating} /> : null}
    </div>
  );
}
