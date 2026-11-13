"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { useLocale } from "@/i18n/locale-context";

/** The quality of the JPEG the camera makes. */
const JPEG_QUALITY = 0.9;

/**
 * Whether this device should use the in-page camera. A laptop or desktop (a fine pointer, not touch-first) ignores the file picker's
 * capture hint, so it gets a camera dialog instead. A phone keeps the capture input, which opens the phone's own camera.
 */
export function inPageCameraSupported(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }
  const touchFirst = window.matchMedia("(pointer: coarse)").matches;
  const finePointer = window.matchMedia("(pointer: fine)").matches;
  const devices: MediaDevices | undefined = typeof navigator === "undefined" ? undefined : navigator.mediaDevices;
  return !touchFirst && finePointer && typeof devices?.getUserMedia === "function";
}

type Phase = "starting" | "live" | "failed";

/**
 * A live camera preview for a laptop (plan 6.2). The stream runs while the dialog is open, and every track stops when it closes, whether
 * the photo was used or not. The photo is a JPEG that goes to the same upload as a gallery pick. If the camera is refused or missing,
 * the dialog says so and offers the file picker instead.
 */
export function CameraCaptureDialog({
  onUse,
  onClose,
  onFallback,
}: {
  onUse: (file: File) => void;
  onClose: () => void;
  /** Opens the file picker: the way out when the camera cannot be used. */
  onFallback: () => void;
}) {
  const { t } = useLocale();
  const videoRef = useRef<HTMLVideoElement>(null);
  const [phase, setPhase] = useState<Phase>("starting");
  const [ready, setReady] = useState(false);
  const [photo, setPhoto] = useState<{ blob: Blob; url: string } | null>(null);
  const [captureFailed, setCaptureFailed] = useState(false);

  // Asks for the camera once, when the dialog opens. A stream that arrives after the dialog closed is stopped at once.
  useEffect(() => {
    let stream: MediaStream | null = null;
    let cancelled = false;
    Promise.resolve()
      .then(() => navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" } }))
      .then((media) => {
        if (cancelled) {
          media.getTracks().forEach((track) => track.stop());
          return;
        }
        stream = media;
        if (videoRef.current) {
          videoRef.current.srcObject = media;
        }
        setPhase("live");
      })
      .catch(() => {
        if (!cancelled) {
          setPhase("failed");
        }
      });
    return () => {
      cancelled = true;
      stream?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  // The preview of a photo is released when it is replaced, and when the dialog goes.
  useEffect(() => {
    return () => {
      if (photo) {
        URL.revokeObjectURL(photo.url);
      }
    };
  }, [photo]);

  function capture() {
    const video = videoRef.current;
    if (!video || !ready) {
      return;
    }
    setCaptureFailed(false);
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const context = canvas.getContext("2d");
    if (!context) {
      setCaptureFailed(true);
      return;
    }
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob((blob) => {
      if (!blob) {
        setCaptureFailed(true);
        return;
      }
      setPhoto({ blob, url: URL.createObjectURL(blob) });
    }, "image/jpeg", JPEG_QUALITY);
  }

  function use() {
    if (!photo) {
      return;
    }
    onUse(new File([photo.blob], "admission-photo.jpg", { type: "image/jpeg" }));
  }

  const footer =
    phase === "failed" ? (
      <>
        <Button variant="ghost" onClick={onClose}>
          {t("common.actions.cancel")}
        </Button>
        <Button onClick={onFallback}>{t("admission.photo.chooseInstead")}</Button>
      </>
    ) : photo ? (
      <>
        <Button variant="secondary" onClick={() => setPhoto(null)}>
          {t("admission.photo.retake")}
        </Button>
        <Button onClick={use}>{t("admission.photo.usePhoto")}</Button>
      </>
    ) : (
      <>
        <Button variant="ghost" onClick={onClose}>
          {t("common.actions.cancel")}
        </Button>
        <Button onClick={capture} disabled={phase !== "live" || !ready}>
          {t("admission.photo.capture")}
        </Button>
      </>
    );

  return (
    <Dialog
      open
      onOpenChange={(next) => {
        if (!next) {
          onClose();
        }
      }}
      title={t("admission.photo.takePhoto")}
      description={t("admission.photo.cameraBody")}
      footer={footer}
    >
      <div className="grid gap-3">
        <div className="relative aspect-[3/4] w-full overflow-hidden rounded-card bg-bg">
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            onLoadedMetadata={() => setReady(true)}
            hidden={photo !== null}
            className="size-full object-cover"
          />
          {photo ? <img src={photo.url} alt={t("admission.photo.preview")} className="size-full object-cover" /> : null}
          {phase === "starting" ? (
            <p className="absolute inset-0 flex items-center justify-center px-4 text-center text-meta text-muted">
              {t("admission.photo.cameraStarting")}
            </p>
          ) : null}
        </div>
        {phase === "failed" ? (
          <p role="alert" className="text-meta text-danger">
            {t("admission.photo.cameraFailed")}
          </p>
        ) : null}
        {captureFailed ? (
          <p role="alert" className="text-meta text-danger">
            {t("admission.photo.captureFailed")}
          </p>
        ) : null}
      </div>
    </Dialog>
  );
}
