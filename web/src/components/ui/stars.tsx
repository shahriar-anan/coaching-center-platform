import { Star } from "lucide-react";
import { cn } from "@/components/ui/cn";

/** The filled star uses the accent colour; the empty one is an outline in the muted text colour. */
const FILLED = "fill-accent text-accent";
const EMPTY = "text-muted";

/**
 * Five stars for a rating, read-only. The stars are decoration: the screen reader hears `label` once for the whole row. A
 * fractional value (an average) fills the nearest whole number of stars.
 */
export function Stars({
  value,
  label,
  size = "size-4",
  className,
}: {
  value: number;
  label: string;
  /** The size class of one star. */
  size?: string;
  className?: string;
}) {
  const filled = Math.round(value);
  return (
    <span role="img" aria-label={label} className={cn("inline-flex items-center gap-0.5", className)}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Star key={n} aria-hidden="true" className={cn(size, n <= filled ? FILLED : EMPTY)} />
      ))}
    </span>
  );
}

/**
 * The rating input: five buttons, one per star, each named by `labelFor` (for example "3 stars"). A button is a real button, so
 * the keyboard and the screen reader work without extra code. Each is 44 px square. A tap calls `onChange` with the number.
 */
export function StarInput({
  value,
  onChange,
  labelFor,
  label,
  disabled = false,
}: {
  /** The rating to show as filled, or null for none yet. */
  value: number | null;
  onChange: (rating: number) => void;
  labelFor: (star: number) => string;
  /** The name of the group of five buttons. */
  label: string;
  disabled?: boolean;
}) {
  return (
    <div role="group" aria-label={label} className="flex items-center gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          aria-label={labelFor(n)}
          disabled={disabled}
          onClick={() => onChange(n)}
          className="inline-flex size-11 items-center justify-center rounded-control focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Star aria-hidden="true" className={cn("size-7", value !== null && n <= value ? FILLED : EMPTY)} />
        </button>
      ))}
    </div>
  );
}
