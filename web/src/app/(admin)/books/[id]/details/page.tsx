"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { BookDetailsForm } from "@/components/book-details-form";
import { BookShell } from "@/components/book-shell";
import { FormError } from "@/components/form-error";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { BookView } from "@/lib/api/types";

export default function BookDetailsPage() {
  const { t } = useLocale();
  const params = useParams();
  const bookId = params.id as string;
  const router = useRouter();
  const [book, setBook] = useState<BookView | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setBook(await api.getBook(bookId));
    } catch (err) {
      setError(err);
    }
  }, [bookId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- client-side fetch
    void load();
  }, [load]);

  if (!book) {
    return (
      <div>
        <FormError error={error} />
        {error ? null : <p className="text-zinc-600">{t("common.loading")}</p>}
      </div>
    );
  }

  return (
    <BookShell book={book} step="details" onError={setError}>
      <FormError error={error} />
      <BookDetailsForm
        book={book}
        onSaved={(saved) => {
          router.push(`/books/${saved.id}`);
        }}
      />
    </BookShell>
  );
}
