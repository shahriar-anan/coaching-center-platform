import Link from "next/link";

/**
 * The label above a block on a student page: small, bold and quiet, with an optional "See all" link on the right.
 * It replaces the 17 px `text-card-title` headings, which read as page titles on a dashboard.
 */
export function SectionLabel({
  id,
  title,
  href,
  action,
}: {
  id?: string;
  title: string;
  href?: string;
  action?: string;
}) {
  return (
    <div className="mb-2 flex items-center justify-between gap-3">
      <h2 id={id} className="text-label font-bold text-text">
        {title}
      </h2>
      {href && action ? (
        <Link href={href} className="inline-flex min-h-9 items-center text-meta font-semibold text-text-accent hover:underline">
          {action}
        </Link>
      ) : null}
    </div>
  );
}
