"use client";

import { useRef, useState } from "react";
import { ApiError } from "@/lib/api/errors";
import {
  useRemoveExamCover,
  useStaffExamCover,
  useUploadExamCover,
  type StaffExam,
} from "@/lib/api/exam/staff-catalog";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { PackageCover } from "@/components/ui/package/package-cover";
import { COVER_MAX_BYTES, COVER_TYPES } from "@/components/staff/catalog/cover-uploader";

/**
 * The Master Admin's cover for one exam (Phase 4, S4.7, E1). It is the package uploader for an exam: the browser checks type
 * and size first, and the server checks them again. The note says the change is live at once.
 */
export function ExamCoverUploader({ exam }: { exam: StaffExam }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const input = useRef<HTMLInputElement>(null);
  const upload = useUploadExamCover(exam.id);
  const remove = useRemoveExamCover(exam.id);
  const cover = useStaffExamCover(exam.id, exam.coverUrl);
  const [error, setError] = useState<string | null>(null);
  const busy = upload.isPending || remove.isPending;
  const hasCover = Boolean(exam.coverUrl);

  async function onChange(file: File | undefined) {
    if (!file) return;
    setError(null);
    if (!COVER_TYPES.includes(file.type)) {
      setError(t("catalog.package.cover.wrongType"));
      return;
    }
    if (file.size > COVER_MAX_BYTES) {
      setError(t("catalog.package.cover.tooLarge"));
      return;
    }
    try {
      await upload.mutateAsync(file);
      toast({ title: t("catalog.package.cover.saved"), tone: "ok" });
    } catch (err) {
      setError(mapError(err, t));
    } finally {
      if (input.current) input.current.value = "";
    }
  }

  async function onRemove() {
    setError(null);
    try {
      await remove.mutateAsync();
      toast({ title: t("catalog.package.cover.removed"), tone: "ok" });
    } catch (err) {
      setError(mapError(err, t));
    }
  }

  return (
    <section aria-labelledby="exam-cover-heading" className="grid max-w-3xl gap-4 rounded-card border border-border bg-card shadow-[var(--card-shadow)] p-5">
      <h3 id="exam-cover-heading" className="text-card-title font-semibold">
        {t("catalog.package.cover.heading")}
      </h3>
      <div className="grid gap-4 sm:grid-cols-[minmax(0,260px)_1fr] sm:items-start">
        <PackageCover
          src={cover.data ?? null}
          alt={t("catalog.package.coverAlt", { title: exam.titleEn })}
          type={null}
          title={exam.titleEn}
          className="rounded-control"
        />
        <div className="grid gap-3">
          <p className="text-meta text-muted">{t("catalog.package.cover.hint")}</p>
          <p className="text-meta text-muted">{t("catalog.examCover.note")}</p>
          <div className="flex flex-wrap gap-2">
            <input
              ref={input}
              type="file"
              accept={COVER_TYPES.join(",")}
              className="sr-only"
              aria-label={hasCover ? t("catalog.package.cover.replace") : t("catalog.package.cover.upload")}
              onChange={(event) => void onChange(event.target.files?.[0])}
              disabled={busy}
            />
            <Button variant="secondary" loading={upload.isPending} disabled={busy} onClick={() => input.current?.click()}>
              {hasCover ? t("catalog.package.cover.replace") : t("catalog.package.cover.upload")}
            </Button>
            {hasCover ? (
              <Button variant="ghost" loading={remove.isPending} disabled={busy} onClick={() => void onRemove()}>
                {t("catalog.package.cover.remove")}
              </Button>
            ) : null}
          </div>
          {error ? (
            <p role="alert" className="text-meta text-danger">
              {error}
            </p>
          ) : null}
        </div>
      </div>
    </section>
  );
}

function mapError(err: unknown, t: (key: string) => string): string {
  const code = err instanceof ApiError ? err.code : null;
  if (code === "FILE_TOO_LARGE") return t("catalog.package.cover.tooLarge");
  if (code === "VALIDATION_FAILED") return t("catalog.package.cover.wrongType");
  return t("catalog.package.cover.failed");
}
