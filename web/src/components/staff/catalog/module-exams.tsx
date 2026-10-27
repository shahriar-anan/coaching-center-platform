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
import { staffCatalogKeys, useCopyExam, useCreateExam, useModuleExams, type ExamInput, type StaffExam } from "@/lib/api/exam/staff-catalog";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import type { ExamType, PaperLanguage } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { NumberInput } from "@/components/ui/number-input";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { RowMenu, type RowMenuItem } from "@/components/ui/row-menu";
import { TextInput } from "@/components/ui/text-input";
import { Table, type Column } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { StatusPill } from "@/components/staff/catalog/status-pill";
import { catalogErrorKey, fieldErrorFor } from "@/components/staff/catalog/errors";

type NewExamForm = {
  titleEn: string;
  titleBn: string;
  descriptionEn: string;
  descriptionBn: string;
  rulesEn: string;
  rulesBn: string;
  paperLanguage: PaperLanguage | "";
  durationMinutes: string;
};

const blankNewExam: NewExamForm = {
  titleEn: "",
  titleBn: "",
  descriptionEn: "",
  descriptionBn: "",
  rulesEn: "",
  rulesBn: "",
  paperLanguage: "",
  durationMinutes: "",
};

function NewExamDialog({ moduleId, open, onOpenChange }: { moduleId: string; open: boolean; onOpenChange: (open: boolean) => void }) {
  const { t } = useLocale();
  const router = useRouter();
  const { toast } = useToast();
  const create = useCreateExam(moduleId);
  const [examType, setExamType] = useState<ExamType>("MCQ");
  const [form, setForm] = useState<NewExamForm>(blankNewExam);
  const [errors, setErrors] = useState<Partial<Record<keyof NewExamForm, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);

  function set<K extends keyof NewExamForm>(key: K, value: NewExamForm[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  /** The body to create, or null with each missing field named. Covers what the server requires on create. */
  function validate(): (ExamInput & { examType: ExamType }) | null {
    const next: Partial<Record<keyof NewExamForm, string>> = {};
    const required = t("staffCatalog.moduleExams.fieldRequired");
    if (!form.titleEn.trim()) {
      next.titleEn = t("staffCatalog.examMeta.titleEnRequired");
    }
    if (!form.titleBn.trim()) {
      next.titleBn = t("staffCatalog.examMeta.titleBnRequired");
    }
    if (!form.descriptionEn.trim()) {
      next.descriptionEn = required;
    }
    if (!form.descriptionBn.trim()) {
      next.descriptionBn = required;
    }
    if (!form.rulesEn.trim()) {
      next.rulesEn = required;
    }
    if (!form.rulesBn.trim()) {
      next.rulesBn = required;
    }
    if (!form.paperLanguage) {
      next.paperLanguage = t("staffCatalog.moduleExams.paperLanguageRequired");
    }
    const duration = Number(form.durationMinutes);
    if (!Number.isInteger(duration) || duration < 2) {
      next.durationMinutes = t("staffCatalog.examMeta.durationInvalid");
    }
    setErrors(next);
    if (Object.keys(next).length > 0 || !form.paperLanguage) {
      return null;
    }
    return {
      examType,
      titleEn: form.titleEn.trim(),
      titleBn: form.titleBn.trim(),
      descriptionEn: form.descriptionEn.trim(),
      descriptionBn: form.descriptionBn.trim(),
      rulesEn: form.rulesEn.trim(),
      rulesBn: form.rulesBn.trim(),
      paperLanguage: form.paperLanguage,
      durationMinutes: duration,
    };
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setFormError(null);
    const body = validate();
    if (!body) {
      return;
    }
    try {
      const exam = await create.mutateAsync(body);
      toast({ title: t("staffCatalog.moduleExams.created"), tone: "ok" });
      router.push(`/staff/exams/${exam.id}?created=1`);
    } catch (e) {
      const fromServer: Partial<Record<keyof NewExamForm, string>> = {};
      for (const key of Object.keys(blankNewExam) as (keyof NewExamForm)[]) {
        const message = fieldErrorFor(e, key);
        if (message) {
          fromServer[key] = message;
        }
      }
      setErrors(fromServer);
      setFormError(t(catalogErrorKey(e)));
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("staffCatalog.moduleExams.newTitle")}
      description={t("staffCatalog.moduleExams.newBody")}
      className="sm:max-w-2xl"
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
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffCatalog.moduleForm.titleEn")} required error={errors.titleEn}>
            <TextInput value={form.titleEn} onChange={(e) => set("titleEn", e.target.value)} dir="ltr" />
          </Field>
          <Field label={t("staffCatalog.moduleForm.titleBn")} required error={errors.titleBn}>
            <TextInput value={form.titleBn} onChange={(e) => set("titleBn", e.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffCatalog.examMeta.descriptionEn")} required error={errors.descriptionEn}>
            <Textarea rows={3} value={form.descriptionEn} onChange={(e) => set("descriptionEn", e.target.value)} dir="ltr" />
          </Field>
          <Field label={t("staffCatalog.examMeta.descriptionBn")} required error={errors.descriptionBn}>
            <Textarea rows={3} value={form.descriptionBn} onChange={(e) => set("descriptionBn", e.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t("staffCatalog.examMeta.rulesEn")} required error={errors.rulesEn}>
            <Textarea rows={3} value={form.rulesEn} onChange={(e) => set("rulesEn", e.target.value)} dir="ltr" />
          </Field>
          <Field label={t("staffCatalog.examMeta.rulesBn")} required error={errors.rulesBn}>
            <Textarea rows={3} value={form.rulesBn} onChange={(e) => set("rulesBn", e.target.value)} />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label={t("staffCatalog.examMeta.paperLanguage")}
            hint={t("staffCatalog.examMeta.paperLanguageHint")}
            required
            error={errors.paperLanguage}
          >
            <Select value={form.paperLanguage} onChange={(e) => set("paperLanguage", e.target.value as PaperLanguage | "")}>
              <option value="">{t("staffCatalog.moduleExams.paperLanguagePick")}</option>
              <option value="BN">{t("staffCatalog.paperLanguage.BN")}</option>
              <option value="EN">{t("staffCatalog.paperLanguage.EN")}</option>
            </Select>
          </Field>
          <Field label={t("staffCatalog.examMeta.duration")} required error={errors.durationMinutes}>
            <NumberInput
              inputMode="numeric"
              min={2}
              step={1}
              value={form.durationMinutes}
              onChange={(e) => set("durationMinutes", e.target.value)}
              className="max-w-32"
            />
          </Field>
        </div>
        {formError ? (
          <p role="alert" className="text-meta text-danger">
            {formError}
          </p>
        ) : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
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
