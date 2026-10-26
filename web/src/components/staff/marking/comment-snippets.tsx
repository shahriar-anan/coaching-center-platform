"use client";

import { useCallback, useMemo, useSyncExternalStore } from "react";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { readStored, subscribe, writeStored } from "@/lib/hooks/local-store";

/** Per device, like the list preferences. The most a marker keeps. */
export const SNIPPET_LIMIT = 20;
const STORAGE_KEY = "marking:comment-snippets";

function parse(raw: string | null): string[] {
  try {
    const value: unknown = JSON.parse(raw ?? "[]");
    return Array.isArray(value)
      ? value.filter((item): item is string => typeof item === "string").slice(0, SNIPPET_LIMIT)
      : [];
  } catch {
    // Damaged storage: the marker starts again with no snippets.
    return [];
  }
}

/** The saved comment snippets on this device. Storage is read through useSyncExternalStore, like useListPrefs. */
export function useCommentSnippets() {
  const raw = useSyncExternalStore(subscribe, () => readStored(STORAGE_KEY), () => null);
  const snippets = useMemo(() => parse(raw), [raw]);

  const save = useCallback(
    (text: string): boolean => {
      const clean = text.trim();
      if (!clean || snippets.includes(clean) || snippets.length >= SNIPPET_LIMIT) {
        return false;
      }
      writeStored(STORAGE_KEY, JSON.stringify([...snippets, clean]));
      return true;
    },
    [snippets],
  );

  const remove = useCallback(
    (index: number) => writeStored(STORAGE_KEY, JSON.stringify(snippets.filter((_, at) => at !== index))),
    [snippets],
  );

  return { snippets, save, remove };
}

/**
 * The snippet row under the comment box: one click inserts a saved comment, the
 * button beside it saves the comment as it stands, and each snippet can be deleted.
 */
export function CommentSnippets({
  comment,
  onInsert,
}: {
  comment: string;
  onInsert: (text: string) => void;
}) {
  const { t } = useLocale();
  const format = useFormat();
  const { snippets, save, remove } = useCommentSnippets();
  const full = snippets.length >= SNIPPET_LIMIT;
  const canSave = comment.trim() !== "" && !full && !snippets.includes(comment.trim());

  return (
    <div className="grid gap-2">
      {snippets.length > 0 ? (
        <ul className="flex flex-wrap gap-2" aria-label={t("staffMarking.snippets.listLabel")}>
          {snippets.map((snippet, index) => (
            <li key={snippet} className="flex max-w-full items-center rounded-control border border-border bg-card">
              <button
                type="button"
                onClick={() => onInsert(snippet)}
                className="min-h-10 min-w-0 truncate px-3 text-meta text-text outline-none hover:bg-bg focus-visible:outline-accent"
              >
                {snippet}
              </button>
              <button
                type="button"
                aria-label={t("staffMarking.snippets.deleteLabel", { snippet })}
                onClick={() => remove(index)}
                className="min-h-10 px-2 text-meta text-muted outline-none hover:text-danger focus-visible:outline-accent"
              >
                {t("staffMarking.snippets.delete")}
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-meta text-muted">{t("staffMarking.snippets.empty")}</p>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" variant="secondary" disabled={!canSave} onClick={() => save(comment)}>
          {t("staffMarking.snippets.save")}
        </Button>
        <p className="text-meta text-muted">
          {full ? t("staffMarking.snippets.full", { limit: format.formatNumber(SNIPPET_LIMIT) }) : t("staffMarking.snippets.help")}
        </p>
      </div>
    </div>
  );
}
