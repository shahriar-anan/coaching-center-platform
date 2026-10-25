"use client";

import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/i18n/locale-context";
import { studentHome } from "@/lib/config";

export default function NotFound() {
  const { t } = useLocale();
  return (
    <main className="mx-auto w-full max-w-[760px] px-4 py-16">
      <EmptyState
        title={t("common.notFound.title")}
        body={t("common.notFound.body")}
        action={<Button href={studentHome}>{t("common.guard.goHome")}</Button>}
      />
    </main>
  );
}
