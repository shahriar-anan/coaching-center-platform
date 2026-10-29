"use client";

import Image from "next/image";
import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";

/**
 * The brand: the school's own mark, in a light and a dark version (from the old
 * site's logo files), next to the portal name. Links to the site home, relative,
 * on either surface.
 */
/**
 * `compact` hides the wordmark below 400 px, so the student header fits at large text sizes (the mark and the name stay for screen readers).
 * `rail` is for the staff sidebar, which is dark in both themes: it always uses the mark for dark backgrounds and the rail colours.
 */
export function Logo({
  className,
  href = "/",
  compact = false,
  tone = "surface",
  wordmark = true,
}: {
  className?: string;
  href?: string;
  compact?: boolean;
  tone?: "surface" | "rail";
  /** Off on the collapsed rail: the name is kept for screen readers and hidden on screen. */
  wordmark?: boolean;
}) {
  const { t } = useLocale();
  const rail = tone === "rail";
  return (
    <Link
      href={href}
      aria-label={t("common.brand.home")}
      className={cn(
        "inline-flex min-w-0 items-center gap-2 text-section",
        rail ? "text-sidebar-text" : "text-text",
        className,
      )}
    >
      {rail ? null : <Image src="/brand/logo-light.png" alt="" width={35} height={40} className="h-10 w-auto sm:h-12 dark:hidden" />}
      <Image
        src="/brand/logo-dark.png"
        alt=""
        width={40}
        height={40}
        className={cn("h-10 w-auto sm:h-12", rail ? "block" : "hidden dark:block")}
      />
      <span className={cn("truncate text-base sm:text-section", compact && "max-[399px]:sr-only", !wordmark && "sr-only")}>
        {t("common.brand.first")}
        <span className={rail ? "text-sidebar-accent" : "text-text-accent"}>{t("common.brand.second")}</span>
      </span>
    </Link>
  );
}
