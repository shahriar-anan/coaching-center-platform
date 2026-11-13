"use client";

import { RequireStudent } from "@/lib/auth/guards";
import { AdmissionPage } from "@/components/student/admission/admission-pages";

export default function Page() {
  return (
    <RequireStudent>
      <AdmissionPage />
    </RequireStudent>
  );
}
