"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/exam/types";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { SearchInput } from "@/components/ui/search-input";
import { TextInput } from "@/components/ui/text-input";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

/** A student as the staff students list shows them: the code, phone and email are what an admin recognises. */
export type FoundStudent = {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  studentCode: string | null;
};

/**
 * Enrols one student at a time. The admin types a student code, phone, email or name, picks one result, checks it, and
 * enrols. There is no bulk step. A student who is already enrolled is shown as such, and the search waits until the
 * admin has typed two characters and stops typing for a moment.
 */
export function OneStudentEnrol({
  enabled,
  disabledHint,
  requireReason,
  enrolledIds,
  enrol,
  headingId,
}: {
  enabled: boolean;
  disabledHint: string;
  requireReason: boolean;
  enrolledIds: Set<string>;
  enrol: (studentId: string, reason: string) => Promise<unknown>;
  headingId: string;
}) {
  const { t } = useLocale();
  const { toast } = useToast();
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [selected, setSelected] = useState<FoundStudent | null>(null);
  const [reason, setReason] = useState("");
  const [reasonError, setReasonError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(query.trim()), 300);
    return () => clearTimeout(timer);
  }, [query]);

  const results = useQuery({
    queryKey: ["staffEnrol", "students", debounced],
    queryFn: ({ signal }) =>
      apiRequest<PageResponse<FoundStudent>>(`/api/v1/students?page=0&size=8&q=${encodeURIComponent(debounced)}`, { signal }),
    enabled: enabled && debounced.length >= 2,
    staleTime: 30_000,
  });

  function choose(student: FoundStudent) {
    setSelected(student);
    setReason("");
    setReasonError(null);
    setError(null);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!selected) {
      return;
    }
    if (requireReason && !reason.trim()) {
      setReasonError(t("staffCatalog.enrol.reasonRequired"));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await enrol(selected.id, reason.trim());
      toast({ title: t("staffCatalog.enrol.done", { name: selected.fullName }), tone: "ok" });
      setSelected(null);
      setQuery("");
      setDebounced("");
    } catch (e) {
      setError(t(catalogErrorKey(e)));
    } finally {
      setBusy(false);
    }
  }

  if (!enabled) {
    return (
      <section aria-labelledby={headingId} className="grid gap-2 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-4">
        <h3 id={headingId} className="text-card-title font-semibold">{t("staffCatalog.enrol.heading")}</h3>
        <p className="text-meta text-muted">{disabledHint}</p>
      </section>
    );
  }

  return (
    <section aria-labelledby={headingId} className="grid gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-4">
      <div className="grid gap-1">
        <h3 id={headingId} className="text-card-title font-semibold">{t("staffCatalog.enrol.heading")}</h3>
        <p className="text-meta text-muted">{t("staffCatalog.enrol.help")}</p>
      </div>

      {selected ? (
        <form onSubmit={submit} noValidate className="grid gap-3 rounded-control border border-border bg-bg p-4">
          <div className="grid gap-0.5">
            <span className="text-label font-semibold">{selected.fullName}</span>
            <span className="text-meta text-muted" dir="ltr">
              {[selected.studentCode, selected.phone, selected.email].filter(Boolean).join(" · ")}
            </span>
          </div>
          {enrolledIds.has(selected.id) ? (
            <p className="text-meta text-muted">{t("staffCatalog.enrol.already")}</p>
          ) : null}
          {requireReason ? (
            <Field label={t("staffCatalog.enrol.reason")} required error={reasonError ?? undefined} hint={t("staffCatalog.enrol.reasonHint")}>
              <TextInput value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} />
            </Field>
          ) : null}
          {error ? (
            <p role="alert" className="text-meta text-danger">{error}</p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" loading={busy} disabled={busy || enrolledIds.has(selected.id)}>
              {t("staffCatalog.enrol.submit")}
            </Button>
            <Button type="button" variant="secondary" onClick={() => setSelected(null)} disabled={busy}>
              {t("common.actions.cancel")}
            </Button>
          </div>
        </form>
      ) : (
        <div className="grid gap-3">
          <SearchInput
            value={query}
            onChange={setQuery}
            placeholder={t("staffCatalog.enrol.searchPlaceholder")}

          />
          {debounced.length >= 2 && results.isPending ? (
            <p className="text-meta text-muted">{t("staffCatalog.enrol.searching")}</p>
          ) : null}
          {debounced.length >= 2 && results.data && results.data.content.length > 0 ? (
            <ul className="grid divide-y divide-border rounded-control border border-border">
              {results.data.content.map((student) => (
                <li key={student.id} className="flex flex-wrap items-center justify-between gap-3 px-3 py-2.5">
                  <span className="grid gap-0.5">
                    <span className="font-medium">{student.fullName}</span>
                    <span className="text-meta text-muted" dir="ltr">
                      {[student.studentCode, student.phone, student.email].filter(Boolean).join(" · ")}
                    </span>
                  </span>
                  {enrolledIds.has(student.id) ? (
                    <span className="text-meta text-muted">{t("staffCatalog.enrol.already")}</span>
                  ) : (
                    <Button variant="secondary" className="min-h-11" onClick={() => choose(student)}>
                      {t("staffCatalog.enrol.choose")}
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          ) : null}
          {debounced.length >= 2 && results.data && results.data.content.length === 0 ? (
            <p className="text-meta text-muted">{t("common.search.noResults")}</p>
          ) : null}
          {debounced.length < 2 ? <p className="text-meta text-muted">{t("staffCatalog.enrol.typeHint")}</p> : null}
        </div>
      )}
    </section>
  );
}
