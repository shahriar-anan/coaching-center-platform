import type { ReactNode } from "react";
import { cn } from "@/components/ui/cn";

/**
 * One landing section: a heading, an optional line under it, and the content
 * in the wide column. `band` turns the section into the navy strip, and
 * `center` centres the heading as the old site does, with a short gold bar
 * under it. The page alternates two flat tones with no lines between them: `plain` is the page colour and `surface` a
 * white band (a faint navy lift on the dark page). A centred section puts its action ("See all") at the right edge from md up.
 */
export function Section({
  id,
  title,
  subtitle,
  action,
  band = false,
  center = false,
  tone = "plain",
  children,
}: {
  id: string;
  title: string;
  subtitle?: string;
  action?: ReactNode;
  band?: boolean;
  center?: boolean;
  tone?: "plain" | "surface";
  children: ReactNode;
}) {
  return (
    <section
      aria-labelledby={id}
      className={cn(
        "px-4 py-10 md:py-12",
        band ? "bg-band text-on-band" : tone === "surface" && "bg-card dark:bg-band/30",
      )}
    >
      <div className="mx-auto max-w-6xl">
        <div
          className={cn(
            "relative mb-6 flex flex-wrap gap-3",
            center ? "flex-col items-center text-center" : "items-end justify-between",
          )}
        >
          <div className="max-w-2xl">
            <h2 id={id} className="text-xl font-bold md:text-2xl">
              {title}
            </h2>
            {center ? <span aria-hidden="true" className="mx-auto mt-3 block h-1 w-12 rounded-full bg-accent" /> : null}
            {subtitle ? (
              <p className={cn("mt-2 text-body", band ? "text-on-band/80" : "text-muted")}>{subtitle}</p>
            ) : null}
          </div>
          {action ? <div className={cn(center && "md:absolute md:bottom-1 md:end-0")}>{action}</div> : null}
        </div>
        {children}
      </div>
    </section>
  );
}
