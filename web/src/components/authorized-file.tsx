"use client";

import { useEffect, useState } from "react";
import { apiBaseUrl } from "@/lib/config";
import { getAccessToken } from "@/lib/auth/token-store";

export function AuthorizedFile({
  fileId,
  alt,
  className,
}: {
  fileId: string;
  alt: string;
  className?: string;
}) {
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;
    const token = getAccessToken();
    if (!token) {
      return;
    }
    void fetch(`${apiBaseUrl}/api/v1/files/${fileId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((response) => (response.ok ? response.blob() : null))
      .then((blob) => {
        if (!blob) {
          return;
        }
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      });
    return () => {
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [fileId]);

  if (!src) {
    return <div className={className} />;
  }
  return <img src={src} alt={alt} className={className} />;
}
