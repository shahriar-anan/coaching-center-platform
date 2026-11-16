"use client";

import Link from "next/link";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import type { PublicBatch } from "@/lib/api/exam/public-admission";
import { useSession } from "@/lib/auth/session-context";
import { BatchCover, batchDatesLine, batchFeeLine, batchName } from "./batch-parts";

/**
 * One batch on the Admission page (plan 3.2): the photo or a plain header, the title, the dates and the monthly fee. The card opens the
 * batch page; Apply is its own button beside it, so the two links are never nested. A closed batch shows "Admissions closed" instead.
 */
export function BatchCard({ batch }: { batch: PublicBatch }) {
  const { t, locale } = useLocale();
  const name = batchName(batch, locale);
  const { session } = useSession();
  return (
    <article className="group flex h-full min-w-0 flex-col overflow-hidden rounded-card border border-border bg-card transition duration-200 ease-out hover:-translate-y-0.5 hover:border-accent">
      <Link href={`/admission/batches/${batch.id}`} className="flex min-w-0 flex-1 flex-col">
        <BatchCover
          photoUrl={batch.photoUrl}
          title={name}
          className="rounded-none"
          alt={t("publicAdmission.card.coverAlt", { title: name })}
          plainLabel={t("publicAdmission.card.plainLabel")}
        />
        <div className="flex min-w-0 flex-1 flex-col gap-2 p-4">
          <p className="line-clamp-2 text-card-title font-bold leading-snug text-text group-hover:text-text-accent">{name}</p>
          <p className="text-meta tabular-nums text-muted">{batchDatesLine(batch, locale, t)}</p>
          <p className="mt-auto pt-1 text-body font-semibold tabular-nums text-text">{batchFeeLine(batch, locale, t)}</p>
        </div>
      </Link>
      <div className="flex min-h-14 items-center justify-end gap-3 border-t border-border px-4 py-3">
        {batch.admissionsOpen ? (
          <>
            {session.status === "anonymous" ? <span className="text-meta text-muted">{t("publicAdmission.apply.needAccount")}</span> : null}
            <Button href={`/admission/apply/${batch.id}`} size="sm">
              {t("publicAdmission.card.apply")}
            </Button>
          </>
        ) : (
          <span className="text-meta font-semibold text-muted">{t("publicAdmission.card.closed")}</span>
        )}
      </div>
    </article>
  );
}
