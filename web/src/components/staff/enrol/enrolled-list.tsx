"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

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
            </li>
          ))}
        </ul>
      )}
      {list.hasNextPage ? (
        <Button variant="secondary" className="justify-self-start" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
          {t("staffAdmin.enrolments.more")}
        </Button>
      ) : null}
    </div>
  );
}
