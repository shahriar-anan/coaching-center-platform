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
export function Logo({ className, href = "/" }: { className?: string; href?: string }) {
  const { t } = useLocale();
  return (
    <Link
      href={href}
      aria-label={t("common.brand.home")}
      className={cn("inline-flex min-w-0 items-center gap-2 text-section text-text", className)}
    >
      <Image src="/brand/logo-light.svg" alt="" width={800} height={771} className="h-9 w-auto sm:h-12 dark:hidden" />
      <Image src="/brand/logo-dark.svg" alt="" width={800} height={771} className="hidden h-9 w-auto sm:h-12 dark:block" />
      <span className="truncate text-base sm:text-section">
        {t("common.brand.first")}
        <span className="text-text-accent">{t("common.brand.second")}</span>
      </span>
    </Link>
  );
}
