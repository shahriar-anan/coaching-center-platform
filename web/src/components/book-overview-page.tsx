"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AuthorizedFile } from "@/components/authorized-file";
import { BookShell } from "@/components/book-shell";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { BookCategoryView, BookView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export function BookOverviewPage({ bookId }: { bookId: string }) {
  const { t } = useLocale();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const [book, setBook] = useState<BookView | null>(null);
  const [categories, setCategories] = useState<BookCategoryView[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [language, setLanguage] = useState<"en" | "bn">("en");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextBook, nextCategories] = await Promise.all([api.getBook(bookId), api.listBookCategories()]);
      setBook(nextBook);
      setCategories(nextCategories);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [bookId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side fetch
    void load();
  }, [load]);

  if (loading && !book) {
    return <p className="text-zinc-600">{t("common.loading")}</p>;
  }

  const category = categories.find((item) => item.id === book?.categoryId) ?? null;
  const categoryName = category ? (language === "bn" ? category.titleBn : category.titleEn) : null;

  return (
    <BookShell book={book} step="view" onError={setError}>
      <FormError error={error} />
      {book ? (
        <>
          <section className="grid gap-6 rounded-lg border border-zinc-200 bg-white p-4 lg:grid-cols-[14rem_minmax(0,1fr)]">
            <div>
              {book.coverFileId ? (
                <AuthorizedFile
                  fileId={book.coverFileId}
                  alt={book.titleEn}
                  className="aspect-[3/4] w-full rounded-lg object-cover"
                />
              ) : (
                <div className="flex aspect-[3/4] items-center justify-center rounded-lg border border-dashed border-zinc-300 text-sm text-zinc-500">
                  {t("books.cover")}
                </div>
              )}
            </div>
            <div>
              <h1 className="text-2xl font-semibold">{book.titleEn}</h1>
              <p className="text-zinc-600">{book.titleBn}</p>
              <div className="mt-3">
                <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-700">
                  {book.available ? t("books.availableNow") : t("books.unavailable")}
                </span>
              </div>
              <dl className="mt-4 grid gap-4 sm:grid-cols-2">
                <div>
                  <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">{t("books.author")}</dt>
                  <dd className="mt-1 text-sm text-zinc-900">{book.author}</dd>
                </div>
                {isMaster ? (
                  <div>
                    <dt className="text-xs font-medium uppercase tracking-wide text-zinc-500">{t("books.price")}</dt>
                    <dd className="mt-1 text-sm text-zinc-900">{book.price != null ? String(book.price) : "—"}</dd>
                  </div>
                ) : null}
              </dl>
            </div>
          </section>
          <section className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-lg font-semibold">{t("books.description")}</h2>
            <p className="mt-2 whitespace-pre-wrap text-zinc-800">{book.description}</p>
          </section>
          <section className="rounded-lg border border-zinc-200 bg-white p-6">
            <h2 className="text-lg font-semibold">{t("books.category")}</h2>
            {categoryName ? (
              <div className="mt-4">
                <div className="flex gap-2">
                  <button
                    type="button"
                    aria-pressed={language === "en"}
                    className={`rounded-md px-3 py-1.5 text-sm ${language === "en" ? "bg-zinc-900 text-white" : "border border-zinc-300 text-zinc-700"}`}
                    onClick={() => setLanguage("en")}
                  >
                    {t("books.titleEn")}
                  </button>
                  <button
                    type="button"
                    aria-pressed={language === "bn"}
                    className={`rounded-md px-3 py-1.5 text-sm ${language === "bn" ? "bg-zinc-900 text-white" : "border border-zinc-300 text-zinc-700"}`}
                    onClick={() => setLanguage("bn")}
                  >
                    {t("books.titleBn")}
                  </button>
                </div>
                <p className="mt-3 text-sm text-zinc-500">{t("books.placedInLabel")}</p>
                <p className="mt-2">
                  <span className="rounded-full border border-zinc-200 bg-zinc-50 px-3 py-1 text-sm font-medium text-zinc-900">
                    {categoryName}
                  </span>
                </p>
              </div>
            ) : (
              <p className="mt-4 text-sm text-zinc-600">{t("books.notPlaced")}</p>
            )}
          </section>
        </>
      ) : null}
    </BookShell>
  );
}

export default function BookViewPage() {
  const params = useParams();
  return <BookOverviewPage bookId={params.id as string} />;
}
