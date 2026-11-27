import { Suspense } from "react";
import { EntriesPage } from "@/components/staff/accounting/entries/entries-page";
import { Skeleton } from "@/components/ui/skeleton";

/* The list reads its filters from the URL, so it sits in a Suspense boundary. */
export default function StaffAccountingEntriesPage() {
  return (
    <Suspense fallback={<Skeleton variant="rows" count={5} />}>
      <EntriesPage />
    </Suspense>
  );
}
