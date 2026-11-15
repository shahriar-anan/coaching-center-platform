"use client";

import { useEffect, useRef, useState, useSyncExternalStore, type ChangeEvent } from "react";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { ADMISSION_PHOTO_PATH, useUploadAdmissionPhoto } from "@/lib/api/exam/offline-admission";
import { useAuthorizedObjectUrl } from "@/lib/hooks/use-authorized-object-url";
import { CameraCaptureDialog, inPageCameraSupported } from "./camera-capture";

/** Nothing changes after the first render on the client, so the device check never needs to subscribe. */
function subscribeNever() {
  return () => undefined;
}

/**
 * The admission photo: a camera button and a gallery button, a preview, and a new pick to retake it. The picture is sent as
 * soon as it is picked. The server keeps the profile photo, which is this same picture (plan side effect 10).
 *
 * On a phone, "Take a photo" opens the phone's camera through the capture input. On a laptop, which ignores that hint, it opens
 * an in-page camera instead. Both send the photo to the same upload.
 */
export function PhotoField({
  hasPhoto,
  error,
  photoPath = ADMISSION_PHOTO_PATH,
  onSend,
}: {
  hasPhoto: boolean;
  error?: string;
  /** The authorised path the saved photo is read from, or null when it cannot be read (the public form). */
  photoPath?: string | null;
  /** Sends the picked photo. The signed-in form uses the student's own upload when this is not given. */
  onSend?: (file: File) => Promise<unknown>;
}) {
  const { t } = useLocale();
  const upload = useUploadAdmissionPhoto();
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);
  // On the server there is no window, so the in-page camera is off until the browser answers.
  const inPageCamera = useSyncExternalStore(subscribeNever, inPageCameraSupported, () => false);
  const saved = useAuthorizedObjectUrl(hasPhoto && photoPath ? photoPath : null);
  const shown = preview ?? saved;
  // A photo on file that cannot be read here (no path) is still on file: say so, instead of showing an empty box.
  const onFile = hasPhoto && !shown;

  // The preview of a pick is released when it is replaced, and when the screen goes.
  useEffect(() => {
    return () => {
      if (preview) {
        URL.revokeObjectURL(preview);
      }
    };
  }, [preview]);

  /** Shows the picture and sends it: the same path for a gallery pick and a camera photo. */
  function send(file: File) {
    setLocalError(null);
    setSent(false);
    setSending(true);
    setPreview(URL.createObjectURL(file));
    const sendFile = onSend ?? ((picked: File) => upload.mutateAsync(picked));
    sendFile(file).then(
      () => {
        setSending(false);
        setSent(true);
      },
      () => {
        setSending(false);
        setLocalError(t("admission.photo.failed"));
      },
    );
  }

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
    send(file);
  }

  function takePhoto() {
    if (inPageCamera) {
      setCameraOpen(true);
      return;
    }
    cameraRef.current?.click();
  }

  const message = localError ?? error ?? null;
  const status = sending ? t("admission.photo.uploading") : sent && !localError ? t("admission.photo.uploaded") : null;

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
            <span className="px-3 text-center text-meta text-muted">{t(onFile ? "admission.photo.onFile" : "admission.photo.missing")}</span>
          )}
        </div>
        <div className="grid flex-1 gap-3">
          <p className="text-meta text-muted">{t("admission.photo.help")}</p>
          <Button type="button" variant="secondary" size="lg" fullWidth onClick={takePhoto}>
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
      {cameraOpen ? (
        <CameraCaptureDialog
          onUse={(file) => {
            setCameraOpen(false);
            send(file);
          }}
          onClose={() => setCameraOpen(false)}
          onFallback={() => {
            setCameraOpen(false);
            galleryRef.current?.click();
          }}
        />
      ) : null}
    </section>
  );
}
