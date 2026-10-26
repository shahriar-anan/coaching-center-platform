"use client";

import { apiUrl } from "@/lib/config";
import { cn } from "@/components/ui/cn";

/** The API path of a package cover as a full URL, or null when the package has no cover. */
export function coverSrc(path: string | null | undefined): string | null {
  return path ? apiUrl(path) : null;
}

/**
 * A package's cover in a 16:9 frame. Until the Master Admin uploads an image, the frame shows
 * the brand gradient with the exam type and the title's first letters, so every card looks finished.
 */
export function PackageCover({
  src,
  alt,
  type,
  title,
  className,
}: {
  src: string | null;
  alt: string;
  /** The exam type the cover stands for; null for a package that holds both types. */
  type: "MCQ" | "WRITTEN" | null;
  title: string;
  className?: string;
}) {
  const initials = title.trim().split(/\s+/).slice(0, 2).map((word) => word.charAt(0)).join("").toUpperCase();
  return (
    <div className={cn("relative aspect-video w-full overflow-hidden bg-band text-on-band", className)}>
      {src ? (
        // The cover comes from the API's own host, so it is not run through the image optimiser.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt={alt} loading="lazy" className="absolute inset-0 size-full object-cover" />
      ) : (
        <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-br from-band via-band to-accent/70">
          <span className="absolute -end-8 -top-8 size-40 rounded-full bg-accent/20" />
          <span className="absolute -bottom-10 -start-6 size-32 rounded-full bg-on-band/10" />
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-1">
            <span className="text-3xl font-extrabold tracking-wide text-accent">{initials || "•"}</span>
            <span className="text-meta font-semibold uppercase tracking-widest text-on-band/80">
              {type === "MCQ" ? "MCQ" : type === "WRITTEN" ? "Written" : "Package"}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
