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
      className={cn("inline-flex items-center gap-2 text-section text-text", className)}
    >
      <Image src="/brand/logo-light.png" alt="" width={35} height={40} className="h-10 w-auto dark:hidden" />
      <Image src="/brand/logo-dark.png" alt="" width={40} height={40} className="hidden h-10 w-auto dark:block" />
      <span>
        {t("common.brand.first")}
        <span className="text-text-accent">{t("common.brand.second")}</span>
      </span>
    </Link>
  );
}
