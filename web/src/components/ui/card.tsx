import Link from "next/link";
import type { ElementType, ReactNode } from "react";
import { ChevronRight } from "lucide-react";
import { cn } from "@/components/ui/cn";

type CardProps = {
  href?: string;
  as?: ElementType;
  padding?: "md" | "lg";
  className?: string;
  children: ReactNode;
};

// The shadow is a token: the admin light theme sets it, the student site has none.
const surface = "block rounded-card border border-border bg-card text-text shadow-[var(--card-shadow)]";

/** A white card with a 16 px radius. With `href` it becomes a link card with a chevron. */
export function Card({ href, as: Tag = "div", padding = "md", className, children }: CardProps) {
  const pad = padding === "lg" ? "p-6" : "p-4";
  if (href) {
    return (
      <Link
        href={href}
        prefetch
        className={cn(
          surface,
          pad,
          "flex items-center justify-between gap-3 transition-colors hover:border-accent",
          className,
        )}
      >
        <span className="min-w-0 flex-1">{children}</span>
        <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-muted" />
      </Link>
    );
  }
  return <Tag className={cn(surface, pad, className)}>{children}</Tag>;
}
