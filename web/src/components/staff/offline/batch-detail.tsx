"use client";

import { useState } from "react";
import Link from "next/link";
import { CalendarDays, Info, Languages, Megaphone, Users, Wallet, type LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { Tabs } from "@/components/ui/tabs";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import {
  batchPhotoPath,
  useArchiveBatch,
  useBatch,
  useBatchStudents,
  usePublishBatch,
  useUnpublishBatch,
  type BatchStudent,
  type BatchView,
} from "@/lib/api/exam/offline-admin";
import { AdmissionMoneyPills } from "./money-pills";
import { BatchFormDialog } from "./batch-form-dialog";
import { BatchClasses } from "./batch-classes";
import { BatchOverview } from "./batch-overview";
import { BatchRoutines } from "./batch-routines";
import { BatchResults } from "./batch-results";
import { offlineErrorText } from "./offline-error";
import { stateLabelKey } from "./labels";

const TABS = ["overview", "classes", "routines", "results", "students"] as const;
type BatchTab = (typeof TABS)[number];

/**
 * One batch (plan 6.3, 8.1). Overview holds its details (the Master Admin edits them). Classes, routines, results and students sit on
 * their own tabs (instructor rates went on 24 Nov: pay no longer comes from the classes); the tab is kept in the URL (`?tab=`), so a refresh or Back lands on the same one.
 */
export function BatchDetail({ id }: { id: string }) {
  const { t } = useLocale();
  const role = useRole();
  const { formatDate, formatMoney, pickTitle } = useFormat();
  const { toast } = useToast();
  const [params, setParams] = useListParams({ tab: "overview" });
  const batch = useBatch(id);
  const students = useBatchStudents(id);
  const archive = useArchiveBatch(id);
  const publish = usePublishBatch(id);
  const unpublish = useUnpublishBatch(id);
  const [editing, setEditing] = useState(false);
  const [archiving, setArchiving] = useState(false);

  if (batch.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (batch.isError) {
    return <ErrorState onRetry={() => batch.refetch()} error={batch.error} />;
  }
  const b = batch.data;
  const name = pickTitle({ titleEn: b.nameEn, titleBn: b.nameBn });
  const archived = b.status === "ARCHIVED";
  const onboarded = b.studentCount;
  const tab: BatchTab = (TABS as readonly string[]).includes(params.tab) ? (params.tab as BatchTab) : "overview";

  const tabs = [
    { value: "overview", label: t("batchPay.tabs.overview") },
    { value: "classes", label: t("batchPay.tabs.classes") },
    { value: "routines", label: t("batchPay.tabs.routines") },
    { value: "results", label: t("staffOfflineResults.tab") },
    { value: "students", label: t("batchPay.tabs.students") },
  ];

  // Onboarded students without a roll number: results cannot be matched to them (plan 7.4).
  const noRoll = (students.data ?? []).filter((row) => row.status === "ONBOARDED" && !row.roll);

  const columns: Column<BatchStudent>[] = [
    {
      key: "name",
      header: t("staffOffline.batches.students.name"),
      cell: (row) => (
        <Link href={`/staff/admissions/${row.admissionId}`} className="font-medium text-text underline-offset-4 hover:underline">
          {row.fullName}
        </Link>
      ),
    },
    {
      key: "roll",
      header: t("staffOffline.batches.students.roll"),
      mobileLabel: t("staffOffline.batches.students.roll"),
      cell: (row) => <span className="font-latin">{row.roll ?? "—"}</span>,
    },
    {
      key: "mobile",
      header: t("staffOffline.batches.students.mobile"),
      mobileLabel: t("staffOffline.batches.students.mobile"),
      cell: (row) => <span className="font-latin">{row.mobile}</span>,
    },
    {
      key: "state",
      header: t("staffOffline.batches.students.state"),
      mobileLabel: t("staffOffline.batches.students.state"),
      cell: (row) => <span>{t(stateLabelKey(row.status))}</span>,
    },
    {
      key: "money",
      header: t("staffOffline.batches.students.money"),
      mobileLabel: t("staffOffline.batches.students.money"),
      cell: (row) => <AdmissionMoneyPills admissionId={row.admissionId} enabled={row.status === "ONBOARDED" || row.status === "CLOSED"} />,
    },
  ];

  return (
    <div className="grid gap-6">
      <PageHeader
        title={name}
        breadcrumbs={[
          { label: t("common.staffNav.dashboard"), href: "/staff" },
          { label: t("staffOffline.batches.title"), href: "/staff/offline-batches" },
          { label: name },
        ]}
        actions={
          role.canManageOfflineBatches && !archived ? (
            // A draft is edited and then published; a published batch is moved back to draft to edit it (V103).
            b.published ? (
              <Button
                variant="secondary"
                loading={unpublish.isPending}
                onClick={() =>
                  unpublish.mutate(undefined, {
                    onSuccess: () => toast({ title: t("staffOffline.batches.publish.movedToDraft"), tone: "ok" }),
                    onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
                  })
                }
              >
                {t("staffOffline.batches.publish.toDraft")}
              </Button>
            ) : (
              <div className="flex flex-wrap gap-2">
                {tab === "overview" ? (
                  <Button variant="secondary" onClick={() => setEditing(true)}>
                    {t("staffOffline.batches.edit")}
                  </Button>
                ) : null}
                <Button
                  loading={publish.isPending}
                  onClick={() =>
                    publish.mutate(undefined, {
                      onSuccess: () => toast({ title: t("staffOffline.batches.publish.published"), tone: "ok" }),
                      onError: (error) => toast({ title: offlineErrorText(error, t), tone: "danger" }),
                    })
                  }
                >
                  {t("staffOffline.batches.publish.publish")}
                </Button>
              </div>
            )
          ) : undefined
        }
      />

      {!archived ? (
        <p
          role="status"
          className={
            b.published
              ? "rounded-control border border-ok/40 bg-ok/5 px-3 py-2 text-body text-text"
              : "rounded-control border border-warn/50 bg-warn/5 px-3 py-2 text-body text-text"
          }
        >
          <span className="font-semibold">{t(b.published ? "staffOffline.batches.publish.publishedLabel" : "staffOffline.batches.publish.draftLabel")}</span>{" "}
          {t(b.published ? "staffOffline.batches.publish.publishedNote" : "staffOffline.batches.publish.draftNote")}
        </p>
      ) : null}

      <Tabs tabs={tabs} value={tab} onChange={(value) => setParams({ tab: value })} ariaLabel={t("batchPay.tabs.label")} wrap />

      {tab === "overview" ? <BatchOverview batchId={id} minutes={b.defaultClassMinutes} onGo={(value) => setParams({ tab: value })} /> : null}

      {tab === "overview" ? (
        <section
          aria-labelledby="batch-details"
          className="grid gap-4 rounded-card border border-border bg-card p-4 shadow-[var(--card-shadow)] sm:p-5"
        >
          <div className="flex items-center gap-3">
            <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-accent/15 text-text-accent">
              <Info className="size-5" strokeWidth={1.75} />
            </span>
            <div className="grid min-w-0 gap-0.5">
              <h3 id="batch-details" className="text-card-title text-text">
                {t("staffOffline.batches.details")}
              </h3>
              {!role.canManageOfflineBatches ? <p className="text-meta text-muted">{t("staffOffline.batches.readOnly")}</p> : null}
            </div>
          </div>

          <BatchPresentation batch={b} />

          <dl className="grid gap-3 sm:grid-cols-2">
            <FieldCell icon={Languages} label={t("staffOffline.batches.nameEn")} value={b.nameEn} />
            <FieldCell icon={Languages} label={t("staffOffline.batches.nameBn")} value={b.nameBn} />
            <FieldCell icon={CalendarDays} label={t("staffOffline.batches.startsOn")} value={b.startsOn ? formatDate(b.startsOn) : "—"} />
            <FieldCell icon={CalendarDays} label={t("staffOffline.batches.endsOn")} value={formatDate(b.endsOn)} />
            <FieldCell icon={Wallet} label={t("staffOffline.batches.fee")} value={formatMoney(Number(b.courseFee)) ?? "—"} />
            <FieldCell icon={Wallet} label={t("staffOffline.batches.installments")} value={String(b.defaultInstallments)} />
            <FieldCell icon={Users} label={t("staffOffline.batches.studentsCount")} value={String(onboarded)} />
            <FieldCell
              icon={Megaphone}
              label={t("staffOffline.batches.catalogue.admissionsOpen")}
              value={t(b.admissionsOpen ? "staffOffline.batches.catalogue.admissionsOpenNow" : "staffOffline.batches.catalogue.admissionsClosedNow")}
            />
          </dl>

          {role.canManageOfflineBatches && !archived ? (
            <div className="grid gap-2 border-t border-border pt-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-meta text-muted">
                  {onboarded > 0 ? t("staffOffline.batches.archiveBlocked", { count: onboarded }) : t("staffOffline.batches.archiveHint")}
                </p>
                <Button variant="danger" disabled={onboarded > 0} onClick={() => setArchiving(true)}>
                  {t("staffOffline.batches.archive")}
                </Button>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      {tab === "classes" ? <BatchClasses batchId={id} archived={archived} classMinutes={b.defaultClassMinutes} /> : null}


      {tab === "routines" ? <BatchRoutines batchId={id} archived={archived} /> : null}

      {tab === "results" ? <BatchResults batchId={id} onGo={(value) => setParams({ tab: value })} /> : null}

      {tab === "students" ? (
        <section aria-labelledby="batch-students" className="grid gap-3">
          <h3 id="batch-students" className="text-card-title text-text">
            {t("staffOffline.batches.students.title")}
          </h3>
          {noRoll.length > 0 ? (
            <p className="flex flex-wrap items-center gap-x-2 text-meta text-warn">
              <span>
                {noRoll.length === 1
                  ? t("staffOffline.batches.students.noRollOne")
                  : t("staffOffline.batches.students.noRoll", { count: noRoll.length })}
              </span>
              <Link href={`/staff/admissions/${noRoll[0].admissionId}`} className="font-semibold underline underline-offset-4">
                {t("staffOffline.batches.students.noRollOpen", { name: noRoll[0].fullName })}
              </Link>
            </p>
          ) : null}
          {students.isError ? (
            <ErrorState onRetry={() => students.refetch()} error={students.error} />
          ) : (
            <Table
              columns={columns}
              rows={students.data ?? []}
              rowKey={(row) => row.admissionId}
              loading={students.isPending}
              empty={<EmptyState title={t("staffOffline.batches.students.empty")} body={t("staffOffline.batches.students.emptyBody")} />}
            />
          )}
        </section>
      ) : null}

      {role.canManageOfflineBatches ? (
        <>
          <BatchFormDialog batch={b} open={editing} onOpenChange={setEditing} />
          <ConfirmDialog
            open={archiving}
            onOpenChange={setArchiving}
            title={t("staffOffline.batches.archiveTitle", { name })}
            body={t("staffOffline.batches.archiveBody")}
            confirmLabel={t("staffOffline.batches.archiveConfirm")}
            tone="danger"
            onConfirm={async () => {
              try {
                await archive.mutateAsync();
                toast({ title: t("staffOffline.batches.archivedDone"), tone: "ok" });
              } catch (error) {
                throw new Error(offlineErrorText(error, t));
              }
            }}
          />
        </>
      ) : null}
    </div>
  );
}

/** One field of the details: a small icon, the label above the value, in a cell of its own. */
function FieldCell({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="flex min-w-0 items-start gap-3 rounded-control border border-border bg-bg px-3 py-2.5">
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted" strokeWidth={1.75} />
      <div className="grid min-w-0 gap-0.5">
        <dt className="text-meta text-muted">{label}</dt>
        <dd className="break-words font-semibold tabular-nums text-text">{value || "—"}</dd>
      </div>
    </div>
  );
}

/**
 * The batch's photo (16:9, read with the staff token) and its English and Bangla descriptions, the first part of the Overview. A batch
 * with neither shows the empty words, so the office can see what is missing.
 */
function BatchPresentation({ batch }: { batch: BatchView }) {
  const { t } = useLocale();
  const photo = useAuthorizedObjectUrl(batch.photoUploaded ? batchPhotoPath(batch) : null);
  const paragraphs = (text: string | null) => (text ? text.split(/\n{2,}/).map((part) => part.trim()).filter(Boolean) : []);
  const en = paragraphs(batch.descriptionEn);
  const bn = paragraphs(batch.descriptionBn);
  return (
    <div className="grid gap-4 sm:grid-cols-[minmax(0,16rem)_minmax(0,1fr)] sm:items-start">
      <div className="flex aspect-video w-full items-center justify-center overflow-hidden rounded-control border border-border-control bg-bg">
        {photo ? (
          // The photo is an object URL made from the authorised download, so it skips the image optimiser.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={photo} alt={t("staffOffline.batches.catalogue.photo")} className="size-full object-cover" />
        ) : (
          <span className="px-3 text-center text-meta text-muted">{t("staffOffline.batches.catalogue.photoMissing")}</span>
        )}
      </div>
      <div className="grid min-w-0 gap-2">
        <h4 className="text-label font-medium text-text">{t("staffOffline.batches.catalogue.descriptionTitle")}</h4>
        {en.length === 0 && bn.length === 0 ? (
          <p className="text-meta text-muted">{t("staffOffline.batches.catalogue.descriptionNone")}</p>
        ) : (
          <>
            {en.map((part, index) => (
              <p key={`en-${index}`} lang="en" className="whitespace-pre-line break-words text-body text-text">
                {part}
              </p>
            ))}
            {bn.map((part, index) => (
              <p key={`bn-${index}`} lang="bn" className="whitespace-pre-line break-words text-body text-text">
                {part}
              </p>
            ))}
          </>
        )}
      </div>
    </div>
  );
}
