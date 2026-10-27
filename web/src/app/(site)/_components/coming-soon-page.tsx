"use client";

import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";

export type ComingSoonKey = "publications" | "blog" | "media" | "resources";

const LABEL = {
  publications: "landing.navPublications",
  blog: "landing.navBlog",
  media: "landing.navMedia",
  resources: "landing.navResources",
} as const;

/** A holding page for a landing menu item that has no content yet. */
export function ComingSoonPage({ page }: { page: ComingSoonKey }) {
  const { t } = useLocale();
  const title = t(LABEL[page]);
  return (
    <>
      <PageHeader title={title} breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: title }]} />
      <EmptyState
        title={t("landing.comingSoonTitle")}
        body={t("landing.comingSoonBody")}
        action={<Button href="/">{t("landing.comingSoonBack")}</Button>}
      />
    </>
  );
}
