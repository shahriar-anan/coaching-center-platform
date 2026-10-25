import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/components/ui/cn";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

type SharedProps = {
  variant?: ButtonVariant;
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
  "inline-flex min-h-11 items-center justify-center gap-2 rounded-control px-5 text-[15px] font-semibold transition-colors duration-150 disabled:cursor-not-allowed disabled:opacity-60";

const variants: Record<ButtonVariant, string> = {
  primary: "bg-action text-on-action hover:opacity-90",
  secondary: "border border-border bg-card text-text hover:bg-bg",
  ghost: "text-text hover:bg-bg",
  danger: "text-danger hover:underline underline-offset-4",
};

function omit<T extends object, K extends keyof T>(source: T, keys: K[]): Omit<T, K> {
  const copy = { ...source };
  for (const key of keys) {
    delete copy[key];
  }
  return copy;
}

function Spinner() {
  return (
    <span
      aria-hidden="true"
      className="inline-block size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
    />
  );
}

/**
 * `variant` sets the look; `loading` disables the button and shows progress.
 * Pass `href` to render a link with the same look.
 */
export function Button(props: ButtonProps | LinkButtonProps) {
  const { variant = "primary", loading = false, fullWidth = false, className, children } = props;
  const classes = cn(base, variants[variant], fullWidth && "w-full", className);

  if (props.href !== undefined) {
    return (
      <Link
        href={props.href}
        prefetch={props.prefetch}
        className={classes}
        aria-disabled={loading || undefined}
      >
        {loading ? <Spinner /> : null}
        {children}
      </Link>
    );
  }

  const rest = omit(props, ["variant", "loading", "fullWidth", "className", "children", "href"]);
  return (
    <button
      type="button"
      {...rest}
      disabled={rest.disabled || loading}
      aria-busy={loading || undefined}
      className={classes}
    >
      {loading ? <Spinner /> : null}
      {children}
    </button>
  );
}
