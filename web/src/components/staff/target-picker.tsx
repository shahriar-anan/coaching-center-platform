"use client";

import { useEffect, useMemo, useRef, useState, type CSSProperties, type KeyboardEvent } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useCategoryTree, type CategoryTreeNode } from "@/lib/api/exam/staff-catalog";
import { useTargetSearch, type TargetKind, type TargetRow } from "@/lib/api/exam/target-search";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { Field } from "@/components/ui/field";
import { Pill } from "@/components/ui/pill";
import { Select } from "@/components/ui/select";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Skeleton } from "@/components/ui/skeleton";
import { TextInput } from "@/components/ui/text-input";

/** The package or exam the picker has chosen. `title` is empty while it is still loading. */
export type PickedTarget = { kind: TargetKind; id: string; title: string };

export type TargetPickerProps = {
  /** "either" shows the Package / Exam switch inside the picker. */
  kind: TargetKind | "either";
  /** One server-side status filter such as "PUBLISHED". Undefined means any status, archived ones included. */
  status?: string;
  /** Statuses removed on the client after the server answers, such as ["DRAFT"]. */
  hide?: string[];
  value: PickedTarget | null;
  onChange: (next: PickedTarget | null) => void;
  /** Called when the user switches between Package and Exam ("either" only). */
  onKindChange?: (kind: TargetKind) => void;
  invalid?: boolean;
  errorText?: string;
  /** The group's visible label, such as "Enrol into". */
  label: string;
  /** Keeps the label for screen readers only, for a dialog whose title already says it. */
  hideLabel?: boolean;
};

/** The search waits this long after typing stops, so the list is not requested on every key. */
const SEARCH_DEBOUNCE_MS = 250;

type CategoryOption = { id: string; title: string; depth: number };

/** Which edges of the result list have more rows beyond them. */
type Edges = { top: boolean; bottom: boolean };

const FADE_PX = 28;

/** A soft fade on the edges that have more rows, so a list with no visible scrollbar still shows it can scroll. */
function fadeStyle(edges: Edges): CSSProperties | undefined {
  if (!edges.top && !edges.bottom) {
    return undefined;
  }
  const from = edges.top ? `transparent 0, black ${FADE_PX}px` : "black 0";
  const to = edges.bottom ? `black calc(100% - ${FADE_PX}px), transparent 100%` : "black 100%";
  const mask = `linear-gradient(to bottom, ${from}, ${to})`;
  return { maskImage: mask, WebkitMaskImage: mask };
}

/** The category tree as a flat list, parents before children. Each child is indented by its depth. */
function flattenCategories(nodes: CategoryTreeNode[], title: (node: CategoryTreeNode) => string, depth = 0): CategoryOption[] {
  return nodes.flatMap((node) => [{ id: node.id, title: title(node), depth }, ...flattenCategories(node.children, title, depth + 1)]);
}

/**
 * Picks one package or one exam. A search box (sent as `q`), a category and, for exams, a type narrow the list. The
 * list is requested 10 rows at a time with "Load more". Once a target is chosen the list collapses to a chosen line
 * with a Change button, so the card stays short. The rows are radio buttons: the arrow keys move between them, and
 * Enter or Space (or a click) chooses the row in focus.
 */
export function TargetPicker({
  kind,
  status,
  hide,
  value,
  onChange,
  onKindChange,
  invalid,
  errorText,
  label,
  hideLabel = false,
}: TargetPickerProps) {
  const { t } = useLocale();
  const { pickTitle, formatNumber } = useFormat();
  const categories = useCategoryTree();
  const categoryOptions = useMemo(() => flattenCategories(categories.data ?? [], pickTitle), [categories.data, pickTitle]);
  const categoryTitles = useMemo(() => new Map(categoryOptions.map((option) => [option.id, option.title])), [categoryOptions]);

  // The Package / Exam tab. A chosen target decides it; without one, the last tab the user picked stays.
  const [draftKind, setDraftKind] = useState<TargetKind>(value?.kind ?? "package");
  const tab: TargetKind = kind === "either" ? (value?.kind ?? draftKind) : kind;

  const listRef = useRef<HTMLDivElement | null>(null);
  const [edges, setEdges] = useState<Edges>({ top: false, bottom: false });
  const updateEdges = () => {
    const list = listRef.current;
    if (!list) {
      return;
    }
    const next = { top: list.scrollTop > 2, bottom: list.scrollTop + list.clientHeight < list.scrollHeight - 2 };
    setEdges((current) => (current.top === next.top && current.bottom === next.bottom ? current : next));
  };
  const [draft, setDraft] = useState("");
  const [q, setQ] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [type, setType] = useState("");
  const [active, setActive] = useState(0);
  const searchRef = useRef<HTMLInputElement>(null);
  const chosenRef = useRef<HTMLDivElement>(null);
  const rowRefs = useRef<(HTMLButtonElement | null)[]>([]);
  // Where the focus goes once the selection changes: the search box after Change, the chosen line after a pick.
  const focusAfter = useRef<"search" | "chosen" | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setQ(draft.trim()), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [draft]);

  useEffect(() => {
    const target = focusAfter.current;
    focusAfter.current = null;
    if (target === "search" && !value) {
      searchRef.current?.focus();
    }
    if (target === "chosen" && value) {
      chosenRef.current?.focus();
    }
  }, [value]);

  // Nothing is requested while the list is hidden behind the chosen line.
  const results = useTargetSearch(
    { kind: tab, q, categoryId, type: tab === "exam" ? type : undefined, status },
    { enabled: !value },
  );
  const rows = (results.data?.pages.flatMap((page) => page.content) ?? []).filter((row) => !hide?.includes(row.status));
  const focusIndex = Math.min(active, Math.max(rows.length - 1, 0));
  useEffect(() => {
    updateEdges();
    // updateEdges only reads the list element; it runs again when the rows change.
  }, [rows.length, tab]);

  function switchKind(next: TargetKind) {
    if (next === tab) {
      return;
    }
    setDraftKind(next);
    setType("");
    setActive(0);
    if (value) {
      onChange(null);
    }
    onKindChange?.(next);
  }

  function change() {
    if (!value) {
      return;
    }
    setDraftKind(value.kind);
    focusAfter.current = "search";
    onChange(null);
  }

  function choose(row: TargetRow) {
    focusAfter.current = "chosen";
    onChange({ kind: tab, id: row.id, title: pickTitle(row) });
  }

  function moveFocus(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const last = rows.length - 1;
    let next: number;
    switch (event.key) {
      case "ArrowDown":
      case "ArrowRight":
        next = index >= last ? 0 : index + 1;
        break;
      case "ArrowUp":
      case "ArrowLeft":
        next = index <= 0 ? last : index - 1;
        break;
      case "Home":
        next = 0;
        break;
      case "End":
        next = last;
        break;
      default:
        return;
    }
    event.preventDefault();
    setActive(next);
    rowRefs.current[next]?.focus();
  }

  return (
    <fieldset className="grid gap-3">
      <legend className={hideLabel ? "sr-only" : "mb-1 text-label font-medium text-text"}>{label}</legend>

      {kind === "either" ? (
        <SegmentedControl
          ariaLabel={label}
          value={tab}
          options={[
            { value: "package", label: t("staffAdmin.picker.kind.PACKAGE") },
            { value: "exam", label: t("staffAdmin.picker.kind.EXAM") },
          ]}
          onChange={(next) => switchKind(next as TargetKind)}
        />
      ) : null}

      {value ? (
        <div
          ref={chosenRef}
          tabIndex={-1}
          className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-border-control bg-bg px-4 py-3 focus:outline-none"
        >
          <div className="grid min-w-0 gap-1">
            <span className="text-meta text-muted">{t("staffAdmin.picker.chosen")}</span>
            <span className="flex flex-wrap items-center gap-2 font-bold text-text">
              <Pill tone="muted">{t(value.kind === "exam" ? "staffAdmin.picker.kind.EXAM" : "staffAdmin.picker.kind.PACKAGE")}</Pill>
              {value.title ? (
                value.title
              ) : (
                <>
                  <span aria-hidden="true" className="inline-block h-4 w-40 animate-pulse rounded-control bg-border motion-reduce:animate-none" />
                  <span className="sr-only">{t("common.states.loading")}</span>
                </>
              )}
            </span>
          </div>
          <Button variant="secondary" onClick={change}>
            {t("staffAdmin.picker.change")}
          </Button>
        </div>
      ) : (
        <div className="grid gap-3">
          <Field label={t("staffAdmin.picker.search")}>
            <TextInput
              ref={searchRef}
              type="search"
              value={draft}
              invalid={invalid}
              placeholder={t("staffAdmin.picker.searchPlaceholder")}
              onChange={(event) => setDraft(event.target.value)}
            />
          </Field>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label={t("staffAdmin.picker.category")}>
              <Select
                value={categoryId}
                onChange={(event) => {
                  setCategoryId(event.target.value);
                  setActive(0);
                }}
              >
                <option value="">{t("staffAdmin.picker.allCategories")}</option>
                {categoryOptions.map((option) => (
                  <option key={option.id} value={option.id}>
                    {"  ".repeat(option.depth) + option.title}
                  </option>
                ))}
              </Select>
            </Field>
            {tab === "exam" ? (
              <Field label={t("staffAdmin.picker.type")}>
                <Select
                  value={type}
                  onChange={(event) => {
                    setType(event.target.value);
                    setActive(0);
                  }}
                >
                  <option value="">{t("staffAdmin.picker.typeAll")}</option>
                  <option value="MCQ">{t("staffAdmin.picker.typeMcq")}</option>
                  <option value="WRITTEN">{t("staffAdmin.picker.typeWritten")}</option>
                </Select>
              </Field>
            ) : null}
          </div>

          <div className="grid gap-2">
            {results.isError ? <ErrorState onRetry={() => void results.refetch()} error={results.error} /> : null}
            {results.isPending ? (
              <Skeleton variant="rows" count={4} height="h-11" />
            ) : rows.length === 0 ? (
              results.isError ? null : (
                <p className="rounded-control border border-dashed border-border p-4 text-meta text-muted">
                  {t(tab === "exam" ? "staffAdmin.picker.emptyExam" : "staffAdmin.picker.emptyPackage")}
                </p>
              )
            ) : (
              <div
                role="radiogroup"
                aria-label={t("staffAdmin.picker.results")}
                ref={listRef}
                onScroll={updateEdges}
                style={fadeStyle(edges)}
                className="scroll-clean max-h-[min(20rem,45dvh)] overflow-y-auto overscroll-contain rounded-control border border-border-control bg-card"
              >
                {rows.map((row, index) => (
                  <button
                    key={row.id}
                    ref={(element) => {
                      rowRefs.current[index] = element;
                    }}
                    type="button"
                    role="radio"
                    aria-checked={index === focusIndex}
                    tabIndex={index === focusIndex ? 0 : -1}
                    onClick={() => choose(row)}
                    onFocus={() => setActive(index)}
                    onKeyDown={(event) => moveFocus(event, index)}
                    className="flex min-h-11 w-full items-center justify-between gap-3 border-b border-border px-4 py-2 text-start transition-colors last:border-b-0 hover:bg-bg focus-visible:-outline-offset-2 focus-visible:outline-2 focus-visible:outline-accent aria-checked:bg-accent/10"
                  >
                    <span className="grid min-w-0 gap-0.5">
                      <span className="font-bold text-text">{pickTitle(row)}</span>
                      <span className="text-meta text-muted">{categoryTitles.get(row.categoryId ?? "") ?? ""}</span>
                    </span>
                    <span className="flex shrink-0 flex-wrap items-center justify-end gap-2 text-meta">
                      {row.examType ? (
                        <Pill tone={row.examType === "MCQ" ? "accent" : "muted"}>
                          {t(row.examType === "MCQ" ? "staffAdmin.picker.typeMcq" : "staffAdmin.picker.typeWritten")}
                        </Pill>
                      ) : null}
                      {row.examCount !== null ? (
                        <span className="tabular-nums text-muted">
                          {t("staffAdmin.picker.examCount", { count: formatNumber(row.examCount) })}
                        </span>
                      ) : null}
                    </span>
                  </button>
                ))}
              </div>
            )}
            {results.hasNextPage ? (
              <Button
                variant="secondary"
                className="justify-self-start"
                loading={results.isFetchingNextPage}
                onClick={() => void results.fetchNextPage()}
              >
                {t("staffAdmin.picker.loadMore")}
              </Button>
            ) : null}
          </div>
        </div>
      )}

      {errorText ? (
        <p role="alert" className="text-meta text-danger">
          {errorText}
        </p>
      ) : null}
    </fieldset>
  );
}
