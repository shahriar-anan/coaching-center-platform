"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { Pill, type PillTone } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import {
  useApproveRetakeRequest,
  useDeclineRetakeRequest,
  useStaffRetakeRequests,
  type RetakeRequestStatus,
  type StaffRetakeRequest,
} from "@/lib/api/exam/retake-requests";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

const STATUSES: RetakeRequestStatus[] = ["PENDING", "APPROVED", "DECLINED"];

const TONE: Record<RetakeRequestStatus, PillTone> = { PENDING: "warn", APPROVED: "ok", DECLINED: "muted" };

/**
 * The centre's list of written retake requests. The Master Admin and the System Admin approve one (which grants the retake) or
 * decline it with a note the student reads on the exam's result page.
 */
export function RetakeRequestsPage() {
  const { t } = useLocale();
  const { formatDateTime, pickTitle } = useFormat();
  const [status, setStatus] = useState<RetakeRequestStatus>("PENDING");
  const list = useStaffRetakeRequests(status);
  const approve = useApproveRetakeRequest();
  const decline = useDeclineRetakeRequest();
  const { toast } = useToast();
  const [declining, setDeclining] = useState<StaffRetakeRequest | null>(null);
  const [note, setNote] = useState("");
  const [failure, setFailure] = useState<string | null>(null);

  async function onApprove(row: StaffRetakeRequest) {
    setFailure(null);
    try {
      await approve.mutateAsync(row.id);
      toast({ title: t("staffCatalog.retakeRequests.approved"), tone: "ok" });
    } catch (caught) {
      setFailure(t(catalogErrorKey(caught)));
    }
  }

  async function onDecline() {
    if (!declining) return;
    setFailure(null);
    try {
      await decline.mutateAsync({ id: declining.id, note });
      toast({ title: t("staffCatalog.retakeRequests.declined"), tone: "ok" });
      setDeclining(null);
      setNote("");
    } catch (caught) {
      setFailure(t(catalogErrorKey(caught)));
    }
  }

  const rows = list.data ?? [];

  return (
    <>
      <PageHeader
        title={t("staffCatalog.retakeRequests.title")}
        breadcrumbs={[{ label: t("staffCatalog.retakeRequests.home"), href: "/staff" }, { label: t("staffCatalog.retakeRequests.title") }]}
      />
      <p className="mb-4 text-body text-muted">{t("staffCatalog.retakeRequests.body")}</p>

      <Tabs
        ariaLabel={t("staffCatalog.retakeRequests.tabsLabel")}
        variant="pill"
        tabs={STATUSES.map((value) => ({ value, label: t(`staffCatalog.retakeRequests.status.${value}`) }))}
        value={status}
        onChange={(value) => setStatus(value as RetakeRequestStatus)}
      />

      <div className="mt-4 grid gap-3">
        {failure ? (
          <p role="alert" className="text-meta text-danger">
            {failure}
          </p>
        ) : null}

        {list.isPending ? (
          <Skeleton variant="card" count={3} />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => void list.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState title={t(`staffCatalog.retakeRequests.empty.${status}`)} />
        ) : (
          rows.map((row) => (
            <Card key={row.id} padding="sm" className="grid gap-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="grid min-w-0 gap-0.5">
                  <p className="text-body font-semibold text-text">{row.studentName || t("staffCatalog.retakeRequests.unnamed")}</p>
                  <p className="text-meta text-muted">
                    {pickTitle({ titleEn: row.examTitleEn, titleBn: row.examTitleBn })}
                  </p>
                </div>
                <Pill tone={TONE[row.status]}>{t(`staffCatalog.retakeRequests.status.${row.status}`)}</Pill>
              </div>
              <dl className="grid gap-1 text-meta text-muted sm:grid-cols-2">
                <div>
                  <dt className="inline">{t("staffCatalog.retakeRequests.requestedAt")}: </dt>
                  <dd className="inline text-text">{formatDateTime(row.requestedAt)}</dd>
                </div>
                {row.decidedAt ? (
                  <div>
                    <dt className="inline">{t("staffCatalog.retakeRequests.decidedAt")}: </dt>
                    <dd className="inline text-text">{formatDateTime(row.decidedAt)}</dd>
                  </div>
                ) : null}
              </dl>
              {row.reason ? (
                <p className="whitespace-pre-line rounded-control bg-bg px-3 py-2 text-meta text-text">
                  <span className="font-semibold text-muted">{t("staffCatalog.retakeRequests.reason")}: </span>
                  {row.reason}
                </p>
              ) : null}
              {row.decisionNote ? (
                <p className="whitespace-pre-line rounded-control bg-bg px-3 py-2 text-meta text-text">
                  <span className="font-semibold text-muted">{t("staffCatalog.retakeRequests.note")}: </span>
                  {row.decisionNote}
                </p>
              ) : null}
              {row.status === "PENDING" ? (
                <div className="flex flex-wrap gap-2">
                  <Button loading={approve.isPending} disabled={approve.isPending || decline.isPending} onClick={() => void onApprove(row)}>
                    {t("staffCatalog.retakeRequests.approve")}
                  </Button>
                  <Button variant="secondary" disabled={approve.isPending || decline.isPending} onClick={() => setDeclining(row)}>
                    {t("staffCatalog.retakeRequests.decline")}
                  </Button>
                </div>
              ) : null}
            </Card>
          ))
        )}
      </div>

      <Dialog
        open={declining !== null}
        onOpenChange={(open) => {
          if (!open) setDeclining(null);
        }}
        title={t("staffCatalog.retakeRequests.declineTitle")}
        description={t("staffCatalog.retakeRequests.declineBody")}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDeclining(null)} disabled={decline.isPending}>
              {t("common.actions.cancel")}
            </Button>
            <Button loading={decline.isPending} disabled={decline.isPending} onClick={() => void onDecline()}>
              {t("staffCatalog.retakeRequests.declineConfirm")}
            </Button>
          </>
        }
      >
        <Field label={t("staffCatalog.retakeRequests.noteLabel")} hint={t("staffCatalog.retakeRequests.noteHint")}>
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} rows={4} maxLength={500} />
        </Field>
      </Dialog>
    </>
  );
}

