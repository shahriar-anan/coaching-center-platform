"use client";

import { useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { useListParams } from "@/lib/url/list-params";
import {
  useAddToRoster,
  usePasteRoster,
  useRemoveFromRoster,
  useRoster,
  useRosterSearch,
  type PasteResult,
  type RosterMember,
} from "@/lib/api/exam/staff-catalog";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { Pagination } from "@/components/ui/pagination";
import { SearchInput } from "@/components/ui/search-input";
import { Table, type Column } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/components/ui/toast";
import { catalogErrorKey } from "@/components/staff/catalog/errors";

function PasteBox({ moduleId }: { moduleId: string }) {
  const { t } = useLocale();
  const { formatNumber } = useFormat();
  const paste = usePasteRoster(moduleId);
  const [text, setText] = useState("");
  const [result, setResult] = useState<PasteResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const values = text
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
    if (values.length === 0) {
      setError(t("staffCatalog.roster.pasteEmpty"));
      return;
    }
    setError(null);
    try {
      setResult(await paste.mutateAsync(values));
      setText("");
    } catch (e) {
      setError(t(catalogErrorKey(e)));
    }
  }

  return (
    <section aria-labelledby="roster-paste-heading" className="grid gap-3 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-4">
      <h3 id="roster-paste-heading" className="text-card-title font-semibold">
        {t("staffCatalog.roster.pasteHeading")}
      </h3>
      <p className="text-meta text-muted">{t("staffCatalog.roster.pasteHelp")}</p>
      <form onSubmit={submit} noValidate className="grid gap-3">
        <label htmlFor="roster-paste" className="sr-only">
          {t("staffCatalog.roster.pasteLabel")}
        </label>
        <Textarea
          id="roster-paste"
          rows={4}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={t("staffCatalog.roster.pastePlaceholder")}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? "roster-paste-error" : undefined}
          dir="ltr"
        />
        {error ? (
          <p id="roster-paste-error" role="alert" className="text-meta text-danger">
            {error}
          </p>
        ) : null}
        <div>
          <Button type="submit" variant="secondary" loading={paste.isPending} disabled={paste.isPending}>
            {t("staffCatalog.roster.paste")}
          </Button>
        </div>
      </form>
      {result ? (
        <div role="status" className="grid gap-2 rounded-control bg-bg p-3 text-meta">
          <p className="font-medium">
            {t("staffCatalog.roster.pasteResult", { count: formatNumber(result.marked.length) })}
          </p>
          {result.unknown.length > 0 ? (
            <div>
              <p className="text-muted">{t("staffCatalog.roster.unknownLines")}</p>
              <ul className="mt-1 grid gap-0.5" dir="ltr">
                {result.unknown.map((line) => (
                  <li key={line} className="font-mono">
                    {line}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

/** Master Admin only: who is marked for this module, add by search or paste, remove with a name. */
export function RosterTab({ moduleId }: { moduleId: string }) {
  const { t } = useLocale();
  const { formatDate } = useFormat();
  const { toast } = useToast();
  const [params, setParams] = useListParams({ q: "", page: 0 });
  const roster = useRoster(moduleId, params.q, params.page);
  const [addQuery, setAddQuery] = useState("");
  const hits = useRosterSearch(moduleId, addQuery);
  const add = useAddToRoster(moduleId);
  const remove = useRemoveFromRoster(moduleId);
  const [removing, setRemoving] = useState<RosterMember | null>(null);
  const [addError, setAddError] = useState<string | null>(null);

  const listed = new Set((roster.data?.content ?? []).map((m) => m.studentId));

  const columns: Column<RosterMember>[] = [
    { key: "name", header: t("staffCatalog.roster.columns.name"), cell: (m) => <span className="font-medium">{m.fullName}</span> },
    { key: "phone", header: t("staffCatalog.roster.columns.phone"), cell: (m) => <span dir="ltr">{m.phone ?? "—"}</span>, mobileLabel: t("staffCatalog.roster.columns.phone") },
    { key: "email", header: t("staffCatalog.roster.columns.email"), cell: (m) => <span dir="ltr">{m.email ?? "—"}</span> },
    { key: "code", header: t("staffCatalog.roster.columns.code"), cell: (m) => <span dir="ltr">{m.studentCode ?? "—"}</span> },
    { key: "added", header: t("staffCatalog.roster.columns.added"), cell: (m) => formatDate(m.addedAt) },
    {
      key: "remove",
      header: <span className="sr-only">{t("staffCatalog.roster.columns.actions")}</span>,
      align: "end",
      cell: (m) => (
        <Button variant="danger" className="min-h-10 px-2" onClick={() => setRemoving(m)}>
          {t("staffCatalog.roster.remove")}
        </Button>
      ),
    },
  ];

  async function addStudent(studentId: string) {
    setAddError(null);
    try {
      await add.mutateAsync(studentId);
      toast({ title: t("staffCatalog.roster.added"), tone: "ok" });
    } catch (e) {
      setAddError(t(catalogErrorKey(e)));
    }
  }

  return (
    <div className="grid gap-6">
      <section aria-labelledby="roster-add-heading" className="grid gap-3">
        <h3 id="roster-add-heading" className="text-card-title font-semibold">
          {t("staffCatalog.roster.addHeading")}
        </h3>
        <SearchInput value={addQuery} onChange={setAddQuery} placeholder={t("staffCatalog.roster.addPlaceholder")} />
        {addQuery.trim().length >= 2 && hits.data && hits.data.length > 0 ? (
          <ul className="grid divide-y divide-border rounded-card border border-border bg-card shadow-[var(--card-shadow)]">
            {hits.data.map((hit) => (
              <li key={hit.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-2.5">
                <span>
                  <span className="font-medium">{hit.fullName}</span>{" "}
                  <span className="text-meta text-muted" dir="ltr">{hit.phone ?? hit.email ?? ""}</span>
                </span>
                {listed.has(hit.id) ? (
                  <span className="text-meta text-muted">{t("staffCatalog.roster.alreadyListed")}</span>
                ) : (
                  <Button variant="secondary" className="min-h-10" loading={add.isPending} onClick={() => addStudent(hit.id)}>
                    {t("staffCatalog.roster.addOne")}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        ) : null}
        {addQuery.trim().length >= 2 && hits.data && hits.data.length === 0 ? (
          <p className="text-meta text-muted">{t("common.search.noResults")}</p>
        ) : null}
        {addError ? (
          <p role="alert" className="text-meta text-danger">
            {addError}
          </p>
        ) : null}
      </section>

      <PasteBox moduleId={moduleId} />

      <section aria-labelledby="roster-list-heading" className="grid gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 id="roster-list-heading" className="text-card-title font-semibold">
            {t("staffCatalog.roster.listHeading")}
          </h3>
          <div className="w-full sm:w-72">
            <SearchInput value={params.q} onChange={(q) => setParams({ q, page: 0 })} placeholder={t("staffCatalog.roster.listSearch")} />
          </div>
        </div>
        {roster.isError && !roster.data ? (
          <ErrorState onRetry={() => roster.refetch()} message={t("staffCatalog.roster.loadError")} />
        ) : (
          <>
            <Table
              columns={columns}
              rows={roster.data?.content ?? []}
              rowKey={(m) => m.studentId}
              loading={roster.isPending}
              empty={
                <EmptyState
                  title={params.q ? t("common.search.noResults") : t("staffCatalog.roster.emptyTitle")}
                  body={params.q ? undefined : t("staffCatalog.roster.emptyBody")}
                />
              }
            />
            <Pagination page={params.page} totalPages={roster.data?.totalPages ?? 0} onPage={(page) => setParams({ page })} />
          </>
        )}
      </section>

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={t("staffCatalog.roster.removeTitle", { name: removing?.fullName ?? "" })}
        body={removing?.startedAny ? t("staffCatalog.roster.removeStartedBody") : t("staffCatalog.roster.removeBody")}
        confirmLabel={t("staffCatalog.roster.removeConfirm")}
        tone="danger"
        onConfirm={async () => {
          if (!removing) {
            return;
          }
          try {
            await remove.mutateAsync(removing.studentId);
            setRemoving(null);
            toast({ title: t("staffCatalog.roster.removed"), tone: "ok" });
          } catch (e) {
            throw new Error(t(catalogErrorKey(e)));
          }
        }}
      />
    </div>
  );
}
