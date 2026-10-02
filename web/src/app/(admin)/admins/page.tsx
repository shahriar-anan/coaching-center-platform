"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ActivationCodePanel } from "@/components/activation-code-panel";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { AccountView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export default function AdminsPage() {
  const { t } = useLocale();
  const user = useUser();
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
      const data = await api.listAdmins(page);
      setRows(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    if (user?.role === "MASTER_ADMIN") {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side list fetch
      void load();
    }
  }, [load, user?.role]);

  if (user?.role !== "MASTER_ADMIN") {
    return <p className="text-zinc-600">{t("errors.accessDenied")}</p>;
  }

  async function toggleStatus(row: AccountView) {
    const next = row.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    await api.changeAdminStatus(row.id, next);
    await load();
  }

  async function reissue(id: string) {
    const res = await api.reissueAdminCode(id);
    setIssuedCode(res.activationCode);
  }

  async function remove(id: string) {
    if (!window.confirm(t("common.confirmDelete"))) {
      return;
    }
    await api.deleteAdmin(id);
    await load();
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">{t("admins.title")}</h1>
        <Link
          href="/admins/new"
          className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800"
        >
          {t("admins.create")}
        </Link>
      </div>

      {issuedCode ? <ActivationCodePanel code={issuedCode} /> : null}
      {error ? <div className="mt-4"><FormError error={error} /></div> : null}

      {loading ? (
        <p className="mt-8 text-zinc-600">{t("common.loading")}</p>
      ) : rows.length === 0 ? (
        <p className="mt-8 text-zinc-600">{t("admins.empty")}</p>
      ) : (
        <div className="mt-6 overflow-x-auto rounded-lg border border-zinc-200 bg-white">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-zinc-200 bg-zinc-50 text-zinc-600">
              <tr>
                <th className="px-4 py-3">{t("admins.fullName")}</th>
                <th className="px-4 py-3">{t("admins.phone")}</th>
                <th className="px-4 py-3">{t("admins.email")}</th>
                <th className="px-4 py-3">{t("admins.status")}</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-zinc-100">
                  <td className="px-4 py-3">{row.fullName}</td>
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
                          {t("admins.reissue")}
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="rounded border border-zinc-300 px-2 py-1 text-xs hover:bg-zinc-50"
                          onClick={() => toggleStatus(row)}
                        >
                          {row.status === "ACTIVE"
                            ? t("admins.deactivate")
                            : t("admins.activate")}
                        </button>
                      )}
                      <button
                        type="button"
                        className="rounded border border-red-300 px-2 py-1 text-xs text-red-800 hover:bg-red-50"
                        onClick={() => remove(row.id)}
                      >
                        {t("common.delete")}
                      </button>
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
