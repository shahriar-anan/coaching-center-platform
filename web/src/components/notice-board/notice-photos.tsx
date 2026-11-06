"use client";

import { useState } from "react";
import { cn } from "@/components/ui/cn";
import { useLocale } from "@/i18n/locale-context";
import type { NoticePhoto } from "@/lib/api/exam/site-notices";
import { apiUrl } from "@/lib/config";
import { formatNumber } from "@/lib/format";

/**
 * The photos of one public notice, one at a time. A translucent grey button covers the photo: a tap shows the next photo, and
 * the last photo leads back to the first. A small counter shows which photo is on screen. A single photo has neither the
 * button nor the counter. `size` is "card" on the notice list
 * and "page" on the notice itself.
 */
export function NoticePhotos({
  title,
  photos,
  size = "card",
}: {
  title: string;
  photos: NoticePhoto[];
  size?: "card" | "page";
}) {
  const { t, locale } = useLocale();
  const [index, setIndex] = useState(0);
  const total = photos.length;
  if (total === 0) {
    return null;
  }
  const shown = Math.min(index, total - 1);
  const photo = photos[shown];

  return (
    <div
      className={cn(
        "relative mx-auto w-full overflow-hidden rounded-control border border-border bg-bg",
        size === "page" ? "max-w-sm" : "max-w-xs",
      )}
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        key={photo.id}
        src={apiUrl(photo.url)}
        alt={t("notices.board.photos.alt", { title, n: shown + 1, m: total })}
        loading="lazy"
        className={cn("w-full object-contain", size === "page" ? "h-72 sm:h-80" : "h-60 sm:h-64")}
      />
      {total > 1 ? (
        <button
          type="button"
          onClick={() => setIndex((shown + 1) % total)}
          aria-label={t("notices.board.photos.next")}
          className="absolute inset-0 h-full w-full bg-muted/35 transition-colors hover:bg-muted/45 focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-accent"
        />
      ) : null}
      {total > 1 ? (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute bottom-2 end-2 rounded-full bg-text/70 px-2 py-0.5 text-pill font-bold tabular-nums text-bg"
        >
          {formatNumber(shown + 1, locale)} / {formatNumber(total, locale)}
        </span>
      ) : null}
    </div>
  );
}
