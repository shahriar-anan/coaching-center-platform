import { Suspense } from "react";
import { MyModulesPage } from "@/components/student/catalog/my-modules-page";
import { RequireStudent } from "@/lib/auth/guards";

/** The guard reads the URL (?next=), so it sits inside a Suspense boundary for the static build. */
export default function MyModules() {
  return (
    <Suspense fallback={null}>
      <RequireStudent>
        <MyModulesPage />
      </RequireStudent>
    </Suspense>
  );
}
