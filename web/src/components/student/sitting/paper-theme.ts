import type { CSSProperties } from "react";

/**
 * The look of the printed paper, in one place. A printed exam paper is white with black type whatever the
 * app theme is (dark mode included), so the paper never reads a theme token. paper-layer.tsx sets these
 * values as CSS variables on the layer, and the paper components use the classes below. No gold, navy,
 * red or green belongs in the paper.
 */

/** The paper's colours: white, black, and two greys for secondary text and rules. */
export const PAPER_COLOR = {
  paper: "#ffffff",
  ink: "#000000",
  muted: "#404040",
  rule: "#d4d4d4",
} as const;

/** English papers set the stems and options in a serif face, so the page reads as print. Bangla keeps its own face. */
export const PAPER_SERIF = 'Georgia, "Times New Roman", serif';

/** The paper's own type scale. The question text size is the reader's choice (paper-text-size.tsx). */
export const PAPER_TYPE = {
  title: "26px",
  body: "16px",
  meta: "14px",
} as const;

/** The CSS variables the paper layer sets. */
export const paperVars = {
  "--paper-bg": PAPER_COLOR.paper,
  "--paper-ink": PAPER_COLOR.ink,
  "--paper-muted": PAPER_COLOR.muted,
  "--paper-rule": PAPER_COLOR.rule,
  "--paper-serif": PAPER_SERIF,
  "--paper-title": PAPER_TYPE.title,
  "--paper-body": PAPER_TYPE.body,
  "--paper-meta": PAPER_TYPE.meta,
} as CSSProperties;

/** Tailwind classes that read the variables above. Inside the paper, use these and never a theme token. */
export const paperClass = {
  paper: "bg-[color:var(--paper-bg)]",
  ink: "text-[color:var(--paper-ink)]",
  muted: "text-[color:var(--paper-muted)]",
  rule: "border-[color:var(--paper-rule)]",
  line: "border-[color:var(--paper-ink)]",
  /** A filled mark: black, with white on it. */
  fill: "bg-[color:var(--paper-ink)]",
  onFill: "text-[color:var(--paper-bg)]",
  title: "text-[length:var(--paper-title)]",
  body: "text-[length:var(--paper-body)]",
  meta: "text-[length:var(--paper-meta)]",
  serif: "font-[family-name:var(--paper-serif)]",
} as const;

/** The face of a stem or an option: the Bangla face for a Bangla paper, the serif stack for an English one. */
export function paperFaceClass(language: "BN" | "EN"): string {
  return language === "BN" ? "font-bn" : paperClass.serif;
}
