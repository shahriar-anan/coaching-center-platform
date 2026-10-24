import { Suspense } from "react";
import { PurchaseResult } from "./purchase-result";

export default function PurchaseResultPage() {
  return (
    <Suspense fallback={<p className="p-8 text-center">Loading</p>}>
      <PurchaseResult />
    </Suspense>
  );
}
