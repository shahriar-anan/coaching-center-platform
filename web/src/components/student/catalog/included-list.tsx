import { Check } from "lucide-react";

/** What a purchase gives the student, as a list with a tick on each line. */
export function IncludedList({ items }: { items: string[] }) {
  return (
    <ul className="grid gap-2">
      {items.map((item) => (
        <li key={item} className="flex min-w-0 items-start gap-2.5 text-body">
          <span aria-hidden="true" className="mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full bg-ok/15 text-ok">
            <Check className="size-3.5" />
          </span>
          <span className="min-w-0">{item}</span>
        </li>
      ))}
    </ul>
  );
}
