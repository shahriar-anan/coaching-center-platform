import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/components/ui/cn";

type IconButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> & {
  /** Accessible name; required because the button has no text. */
  label: string;
  icon: ReactNode;
};

export function IconButton({ label, icon, className, ...rest }: IconButtonProps) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      {...rest}
      className={cn(
        "inline-flex size-11 items-center justify-center rounded-control text-text transition-colors duration-150 hover:bg-bg active:bg-border disabled:cursor-not-allowed disabled:opacity-60",
        className,
      )}
    >
      {icon}
    </button>
  );
}
