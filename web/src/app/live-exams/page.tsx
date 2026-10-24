"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";

function ExamName() {
  const name = useSearchParams().get("name")?.trim() ?? "";
  return (
    <main className="mx-auto w-full max-w-lg px-4 py-16">
      <h1 className="text-3xl font-semibold">{name || "Exam"}</h1>
    </main>
  );
}

export default function LiveExamPage() {
  return (
    <Suspense fallback={<p className="p-8 text-center">Loading</p>}>
      <ExamName />
    </Suspense>
  );
}
