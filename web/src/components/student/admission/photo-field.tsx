"use client";

import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { ADMISSION_PHOTO_PATH, useUploadAdmissionPhoto } from "@/lib/api/exam/offline-admission";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";

/**
 * The admission photo: a camera button and a gallery button, a preview, and a new pick to retake it. The picture is sent as
 * soon as it is picked. The server keeps the profile photo, which is this same picture (plan side effect 10).
 */
export function PhotoField({ hasPhoto, error }: { hasPhoto: boolean; error?: string }) {
  const { t } = useLocale();
  const upload = useUploadAdmissionPhoto();
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const saved = useAuthorizedObjectUrl(hasPhoto ? ADMISSION_PHOTO_PATH : null);
  const shown = preview ?? saved;

  // The preview of a pick is released when it is replaced, and when the screen goes.
  useEffect(() => {
    return () => {
      if (preview) {
        URL.revokeObjectURL(preview);
      }
    };
  }, [preview]);

  function pick(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // Clear the input, so picking the same file again still sends a change.
    event.target.value = "";
    if (!file) {
      return;
    }
    if (!file.type.startsWith("image/")) {
      setLocalError(t("admission.photo.wrongType"));
      return;
    }
    setLocalError(null);
    setPreview(URL.createObjectURL(file));
    upload.mutate(file, {
      onError: () => setLocalError(t("admission.photo.failed")),
    });
  }

  const message = localError ?? error ?? null;
  const status = upload.isPending
    ? t("admission.photo.uploading")
    : upload.isSuccess && !localError
      ? t("admission.photo.uploaded")
      : null;

  return (
    <section aria-labelledby="admission-photo-heading" className="grid gap-3 rounded-card border border-border bg-card p-4">
      <h2 id="admission-photo-heading" className="text-label font-semibold text-text">
        {t("admission.section.photo")}
      </h2>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <div className="flex h-44 w-36 shrink-0 items-center justify-center overflow-hidden rounded-control border border-border-control bg-bg">
          {shown ? (
            <img src={shown} alt={t("admission.photo.preview")} className="size-full object-cover" />
          ) : (
            <span className="px-3 text-center text-meta text-muted">{t("admission.photo.missing")}</span>
          )}
        </div>
        <div className="grid flex-1 gap-3">
          <p className="text-meta text-muted">{t("admission.photo.help")}</p>
          <Button type="button" variant="secondary" size="lg" fullWidth onClick={() => cameraRef.current?.click()}>
            {t("admission.photo.takePhoto")}
          </Button>
          <Button type="button" variant="secondary" size="lg" fullWidth onClick={() => galleryRef.current?.click()}>
            {t("admission.photo.chooseGallery")}
          </Button>
          {/* The two pickers are driven by the buttons above, so they are hidden from keyboards and screen readers. */}
          <input
            ref={cameraRef}
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            tabIndex={-1}
            aria-hidden="true"
            onChange={pick}
          />
          <input
            ref={galleryRef}
            type="file"
            accept="image/*"
            className="sr-only"
            tabIndex={-1}
            aria-hidden="true"
            onChange={pick}
          />
          {status ? (
            <p role="status" className="text-meta text-muted">
              {status}
            </p>
          ) : null}
          {message ? (
            <p role="alert" className="text-meta text-danger">
              {message}
            </p>
          ) : null}
        </div>
      </div>
    </section>
  );
}
