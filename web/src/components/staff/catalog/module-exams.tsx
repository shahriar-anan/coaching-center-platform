"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useCopyExam, useCreateExam, useModuleExams, type StaffExam } from "@/lib/api/exam/staff-catalog";
import type { ExamType } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { RowMenu, type RowMenuItem } from "@/components/ui/row-menu";
import { TextInput } from "@/components/ui/text-input";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

function NewExamDialog({ moduleId, open, onOpenChange }: { moduleId: string; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const router = useRouter();
  const { toast } = useToast();
  const create = useCreateExam(moduleId);
  const [examType, setExamType] = useState<ExamType>("MCQ");
  const [titleEn, setTitleEn] = useState("");
  const [titleBn, setTitleBn] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!titleEn.trim() || !titleBn.trim()) {
      setError(t("staffCatalog.moduleExams.titlesRequired"));
      return;
    }
    setError(null);
    try {
      const exam = await create.mutateAsync({ examType, titleEn: titleEn.trim(), titleBn: titleBn.trim() });
      toast({ title: t("staffCatalog.moduleExams.created"), tone: "ok" });
      router.push(`/staff/exams/${exam.id}`);
    } catch (e) {
      setError(t(catalogErrorKey(e)));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffCatalog.moduleExams.newTitle")}
      description={t("staffCatalog.moduleExams.newBody")}
    >
      <form onSubmit={submit} noValidate className="grid gap-4">
        <fieldset className="grid gap-2">
          <legend className="mb-1 text-label font-medium">{t("staffCatalog.moduleExams.typeLabel")}</legend>
          {(["MCQ", "WRITTEN"] as ExamType[]).map((type) => (
            <label key={type} className="flex min-h-11 items-center gap-3 rounded-control border border-border px-3">
              <input type="radio" name="examType" value={type} checked={examType === type} onChange={() => setExamType(type)} className="size-4" />
              <span>
                <span className="block font-medium">{t(`staffCatalog.examType.${type}`)}</span>
                <span className="block text-meta text-muted">{t(`staffCatalog.moduleExams.typeHint.${type}`)}</span>
              </span>
            </label>
          ))}
        </fieldset>
        <Field label={t("staffCatalog.moduleForm.titleEn")} required>
          <TextInput value={titleEn} onChange={(e) => setTitleEn(e.target.value)} dir="ltr" />
        </Field>
        <Field label={t("staffCatalog.moduleForm.titleBn")} required error={error ?? undefined}>
          <TextInput value={titleBn} onChange={(e) => setTitleBn(e.target.value)} />
        </Field>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => onOpenChange(false)}>
            {t("common.actions.cancel")}
          </Button>
          <Button type="submit" loading={create.isPending} disabled={create.isPending}>
            {t("staffCatalog.moduleExams.create")}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

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
  const [creating, setCreating] = useState(false);

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
    </div>
  );
}
