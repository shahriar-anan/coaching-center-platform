"use client";

import { useRouter } from "next/navigation";
import { BookDetailsForm } from "@/components/book-details-form";
import { BookShell } from "@/components/book-shell";

export default function NewBookPage() {
  const router = useRouter();
  return (
    <BookShell book={null} step="details">
      <BookDetailsForm book={null} onSaved={(book) => router.push(`/books/${book.id}`)} />
    </BookShell>
  );
}
