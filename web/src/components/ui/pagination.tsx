"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { formatNumber } from "@/lib/format";

/** Server pages are 0-based in the API; the screen shows them 1-based. */
export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  const { t, locale } = useLocale();
  if (totalPages <= 1) {
    return null;
  }
  const shown = (n: number) => formatNumber(n + 1, locale);
  return (
    <nav aria-label={t("common.pagination.label")} className="flex items-center justify-between gap-3">
      <Button
        variant="secondary"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
        aria-label={t("common.pagination.prev")}
      >
        <ChevronLeft aria-hidden="true" className="size-4" />
        <span className="hidden sm:inline">{t("common.pagination.prev")}</span>
      </Button>
      <p className="text-meta text-muted tabular-nums" aria-live="polite">
        {t("common.pagination.of", { page: shown(page), total: formatNumber(totalPages, locale) })}
      </p>
      <Button
        variant="secondary"
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
        aria-label={t("common.pagination.next")}
      >
        <span className="hidden sm:inline">{t("common.pagination.next")}</span>
        <ChevronRight aria-hidden="true" className="size-4" />
      </Button>
    </nav>
  );
}
