"use client";

import { useState } from "react";
import Link from "next/link";
import { keepPreviousData, useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { useRemoveFromRoster } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Dialog } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { Pill, type PillTone } from "@/components/ui/pill";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { staffHref } from "@/lib/config";

/**
 * One active grant, as the enrolment list returns it. The newest enrolment comes first. The plan fields are set only for a
 * Master Admin, and only when the grant is a direct enrolment that took a payment (plan section 6).
 */
export type EnrolledRow = {
  /** The direct-enrolment plan on this grant, when it has one. */
  paymentPlanId?: string | null;
  /** Taka paid and still due on the plan, and its status. Left out for anyone but the Master Admin. */
  paid?: number | null;
  due?: number | null;
  planStatus?: string | null;
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
  /** The student is on this package's roster. A row can have this and a grant at once (plan 14.5). */
  onRoster?: boolean | null;
  /** The student has sat an exam in this package, so their result stays if the roster row is removed. */
  startedAny?: boolean | null;
  /** The scope of the grant: MODULE for a package grant, EXAM for one exam. Null for a roster row with no grant. */
  grantScope?: "MODULE" | "EXAM" | null;
  /** The package a MODULE grant belongs to, for the link on an exam's list. Not in the contract yet: without it there is no link. */
  moduleId?: string | null;
};

const PAGE_SIZE = 20;

/**
 * Paid or Due for a direct enrolment, linked to its installment plan. Shown to the Master Admin only; the API leaves the figures
 * out for anyone else, and the caller checks the role as well (plan E67).
 */
function PlanPill({ planId, due, status }: { planId: string; due?: number | null; status?: string | null }) {
  const { t } = useLocale();
  const { formatMoney } = useFormat();
  const settled = status === "COMPLETED" || (due ?? 0) <= 0;
  const tone: PillTone = settled ? "ok" : status === "OVERDUE" ? "danger" : "warn";
  const label = settled ? t("staffAdmin.enrolments.plan.paid") : t("staffAdmin.enrolments.plan.due", { amount: formatMoney(due ?? 0) ?? "" });
  return (
    <Link href={staffHref(`/staff/installments/${planId}`)} title={t("staffAdmin.enrolments.plan.open")} className="self-center rounded-full outline-none focus-visible:ring-2 focus-visible:ring-accent">
      <Pill tone={tone}>{label}</Pill>
    </Link>
  );
}

/**
 * The students who hold access to one package (`MODULE`) or one exam (`EXAM`). A package list has a search and loads more
 * students on request. Every enrol call invalidates the `staffEnrol` keys, so a new student appears at the top.
 */
export function EnrolledList({ scope, targetId }: { scope: "MODULE" | "EXAM"; targetId: string }) {
  const { t } = useLocale();
  const { formatDate, formatNumber } = useFormat();
  const base = scope === "MODULE" ? `/api/v1/live-exam-modules/${targetId}/enrolments` : `/api/v1/live-exams/${targetId}/enrolments`;
  const { isMasterAdmin } = useRole();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  // Only a package list is searched. The search box debounces what is typed, so the query waits for a pause.
  const [search, setSearch] = useState("");
  const q = scope === "MODULE" ? search.trim() : "";
  const [revoking, setRevoking] = useState<EnrolledRow | null>(null);
  const [reason, setReason] = useState("");
  const [failure, setFailure] = useState<string | null>(null);
  // A roster student is removed from the package's roster, with a confirmation. Only a package has a roster.
  const [removingRoster, setRemovingRoster] = useState<EnrolledRow | null>(null);
  const removeRoster = useRemoveFromRoster(targetId);
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
    queryKey: ["staffEnrol", "enrolled", scope, targetId, q],
    queryFn: ({ pageParam, signal }) =>
      apiRequest<PageResponse<EnrolledRow>>(
        `${base}?page=${pageParam}&size=${PAGE_SIZE}${q ? `&q=${encodeURIComponent(q)}` : ""}`,
        { signal },
      ),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    // A new search keeps the last list on screen until the new one arrives, so the search box keeps its focus.
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });

  const rows = list.data?.pages.flatMap((page) => page.content) ?? [];
  const total = list.data?.pages[0]?.totalElements ?? 0;

  return (
    <div className="grid gap-3">
      {scope === "MODULE" ? (
        <div className="w-full sm:w-72">
          <SearchInput value={search} onChange={setSearch} placeholder={t("staffCatalog.roster.listSearch")} />
        </div>
      ) : null}

      {list.isPending ? (
        <Skeleton variant="card" count={2} />
      ) : (
        <>
          <p className="text-meta tabular-nums font-bold text-muted">{t("staffAdmin.enrolments.total", { count: formatNumber(total) })}</p>
          {rows.length === 0 ? (
            <p className="rounded-control border border-dashed border-border p-4 text-meta text-muted">
              {q ? t("common.search.noResults") : t("staffAdmin.enrolments.empty")}
            </p>
          ) : (
            <ul className="grid gap-2">
              {rows.map((row) => {
                // A package's roster row can be removed, even when the student also holds a grant on the package.
                const rosterRow = scope === "MODULE" && (row.onRoster === true || row.source === "ROSTER");
                // A package grant seen from an exam is not revoked here: that would close the whole package.
                const throughPackage = scope === "EXAM" && row.grantScope === "MODULE";
                const canRevoke = isMasterAdmin && row.source === "MANUAL" && row.grantId !== null && !throughPackage;
                return (
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
                    {isMasterAdmin && row.paymentPlanId ? (
                      <PlanPill planId={row.paymentPlanId} due={row.due} status={row.planStatus} />
                    ) : null}
                    <span className="grid gap-0.5 text-end text-meta text-muted">
                      <span className="font-bold text-text">
                        {throughPackage ? t("staffAdmin.enrolments.throughPackage") : t(`staffAdmin.enrolments.source.${row.source}`)}
                      </span>
                      <span className="tabular-nums">{t("staffAdmin.enrolments.addedOn", { date: formatDate(row.enrolledAt) })}</span>
                    </span>
                    {isMasterAdmin && rosterRow ? (
                      <Button variant="danger" onClick={() => setRemovingRoster(row)}>
                        {t("staffCatalog.roster.remove")}
                      </Button>
                    ) : null}
                    {canRevoke ? (
                      <Button variant="secondary" onClick={() => { setFailure(null); setReason(""); setRevoking(row); }}>
                        {t("staffAdmin.enrolments.revoke")}
                      </Button>
                    ) : null}
                    {scope === "EXAM" && row.moduleId ? (
                      <Button variant="secondary" href={staffHref(`/staff/modules/${row.moduleId}?tab=roster`)}>
                        {t("staffAdmin.enrolments.openPackage")}
                      </Button>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          )}
          {list.hasNextPage ? (
            <Button variant="secondary" className="justify-self-start" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
              {t("staffAdmin.enrolments.more")}
            </Button>
          ) : null}
        </>
      )}

      <ConfirmDialog
        open={removingRoster !== null}
        onOpenChange={(open) => !open && setRemovingRoster(null)}
        title={t("staffCatalog.roster.removeTitle", { name: removingRoster?.fullName ?? "" })}
        body={removingRoster?.startedAny === true ? t("staffCatalog.roster.removeStartedBody") : t("staffCatalog.roster.removeBody")}
        confirmLabel={t("staffCatalog.roster.removeConfirm")}
        tone="danger"
        onConfirm={async () => {
          if (!removingRoster) {
            return;
          }
          try {
            await removeRoster.mutateAsync(removingRoster.studentId);
            toast({ title: t("staffCatalog.roster.removed"), tone: "ok" });
          } catch (caught) {
            throw new Error(t(catalogErrorKey(caught)));
          }
        }}
      />

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
