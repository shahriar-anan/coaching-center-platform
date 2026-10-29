"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, X } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { useModules } from "@/lib/api/exam/staff-catalog";
import { useExamList } from "@/lib/api/exam/staff-exams";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { SearchInput } from "@/components/ui/search-input";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";
import { EnrolledList } from "@/components/staff/enrol/enrolled-list";

/** A student as the staff students list shows them. The code, phone and email are what an admin recognises. */
export type FoundStudent = {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  studentCode: string | null;
};

type Scope = "PACKAGE" | "EXAM";
type Outcome = { ok: true } | { ok: false; message: string };

/** Results appear from the second character, and update as each character is typed (the wait is only for the typing to pause). */
const MIN_CHARS = 2;

function useStudentFinder(text: string) {
  const [term, setTerm] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => setTerm(text.trim()), 200);
    return () => clearTimeout(timer);
  }, [text]);
  const query = useQuery({
    queryKey: ["staffEnrol", "find", term],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<FoundStudent>>(`/api/v1/students?page=0&size=10&q=${encodeURIComponent(term)}`, { signal }),
    enabled: term.length >= MIN_CHARS,
    staleTime: 30_000,
  });
  return { term, query };
}

/** Name, email, student code and phone, shown together so the admin can tell two students with similar names apart. */
function StudentLine({ student }: { student: FoundStudent }) {
  return (
    <span className="grid gap-0.5">
      <span className="font-bold text-text">{student.fullName}</span>
      <span className="text-meta text-muted" dir="ltr">
        {[student.studentCode, student.phone, student.email].filter(Boolean).join(" · ")}
      </span>
    </span>
  );
}

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
  const [targetId, setTargetId] = useState(initialExamId ?? initialPackageId ?? "");
  const [text, setText] = useState("");
  const [list, setList] = useState<FoundStudent[]>(initialStudents);
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<{ target?: string; list?: string; reason?: string }>({});
  const [outcomes, setOutcomes] = useState<Record<string, Outcome>>({});
  const [busy, setBusy] = useState(false);

  const finder = useStudentFinder(text);
  const found = finder.query.data?.content ?? [];
  const inList = useMemo(() => new Set(list.map((student) => student.id)), [list]);

  const packages = (useModules({ status: "PUBLISHED" }).data?.content ?? []).filter((row) => row.status === "PUBLISHED");
  const exams = useExamList({ status: "PUBLISHED" });
  const publishedExams = (exams.data?.pages.flatMap((page) => page.content) ?? []).filter((row) => row.status === "PUBLISHED");

  function changeScope(next: Scope) {
    setScope(next);
    setTargetId("");
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

  const targets = scope === "PACKAGE" ? packages : publishedExams;
  const failedCount = list.filter((student) => outcomes[student.id]?.ok === false).length;

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
      {/* Left: the target and the search. */}
      <div className="grid gap-4">
        <Card as="section" padding="md" className="grid gap-4">
          <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.targetHeading")}</h2>
          <fieldset className="grid gap-2">
            <legend className="sr-only">{t("staffAdmin.enrol.scope")}</legend>
            <div className="inline-flex w-fit gap-1 rounded-full border border-border bg-card p-1 text-meta">
              {(["PACKAGE", "EXAM"] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={scope === value}
                  onClick={() => changeScope(value)}
                  className={`min-h-11 rounded-full px-4 font-medium ${scope === value ? "bg-action text-on-action" : "text-muted hover:text-text"}`}
                >
                  {t(value === "PACKAGE" ? "staffAdmin.enrol.scopePackage" : "staffAdmin.enrol.scopeExam")}
                </button>
              ))}
            </div>
          </fieldset>
          <Field label={t(scope === "PACKAGE" ? "staffAdmin.enrol.package" : "staffAdmin.enrol.exam")} error={errors.target} required hint={t("staffAdmin.enrol.publishedOnly")}>
            <Select value={targetId} invalid={Boolean(errors.target)} onChange={(event) => setTargetId(event.target.value)}>
              <option value="">{t(scope === "PACKAGE" ? "staffAdmin.enrol.pickPackage" : "staffAdmin.enrol.pickExam")}</option>
              {targets.map((row) => (
                <option key={row.id} value={row.id}>
                  {pickTitle(row)}
                </option>
              ))}
            </Select>
          </Field>
        </Card>

        <Card as="section" padding="md" className="grid gap-4">
          <h2 className="text-card-title font-semibold">{t("staffAdmin.enrol.findHeading")}</h2>
          <SearchInput value={text} onChange={setText} placeholder={t("staffAdmin.enrol.searchPlaceholder")} />
          <p className="text-meta text-muted">{t("staffAdmin.enrol.findHelp")}</p>
          {finder.term.length < MIN_CHARS ? null : finder.query.isPending ? (
            <p className="text-meta text-muted">{t("staffAdmin.enrol.searching")}</p>
          ) : found.length === 0 ? (
            <p className="text-meta text-muted">{t("common.search.noResults")}</p>
          ) : (
            <ul className="grid gap-2">
              {found.map((student) => (
                <li key={student.id} className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-border-control bg-card px-4 py-3 shadow-sm">
                  <StudentLine student={student} />
                  {inList.has(student.id) ? (
                    <span className="inline-flex items-center gap-1 text-meta text-ok-text">
                      <Check aria-hidden="true" className="size-4" />
                      {t("staffAdmin.enrol.inList")}
                    </span>
                  ) : (
                    <Button variant="secondary" className="min-h-11" onClick={() => add(student)}>
                      {t("staffAdmin.enrol.add")}
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
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
          {list.length === 0 ? (
            <p className="rounded-control border border-dashed border-border p-4 text-meta text-muted">{t("staffAdmin.enrol.listEmpty")}</p>
          ) : (
            <ul className="grid gap-2">
              {list.map((student) => {
                const outcome = outcomes[student.id];
                return (
                  <li key={student.id} className="flex items-center justify-between gap-3 rounded-control border border-border-control bg-card px-4 py-3 shadow-sm">
                    <div className="grid min-w-0 gap-0.5">
                      <StudentLine student={student} />
                      {outcome && !outcome.ok ? (
                        <span role="alert" className="text-meta text-danger">{outcome.message}</span>
                      ) : null}
                    </div>
                    <button
                      type="button"
                      onClick={() => remove(student.id)}
                      disabled={busy}
                      aria-label={t("staffAdmin.enrol.remove", { name: student.fullName })}
                      className="grid size-11 shrink-0 place-items-center rounded-control text-muted hover:bg-bg hover:text-text disabled:opacity-50"
                    >
                      <X aria-hidden="true" className="size-4" />
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
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
