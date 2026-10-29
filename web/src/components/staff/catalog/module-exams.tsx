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
import { staffCatalogKeys, useCopyExam, useModuleExams, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { RowMenu, type RowMenuItem } from "@/components/ui/row-menu";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { NewExamDialog } from "@/components/staff/catalog/new-exam-dialog";

/** Exams sorted by date, undated last. An exam is ready when it has content, a price and a date (U6). */
function byDate(a: StaffExam, b: StaffExam): number {
  if (a.liveAt === b.liveAt) {
    return 0;
  }
  if (a.liveAt === null) {
    return 1;
  }
  if (b.liveAt === null) {
    return -1;
  }
  return new Date(a.liveAt).getTime() - new Date(b.liveAt).getTime();
}

/** Exams of one module. The type is chosen at create and does not change. */
export function ModuleExams({ moduleId }: { moduleId: string }) {
  const { t, locale } = useLocale();
  const { formatDateTime, formatMoney, formatNumber } = useFormat();
  const { toast } = useToast();
  const router = useRouter();
  const role = useRole();
  const exams = useModuleExams(moduleId);
  const copy = useCopyExam(moduleId);
  const queryClient = useQueryClient();
  // "Create another exam" from the editor's next-step card opens the dialog here (?new=1).
  const searchParams = useSearchParams();
  const [creating, setCreating] = useState(() => searchParams.get("new") === "1");

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
  const [archiving, setArchiving] = useState<StaffExam | null>(null);

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

  const rowMenu = (exam: StaffExam) => {
    const items: RowMenuItem[] = [
      { label: t("staffCatalog.moduleExams.menu.edit"), href: `/staff/exams/${exam.id}` },
      { label: t("staffCatalog.moduleExams.participation"), href: `/staff/participation?moduleId=${moduleId}` },
      { label: t("staffCatalog.moduleExams.leaderboard"), href: `/staff/exams/${exam.id}/leaderboard` },
    ];
    if (role.canAuthor) {
      items.push({ label: t("staffCatalog.moduleExams.menu.copy"), onClick: () => copyExam(exam) });
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
        <Link href={`/staff/exams/${exam.id}`} className="font-medium hover:underline">
          {locale === "bn" ? exam.titleBn : exam.titleEn}
        </Link>
      ),
    },
    {
      key: "type",
      header: t("staffCatalog.moduleExams.columns.type"),
      cell: (exam) => t(`staffCatalog.examType.${exam.examType}`),
    },
    {
      key: "date",
      header: t("staffCatalog.moduleExams.columns.date"),
      cell: (exam) => (exam.liveAt ? formatDateTime(exam.liveAt) : t("staffCatalog.dateNotSet")),
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
      cell: (exam) => <span className="tabular-nums">{formatMoney(exam.price) ?? t("staffCatalog.examMeta.noPrice")}</span>,
    },
    {
      key: "content",
      header: t("staffCatalog.moduleExams.columns.content"),
      cell: (exam) => {
        if (exam.examType === "MCQ") {
          const count = exam.questions?.length ?? 0;
          return count > 0 ? (
            t("staffCatalog.moduleExams.questionCount", { n: formatNumber(count) })
          ) : (
            <span className="text-warn">{t("staffCatalog.moduleExams.noQuestions")}</span>
          );
        }
        return exam.questionPdf ? t("staffCatalog.moduleExams.pdfPresent") : <span className="text-warn">{t("staffCatalog.moduleExams.noPaper")}</span>;
      },
    },
    {
      key: "status",
      header: t("staffCatalog.moduleExams.columns.status"),
      cell: (exam) => <StatusPill status={exam.status} />,
    },
    {
      key: "actions",
      header: <span className="sr-only">{t("staffCatalog.moduleExams.columns.links")}</span>,
      align: "end",
      cell: rowMenu,
    },
  ];

  return (
    <div className="grid gap-4">
      {role.canAuthor ? (
        <div className="flex justify-end">
          <Button onClick={() => setCreating(true)}>{t("staffCatalog.moduleExams.new")}</Button>
        </div>
      ) : null}
      {exams.isError && !exams.data ? (
        <ErrorState onRetry={() => exams.refetch()} message={t("staffCatalog.moduleExams.loadError")} />
      ) : (
        <Table
          columns={columns}
          rows={[...(exams.data?.content ?? [])].sort(byDate)}
          rowKey={(exam) => exam.id}
          loading={exams.isPending}
          empty={<EmptyState title={t("staffCatalog.moduleExams.emptyTitle")} body={t("staffCatalog.moduleExams.emptyBody")} />}
        />
      )}
      {role.canAuthor ? <NewExamDialog moduleId={moduleId} open={creating} onOpenChange={setCreating} /> : null}
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
