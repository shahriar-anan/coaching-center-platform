"use client";

import { ApiError, errorMessageKey } from "@/lib/api/errors";
import { useLocale } from "@/i18n/locale-context";

type Props = {
  error: unknown;
};

export function FormError({ error }: Props) {
  const { t } = useLocale();
  if (!error) {
    return null;
  }
  const apiError = error instanceof ApiError ? error : null;
  const message = apiError
    ? t(errorMessageKey(apiError.code))
    : error instanceof Error
      ? error.message
      : t("errors.generic");

  return (
    <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-900">
      <p>{message}</p>
      {apiError?.requestId ? (
        <p className="mt-1 text-xs text-red-800">
          {t("common.requestId", { id: apiError.requestId })}
        </p>
      ) : null}
    </div>
  );
}
