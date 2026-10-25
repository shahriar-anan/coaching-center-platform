"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { useRole } from "@/lib/auth/guards";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { cn } from "@/components/ui/cn";
import { Pill, type PillTone } from "@/components/ui/pill";

const DEBOUNCE_MS = 300;
const MIN_CHARS = 2;
const LIST_ID = "staff-quick-search-results";

type StudentHit = { id: string; fullName: string; phone: string; studentCode: string | null };
type ModuleHit = { id: string; titleEn: string; titleBn: string; status: string };

type Option = { id: string; href: string; primary: string; secondary: ReactNode };

const STATUS_TONE: Record<string, PillTone> = { DRAFT: "muted", PUBLISHED: "ok", ARCHIVED: "muted" };

/** Keys that mean "typing in a field", so `/` must not steal them. */
function isEditable(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}

/**
 * Staff top-bar search. Typing a name, phone, email or student code offers
 * matching students; typing a title offers modules. `/` focuses the field,
 * ↑ and ↓ move through the results and Enter opens the highlighted one.
 * Shown to the roles that can author or see students, so nobody sees a dead search.
 */
export function StaffQuickSearch() {
  const { t, locale } = useLocale();
  const role = useRole();
  const router = useRouter();
  const [text, setText] = useState("");
  const [term, setTerm] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);

  // Debounce the query so each keystroke does not fire a request.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setTerm(text.trim());
      setActive(-1);
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [text]);

  // `/` moves focus into the search, unless the user is already typing somewhere.
  useEffect(() => {
    const onKey = (event: globalThis.KeyboardEvent) => {
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

  const studentRows = students.data?.content ?? [];
  const moduleRows = modules.data?.content ?? [];
  const options: Option[] = [
    ...studentRows.map((row) => ({
      id: `student-${row.id}`,
      href: `/staff/students/${row.id}`,
      primary: row.fullName,
      secondary: [row.phone, row.studentCode].filter(Boolean).join(" · "),
    })),
    ...moduleRows.map((row) => ({
      id: `module-${row.id}`,
      href: `/staff/modules/${row.id}`,
      primary: locale === "bn" ? row.titleBn : row.titleEn,
      secondary: (
        <Pill tone={STATUS_TONE[row.status] ?? "muted"}>{t(`staffCatalog.status.${row.status}`)}</Pill>
      ),
    })),
  ];

  const showList = open && text.trim().length > 0;
  const loading = enabled && (students.isFetching || modules.isFetching);
  const nothing = enabled && !loading && options.length === 0;
  const activeOption = showList && active >= 0 ? options[active] : undefined;

  function close() {
    setOpen(false);
    setActive(-1);
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Escape") {
      close();
      inputRef.current?.blur();
      return;
    }
    if (!showList || options.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((index) => (index + 1) % options.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((index) => (index <= 0 ? options.length - 1 : index - 1));
    } else if (event.key === "Enter" && activeOption) {
      event.preventDefault();
      close();
      router.push(activeOption.href);
    }
  }

  return (
    <div className="relative w-full max-w-sm">
      <Search aria-hidden="true" className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
      <input
        ref={inputRef}
        type="search"
        role="combobox"
        aria-label={t("common.quickSearch.label")}
        aria-expanded={showList}
        aria-controls={LIST_ID}
        aria-autocomplete="list"
        aria-activedescendant={activeOption ? activeOption.id : undefined}
        placeholder={t("common.quickSearch.placeholder")}
        value={text}
        onChange={(event) => {
          setText(event.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={close}
        onKeyDown={onKeyDown}
        className="min-h-10 w-full rounded-control border border-border bg-card ps-9 pe-3 text-body text-text placeholder:text-muted focus:border-accent"
      />
      {showList ? (
        <div
          id={LIST_ID}
          role="listbox"
          aria-label={t("common.quickSearch.label")}
          className="absolute inset-x-0 top-full z-40 mt-1 grid max-h-96 gap-1 overflow-y-auto rounded-card border border-border bg-card p-2 shadow-lg"
        >
          {loading ? <p className="px-2 py-1.5 text-meta text-muted">{t("common.quickSearch.loading")}</p> : null}
          {nothing ? <p className="px-2 py-1.5 text-meta text-muted">{t("common.quickSearch.empty")}</p> : null}
          {studentRows.length > 0 ? (
            <Group title={t("common.quickSearch.students")}>
              {options.slice(0, studentRows.length).map((option, index) => (
                <Hit key={option.id} option={option} selected={index === active} onPick={close} />
              ))}
            </Group>
          ) : null}
          {moduleRows.length > 0 ? (
            <Group title={t("common.quickSearch.modules")}>
              {options.slice(studentRows.length).map((option, index) => (
                <Hit
                  key={option.id}
                  option={option}
                  selected={studentRows.length + index === active}
                  onPick={close}
                />
              ))}
            </Group>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5">
      <p className="px-2 pt-1 text-pill font-semibold text-muted">{title}</p>
      {children}
    </div>
  );
}

function Hit({ option, selected, onPick }: { option: Option; selected: boolean; onPick: () => void }) {
  return (
    <Link
      id={option.id}
      href={option.href}
      prefetch
      role="option"
      aria-selected={selected}
      tabIndex={-1}
      onMouseDown={(event) => event.preventDefault()}
      onClick={onPick}
      className={cn("grid gap-0.5 rounded-control px-2 py-1.5 outline-none hover:bg-bg", selected && "bg-bg")}
    >
      <span className="truncate text-label text-text">{option.primary}</span>
      {option.secondary ? <span className="truncate text-meta text-muted">{option.secondary}</span> : null}
    </Link>
  );
}
