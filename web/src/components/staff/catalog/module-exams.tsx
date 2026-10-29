"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useQueryClient } from "@tanstack/react-query";
import { apiRequest } from "@/lib/api/client";
import {
  staffCatalogKeys,
  useAddExamToPackage,
  useCopyExam,
  useExamSearch,
  useModule,
  useModuleExams,
  useRemoveExamFromPackage,
  useReorderPackageExams,
  type StaffExam,
} from "@/lib/api/exam/staff-catalog";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { RowMenu, type RowMenuItem } from "@/components/ui/row-menu";
import { SearchInput } from "@/components/ui/search-input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey, isApiError } from "@/components/staff/catalog/errors";
import { NewExamDialog } from "@/components/staff/catalog/new-exam-dialog";
import { PendingChangesBanner } from "@/components/staff/catalog/pending-changes-banner";
import { alsoInCount, hasPendingDot, movedOrder, priceOfRow } from "@/components/staff/catalog/package-rows";

/** Adds an exam that already exists to this package. Exams already in it are shown but cannot be added again. */
function AddExistingDialog({
  moduleId,
  memberIds,
  open,
  onOpenChange,
}: {
  moduleId: string;
  memberIds: Set<string>;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t, locale } = useLocale();
  const { toast } = useToast();
  const [q, setQ] = useState("");
  const search = useExamSearch(q);
  const add = useAddExamToPackage(moduleId);
  const [failure, setFailure] = useState<string | null>(null);

  async function addExam(exam: StaffExam) {
    setFailure(null);
    try {
      await add.mutateAsync(exam.id);
      toast({ title: t("staffCatalog.moduleExams.added"), tone: "ok" });
    } catch (e) {
      // A clash on the server says the exam is in the package already; the message is the same either way.
      setFailure(isApiError(e, "EXAM_ALREADY_IN_PACKAGE") ? t("staffCatalog.errors.EXAM_ALREADY_IN_PACKAGE") : t(catalogErrorKey(e)));
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={t("staffCatalog.moduleExams.addTitle")} description={t("staffCatalog.moduleExams.addBody")} className="sm:max-w-2xl">
      <div className="grid gap-3">
        <SearchInput value={q} onChange={setQ} placeholder={t("staffCatalog.moduleExams.addPlaceholder")} />
        {failure ? (
          <p role="alert" className="text-meta text-danger">
            {failure}
          </p>
        ) : null}
        {q.trim().length < 2 ? (
          <p className="text-meta text-muted">{t("staffCatalog.moduleExams.addHint")}</p>
        ) : search.isPending ? (
          <Skeleton variant="rows" count={3} />
        ) : search.isError ? (
          <ErrorState onRetry={() => search.refetch()} message={t("staffCatalog.moduleExams.loadError")} />
        ) : (search.data?.content ?? []).length === 0 ? (
          <EmptyState title={t("staffCatalog.moduleExams.addEmpty")} />
        ) : (
          <ul className="grid gap-2">
            {(search.data?.content ?? []).map((exam) => {
              const inside = memberIds.has(exam.id);
              return (
                <li key={exam.id} className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-border bg-card p-3">
                  <div className="min-w-0">
                    <p className="font-medium break-words">{locale === "bn" ? exam.titleBn : exam.titleEn}</p>
                    <p className="text-meta text-muted">{t(`staffCatalog.examType.${exam.examType}`)}</p>
                  </div>
                  {inside ? (
                    <span className="text-meta text-muted">{t("staffCatalog.moduleExams.alreadyIn")}</span>
                  ) : (
                    <Button variant="secondary" disabled={add.isPending} onClick={() => addExam(exam)}>
                      {t("staffCatalog.moduleExams.add")}
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </Dialog>
  );
}

/**
 * The exams of one package, in the package's order. Each row has an Open button, a title link, a pending dot, and
 * move up, move down and remove. On a published package these changes are staged with the rest of the package.
 */
export function ModuleExams({ moduleId }: { moduleId: string }) {
  const { t, locale } = useLocale();
  const { formatDateTime, formatMoney, formatNumber, formatTimeRange } = useFormat();
  const { toast } = useToast();
  const router = useRouter();
  const role = useRole();
  const pkg = useModule(moduleId);
  const exams = useModuleExams(moduleId);
  const copy = useCopyExam(moduleId);
  const remove = useRemoveExamFromPackage(moduleId);
  const reorder = useReorderPackageExams(moduleId);
  const queryClient = useQueryClient();
  // "Create another exam" from the editor's next-step card opens the dialog here (?new=1).
  const searchParams = useSearchParams();
  const [creating, setCreating] = useState(() => searchParams.get("new") === "1");
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<StaffExam | null>(null);
  const [archiving, setArchiving] = useState<StaffExam | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  // Remove the flag once it has opened the dialog, so a reload does not open it again.
  useEffect(() => {
    if (searchParams.get("new") !== "1") {
      return;
    }
    const params = new URLSearchParams(searchParams.toString());
    params.delete("new");
    const query = params.toString();
    window.history.replaceState(null, "", `${window.location.pathname}${query ? `?${query}` : ""}`);
  }, [searchParams]);

  const rows = exams.data?.content ?? [];
  const ids = rows.map((exam) => exam.id);
  const memberIds = new Set(ids);
  const published = pkg.data?.status === "PUBLISHED";

  async function archiveExam(exam: StaffExam) {
    try {
      await apiRequest<StaffExam>(`/api/v1/live-exams/${exam.id}/archive`, { method: "POST", body: {} });
      await queryClient.invalidateQueries({ queryKey: staffCatalogKeys.moduleExams(moduleId) });
      toast({ title: t("staffCatalog.editor.archived"), tone: "ok" });
      setArchiving(null);
    } catch (e) {
      toast({ title: t(catalogErrorKey(e)), tone: "danger" });
    }
  }

  function copyExam(exam: StaffExam) {
    copy.mutate(exam.id, {
      onSuccess: (copied) => {
        toast({ title: t("staffCatalog.moduleExams.copied"), tone: "ok" });
        router.push(`/staff/exams/${copied.id}`);
      },
      onError: (e) => toast({ title: t(catalogErrorKey(e)), tone: "danger" }),
    });
  }

  async function move(index: number, step: -1 | 1) {
    const next = movedOrder(ids, index, step);
    if (next === ids) {
      return;
    }
    setFailure(null);
    try {
      await reorder.mutateAsync(next);
    } catch (e) {
      setFailure(t(catalogErrorKey(e)));
    }
  }

  async function removeExam(exam: StaffExam) {
    setFailure(null);
    try {
      await remove.mutateAsync(exam.id);
      toast({ title: t("staffCatalog.moduleExams.removed"), tone: "ok" });
      setRemoving(null);
    } catch (e) {
      setFailure(t(catalogErrorKey(e)));
    }
  }

  const rowMenu = (exam: StaffExam) => {
    const items: RowMenuItem[] = [
      { label: t("staffCatalog.moduleExams.menu.edit"), href: `/staff/exams/${exam.id}` },
      { label: t("staffCatalog.moduleExams.participation"), href: `/staff/participation?moduleId=${moduleId}` },
      { label: t("staffCatalog.moduleExams.leaderboard"), href: `/staff/exams/${exam.id}/leaderboard` },
    ];
    if (role.canAuthor) {
      items.push({ label: t("staffCatalog.moduleExams.menu.copy"), onClick: () => copyExam(exam) });
    }
    if (role.canAuthor) {
      items.push({ label: t("staffCatalog.moduleExams.menu.remove"), onClick: () => setRemoving(exam) });
    }
    if (role.canDelete && exam.status !== "ARCHIVED") {
      items.push({ label: t("staffCatalog.moduleExams.menu.archive"), onClick: () => setArchiving(exam) });
    }
    // SLOT: results link to /staff/exams/[examId]/results (OPS route). Add the menu item once that route is merged.
    return (
      <div onClick={(e) => e.stopPropagation()}>
        <RowMenu items={items} label={t("staffCatalog.moduleExams.menu.label", { name: locale === "bn" ? exam.titleBn : exam.titleEn })} />
      </div>
    );
  };

  const columns: Column<StaffExam>[] = [
    {
      key: "name",
      header: t("staffCatalog.moduleExams.columns.name"),
      cell: (exam) => (
        <span className="flex items-center gap-2">
          <Link href={`/staff/exams/${exam.id}`} className="font-medium hover:underline">
            {locale === "bn" ? exam.titleBn : exam.titleEn}
          </Link>
          {hasPendingDot(exam) ? (
            <span role="img" aria-label={t("staffCatalog.moduleExams.pendingDot")} className="size-2 shrink-0 rounded-full bg-warn" />
          ) : null}
        </span>
      ),
    },
    {
      key: "type",
      header: t("staffCatalog.moduleExams.columns.type"),
      cell: (exam) => t(`staffCatalog.examType.${exam.examType}`),
    },
    {
      key: "mode",
      header: t("staffCatalog.moduleExams.columns.mode"),
      cell: (exam) => t(`staffCatalog.examMeta.mode.${exam.mode ?? "LIVE"}`),
    },
    {
      key: "window",
      header: t("staffCatalog.moduleExams.columns.window"),
      cell: (exam) => {
        if (exam.mode === "PRACTICE" || !exam.liveAt) {
          return <span className="text-muted">{t("staffCatalog.dateNotSet")}</span>;
        }
        return exam.endAt ? formatTimeRange(exam.liveAt, exam.endAt) : formatDateTime(exam.liveAt);
      },
    },
    {
      key: "duration",
      header: t("staffCatalog.moduleExams.columns.duration"),
      align: "end",
      cell: (exam) => <span className="tabular-nums">{t("staffCatalog.moduleExams.minutes", { n: formatNumber(exam.durationMinutes) })}</span>,
    },
    {
      key: "price",
      header: t("staffCatalog.moduleExams.columns.price"),
      align: "end",
      cell: (exam) => {
        const price = priceOfRow(exam);
        if (price.free) {
          return t("staffCatalog.moduleExams.free");
        }
        if (price.discounted) {
          return (
            <span className="tabular-nums">
              <s className="text-muted">{formatMoney(price.list)}</s> {formatMoney(price.current)}
            </span>
          );
        }
        return <span className="tabular-nums">{formatMoney(price.current) ?? t("staffCatalog.examMeta.noPrice")}</span>;
      },
    },
    {
      key: "status",
      header: t("staffCatalog.moduleExams.columns.status"),
      cell: (exam) => <StatusPill status={exam.status} />,
    },
    {
      key: "also",
      header: t("staffCatalog.moduleExams.columns.also"),
      cell: (exam) => {
        const count = alsoInCount(exam, moduleId);
        return count > 0 ? t("staffCatalog.moduleExams.alsoIn", { count: formatNumber(count) }) : <span className="text-muted">—</span>;
      },
    },
    {
      key: "actions",
      header: <span className="sr-only">{t("staffCatalog.moduleExams.columns.links")}</span>,
      align: "end",
      cell: (exam) => {
        const index = ids.indexOf(exam.id);
        return (
          <div className="flex items-center justify-end gap-1">
            <Button href={`/staff/exams/${exam.id}`} variant="secondary">
              {t("staffCatalog.moduleExams.open")}
            </Button>
            {role.canAuthor ? (
              <>
                <button
                  type="button"
                  aria-label={t("staffCatalog.moduleExams.moveUp", { name: locale === "bn" ? exam.titleBn : exam.titleEn })}
                  disabled={index <= 0 || reorder.isPending}
                  onClick={() => move(index, -1)}
                  className="min-h-11 min-w-11 rounded-control text-body hover:bg-field disabled:opacity-40"
                >
                  ↑
                </button>
                <button
                  type="button"
                  aria-label={t("staffCatalog.moduleExams.moveDown", { name: locale === "bn" ? exam.titleBn : exam.titleEn })}
                  disabled={index < 0 || index >= ids.length - 1 || reorder.isPending}
                  onClick={() => move(index, 1)}
                  className="min-h-11 min-w-11 rounded-control text-body hover:bg-field disabled:opacity-40"
                >
                  ↓
                </button>
              </>
            ) : null}
            {rowMenu(exam)}
          </div>
        );
      },
    },
  ];

  return (
    <div className="grid gap-4">
      {published && pkg.data?.pendingChanges ? <PendingChangesBanner masterAdmin={role.canPublishExam} /> : null}
      {published ? <p className="text-meta text-muted">{t("staffCatalog.moduleExams.stagedNote")}</p> : null}
      {role.canAuthor ? (
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" onClick={() => setAdding(true)}>
            {t("staffCatalog.moduleExams.addExisting")}
          </Button>
          <Button onClick={() => setCreating(true)}>{t("staffCatalog.moduleExams.new")}</Button>
        </div>
      ) : null}
      {failure ? (
        <p role="alert" className="text-meta text-danger">
          {failure}
        </p>
      ) : null}
      {exams.isError && !exams.data ? (
        <ErrorState onRetry={() => exams.refetch()} message={t("staffCatalog.moduleExams.loadError")} />
      ) : (
        <Table
          columns={columns}
          rows={rows}
          rowKey={(exam) => exam.id}
          loading={exams.isPending}
          empty={<EmptyState title={t("staffCatalog.moduleExams.emptyTitle")} body={t("staffCatalog.moduleExams.emptyBody")} />}
        />
      )}
      {role.canAuthor ? <NewExamDialog moduleId={moduleId} open={creating} onOpenChange={setCreating} /> : null}
      {role.canAuthor ? (
        <AddExistingDialog moduleId={moduleId} memberIds={memberIds} open={adding} onOpenChange={setAdding} />
      ) : null}
      {role.canAuthor && removing ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => {
            if (!open) setRemoving(null);
          }}
          title={t("staffCatalog.moduleExams.removeTitle", { name: locale === "bn" ? removing.titleBn : removing.titleEn })}
          body={t("staffCatalog.moduleExams.removeBody")}
          confirmLabel={t("staffCatalog.moduleExams.removeConfirm")}
          tone="danger"
          onConfirm={() => removeExam(removing)}
        />
      ) : null}
      {role.canDelete && archiving ? (
        <ConfirmDialog
          open
          onOpenChange={(open) => {
            if (!open) setArchiving(null);
          }}
          title={t("staffCatalog.editor.archiveTitle", { name: locale === "bn" ? archiving.titleBn : archiving.titleEn })}
          body={t("staffCatalog.editor.archiveBody")}
          confirmLabel={t("staffCatalog.editor.archiveConfirm")}
          tone="danger"
          onConfirm={() => archiveExam(archiving)}
        />
      ) : null}
    </div>
  );
}
