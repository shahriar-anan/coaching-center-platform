"use client";

import { useState } from "react";
import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

/** One active grant, as the enrolment list returns it. The newest enrolment comes first. */
export type EnrolledRow = {
  /** Null when access comes from a roster row, which has no grant to revoke. */
  grantId: string | null;
  studentId: string;
  fullName: string;
  studentCode: string | null;
  phone: string | null;
  email: string | null;
  source: "ORDER" | "MANUAL" | "BUNDLE" | "ROSTER";
  reason: string | null;
  enrolledAt: string;
};

const PAGE_SIZE = 20;

/**
 * The students who hold access to one package (`MODULE`) or one exam (`EXAM`). The list is refetched after each direct
 * enrolment, because every enrol call invalidates the `staffEnrol` keys, so a new student appears at the top.
 */
export function EnrolledList({ scope, targetId }: { scope: "MODULE" | "EXAM"; targetId: string }) {
  const { t } = useLocale();
  const { formatDate, formatNumber } = useFormat();
  const base = scope === "MODULE" ? `/api/v1/live-exam-modules/${targetId}/enrolments` : `/api/v1/live-exams/${targetId}/enrolments`;
  const { isMasterAdmin } = useRole();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [revoking, setRevoking] = useState<EnrolledRow | null>(null);
  const [reason, setReason] = useState("");
  const [failure, setFailure] = useState<string | null>(null);
  // Only a direct enrolment (a manual grant) can be taken back here; a purchase is not revoked from this list.
  const revoke = useMutation({
    mutationFn: ({ grantId, reason: text }: { grantId: string; reason: string }) =>
      apiRequest<unknown>(`/api/v1/live-exam-grants/${grantId}?reason=${encodeURIComponent(text.trim())}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["staffEnrol"] }),
  });

  async function confirmRevoke() {
    if (!revoking?.grantId || !reason.trim()) return;
    setFailure(null);
    try {
      await revoke.mutateAsync({ grantId: revoking.grantId, reason });
      toast({ title: t("staffAdmin.enrolments.revoked"), tone: "ok" });
      setRevoking(null);
      setReason("");
    } catch (caught) {
      setFailure(t(catalogErrorKey(caught)));
    }
  }

  const list = useInfiniteQuery({
    queryKey: ["staffEnrol", "enrolled", scope, targetId],
    queryFn: ({ pageParam, signal }) =>
      apiRequest<PageResponse<EnrolledRow>>(`${base}?page=${pageParam}&size=${PAGE_SIZE}`, { signal }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    staleTime: 30_000,
  });

  if (list.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  const rows = list.data?.pages.flatMap((page) => page.content) ?? [];
  const total = list.data?.pages[0]?.totalElements ?? 0;

  return (
    <div className="grid gap-3">
      <p className="text-meta tabular-nums font-bold text-muted">{t("staffAdmin.enrolments.total", { count: formatNumber(total) })}</p>
      {rows.length === 0 ? (
        <p className="rounded-control border border-dashed border-border p-4 text-meta text-muted">{t("staffAdmin.enrolments.empty")}</p>
      ) : (
        <ul className="grid gap-2">
          {rows.map((row) => (
            <li
              key={row.grantId ?? row.studentId}
              className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-border-control bg-card px-4 py-3 shadow-sm"
            >
              <span className="grid min-w-0 gap-0.5">
                <span className="font-bold text-text">{row.fullName}</span>
                <span className="text-meta text-muted" dir="ltr">
                  {[row.studentCode, row.phone, row.email].filter(Boolean).join(" · ")}
                </span>
              </span>
              <span className="grid gap-0.5 text-end text-meta text-muted">
                <span className="font-bold text-text">{t(`staffAdmin.enrolments.source.${row.source}`)}</span>
                <span className="tabular-nums">{t("staffAdmin.enrolments.addedOn", { date: formatDate(row.enrolledAt) })}</span>
              </span>
              {isMasterAdmin && row.source === "MANUAL" && row.grantId ? (
                <Button variant="secondary" onClick={() => { setFailure(null); setReason(""); setRevoking(row); }}>
                  {t("staffAdmin.enrolments.revoke")}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {list.hasNextPage ? (
        <Button variant="secondary" className="justify-self-start" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
          {t("staffAdmin.enrolments.more")}
        </Button>
      ) : null}

      <Dialog
        open={revoking !== null}
        onOpenChange={(open) => {
          if (!open) setRevoking(null);
        }}
        title={t("staffAdmin.enrolments.revokeTitle")}
        description={t("staffAdmin.enrolments.revokeBody", { name: revoking?.fullName ?? "" })}
        footer={
          <>
            <Button variant="secondary" disabled={revoke.isPending} onClick={() => setRevoking(null)}>
              {t("common.actions.cancel")}
            </Button>
            <Button loading={revoke.isPending} disabled={revoke.isPending || !reason.trim()} onClick={() => void confirmRevoke()}>
              {t("staffAdmin.enrolments.revokeConfirm")}
            </Button>
          </>
        }
      >
        <div className="grid gap-2">
          <Field label={t("staffAdmin.enrolments.revokeReason")} required hint={t("staffAdmin.enrolments.revokeReasonHint")}>
            <Textarea value={reason} maxLength={500} rows={3} onChange={(event) => setReason(event.target.value)} />
          </Field>
          {failure ? (
            <p role="alert" className="text-meta text-danger">
              {failure}
            </p>
          ) : null}
        </div>
      </Dialog>
    </div>
  );
}
