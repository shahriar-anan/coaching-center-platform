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
    <div role="alert" className="rounded-control border border-danger bg-danger/10 px-3 py-2 text-meta text-danger">
      <p>{message}</p>
      {apiError?.requestId ? (
        <p className="mt-1 text-meta text-danger">
          {t("common.requestId", { id: apiError.requestId })}
        </p>
      ) : null}
    </div>
  );
}
