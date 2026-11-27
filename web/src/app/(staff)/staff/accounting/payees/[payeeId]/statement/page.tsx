import { Suspense } from "react";
import { StatementPage } from "@/components/staff/accounting/print/statement";
import { Skeleton } from "@/components/ui/skeleton";

/* The range is read from the URL, so the page sits in a Suspense boundary. */
export default async function StaffAccountingStatementPage({ params }: { params: Promise<{ payeeId: string }> }) {
  const { payeeId } = await params;
  return (
    <Suspense fallback={<Skeleton variant="card" count={1} />}>
      <StatementPage payeeId={payeeId} />
    </Suspense>
  );
}
