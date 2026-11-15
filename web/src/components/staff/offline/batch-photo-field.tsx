"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";

/** The batch photo as the dialog holds it until Save: nothing new, a new file, or the stored photo removed. */
export type BatchPhotoChange = { kind: "none" } | { kind: "set"; file: File } | { kind: "remove" };

/** JPEG, PNG or WebP, up to 5 MB (plan 3.1). The browser checks them first; the server checks them again. */
export const BATCH_PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const BATCH_PHOTO_MAX_BYTES = 5 * 1024 * 1024;

/**
 * The batch photo control, the same pattern as the profile photo: choose a file, check the preview, remove asks nothing extra here.
 * The file is sent when the batch is saved, so the dialog owns the choice. The stored photo is read with the staff token.
 */
export function BatchPhotoField({
  storedPath,
  hasPhoto,
  change,
  onChange,
  error,
}: {
  /** The authorised photo path of a saved batch, or null for a new batch. */
  storedPath: string | null;
  hasPhoto: boolean;
  change: BatchPhotoChange;
  onChange: (next: BatchPhotoChange) => void;
  error?: string | null;
}) {
  const { t } = useLocale();
  const input = useRef<HTMLInputElement>(null);
  const stored = useAuthorizedObjectUrl(hasPhoto && change.kind !== "remove" ? storedPath : null);
  const [problem, setProblem] = useState<string | null>(null);

  // The preview of a chosen file, released when the choice changes and when the dialog closes.
  const preview = useMemo(() => (change.kind === "set" ? URL.createObjectURL(change.file) : null), [change]);
  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [preview]);

  function choose(file: File | undefined) {
    if (!file) return;
    setProblem(null);
    if (!BATCH_PHOTO_TYPES.includes(file.type)) {
      setProblem(t("staffOffline.batches.catalogue.photoWrongType"));
      return;
    }
    if (file.size > BATCH_PHOTO_MAX_BYTES) {
      setProblem(t("staffOffline.batches.catalogue.photoTooLarge"));
      return;
    }
    onChange({ kind: "set", file });
  }

  const shown = change.kind === "set" ? preview : change.kind === "remove" ? null : stored;
  const hasAfter = change.kind === "set" || (hasPhoto && change.kind !== "remove");
  const message = problem ?? error ?? null;

  return (
    <div className="grid gap-3 sm:grid-cols-[auto_minmax(0,1fr)] sm:items-start">
      <div className="flex aspect-video w-full items-center justify-center overflow-hidden rounded-control border border-border-control bg-bg sm:w-56">
        {shown ? (
          // The preview is an object URL, and the stored photo is an object URL too, so neither goes through the image optimiser.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={shown} alt={change.kind === "set" ? t("staffOffline.batches.catalogue.photoPreview") : ""} className="size-full object-cover" />
        ) : (
          <span className="px-3 text-center text-meta text-muted">{t("staffOffline.batches.catalogue.photoMissing")}</span>
        )}
      </div>
      <div className="grid min-w-0 gap-2">
        <span className="text-meta text-muted">{t("staffOffline.batches.catalogue.photo")}</span>
        <div className="flex flex-wrap gap-2">
          <input
            ref={input}
            type="file"
            accept={BATCH_PHOTO_TYPES.join(",")}
            className="hidden"
            onChange={(event) => {
              choose(event.target.files?.[0]);
              // Cleared so the same file can be chosen again after a change of mind.
              event.target.value = "";
            }}
          />
          <Button type="button" variant="secondary" size="sm" onClick={() => input.current?.click()}>
            {hasAfter ? t("staffOffline.batches.catalogue.photoChange") : t("staffOffline.batches.catalogue.photoChoose")}
          </Button>
          {change.kind === "set" ? (
            <Button type="button" variant="ghost" size="sm" onClick={() => onChange({ kind: "none" })}>
              {t("staffOffline.batches.catalogue.photoUndo")}
            </Button>
          ) : hasPhoto && change.kind !== "remove" ? (
            <Button type="button" variant="ghost" size="sm" onClick={() => onChange({ kind: "remove" })}>
              {t("staffOffline.batches.catalogue.photoRemove")}
            </Button>
          ) : null}
        </div>
        <p className="text-meta text-muted">{t("staffOffline.batches.catalogue.photoHelp")}</p>
        {message ? (
          <p role="alert" className="text-meta text-danger">
            {message}
          </p>
        ) : null}
      </div>
    </div>
  );
}
