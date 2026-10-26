"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useModuleExams, useModules } from "@/lib/api/exam/staff-catalog";
import { useGrantAccess, type GrantScope } from "@/lib/api/exam/staff-admin";
import { adminErrorText } from "./admin-error";

type FieldErrors = { module?: string; exam?: string; reason?: string };

/**
 * Grants a module or one exam to a student, by hand, with a reason. The reason
 * goes to the audit log. Master Admin and System Admin can use it.
 */
export function GrantAccessForm({ studentId, onDone }: { studentId: string; onDone?: () => void }) {
  const { t } = useLocale();
  const { pickTitle } = useFormat();
  const { toast } = useToast();
  const grant = useGrantAccess(studentId);
  const modules = useModules();

  const [scope, setScope] = useState<GrantScope>("MODULE");
  const [moduleId, setModuleId] = useState("");
  const [examId, setExamId] = useState("");
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);

  const exams = useModuleExams(scope === "EXAM" && moduleId ? moduleId : undefined);
  const moduleOptions = (modules.data?.content ?? []).filter((row) => row.status !== "ARCHIVED");
  const examOptions = exams.data?.content ?? [];

  async function submit(event: FormEvent) {
    event.preventDefault();
    const next: FieldErrors = {};
    if (!moduleId) {
      next.module = t("staffAdmin.grant.moduleRequired");
    }
    if (scope === "EXAM" && !examId) {
      next.exam = t("staffAdmin.grant.examRequired");
    }
    if (!reason.trim()) {
      next.reason = t("staffAdmin.grant.reasonRequired");
    }
    setErrors(next);
    setFormError(null);
    if (Object.keys(next).length > 0) {
      return;
    }

    const body = scope === "MODULE" ? { scope, moduleId, reason } : { scope, examId, reason };
    try {
      await grant.mutateAsync(body);
      toast({ title: t("staffAdmin.grant.done"), tone: "ok" });
      setModuleId("");
      setExamId("");
      setReason("");
      onDone?.();
    } catch (e) {
      setFormError(adminErrorText(e, t));
    }
  }

  return (
    <form className="grid gap-4" onSubmit={submit} noValidate>
      <h3 className="text-card font-semibold text-text">{t("staffAdmin.grant.title")}</h3>

      <fieldset className="grid gap-2">
        <legend className="text-label font-medium text-text">{t("staffAdmin.grant.scope")}</legend>
        {(["MODULE", "EXAM"] as const).map((value) => (
          <label key={value} className="flex min-h-10 items-center gap-3 text-body text-text">
            <input
              type="radio"
              name="grant-scope"
              value={value}
              checked={scope === value}
              onChange={() => {
                setScope(value);
                setExamId("");
                setErrors({});
              }}
              className="size-5 accent-action"
            />
            {t(value === "MODULE" ? "staffAdmin.grant.scopeModule" : "staffAdmin.grant.scopeExam")}
          </label>
        ))}
      </fieldset>

      <Field label={t("staffAdmin.grant.module")} error={errors.module} required>
        <Select
          value={moduleId}
          invalid={Boolean(errors.module)}
          onChange={(event) => {
            setModuleId(event.target.value);
            setExamId("");
          }}
        >
          <option value="">{t("staffAdmin.grant.pickModule")}</option>
          {moduleOptions.map((row) => (
            <option key={row.id} value={row.id}>
              {pickTitle(row)}
            </option>
          ))}
        </Select>
      </Field>

      {scope === "EXAM" && moduleId ? (
        examOptions.length > 0 ? (
          <Field label={t("staffAdmin.grant.exam")} error={errors.exam} required>
            <Select
              value={examId}
              invalid={Boolean(errors.exam)}
              onChange={(event) => setExamId(event.target.value)}
            >
              <option value="">{t("staffAdmin.grant.pickExam")}</option>
              {examOptions.map((row) => (
                <option key={row.id} value={row.id}>
                  {pickTitle(row)}
                </option>
              ))}
            </Select>
          </Field>
        ) : (
          <p className="text-meta text-muted">{t("staffAdmin.grant.noExams")}</p>
        )
      ) : null}

      <Field label={t("staffAdmin.grant.reason")} hint={t("staffAdmin.grant.reasonHint")} error={errors.reason} required>
        <Textarea
          value={reason}
          maxLength={500}
          rows={3}
          invalid={Boolean(errors.reason)}
          onChange={(event) => setReason(event.target.value)}
        />
      </Field>

      {formError ? (
        <p role="alert" className="text-meta text-danger">
          {formError}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={grant.isPending}>
          {t("staffAdmin.grant.submit")}
        </Button>
        {onDone ? (
          <Button variant="ghost" onClick={onDone} disabled={grant.isPending}>
            {t("common.actions.cancel")}
          </Button>
        ) : null}
      </div>
    </form>
  );
}
