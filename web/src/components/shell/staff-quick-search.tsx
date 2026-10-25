"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { cn } from "@/components/ui/cn";

const DEBOUNCE_MS = 300;
const MIN_CHARS = 2;

type StudentHit = { id: string; fullName: string; phone: string; studentCode: string | null };
type ModuleHit = { id: string; titleEn: string; titleBn: string; status: string };

/** Keys that mean "typing in a field", so `/` must not steal them. */
function isEditable(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}

/**
 * Staff top-bar search. Typing a name, phone, email or student code offers
 * matching students; typing a title offers modules. `/` focuses the field.
 * Shown to the roles that can author, so nobody sees a dead search.
 */
export function StaffQuickSearch() {
  const { t, locale } = useLocale();
  const role = useRole();
  const [text, setText] = useState("");
  const [term, setTerm] = useState("");
  const [open, setOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // Debounce the query so each keystroke does not fire a request.
  useEffect(() => {
    const timer = window.setTimeout(() => setTerm(text.trim()), DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [text]);

  // `/` moves focus into the search, unless the user is already typing somewhere.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return;
      if (isEditable(event.target)) return;
      event.preventDefault();
      inputRef.current?.focus();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const enabled = term.length >= MIN_CHARS;

  const students = useQuery({
    queryKey: ["staffQuickSearch", "students", term],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<StudentHit>>(
        `/api/v1/students?page=0&size=5&q=${encodeURIComponent(term)}`,
        { signal },
      ),
    enabled: enabled && role.canSeeStudents,
    staleTime: 30_000,
  });

  const modules = useQuery({
    queryKey: ["staffQuickSearch", "modules", term],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<ModuleHit>>(
        `/api/v1/live-exam-modules?page=0&size=5&q=${encodeURIComponent(term)}`,
        { signal },
      ),
    enabled,
    staleTime: 30_000,
  });

  if (!role.canAuthor && !role.canSeeStudents) {
    return null;
  }

  const showList = open && text.trim().length > 0;
  const loading = enabled && (students.isFetching || modules.isFetching);
  const studentRows = students.data?.content ?? [];
  const moduleRows = modules.data?.content ?? [];
  const nothing = enabled && !loading && studentRows.length === 0 && moduleRows.length === 0;

  return (
    <div className="relative w-full max-w-sm">
      <Search aria-hidden="true" className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
      <input
        ref={inputRef}
        type="search"
        role="combobox"
        aria-label={t("common.quickSearch.label")}
        aria-expanded={showList}
        aria-controls="staff-quick-search-results"
        placeholder={t("common.quickSearch.placeholder")}
        value={text}
        onChange={(event) => {
          setText(event.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            setOpen(false);
            inputRef.current?.blur();
          }
        }}
        className="min-h-10 w-full rounded-control border border-border bg-card ps-9 pe-3 text-body text-text placeholder:text-muted focus:border-accent"
      />
      {showList ? (
        <div
          id="staff-quick-search-results"
          role="listbox"
          aria-label={t("common.quickSearch.label")}
          className="absolute inset-x-0 top-full z-40 mt-1 grid max-h-96 gap-1 overflow-y-auto rounded-card border border-border bg-card p-2 shadow-lg"
        >
          {loading ? <p className="px-2 py-1.5 text-meta text-muted">{t("common.quickSearch.loading")}</p> : null}
          {nothing ? <p className="px-2 py-1.5 text-meta text-muted">{t("common.quickSearch.empty")}</p> : null}
          {studentRows.length > 0 ? (
            <Group title={t("common.quickSearch.students")}>
              {studentRows.map((row) => (
                <Hit
                  key={row.id}
                  href={`/staff/students/${row.id}`}
                  primary={row.fullName}
                  secondary={[row.phone, row.studentCode].filter(Boolean).join(" · ")}
                  onPick={() => setOpen(false)}
                />
              ))}
            </Group>
          ) : null}
          {moduleRows.length > 0 ? (
            <Group title={t("common.quickSearch.modules")}>
              {moduleRows.map((row) => (
                <Hit
                  key={row.id}
                  href={`/staff/modules/${row.id}`}
                  primary={locale === "bn" ? row.titleBn : row.titleEn}
                  secondary={row.status}
                  onPick={() => setOpen(false)}
                />
              ))}
            </Group>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <p className="px-2 pt-1 text-pill font-semibold text-muted">{title}</p>
      {children}
    </div>
  );
}

function Hit({
  href,
  primary,
  secondary,
  onPick,
}: {
  href: string;
  primary: string;
  secondary: string;
  onPick: () => void;
}) {
  return (
    <Link
      href={href}
      prefetch
      role="option"
      aria-selected={false}
      onMouseDown={(event) => event.preventDefault()}
      onClick={onPick}
      className={cn("grid gap-0.5 rounded-control px-2 py-1.5 outline-none hover:bg-bg focus-visible:bg-bg")}
    >
      <span className="truncate text-label text-text">{primary}</span>
      {secondary ? <span className="truncate text-meta text-muted">{secondary}</span> : null}
    </Link>
  );
}
