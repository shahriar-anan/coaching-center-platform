"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { AuthorizedFile } from "@/components/authorized-file";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import type { BookView } from "@/lib/api/types";
import { useUser } from "@/lib/auth/session-context";

export type BookStep = "view" | "details";

export function BookShell({
  book,
  step,
  onError,
  children,
}: {
  book: BookView | null;
  step: BookStep;
  onError?: (error: unknown) => void;
  children: ReactNode;
}) {
  const { t } = useLocale();
  const router = useRouter();
  const user = useUser();
  const isMaster = user?.role === "MASTER_ADMIN";
  const bookId = book?.id;

  async function remove() {
    if (!bookId || !window.confirm(t("books.deleteConfirm"))) {
      return;
    }
    try {
      await api.deleteBook(bookId);
      router.push("/books");
    } catch (err) {
      onError?.(err);
    }
  }

  return (
    <div className="space-y-6">
      <header className="space-y-4 rounded-lg border border-zinc-200 bg-white p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-3">
            <Link href="/books" className="text-sm text-zinc-600 hover:text-zinc-900">
              {t("books.title")}
            </Link>
            <nav aria-label={t("books.steps")} className="flex flex-wrap gap-2 text-sm">
              {bookId ? (
                <StepLink href={`/books/${bookId}`} current={step === "view"}>
                  {t("books.viewBook")}
                </StepLink>
              ) : null}
              <StepLink href={bookId ? `/books/${bookId}/details` : "/books/new"} current={step === "details"}>
                {t("books.stepDetails")}
              </StepLink>
            </nav>
          </div>
          {isMaster && book ? (
            <button
              type="button"
              className="rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-800"
              onClick={() => void remove()}
            >
              {t("common.delete")}
            </button>
          ) : null}
        </div>
        {step === "view" ? null : (
          <div className="flex items-center gap-4">
            {book?.coverFileId ? (
              <AuthorizedFile fileId={book.coverFileId} alt={book.titleEn} className="h-14 w-14 rounded object-cover" />
            ) : null}
            <div>
              <h1 className="text-2xl font-semibold">{book ? book.titleEn : t("books.createTitle")}</h1>
              {book ? <p className="text-zinc-600">{book.titleBn}</p> : null}
              {book ? (
                <div className="mt-2">
                  <StatusChip>{book.available ? t("books.availableNow") : t("books.unavailable")}</StatusChip>
                </div>
              ) : null}
            </div>
          </div>
        )}
      </header>
      {children}
    </div>
  );
}

function StatusChip({ children }: { children: ReactNode }) {
  return <span className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs text-zinc-700">{children}</span>;
}

function StepLink({
  href,
  current,
  children,
}: {
  href: string;
  current: boolean;
  children: ReactNode;
}) {
  const className = current
    ? "rounded-md bg-zinc-900 px-3 py-1.5 font-medium text-white"
    : "rounded-md border border-zinc-300 px-3 py-1.5 text-zinc-700 hover:bg-zinc-50";
  return (
    <Link href={href} className={className} aria-current={current ? "page" : undefined}>
      {children}
    </Link>
  );
}
