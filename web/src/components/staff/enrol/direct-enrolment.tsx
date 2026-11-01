"use client";

import { useMemo, useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiRequest } from "@/lib/api/client";
import { useExam, useModule } from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";
import { TargetPicker, type PickedTarget } from "@/components/staff/target-picker";
import { StudentFinder, StudentList, type FoundStudent } from "@/components/staff/student-picker";

export type { FoundStudent };

type Scope = "PACKAGE" | "EXAM";
type Outcome = { ok: true } | { ok: false; message: string };

/**
 * Master Admin only: add students to a list, one search at a time, then enrol everyone on the list in one go with a
 * reason. A package or an exam is chosen first, and only published ones can be chosen. Each student gets their own result.
 * A package or exam passed in from its menu is chosen already.
 */
export function DirectEnrolment({
  initialStudents,
  initialPackageId,
  initialExamId,
}: {
  initialStudents: FoundStudent[];
  initialPackageId?: string;
  initialExamId?: string;
}) {
  const { t } = useLocale();
  const { pickTitle, formatNumber } = useFormat();
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const [scope, setScope] = useState<Scope>(initialExamId ? "EXAM" : "PACKAGE");
  // The target named in the URL. Its title comes from the single read; a failed read shows the id instead.
  const presetExam = useExam(initialExamId);
  const presetPackage = useModule(initialPackageId);
  const preset: PickedTarget | null = initialExamId
    ? { kind: "exam", id: initialExamId, title: pickTitle(presetExam.data) || (presetExam.isError ? initialExamId : "") }
    : initialPackageId
      ? { kind: "package", id: initialPackageId, title: pickTitle(presetPackage.data) || (presetPackage.isError ? initialPackageId : "") }
      : null;
  // Undefined until the user chooses or clears a target. After that their choice replaces the preset.
  const [picked, setPicked] = useState<PickedTarget | null | undefined>(undefined);
  const target = picked === undefined ? preset : picked;
  const targetId = target?.id ?? "";

  const [list, setList] = useState<FoundStudent[]>(initialStudents);
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<{ target?: string; list?: string; reason?: string }>({});
  const [outcomes, setOutcomes] = useState<Record<string, Outcome>>({});
  const [busy, setBusy] = useState(false);

  const inList = useMemo(() => new Set(list.map((student) => student.id)), [list]);

  function chooseTarget(next: PickedTarget | null) {
    setPicked(next);
    if (next) {
      setScope(next.kind === "exam" ? "EXAM" : "PACKAGE");
    }
    setErrors((current) => ({ ...current, target: undefined }));
  }

  function changeScope(next: Scope) {
    setScope(next);
    setOutcomes({});
    setErrors({});
  }

  function add(student: FoundStudent) {
    setErrors((current) => ({ ...current, list: undefined }));
    setList((current) => (current.some((row) => row.id === student.id) ? current : [...current, student]));
    setOutcomes((current) => {
      const next = { ...current };
      delete next[student.id];
      return next;
    });
  }

  function remove(studentId: string) {
    setList((current) => current.filter((row) => row.id !== studentId));
  }

  async function enrolAll(event: FormEvent) {
    event.preventDefault();
    const next: typeof errors = {};
    if (!targetId) {
      next.target = scope === "PACKAGE" ? t("staffAdmin.enrol.packageRequired") : t("staffAdmin.enrol.examRequired");
    }
    if (list.length === 0) {
      next.list = t("staffAdmin.enrol.listRequired");
    }
    if (!reason.trim()) {
      next.reason = t("staffAdmin.enrol.reasonRequired");
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }

    setBusy(true);
    const pending = list.filter((student) => !outcomes[student.id] || !outcomes[student.id].ok);
    for (const student of pending) {
      const body =
        scope === "PACKAGE"
          ? { scope: "MODULE", moduleId: targetId, studentId: student.id, reason: reason.trim() }
          : { scope: "EXAM", examId: targetId, studentId: student.id, reason: reason.trim() };
      try {
        await apiRequest("/api/v1/live-exam-grants", { method: "POST", body });
        setOutcomes((current) => ({ ...current, [student.id]: { ok: true } }));
      } catch (e) {
        setOutcomes((current) => ({ ...current, [student.id]: { ok: false, message: t(catalogErrorKey(e)) } }));
      }
    }
    setBusy(false);

    // Students who were enrolled leave the list; the rest stay so the admin can try them again.
    setList((current) => current.filter((student) => outcomes[student.id]?.ok !== true));
    const done = pending.length;
    toast({ title: t("staffAdmin.enrol.finished", { count: formatNumber(done) }), tone: "ok" });
    void queryClient.invalidateQueries({ queryKey: ["staffEnrol"] });
  }

  const failedCount = list.filter((student) => outcomes[student.id]?.ok === false).length;

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
      {/* Left: the target and the search. */}
      <div className="grid gap-4">
        <Card as="section" padding="md" className="grid gap-4">
          <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.targetHeading")}</h2>
          <TargetPicker
            kind="either"
            status="PUBLISHED"
            label={t("staffAdmin.enrol.scope")}
            value={target}
            onChange={chooseTarget}
            onKindChange={(kind) => changeScope(kind === "exam" ? "EXAM" : "PACKAGE")}
            invalid={Boolean(errors.target)}
            errorText={errors.target}
          />
          <p className="text-meta text-muted">{t("staffAdmin.enrol.publishedOnly")}</p>
        </Card>

        <Card as="section" padding="md" className="grid gap-4">
          <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.findHeading")}</h2>
          <StudentFinder listedIds={inList} onAdd={add} />
        </Card>
      </div>

      {/* Right: the list that will be enrolled, the reason and the one action. */}
      <form onSubmit={enrolAll} noValidate className="grid gap-4">
        <Card as="section" padding="md" className="grid gap-4">
          <div className="flex items-baseline justify-between gap-3">
            <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.listHeading")}</h2>
            <span className="text-meta tabular-nums text-muted">{t("staffAdmin.enrol.count", { count: formatNumber(list.length) })}</span>
          </div>
          {errors.list ? (
            <p role="alert" className="text-meta text-danger">{errors.list}</p>
          ) : null}
          <StudentList
            students={list}
            onRemove={remove}
            busy={busy}
            renderNote={(student) => {
              const outcome = outcomes[student.id];
              return outcome && !outcome.ok ? <span role="alert" className="text-meta text-danger">{outcome.message}</span> : null;
            }}
          />
          <Field label={t("staffAdmin.enrol.reason")} hint={t("staffAdmin.enrol.reasonHint")} error={errors.reason} required>
            <Textarea value={reason} maxLength={500} rows={3} invalid={Boolean(errors.reason)} onChange={(event) => setReason(event.target.value)} />
          </Field>
          {failedCount > 0 ? (
            <p className="text-meta text-danger">{t("staffAdmin.enrol.failedNote", { count: formatNumber(failedCount) })}</p>
          ) : null}
          <div className="flex flex-wrap items-center gap-3">
            <Button type="submit" loading={busy} disabled={busy || list.length === 0}>
              {t("staffAdmin.enrol.submit", { count: formatNumber(list.length) })}
            </Button>
          </div>
        </Card>
      </form>

      {/* Below both columns: who is already on the chosen package or exam, newest first, so a student just enrolled is at the top. */}
      {targetId ? (
        <Card as="section" padding="md" className="grid gap-4 lg:col-span-2">
          <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.enrolledHeading")}</h2>
          <EnrolledList scope={scope === "PACKAGE" ? "MODULE" : "EXAM"} targetId={targetId} />
        </Card>
      ) : null}

    </div>
  );
}
