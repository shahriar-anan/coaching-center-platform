import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

/** Heights: sm 40 px (staff), md 44 px (default), lg 48 px (student primary). */
export type ButtonSize = "sm" | "md" | "lg";

type SharedProps = {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  fullWidth?: boolean;
  className?: string;
  children?: ReactNode;
};

export type ButtonProps = SharedProps &
  Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children" | "className"> & {
    href?: undefined;
  };

export type LinkButtonProps = SharedProps & {
  href: string;
  prefetch?: boolean;
};

const base =
  "relative inline-flex items-center justify-center gap-2 rounded-control text-label transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-60 aria-disabled:pointer-events-none aria-disabled:opacity-60";

const sizes: Record<ButtonSize, string> = {
  sm: "min-h-10 px-4",
  md: "min-h-11 px-5",
  lg: "min-h-12 px-6",
};

const variants: Record<ButtonVariant, string> = {
  primary: "bg-action text-on-action hover:opacity-90 active:opacity-80",
  secondary: "border border-border bg-card text-text hover:bg-bg active:bg-border",
  ghost: "text-text hover:bg-bg active:bg-border",
  danger: "text-danger hover:underline underline-offset-4 active:opacity-80",
};

function omit<T extends object, K extends keyof T>(source: T, keys: K[]): Omit<T, K> {
  const copy = { ...source };
  for (const key of keys) {
    delete copy[key];
  }
  return copy;
}

/** Drawn over the label, so the button keeps its width while loading. */
function Spinner() {
  return (
    <span
      aria-hidden="true"
      className="absolute start-1/2 top-1/2 inline-block size-4 -translate-x-1/2 -translate-y-1/2 animate-spin rounded-full border-2 border-current border-t-transparent"
    />
  );
}

/**
 * `variant` sets the look; `loading` disables the button and shows progress.
 * Pass `href` to render a link with the same look.
 */
export function Button(props: ButtonProps | LinkButtonProps) {
  const { variant = "primary", size = "md", loading = false, fullWidth = false, className, children } = props;
  const classes = cn(base, sizes[size], variants[variant], fullWidth && "w-full", className);

  if (props.href !== undefined) {
    return (
      <Link
        href={props.href}
        prefetch={props.prefetch}
        className={classes}
        aria-disabled={loading || undefined}
      >
        <span className={cn("inline-flex items-center justify-center gap-2", loading && "invisible")}>{children}</span>
        {loading ? <Spinner /> : null}
      </Link>
    );
  }

  const rest = omit(props, ["variant", "size", "loading", "fullWidth", "className", "children", "href"]);
  return (
    <button
      type="button"
      {...rest}
      disabled={rest.disabled || loading}
      aria-busy={loading || undefined}
      className={classes}
    >
      <span className={cn("inline-flex items-center justify-center gap-2", loading && "invisible")}>{children}</span>
      {loading ? <Spinner /> : null}
    </button>
  );
}
