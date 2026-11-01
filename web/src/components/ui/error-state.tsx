"use client";

import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { isNetworkError, isNotFoundError, isServerError } from "@/lib/api/errors";

/**
 * A failed load. Shown above the last good content, never instead of it: the
 * caller keeps rendering what it has and places this above it.
 *
 * The text follows the error when the caller passes it: a connection line only for no response, a server line for a
 * 5xx, a not-found line for a missing item, and a generic line for anything else. Without an error the connection
 * line is shown, as it always was.
 */
export function ErrorState({ onRetry, message, error }: { onRetry: () => void; message?: string; error?: unknown }) {
  const { t } = useLocale();
  let text = t("common.states.errorBody");
  if (error != null && !isNetworkError(error)) {
    if (isServerError(error)) text = t("common.states.serverError");
    else if (isNotFoundError(error)) text = t("common.notFound.generic");
    else text = t("common.error.body");
  }
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-control border border-danger/40 bg-danger/5 px-4 py-3"
    >
      <p className="text-body text-text">{message ?? text}</p>
      <Button variant="secondary" onClick={onRetry}>
        {t("common.actions.retry")}
      </Button>
    </div>
  );
}
