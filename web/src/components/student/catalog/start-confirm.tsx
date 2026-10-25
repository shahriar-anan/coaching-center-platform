"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { ExamType } from "@/lib/api/exam/catalog";
import { formatDateTime, formatDuration } from "@/lib/format";
import { serverNow } from "@/lib/time/clock";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Sheet } from "@/components/ui/sheet";
import { examPath } from "./catalog-helpers";

/** The LMS pre-start screen before WS4's sitting takes the exam. */
export function StartConfirm({
  open,
  onOpenChange,
  mode,
  exam,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "live" | "retake";
  exam: {
    id: string;
    title: string;
    examType: ExamType;
    durationMinutes: number;
    endAt: string | null;
    rules: string;
  };
}) {
  const { t, locale } = useLocale();
  const router = useRouter();
  const [going, setGoing] = useState(false);
  const ends =
    mode === "live" && exam.endAt
      ? exam.endAt
      : new Date(serverNow() + exam.durationMinutes * 60_000).toISOString();

  function start() {
    setGoing(true);
    router.push(`${examPath(exam.id)}/sit?start=${mode}`);
  }

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={t(mode === "live" ? "catalog.start.title" : "catalog.start.practiceTitle")}
    >
      <div className="grid gap-3">
        <p className="font-semibold">{exam.title}</p>
        <ul className="grid list-disc gap-1.5 pl-5 text-body">
          <li>{t("catalog.start.duration", { duration: formatDuration(exam.durationMinutes, locale) })}</li>
          <li>{t("catalog.start.clock")}</li>
          <li>{t("catalog.start.endsAt", { when: formatDateTime(ends, locale) })}</li>
          {mode === "live" && exam.examType === "MCQ" ? <li>{t("catalog.start.liveForfeit")}</li> : null}
          {mode === "retake" ? <li>{t("catalog.start.practiceNote")}</li> : null}
          {exam.examType === "WRITTEN" ? <li>{t("catalog.start.written")}</li> : null}
          <li>{t("catalog.start.connection")}</li>
        </ul>
        {exam.rules ? (
          <div className="rounded-control bg-bg p-3 text-meta">
            <p className="mb-1 font-semibold">{t("catalog.exam.rules")}</p>
            <p className="whitespace-pre-line text-muted">{exam.rules}</p>
          </div>
        ) : null}
        <Button fullWidth loading={going} onClick={start}>
          {t("catalog.start.confirm")}
        </Button>
        <Button variant="secondary" fullWidth onClick={() => onOpenChange(false)}>
          {t("catalog.start.notNow")}
        </Button>
      </div>
    </Sheet>
  );
}
