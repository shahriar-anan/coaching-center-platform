import { Suspense } from "react";
import { PurchaseResult } from "@/components/student/purchase/purchase-result";

export default function PurchaseResultPage() {
  // useSearchParams needs a Suspense boundary for static prerendering.
  return (
    <Suspense fallback={null}>
      <PurchaseResult />
    </Suspense>
  );
}
