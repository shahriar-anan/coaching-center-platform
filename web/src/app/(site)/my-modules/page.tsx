import { Suspense } from "react";
import { MyExamsPage } from "@/components/student/catalog/my-exams-page";
import { RequireStudent } from "@/lib/auth/guards";

/** The guard reads the URL (?next=), so it sits inside a Suspense boundary for the static build. */
export default function MyModules() {
  return (
    <Suspense fallback={null}>
      <RequireStudent>
        <MyExamsPage />
      </RequireStudent>
    </Suspense>
  );
}
