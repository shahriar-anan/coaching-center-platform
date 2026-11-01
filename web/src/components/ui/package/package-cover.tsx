"use client";

import { apiUrl } from "@/lib/config";
import { cn } from "@/components/ui/cn";

/** The API path of a package cover as a full URL, or null when the package has no cover. */
export function coverSrc(path: string | null | undefined): string | null {
  return path ? apiUrl(path) : null;
}

/**
 * A package's cover in a 16:9 frame. Until the Master Admin uploads an image, the frame shows
 * the brand gradient with the title and the exam type, so every card looks finished. The title is clamped to
 * two lines on a phone and three from sm up. The fallback is decorative (aria-hidden): every caller already shows
 * the title as text, so a screen reader reads it once.
 * `band` is the detail page's cover on a phone: 160 px tall, and 16:9 from lg up (Phase 4, S4.3).
 */
export function PackageCover({
  src,
  alt,
  type,
  title,
  className,
  band = false,
}: {
  src: string | null;
  alt: string;
  /** The exam type the cover stands for; null for a package that holds both types. */
  type: "MCQ" | "WRITTEN" | null;
  title: string;
  className?: string;
  band?: boolean;
}) {
  const name = title.trim();
  return (
    <div className={cn("relative w-full overflow-hidden bg-band text-on-band", band ? "h-40 lg:aspect-video lg:h-auto" : "aspect-video", className)}>
      {src ? (
        // The cover comes from the API's own host, so it is not run through the image optimiser.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt={alt} loading="lazy" className="absolute inset-0 size-full object-cover" />
      ) : (
        <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-br from-band via-band to-accent/70">
          <span className="absolute -end-8 -top-8 size-40 rounded-full bg-accent/20" />
          <span className="absolute -bottom-10 -start-6 size-32 rounded-full bg-on-band/10" />
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 px-4 text-center">
            {name ? (
              <span className="line-clamp-2 max-w-full break-words text-card-title text-on-band sm:line-clamp-3">{name}</span>
            ) : null}
            <span className="text-meta font-semibold uppercase tracking-widest text-on-band/80">
              {type === "MCQ" ? "MCQ" : type === "WRITTEN" ? "Written" : "Package"}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
