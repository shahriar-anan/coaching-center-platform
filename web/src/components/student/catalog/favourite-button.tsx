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
 * The mark is a 44 px icon in the top-right corner of a relative parent (Phase 4, S4.4), so it takes no width from the text. A
 * refused change says so in a toast. It sits above the stretched link of a row, so it can be pressed without opening the row.
 */
export function FavouriteButton({
  kind,
  id,
  initial,
  next,
  className,
}: {
  kind: FavouriteKind;
  id: string;
  initial: boolean | null | undefined;
  /** Where a visitor returns after logging in. */
  next: string;
  className?: string;
}) {
  const { session } = useSession();
  if (session.status === "anonymous") {
    return <VisitorMark next={next} className={className} />;
  }
  if (session.status === "authenticated" && session.user.role === "STUDENT") {
    return <StudentToggle kind={kind} id={id} initial={Boolean(initial)} className={className} />;
  }
  return null;
}

const MARK =
  "absolute end-2 top-2 z-10 inline-flex size-11 shrink-0 items-center justify-center rounded-full border border-border bg-card text-muted transition-colors hover:text-text-accent";

function VisitorMark({ next, className }: { next: string; className?: string }) {
  const { t } = useLocale();
  return (
    <Link
      href={`/login?next=${encodeURIComponent(next)}`}
      prefetch={false}
      aria-label={t("catalog.favourite.label")}
      className={cn(MARK, className)}
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
      className={cn(MARK, on && "text-text-accent", className)}
    >
      <Star aria-hidden="true" className={cn("size-5", on && "fill-current")} />
      <span className="sr-only">{t("catalog.favourite.label")}</span>
    </button>
  );
}
