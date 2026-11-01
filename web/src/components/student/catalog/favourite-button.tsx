"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Star } from "lucide-react";
import { useFavouriteToggle, type FavouriteKind } from "@/lib/api/exam/favourites";
import { useSession } from "@/lib/auth/session-context";
import { useLocale } from "@/i18n/locale-context";
import { cn } from "@/components/ui/cn";
import { useToast } from "@/components/ui/toast";

/**
 * The favourite mark on an exam row, a package card or the package or exam detail (plan S3.3). A signed-in student gets a toggle
 * that changes at once. A visitor is sent to log in first, and then back to `next`. Staff and a session still loading get no mark.
 * By default the mark is a 44 px icon in the top-right corner of a relative parent (Phase 4, S4.4), so it takes no width from the
 * text. A row with `placement="inline"` takes it as its last flex item instead, 40 px, beside the text. A refused change says so in
 * a toast. Either way it sits above the stretched link of a row, so it can be pressed without opening the row.
 */
export function FavouriteButton({
  kind,
  id,
  initial,
  next,
  className,
  placement = "corner",
}: {
  kind: FavouriteKind;
  id: string;
  initial: boolean | null | undefined;
  /** Where a visitor returns after logging in. */
  next: string;
  className?: string;
  /** "corner" (the detail pages) or "inline" (a row, as its last item). */
  placement?: "corner" | "inline";
}) {
  const { session } = useSession();
  const mark = placement === "inline" ? INLINE : CORNER;
  if (session.status === "anonymous") {
    return <VisitorMark next={next} className={cn(mark, className)} />;
  }
  if (session.status === "authenticated" && session.user.role === "STUDENT") {
    return <StudentToggle kind={kind} id={id} initial={Boolean(initial)} className={cn(mark, className)} />;
  }
  return null;
}

const CORNER =
  "absolute end-2 top-2 z-10 inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-border bg-card text-muted transition-colors hover:text-text-accent";

/** The inline mark: a normal flex item, so nothing covers the row's text. The z-index keeps it above a row's stretched link. */
const INLINE =
  "relative z-10 inline-flex size-10 shrink-0 items-center justify-center rounded-full border border-border bg-card text-muted transition-colors hover:text-text-accent";

function VisitorMark({ next, className }: { next: string; className?: string }) {
  const { t } = useLocale();
  return (
    <Link
      href={`/login?next=${encodeURIComponent(next)}`}
      prefetch={false}
      aria-label={t("catalog.favourite.label")}
      className={className}
    >
      <Star aria-hidden="true" className="size-5" />
    </Link>
  );
}

function StudentToggle({ kind, id, initial, className }: { kind: FavouriteKind; id: string; initial: boolean; className?: string }) {
  const { t } = useLocale();
  const { toast } = useToast();
  const { on, pending, failed, toggle } = useFavouriteToggle(kind, id, initial);
  // A refused change goes back to its last state (in the hook) and says so in a toast, once per refusal.
  useEffect(() => {
    if (failed) toast({ title: t("catalog.favourite.failed"), tone: "danger" });
  }, [failed, toast, t]);
  return (
    <button
      type="button"
      aria-pressed={on}
      disabled={pending}
      onClick={toggle}
      className={cn(className, on && "text-text-accent")}
    >
      <Star aria-hidden="true" className={cn("size-5", on && "fill-current")} />
      <span className="sr-only">{t("catalog.favourite.label")}</span>
    </button>
  );
}
