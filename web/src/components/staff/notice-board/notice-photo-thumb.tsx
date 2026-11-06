"use client";

import { useEffect, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { apiBlob } from "@/lib/api/client";

/**
 * A staff photo thumbnail. The staff route needs the sign-in, which a plain image tag cannot send, so the bytes are fetched
 * with the token and shown through an object URL. The object URL is released when the thumbnail changes or goes.
 */
export function NoticePhotoThumb({ path, alt }: { path: string; alt: string }) {
  const { t } = useLocale();
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;
    apiBlob(path)
      .then((blob) => {
        if (cancelled) {
          return;
        }
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      })
      .catch(() => {
        if (!cancelled) {
          setFailed(true);
        }
      });
    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [path]);

  if (failed) {
    return <span className="flex size-full items-center justify-center p-1 text-center text-meta text-muted">{t("notices.board.staff.attachments.thumbFailed")}</span>;
  }
  if (!src) {
    return <span aria-hidden="true" className="block size-full animate-pulse bg-border/50" />;
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={alt} className="size-full object-contain" />;
}
