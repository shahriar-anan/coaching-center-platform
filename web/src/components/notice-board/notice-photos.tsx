"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { useLocale } from "@/i18n/locale-context";
import type { NoticePhoto } from "@/lib/api/exam/site-notices";
import { apiUrl } from "@/lib/config";

/**
 * The photos of one public notice. One photo fills the width (at most 70 vh, the whole picture visible). Two or more form a
 * grid: two columns, three from `md`. Tapping a photo opens it full size in a dialog with previous and next; Esc closes it.
 */
export function NoticePhotos({ title, photos }: { title: string; photos: NoticePhoto[] }) {
  const { t } = useLocale();
  const [openIndex, setOpenIndex] = useState<number | null>(null);
  const total = photos.length;
  const single = total === 1;

  const altFor = (index: number) => t("notices.board.photos.alt", { title, n: index + 1, m: total });

  return (
    <section aria-label={t("notices.board.photos.label")} className={single ? "grid" : "grid grid-cols-2 gap-2 md:grid-cols-3"}>
      {photos.map((photo, index) => (
        <button
          key={photo.id}
          type="button"
          onClick={() => setOpenIndex(index)}
          aria-label={t("notices.board.photos.open", { n: index + 1, m: total })}
          className="flex min-h-11 overflow-hidden rounded-control border border-border bg-bg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={apiUrl(photo.url)}
            alt={altFor(index)}
            loading="lazy"
            className={single ? "max-h-[70vh] w-full object-contain" : "aspect-[4/3] w-full object-contain"}
          />
        </button>
      ))}
      <Dialog
        open={openIndex !== null}
        onOpenChange={(open) => {
          if (!open) setOpenIndex(null);
        }}
        title={openIndex === null ? title : t("notices.board.photos.counter", { n: openIndex + 1, m: total })}
        className="sm:max-w-3xl"
      >
        {openIndex !== null ? (
          <div className="grid gap-3">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={apiUrl(photos[openIndex].url)}
              alt={altFor(openIndex)}
              className="max-h-[70vh] w-full rounded-control bg-bg object-contain"
            />
            <div className="flex items-center justify-between gap-2">
              <Button variant="secondary" disabled={openIndex === 0} onClick={() => setOpenIndex(openIndex - 1)}>
                {t("notices.board.photos.previous")}
              </Button>
              <Button variant="secondary" disabled={openIndex >= total - 1} onClick={() => setOpenIndex(openIndex + 1)}>
                {t("notices.board.photos.next")}
              </Button>
            </div>
          </div>
        ) : null}
      </Dialog>
    </section>
  );
}
