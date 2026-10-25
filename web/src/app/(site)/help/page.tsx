"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { SearchInput } from "@/components/ui/search-input";

const SECTIONS = ["buy", "live", "mcq", "written", "results", "account"] as const;
type Section = (typeof SECTIONS)[number];

type Item = { id: string; q: string; a: string };

/**
 * Help centre. The FAQ is native `<details>` (works without script). The search
 * filters the loaded questions and answers, case-insensitively.
 */
export default function HelpPage() {
  const { t } = useLocale();
  const [query, setQuery] = useState("");

  const sections = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return SECTIONS.map((section: Section) => {
      const items: Item[] = [1, 2]
        .map((n) => ({
          id: `${section}-${n}`,
          q: t(`legal.help.sections.${section}.q${n}`),
          a: t(`legal.help.sections.${section}.a${n}`),
        }))
        .filter((item) => !needle || `${item.q} ${item.a}`.toLowerCase().includes(needle));
      return { section, items };
    }).filter((entry) => entry.items.length > 0);
  }, [query, t]);

  return (
    <>
      <PageHeader title={t("legal.help.title")} breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("legal.help.title") }]} />
      <p role="note" className="mb-4 rounded-control bg-warn/10 px-4 py-3 text-body text-warn">
        {t("legal.banner")}
      </p>

      <div className="mb-6">
        <label htmlFor="help-search" className="sr-only">
          {t("legal.help.searchLabel")}
        </label>
        <SearchInput id="help-search" value={query} onChange={setQuery} placeholder={t("legal.help.searchPlaceholder")} />
      </div>

      {sections.length === 0 ? (
        <EmptyState title={t("legal.help.noResults")} />
      ) : (
        <div className="grid gap-8">
          {sections.map(({ section, items }) => (
            <section key={section} aria-labelledby={`help-${section}`} className="grid gap-3">
              <h2 id={`help-${section}`} className="text-section font-bold text-text">
                {t(`legal.help.sections.${section}.title`)}
              </h2>
              <Card padding="md" className="divide-y divide-border p-0">
                {items.map((item) => (
                  <details key={item.id} className="group px-4 py-3">
                    <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 font-semibold text-text">
                      {item.q}
                      <span aria-hidden="true" className="text-text-accent transition-transform group-open:rotate-90">
                        ›
                      </span>
                    </summary>
                    <p className="pb-2 pt-1 text-body leading-relaxed text-muted">{item.a}</p>
                  </details>
                ))}
              </Card>
            </section>
          ))}
        </div>
      )}

      <Card padding="lg" className="mt-10 grid gap-2">
        <h2 className="text-card font-bold text-text">{t("legal.help.contactTitle")}</h2>
        <p className="text-body text-muted">{t("legal.help.contactBody")}</p>
        <Link href="/contact" prefetch className="font-semibold text-text-accent underline underline-offset-4">
          {t("legal.help.contactLink")}
        </Link>
      </Card>
    </>
  );
}
