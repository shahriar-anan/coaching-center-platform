"use client";

import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useRole } from "@/lib/auth/guards";
import { useModuleExams, type StaffModule } from "@/lib/api/exam/staff-catalog";
import { Stepper, type StepperStep } from "@/components/ui/stepper";

/**
 * The setup steps of one module, with what each needs and where to fix it (U4).
 * Master-Admin-only steps stay on the page as information for a System Admin, never as a control.
 * The last line says whether students can see and buy the module now, and why not.
 */
export function ModuleSetup({ module }: { module: StaffModule }) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const role = useRole();
  const exams = useModuleExams(module.id);
  const list = exams.data?.content ?? [];
  const total = (module.mcqCount ?? 0) + (module.writtenCount ?? 0);
  const published = list.filter((exam) => exam.status === "PUBLISHED").length;
  const drafts = list.filter((exam) => exam.status === "DRAFT");
  const draftsWithoutContent = drafts.filter((exam) =>
    exam.examType === "MCQ" ? (exam.questions?.length ?? 0) === 0 : exam.questionPdf !== true,
  ).length;
  const isMaster = role.canEditPrice;
  const masterOnly = t("staffCatalog.setup.masterOnly");

  const steps: StepperStep[] = [
    { key: "details", label: t("staffCatalog.setup.steps.details"), state: "done", href: `/staff/modules/${module.id}?tab=details` },
    isMaster
      ? {
          key: "price",
          label: t("staffCatalog.setup.steps.price"),
          state: module.price !== null ? "done" : "todo",
          note: module.price !== null ? undefined : t("staffCatalog.setup.priceMissing"),
          href: `/staff/modules/${module.id}?tab=details`,
        }
      : { key: "price", label: t("staffCatalog.setup.steps.price"), state: "info", note: masterOnly },
    {
      key: "exams",
      label: t("staffCatalog.setup.steps.exams"),
      state: total > 0 ? "done" : "todo",
      note: total > 0 ? undefined : t("staffCatalog.setup.noExams"),
      href: `/staff/modules/${module.id}?tab=exams`,
    },
    {
      key: "publish",
      label: t("staffCatalog.setup.steps.publish"),
      state: module.status === "PUBLISHED" ? "done" : "todo",
      note: module.status === "PUBLISHED" ? undefined : t("staffCatalog.setup.notPublished"),
      href: `/staff/modules/${module.id}?tab=details`,
    },
    isMaster
      ? {
          key: "teacher",
          label: t("staffCatalog.setup.steps.teacher"),
          state: module.instructor ? "done" : "todo",
          note: module.instructor ? module.instructor.name : t("staffCatalog.setup.noTeacher"),
          href: `/staff/modules/${module.id}?tab=instructor`,
        }
      : {
          key: "teacher",
          label: t("staffCatalog.setup.steps.teacher"),
          state: "info",
          note: module.instructor ? module.instructor.name : masterOnly,
        },
    isMaster
      ? {
          key: "roster",
          label: t("staffCatalog.setup.steps.roster"),
          state: "info",
          note: t("staffCatalog.setup.rosterNote"),
          href: `/staff/modules/${module.id}?tab=roster`,
        }
      : { key: "roster", label: t("staffCatalog.setup.steps.roster"), state: "info", note: masterOnly },
  ];

  const sellable = module.status === "PUBLISHED" && (module.price !== null || list.some((exam) => exam.price !== null && exam.status === "PUBLISHED"));
  const reason =
    module.status === "ARCHIVED"
      ? t("staffCatalog.setup.reasonArchived")
      : module.status !== "PUBLISHED"
        ? t("staffCatalog.setup.reasonNotPublished")
        : !sellable
          ? t("staffCatalog.setup.reasonNoPrice")
          : t("staffCatalog.setup.visible");

  return (
    <section aria-labelledby="module-setup-heading" className="grid gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-5">
      <h2 id="module-setup-heading" className="text-label font-semibold">
        {t("staffCatalog.setup.heading")}
      </h2>
      <Stepper steps={steps} orientation="horizontal" />
      <p className="text-meta text-muted" aria-live="polite">
        {t("staffCatalog.setup.summary", {
          total: formatNumber(total),
          published: formatNumber(published),
          draft: formatNumber(drafts.length),
        })}
        {draftsWithoutContent > 0 ? ` · ${t("staffCatalog.setup.draftsNoContent", { n: formatNumber(draftsWithoutContent) })}` : null}
      </p>
      <p className="text-meta">{reason}</p>
    </section>
  );
}
