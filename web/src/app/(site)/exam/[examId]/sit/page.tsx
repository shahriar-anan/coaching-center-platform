"use client";

import { use } from "react";
import { SittingEntry, type Start } from "@/components/student/sitting/sitting-entry";
import { RequireStudent } from "@/lib/auth/guards";

export default function SitPage({
  params,
  searchParams,
}: {
  params: Promise<{ examId: string }>;
  searchParams: Promise<{ start?: string }>;
}) {
  const { examId } = use(params);
  const { start } = use(searchParams);
  const mode: Start = start === "live" || start === "retake" ? start : null;
  return (
    <RequireStudent>
      <SittingEntry examId={examId} start={mode} />
    </RequireStudent>
  );
}
