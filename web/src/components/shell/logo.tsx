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
/** `compact` hides the wordmark below 400 px, so the student header fits at large text sizes (the mark and the name stay for screen readers). */
export function Logo({ className, href = "/", compact = false }: { className?: string; href?: string; compact?: boolean }) {
  const { t } = useLocale();
  return (
    <Link
      href={href}
      aria-label={t("common.brand.home")}
      className={cn("inline-flex min-w-0 items-center gap-2 text-section text-text", className)}
    >
      <Image src="/brand/logo-light.png" alt="" width={35} height={40} className="h-10 w-auto sm:h-12 dark:hidden" />
      <Image src="/brand/logo-dark.png" alt="" width={40} height={40} className="hidden h-10 w-auto sm:h-12 dark:block" />
      <span className={cn("truncate text-base sm:text-section", compact && "max-[399px]:sr-only")}>
        {t("common.brand.first")}
        <span className="text-text-accent">{t("common.brand.second")}</span>
      </span>
    </Link>
  );
}
