import type { ReactNode } from "react";

/** A purchasable product as a link card. Stub until P-5 lands. */
export function ProductCard({
  href,
  title,
  subtitle,
  pills,
  examCount,
  footer,
}: {
  href: string;
  title: string;
  subtitle?: string;
  pills?: ReactNode;
  price: number | null;
  examCount: number;
  footer?: ReactNode;
}) {
  return (
    <a href={href}>
      <span>{title}</span>
      {subtitle ? <span>{subtitle}</span> : null}
      {pills}
      <span>{examCount}</span>
      {footer}
    </a>
  );
}
