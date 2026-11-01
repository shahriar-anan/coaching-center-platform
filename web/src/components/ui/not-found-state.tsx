import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";

/**
 * The address names nothing: the exam, package or category was removed, or the address is wrong. Shown in place of the
 * page's content, with a way back to a page that exists.
 */
export function NotFoundState({
  title,
  body,
  backHref,
  backLabel,
}: {
  title: string;
  body?: string;
  backHref: string;
  backLabel: string;
}) {
  return <EmptyState title={title} body={body} action={<Button variant="secondary" href={backHref}>{backLabel}</Button>} />;
}
