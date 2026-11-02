"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Receipt, type LucideIcon } from "lucide-react";
import { useLocale } from "@/i18n/locale-context";
import { formatDate } from "@/lib/format";
import { Pill } from "@/components/ui/pill";
import type { AccessEnd } from "./my-exams-rows";

/** The access as a pill at the card's top right: lifetime in green, an end date plain, expired in amber (plan S1.3). */
export function AccessPill({ end }: { end: AccessEnd }) {
  const { t, locale } = useLocale();
  if (end.kind === "expired") {
    return <Pill tone="warn">{t("catalog.myExams.expired")}</Pill>;
  }
  if (end.kind === "lifetime") {
    return <Pill tone="ok">{t("catalog.myExams.accessLifetime")}</Pill>;
  }
  return <Pill tone="muted">{t("catalog.myExams.accessUntil", { date: formatDate(end.endsAt, locale, { year: "auto" }) })}</Pill>;
}

/** A title, or what the row shows while its title loads or when the read fails. */
export function TitleText({ title, pending, href }: { title: string; pending: boolean; href: string }) {
  const { t } = useLocale();
  if (!title) {
    return <span className="text-muted">{pending ? t("catalog.loading") : t("catalog.myExams.detailsMissing")}</span>;
  }
  return (
    <Link href={href} prefetch={false} className="font-semibold text-text underline-offset-4 hover:underline">
      {title}
    </Link>
  );
}

/** "View payment" for a row bought with an order (Phase 4, S4.6). A roster or manual grant has no order, so no button. */
function PaymentLink({ orderId }: { orderId: string }) {
  const { t } = useLocale();
  return (
    <Link
      href={`/account/payments?order=${encodeURIComponent(orderId)}`}
      prefetch={false}
      className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-control border border-border px-3 text-meta font-semibold text-text transition-colors hover:border-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
    >
      <Receipt aria-hidden="true" className="size-4" />
      {t("catalog.myExams.viewPayment")}
    </Link>
  );
}

/**
 * One owned exam or package: a type icon, the title with the access pill at its right, a line of facts (type and how it was
 * got), and the payment button when it was bought.
 */
export function OwnedCard({
  icon: Icon,
  title,
  access,
  facts,
  orderId,
}: {
  icon: LucideIcon;
  title: ReactNode;
  access: ReactNode;
  facts: (string | null)[];
  orderId?: string | null;
}) {
  const shown = facts.filter((fact): fact is string => Boolean(fact));
  return (
    <li>
      <div className="flex gap-3 rounded-card border border-border bg-card p-4">
        <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-control bg-accent/15 text-text-accent">
          <Icon className="size-5" />
        </span>
        <div className="grid min-w-0 flex-1 gap-1.5">
          <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
            <p className="min-w-0 text-body">{title}</p>
            {access}
          </div>
          {shown.length ? <p className="text-meta text-muted">{shown.join(" · ")}</p> : null}
          {orderId ? <PaymentLink orderId={orderId} /> : null}
        </div>
      </div>
    </li>
  );
}
