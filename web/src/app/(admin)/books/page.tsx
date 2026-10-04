"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { BookCategoryView, BookView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export default function BooksPage() {
  const { t } = useLocale();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const [rows, setRows] = useState<BookView[]>([]);
  const [categories, setCategories] = useState<BookCategoryView[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [data, nextCategories] = await Promise.all([api.listBooks(page), api.listBookCategories()]);
      setRows(data.content);
      setTotalPages(data.totalPages);
      setCategories(nextCategories);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side list fetch
    void load();
  }, [load]);

  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold">{t("books.title")}</h1>
        <Link href="/books/new" className="rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800">
          {t("books.create")}
        </Link>
      </div>
      <FormError error={error} />
      {loading ? (
        <p className="mt-8 text-zinc-600">{t("common.loading")}</p>
      ) : rows.length === 0 ? (
        <p className="mt-8 text-zinc-600">{t("books.empty")}</p>
      ) : (
        <table className="mt-6 w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-zinc-200 text-left">
              <th className="px-2 py-2">{t("books.titleEn")}</th>
              <th className="px-2 py-2">{t("books.category")}</th>
              <th className="px-2 py-2">{t("books.availableNow")}</th>
              {isMaster ? <th className="px-2 py-2">{t("books.price")}</th> : null}
              <th className="px-2 py-2" />
            </tr>
          </thead>
          <tbody>
            {rows.map((book) => {
              const category = categories.find((item) => item.id === book.categoryId);
              return (
                <tr key={book.id} className="border-b border-zinc-100">
                  <td className="px-2 py-2">
                    <Link href={`/books/${book.id}`} className="font-medium text-zinc-900 hover:underline">
                      {book.titleEn}
                    </Link>
                    <div className="text-zinc-500">{book.titleBn}</div>
                  </td>
                  <td className="px-2 py-2">{category?.titleEn ?? "—"}</td>
                  <td className="px-2 py-2">{book.available ? t("books.availableNow") : t("books.unavailable")}</td>
                  {isMaster ? <td className="px-2 py-2">{book.price ?? "—"}</td> : null}
                  <td className="px-2 py-2 text-right">
                    <Link
                      href={`/books/${book.id}`}
                      className="inline-block rounded-md bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-zinc-800"
                    >
                      {t("books.viewBook")}
                    </Link>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
      {totalPages > 1 ? (
        <div className="mt-6 flex gap-2">
          <button type="button" disabled={page === 0} className="rounded border px-3 py-1 disabled:opacity-40" onClick={() => setPage((current) => current - 1)}>
            {t("common.previous")}
          </button>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            className="rounded border px-3 py-1 disabled:opacity-40"
            onClick={() => setPage((current) => current + 1)}
          >
            {t("common.next")}
          </button>
        </div>
      ) : null}
    </div>
  );
}
