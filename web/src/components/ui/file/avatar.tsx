"use client";

import { useState } from "react";

export type AvatarProps = {
  /** A loadable image address, for example an object URL the caller made. Null shows initials. */
  src: string | null;
  /** The person's name. Its first letters are the initials, and it is the accessible name by default. */
  name: string;
  /** Width and height in CSS pixels. */
  size: number;
  /** The accessible name, when it should differ from `name`. */
  alt?: string;
};

/** Up to two letters: the first letter of the first two words of the name. */
export function initialsOf(name: string): string {
  const letters = name
    .trim()
    .split(/\s+/)
    .filter((word) => word.length > 0)
    .slice(0, 2)
    .map((word) => Array.from(word)[0] ?? "")
    .join("");
  return letters.toLocaleUpperCase() || "?";
}

/**
 * A round picture. It shows initials when there is no `src`, or when the image fails to load, so it never shows a
 * broken image. Pure presentation: it does not fetch anything.
 */
export function Avatar({ src, name, size, alt }: AvatarProps) {
  // The failed address, not a flag: a new `src` gets its own attempt.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const showImage = src !== null && src !== failedSrc;
  const label = alt ?? name;

  return (
    <span
      role="img"
      aria-label={label}
      className="inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full"
      style={{ width: size, height: size }}
    >
      {showImage ? (
        // The picture is decoded from a blob or an authorised path, so next/image cannot optimise it.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={src}
          alt=""
          width={size}
          height={size}
          decoding="async"
          onError={() => setFailedSrc(src)}
          className="size-full rounded-full object-cover"
        />
      ) : (
        <span
          aria-hidden="true"
          className="flex size-full items-center justify-center rounded-full bg-accent/15 font-semibold text-text-accent"
          style={{ fontSize: Math.max(10, Math.round(size * 0.4)) }}
        >
          {initialsOf(name)}
        </span>
      )}
    </span>
  );
}
