"use client";

import { useRef, useState } from "react";
import { ApiError } from "@/lib/api/errors";
import { useRemoveModuleCover, useStaffModuleCover, useUploadModuleCover, type StaffModule } from "@/lib/api/exam/staff-catalog";
import { useLocale } from "@/i18n/locale-context";
import { useToast } from "@/components/ui/toast";
import { Button } from "@/components/ui/button";
import { PackageCover } from "@/components/ui/package/package-cover";

/** The Master Admin's cover image. The browser checks type and size first; the server checks them again. */
export const COVER_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const COVER_MAX_BYTES = 2 * 1024 * 1024;

export function CoverUploader({ module }: { module: StaffModule }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const input = useRef<HTMLInputElement>(null);
  const upload = useUploadModuleCover(module.id);
  const remove = useRemoveModuleCover(module.id);
  const cover = useStaffModuleCover(module.id, module.coverUrl);
  const [error, setError] = useState<string | null>(null);
  const busy = upload.isPending || remove.isPending;
  const hasCover = Boolean(module.coverUrl);

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
    <section aria-labelledby="module-cover-heading" className="grid max-w-3xl gap-4 rounded-card border border-border bg-card p-5">
      <h2 id="module-cover-heading" className="text-card-title font-semibold">
        {t("catalog.package.cover.heading")}
      </h2>
      <div className="grid gap-4 sm:grid-cols-[minmax(0,260px)_1fr] sm:items-start">
        <PackageCover
          src={cover.data ?? null}
          alt={t("catalog.package.coverAlt", { title: module.titleEn })}
          type={null}
          title={module.titleEn}
          className="rounded-control"
        />
        <div className="grid gap-3">
          <p className="text-meta text-muted">{t("catalog.package.cover.hint")}</p>
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
