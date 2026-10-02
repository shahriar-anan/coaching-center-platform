"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ActivationCodePanel } from "@/components/activation-code-panel";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { AccountView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export default function StudentsPage() {
  const { t } = useLocale();
  const user = useUser();
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState<AccountView[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [issuedCode, setIssuedCode] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listStudents(query, page);
      setRows(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [page, query]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side list fetch
    void load();
  }, [load]);

  function onSearch(e: React.FormEvent) {
    e.preventDefault();
    setPage(0);
    setQuery(q);
  }

  async function reissue(id: string) {
    setError(null);
    try {
      const res = await api.reissueStudentCode(id);
      setIssuedCode(res.activationCode);
    } catch (err) {
      setError(err);
    }
  }

  async function remove(id: string) {
    if (!window.confirm(t("common.confirmDelete"))) {
      return;
    }
    await api.deleteStudent(id);
    await load();
  }

  const isMaster = user?.role === "MASTER_ADMIN";

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">{t("students.title")}</h1>
        <Link
          href="/students/new"
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800"
        >
          {t("students.create")}
        </Link>
      </div>

      <form className="mt-6 flex flex-wrap gap-2" onSubmit={onSearch}>
        <input
          className="min-w-[16rem] flex-1 rounded-md border border-zinc-300 px-3 py-2"
          placeholder={t("students.searchPlaceholder")}
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <button
          type="submit"
          className="rounded-md border border-zinc-300 px-4 py-2 hover:bg-white"
        >
          {t("common.search")}
        </button>
      </form>

      {issuedCode ? <ActivationCodePanel code={issuedCode} /> : null}

      {error ? (
        <div className="mt-4">
          <FormError error={error} />
        </div>
      ) : null}

      {loading ? (
        <p className="mt-8 text-zinc-600">{t("common.loading")}</p>
      ) : rows.length === 0 ? (
        <p className="mt-8 text-zinc-600">{t("students.empty")}</p>
      ) : (
        <div className="mt-6 overflow-x-auto rounded-lg border border-zinc-200 bg-white">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-zinc-200 bg-zinc-50 text-zinc-600">
              <tr>
                <th className="px-4 py-3">{t("students.fullName")}</th>
                <th className="px-4 py-3">{t("students.code")}</th>
                <th className="px-4 py-3">{t("students.phone")}</th>
                <th className="px-4 py-3">{t("students.email")}</th>
                <th className="px-4 py-3">{t("students.status")}</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-zinc-100">
                  <td className="px-4 py-3">{row.fullName}</td>
                  <td className="px-4 py-3 font-mono text-xs">
                    {row.studentCode ?? "—"}
                  </td>
                  <td className="px-4 py-3">{row.phone}</td>
                  <td className="px-4 py-3">{row.email}</td>
                  <td className="px-4 py-3">
                    {t(`status.${row.status}` as "status.ACTIVE")}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-2">
                      {row.status === "PENDING_ACTIVATION" ? (
                        <button
                          type="button"
                          className="rounded border border-zinc-300 px-2 py-1 text-xs hover:bg-zinc-50"
                          onClick={() => reissue(row.id)}
                        >
                          {t("students.reissue")}
                        </button>
                      ) : null}
                      {isMaster ? (
                        <button
                          type="button"
                          className="rounded border border-red-300 px-2 py-1 text-xs text-red-800 hover:bg-red-50"
                          onClick={() => remove(row.id)}
                        >
                          {t("common.delete")}
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between text-sm">
          <button
            type="button"
            disabled={page <= 0}
            className="rounded border border-zinc-300 px-3 py-1 disabled:opacity-40"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            {t("common.previous")}
          </button>
          <span>{t("common.page", { page: page + 1, total: totalPages })}</span>
          <button
            type="button"
            disabled={page >= totalPages - 1}
            className="rounded border border-zinc-300 px-3 py-1 disabled:opacity-40"
            onClick={() => setPage((p) => p + 1)}
          >
            {t("common.next")}
          </button>
        </div>
      ) : null}
    </div>
  );
}
