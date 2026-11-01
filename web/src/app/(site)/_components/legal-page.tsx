"use client";

import { ChevronDown } from "lucide-react";
import { useLocale, type Locale } from "@/i18n/locale-context";
import { catalogs } from "@/i18n/messages";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";

export type LegalKey = "about" | "terms" | "privacy" | "refund";

/** One block of a legal page: `h` a heading, `p` a paragraph, `li` a list item. */
type LegalBlock = { type: "h" | "p" | "li"; text: string };

/**
 * The page's text as blocks, or null while this language has only the placeholder (`body1`, `body2`).
 * Read from the catalog directly, because `t` returns strings only.
 */
function legalBlocks(locale: Locale, page: LegalKey): LegalBlock[] | null {
  const section = (catalogs[locale].legal as Record<string, unknown>)[page] as
    | { blocks?: Record<string, LegalBlock> }
    | undefined;
  if (!section?.blocks) {
    return null;
  }
  return Object.keys(section.blocks)
    .sort((a, b) => Number(a) - Number(b))
    .map((key) => section.blocks![key]);
}

/** The anchor of the n-th heading, so the contents list can link to it. */
const headingId = (number: number) => `section-${number}`;

/** The page's headings in order, with their number and anchor, for the "On this page" list. Their text is the page's own. */
function headings(blocks: LegalBlock[]) {
  return blocks
    .filter((block) => block.type === "h")
    .map((block, index) => ({ id: headingId(index + 1), number: index + 1, text: block.text }));
}

type Group = (LegalBlock & { id?: string }) | { type: "ul"; items: string[] };

/** Consecutive list items share one list, so the bullets sit together. Each heading gets its anchor. */
function groupBlocks(blocks: LegalBlock[]): Group[] {
  let headingNumber = 0;
  return blocks.reduce<Group[]>((groups, block) => {
    const last = groups[groups.length - 1];
    if (block.type === "li") {
      if (last && last.type === "ul") {
        last.items.push(block.text);
      } else {
        groups.push({ type: "ul", items: [block.text] });
      }
    } else if (block.type === "h") {
      headingNumber += 1;
      groups.push({ ...block, id: headingId(headingNumber) });
    } else {
      groups.push(block);
    }
    return groups;
  }, []);
}

/** A readable long-form page. Text that has arrived renders as blocks; otherwise the placeholder note shows. */
export function LegalPage({ page }: { page: LegalKey }) {
  const { t, locale } = useLocale();
  // The old site has these four pages in English only, so Bangla visitors read the English text until a Bangla version exists.
  const own = legalBlocks(locale, page);
  const blocks = own ?? legalBlocks("en", page);
  const fallback = own === null && blocks !== null;
  return (
    <>
      <PageHeader
        title={t(`legal.${page}.title`)}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t(`legal.${page}.title`) }]}
      />
      {blocks ? (
        <div lang={fallback ? "en" : undefined} className="grid gap-4">
          {headings(blocks).length >= 3 ? (
            <details className="group rounded-card border border-border bg-card px-4 py-1 shadow-sm">
              <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 text-label font-bold text-text">
                {t("legal.toc")}
                <ChevronDown aria-hidden="true" className="size-4 text-muted transition-transform group-open:rotate-180" />
              </summary>
              <nav aria-label={t("legal.toc")} className="border-t border-border py-3">
                <ol className="grid gap-x-6 gap-y-1 text-body sm:grid-cols-2">
                  {headings(blocks).map((heading) => (
                    <li key={heading.id}>
                      <a
                        href={`#${heading.id}`}
                        className="inline-flex min-h-9 items-center gap-2 rounded-control text-text-accent hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
                      >
                        <span aria-hidden="true" className="w-6 shrink-0 text-meta tabular-nums text-muted">
                          {heading.number}.
                        </span>
                        {heading.text}
                      </a>
                    </li>
                  ))}
                </ol>
              </nav>
            </details>
          ) : null}

          <Card padding="md" className="min-w-0 break-words text-body sm:p-8">
            <div className="grid gap-4 leading-7 text-text">
              {groupBlocks(blocks).map((group, index) => {
                if (group.type === "h") {
                  return (
                    <h2
                      key={index}
                      id={group.id}
                      className="mt-4 scroll-mt-24 border-s-4 border-accent ps-3 text-section font-bold leading-snug text-text first:mt-0"
                    >
                      {group.text}
                    </h2>
                  );
                }
                if (group.type === "ul") {
                  return (
                    <ul key={index} className="grid list-disc gap-2 ps-6 marker:text-accent">
                      {group.items.map((item, itemIndex) => (
                        <li key={itemIndex} className="ps-1">
                          {item}
                        </li>
                      ))}
                    </ul>
                  );
                }
                return <p key={index}>{group.text}</p>;
              })}
            </div>
          </Card>
        </div>
      ) : (
        <>
          <p role="note" className="mb-4 rounded-control bg-warn/10 px-4 py-3 text-body text-warn">
            {t("legal.banner")}
          </p>
          <Card padding="lg" className="grid gap-4 text-body leading-relaxed">
            <p>{t(`legal.${page}.body1`)}</p>
            <p className="text-muted">{t(`legal.${page}.body2`)}</p>
          </Card>
        </>
      )}
    </>
  );
}
