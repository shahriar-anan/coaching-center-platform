"use client";

import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";

/**
 * A failed load. Shown above the last good content, never instead of it: the
 * caller keeps rendering what it has and places this above it.
 */
export function ErrorState({ onRetry, message }: { onRetry: () => void; message?: string }) {
  const { t } = useLocale();
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-danger/40 bg-danger/5 px-4 py-3"
    >
      <p className="text-body text-text">{message ?? t("common.states.errorBody")}</p>
      <Button variant="secondary" onClick={onRetry}>
        {t("common.actions.retry")}
      </Button>
    </div>
  );
}
