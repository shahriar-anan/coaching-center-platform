"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useCreateExam, useModuleExams, type StaffExam } from "@/lib/api/exam/staff-catalog";
import type { ExamType } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
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
          <legend className="mb-1 text-sm font-medium">{t("staffCatalog.moduleExams.typeLabel")}</legend>
          {(["MCQ", "WRITTEN"] as ExamType[]).map((type) => (
            <label key={type} className="flex min-h-11 items-center gap-3 rounded-control border border-border px-3">
              <input type="radio" name="examType" value={type} checked={examType === type} onChange={() => setExamType(type)} className="size-4" />
              <span>
                <span className="block font-medium">{t(`staffCatalog.examType.${type}`)}</span>
                <span className="block text-sm text-muted">{t(`staffCatalog.moduleExams.typeHint.${type}`)}</span>
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

/** Exams of one module. The type is chosen at create and does not change. */
export function ModuleExams({ moduleId }: { moduleId: string }) {
  const { t, locale } = useLocale();
  const { formatDateTime } = useFormat();
  const role = useRole();
  const exams = useModuleExams(moduleId);
  const [creating, setCreating] = useState(false);

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
      key: "language",
      header: t("staffCatalog.moduleExams.columns.language"),
      cell: (exam) => t(`staffCatalog.paperLanguage.${exam.paperLanguage}`),
    },
    {
      key: "date",
      header: t("staffCatalog.moduleExams.columns.date"),
      cell: (exam) => (exam.liveAt ? formatDateTime(exam.liveAt) : t("staffCatalog.dateNotSet")),
    },
    {
      key: "status",
      header: t("staffCatalog.moduleExams.columns.status"),
      cell: (exam) => <StatusPill status={exam.status} />,
    },
    {
      key: "links",
      header: t("staffCatalog.moduleExams.columns.links"),
      cell: (exam) => (
        <span className="flex flex-wrap gap-x-3 gap-y-1 text-sm">
          <Link href={`/staff/participation?moduleId=${moduleId}`} className="text-action hover:underline">
            {t("staffCatalog.moduleExams.participation")}
          </Link>
          <Link href={`/staff/exams/${exam.id}/leaderboard`} className="text-action hover:underline">
            {t("staffCatalog.moduleExams.leaderboard")}
          </Link>
        </span>
      ),
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
          rows={exams.data?.content ?? []}
          rowKey={(exam) => exam.id}
          loading={exams.isPending}
          empty={<EmptyState title={t("staffCatalog.moduleExams.emptyTitle")} body={t("staffCatalog.moduleExams.emptyBody")} />}
        />
      )}
      {role.canAuthor ? <NewExamDialog moduleId={moduleId} open={creating} onOpenChange={setCreating} /> : null}
    </div>
  );
}
