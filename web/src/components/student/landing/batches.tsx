import Link from "next/link";
import { Check } from "lucide-react";
import { batches } from "@/content/site-info";
import { useLocale } from "@/i18n/locale-context";
import { Section } from "@/components/student/landing/section";

type Batch = { name: string; kind: "foundation" | "mcq" | "written" | "special" };

/** The poster's batches. MCQ and written batches open the exam site; the rest are offline. */
export function Batches() {
  const { t } = useLocale();
  const groups = [
    { title: t("landing.groupBjs"), items: batches.bjs },
    { title: t("landing.groupBar"), items: batches.bar },
  ];
  return (
    <Section id="landing-batches" title={t("landing.batchesTitle")} subtitle={t("landing.batchesSubtitle")}>
      <div className="grid gap-4 md:grid-cols-2">
        {groups.map((group) => (
          <div key={group.title} className="min-w-0 rounded-card border border-border bg-card p-5">
            <h3 className="text-lg font-bold text-text-accent">{group.title}</h3>
            <ul className="mt-3 grid gap-2">
              {group.items.map((batch) => (
                <li key={batch.name}>
                  <BatchRow batch={batch} />
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </Section>
  );
}

function BatchRow({ batch }: { batch: Batch }) {
  const { t } = useLocale();
  const row = "flex items-center justify-between gap-3 rounded-control px-3 py-2.5";
  const content = (
    <>
      <span className="flex min-w-0 items-center gap-2 font-semibold">
        <Check aria-hidden="true" className="size-4 shrink-0 text-accent" />
        <span className="min-w-0">{batch.name}</span>
      </span>
    </>
  );
  if (batch.kind === "mcq" || batch.kind === "written") {
    const href = batch.kind === "mcq" ? "/mcq" : "/written";
    return (
      <Link href={href} className={`${row} transition-colors hover:bg-bg`}>
        {content}
        <span className="shrink-0 text-meta font-semibold text-text-accent">{t("landing.batchOpen")}</span>
      </Link>
    );
  }
  return (
    <div className={row}>
      {content}
      <span className="shrink-0 text-meta text-muted">
        {batch.kind === "foundation" ? t("landing.kindFoundation") : t("landing.kindSpecial")}
      </span>
    </div>
  );
}
