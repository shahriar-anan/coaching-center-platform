"use client";

import { useLocale } from "@/i18n/locale-context";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";

export type LegalKey = "about" | "terms" | "privacy" | "refund";

/** A readable long-form page with the placeholder banner until the client's text arrives. */
export function LegalPage({ page }: { page: LegalKey }) {
  const { t } = useLocale();
  return (
    <>
      <PageHeader
        title={t(`legal.${page}.title`)}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t(`legal.${page}.title`) }]}
      />
      <p role="note" className="mb-4 rounded-control bg-warn/10 px-4 py-3 text-body text-warn">
        {t("legal.banner")}
      </p>
      <Card padding="lg" className="grid gap-4 text-body leading-relaxed">
        <p>{t(`legal.${page}.body1`)}</p>
        <p className="text-muted">{t(`legal.${page}.body2`)}</p>
      </Card>
    </>
  );
}
