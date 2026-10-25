"use client";

import { useLocale } from "@/i18n/locale-context";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";

/** Placeholder contact details until the client's own are added. */
export default function ContactPage() {
  const { t } = useLocale();
  return (
    <>
      <PageHeader
        title={t("legal.contact.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("legal.contact.title") }]}
      />
      <p role="note" className="mb-4 rounded-control bg-warn/10 px-4 py-3 text-sm text-warn">
        {t("legal.banner")}
      </p>
      <Card padding="lg" className="grid gap-5">
        <p className="text-[16px] leading-relaxed">{t("legal.contact.body")}</p>
        <dl className="grid gap-4 text-[16px]">
          <div className="grid gap-0.5">
            <dt className="text-sm text-muted">{t("legal.contact.phoneLabel")}</dt>
            <dd className="font-semibold tabular-nums">{t("legal.contact.phoneValue")}</dd>
          </div>
          <div className="grid gap-0.5">
            <dt className="text-sm text-muted">{t("legal.contact.emailLabel")}</dt>
            <dd className="break-all font-semibold">{t("legal.contact.emailValue")}</dd>
          </div>
          <div className="grid gap-0.5">
            <dt className="text-sm text-muted">{t("legal.contact.addressLabel")}</dt>
            <dd className="font-semibold">{t("legal.contact.addressValue")}</dd>
          </div>
        </dl>
        <p className="text-sm text-muted">{t("legal.contact.hours")}</p>
      </Card>
    </>
  );
}
