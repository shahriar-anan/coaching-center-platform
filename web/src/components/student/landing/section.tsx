import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

/**
 * One landing section: a heading, an optional line under it, and the content
 * in the wide column. `band` turns the section into the navy strip.
 */
export function Section({
  id,
  title,
  subtitle,
  action,
  band = false,
  children,
}: {
  id: string;
  title: string;
  subtitle?: string;
  action?: ReactNode;
  band?: boolean;
  children: ReactNode;
}) {
  return (
    <section
      aria-labelledby={id}
      className={cn("px-4 py-12 md:py-16", band && "bg-band text-on-band")}
    >
      <div className="mx-auto max-w-6xl">
        <div className="mb-8 flex flex-wrap items-end justify-between gap-3">
          <div className="max-w-2xl">
            <h2 id={id} className="text-2xl font-bold md:text-3xl">
              {title}
            </h2>
            {subtitle ? (
              <p className={cn("mt-2 text-body", band ? "text-on-band/80" : "text-muted")}>{subtitle}</p>
            ) : null}
          </div>
          {action}
        </div>
        {children}
      </div>
    </section>
  );
}
