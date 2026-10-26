"use client";

import { useEffect } from "react";
import { studentHome } from "@/lib/config";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { useLocale } from "@/i18n/locale-context";

export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const { t } = useLocale();
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <main className="mx-auto w-full max-w-[760px] px-4 py-16">
      <EmptyState
        title={t("common.error.title")}
        body={t("common.error.body")}
        action={
          <div className="flex flex-wrap justify-center gap-3">
            <Button onClick={reset}>{t("common.actions.retry")}</Button>
            <Button variant="secondary" href={studentHome}>{t("common.guard.goHome")}</Button>
          </div>
        }
      />
    </main>
  );
}
