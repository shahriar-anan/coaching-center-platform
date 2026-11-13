"use client";

import { ErrorState } from "@/components/ui/error-state";
import { Skeleton } from "@/components/ui/skeleton";
import { PrintSheet } from "@/components/student/admission/print-sheet";
import { useAdmission } from "@/lib/api/exam/offline-admin";
import { admissionPhotoPath } from "./admission-photo";

/** The printable form of one admission: the same A4 sheet the student prints, filled with this admission's data. */
export function AdmissionPrint({ id }: { id: string }) {
  const admission = useAdmission(id);
  if (admission.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (admission.isError) {
    return <ErrorState onRetry={() => admission.refetch()} error={admission.error} />;
  }
  const a = admission.data;
  return (
    <PrintSheet
      admission={{ form: a.form, hasPhoto: a.hasPhoto, submittedAt: a.submittedAt }}
      photoPath={admissionPhotoPath(a.id)}
      backHref={`/staff/admissions/${a.id}`}
    />
  );
}
