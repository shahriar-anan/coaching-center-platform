"use client";

import { RequireStudent } from "@/lib/auth/guards";
import { AdmissionPrintPage } from "@/components/student/admission/admission-pages";

export default function Page() {
  return (
    <RequireStudent>
      <AdmissionPrintPage />
    </RequireStudent>
  );
}
