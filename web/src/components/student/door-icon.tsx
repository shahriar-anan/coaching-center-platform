import { cn } from "@/components/ui/cn";

/**
 * The MCQ and written door icons. They are the old site's own icons (its exam.svg for MCQ, and the
 * document icon of its exam list for written), drawn as masks so they take the text colour in both themes.
 */
const ICONS = {
  mcq: "/icons/exam.svg",
  written: "/icons/file-alt.svg",
} as const;

export function DoorIcon({ kind, className = "size-10" }: { kind: keyof typeof ICONS; className?: string }) {
  const src = `url(${ICONS[kind]})`;
  return (
    <span
      aria-hidden="true"
      className={cn("mx-auto block bg-text-accent", className)}
      style={{ maskImage: src, WebkitMaskImage: src, maskRepeat: "no-repeat", maskPosition: "center", maskSize: "contain" }}
    />
  );
}
