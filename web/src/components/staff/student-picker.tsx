"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Check, X } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { SearchInput } from "@/components/ui/search-input";

/** A student as the staff students list shows them. The code, phone and email are what an admin recognises. */
export type FoundStudent = {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  studentCode: string | null;
};

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
export function StudentLine({ student }: { student: FoundStudent }) {
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
 * The search box and its results. A student already in the list shows "In the list" instead of Add. With `max`, Add is
 * disabled once `listedIds` holds that many students, and a line says why.
 */
export function StudentFinder({
  listedIds,
  max,
  onAdd,
}: {
  listedIds: ReadonlySet<string>;
  max?: number;
  onAdd: (student: FoundStudent) => void;
}) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const [text, setText] = useState("");
  const finder = useStudentFinder(text);
  const found = finder.query.data?.content ?? [];
  const full = max !== undefined && listedIds.size >= max;

  return (
    <div className="grid gap-4">
      <SearchInput value={text} onChange={setText} placeholder={t("staffAdmin.enrol.searchPlaceholder")} />
      <p className="text-meta text-muted">{t("staffAdmin.enrol.findHelp")}</p>
      {full ? <p className="text-meta text-muted">{t("staffAdmin.picker.studentsMax", { max: formatNumber(max) })}</p> : null}
      {finder.term.length < MIN_CHARS ? null : finder.query.isPending ? (
        <p className="text-meta text-muted">{t("staffAdmin.enrol.searching")}</p>
      ) : found.length === 0 ? (
        <p className="text-meta text-muted">{t("staffAdmin.picker.studentNone")}</p>
      ) : (
        <ul className="grid gap-2">
          {found.map((student) => (
            <li key={student.id} className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-border-control bg-card px-4 py-3 shadow-sm">
              <StudentLine student={student} />
              {listedIds.has(student.id) ? (
                <span className="inline-flex items-center gap-1 text-meta text-ok-text">
                  <Check aria-hidden="true" className="size-4" />
                  {t("staffAdmin.enrol.inList")}
                </span>
              ) : (
                <Button variant="secondary" className="min-h-11" disabled={full} onClick={() => onAdd(student)}>
                  {t("staffAdmin.enrol.add")}
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * The students on the list, each with a Remove button. `renderNote` adds a line under a student, such as the reason an
 * enrolment failed.
 */
export function StudentList({
  students,
  onRemove,
  busy = false,
  renderNote,
}: {
  students: FoundStudent[];
  onRemove: (studentId: string) => void;
  busy?: boolean;
  renderNote?: (student: FoundStudent) => ReactNode;
}) {
  const { t } = useLocale();
  if (students.length === 0) {
    return <p className="rounded-control border border-dashed border-border p-4 text-meta text-muted">{t("staffAdmin.enrol.listEmpty")}</p>;
  }
  return (
    <ul className="grid gap-2">
      {students.map((student) => (
        <li key={student.id} className="flex items-center justify-between gap-3 rounded-control border border-border-control bg-card px-4 py-3 shadow-sm">
          <div className="grid min-w-0 gap-0.5">
            <StudentLine student={student} />
            {renderNote?.(student)}
          </div>
          <button
            type="button"
            onClick={() => onRemove(student.id)}
            disabled={busy}
            aria-label={t("staffAdmin.enrol.remove", { name: student.fullName })}
            className="grid size-11 shrink-0 place-items-center rounded-control text-muted hover:bg-bg hover:text-text disabled:opacity-50"
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </li>
      ))}
    </ul>
  );
}

/**
 * Finder and list together, for a screen that keeps one list of students. `value` is the list, and `onChange` gets the
 * next list. With `max`, no more than that many students can be added.
 */
export function StudentPicker({
  value,
  onChange,
  max,
}: {
  value: FoundStudent[];
  onChange: (next: FoundStudent[]) => void;
  max?: number;
}) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const listedIds = useMemo(() => new Set(value.map((student) => student.id)), [value]);
  const full = max !== undefined && value.length >= max;

  function add(student: FoundStudent) {
    if (listedIds.has(student.id) || full) {
      return;
    }
    onChange([...value, student]);
  }

  function remove(studentId: string) {
    onChange(value.filter((student) => student.id !== studentId));
  }

  const count = formatNumber(value.length);
  return (
    <div className="grid gap-4">
      <StudentFinder listedIds={listedIds} max={max} onAdd={add} />
      <p className="text-meta tabular-nums text-muted">
        {max === undefined
          ? t("staffAdmin.picker.studentCount", { count })
          : t("staffAdmin.picker.studentCountOf", { count, max: formatNumber(max) })}
      </p>
      <StudentList students={value} onRemove={remove} />
    </div>
  );
}
