"use client";

import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";

/**
 * The brand: text and an emoji, from the client mockup. This is the one place
 * to swap in a logo file later. Links to the site home, relative, on either surface.
 */
export function Logo({ className, href = "/" }: { className?: string; href?: string }) {
  const { t } = useLocale();
  return (
    <Link
      href={href}
      aria-label={t("common.brand.home")}
      className={cn("inline-flex items-center gap-1.5 text-[19px] font-bold text-text", className)}
    >
      <span aria-hidden="true">⚖️</span>
      <span>
        {t("common.brand.first")}
        <span className="text-text-accent">{t("common.brand.second")}</span>
      </span>
    </Link>
  );
}
