"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { FileDrop } from "@/components/ui/file-drop";
import { useToast } from "@/components/ui/toast";
import { useLocale } from "@/i18n/locale-context";
import { useFormat } from "@/lib/format/use-format";
import { openAuthorizedFile, useUploadSigned, type AdmissionDetail } from "@/lib/api/exam/offline-admin";
import { FormReadOnly } from "./form-readonly";
import { offlineErrorText } from "./offline-error";

/** The signed paper's size limit, as the API checks it (20 MB). */
const SIGNED_MAX_BYTES = 20 * 1024 * 1024;

/**
 * The form tab (plan 6.3): print the form, upload the signed copy (a PDF or a phone photo of the paper, optional and at any time), and the
 * form itself. The upload never blocks anything.
 */
export function FormTab({ admission, canReview }: { admission: AdmissionDetail; canReview: boolean }) {
  const { t } = useLocale();
  const { formatDateTime } = useFormat();
  const { toast } = useToast();
  const upload = useUploadSigned(admission.id);
  const [error, setError] = useState<string | null>(null);
  const signed = admission.signedCopy;

  return (
    <div className="grid gap-5">
      <section aria-labelledby="signed-title" className="grid gap-3 rounded-card border border-border-control bg-card p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 id="signed-title" className="text-card-title text-text">
            {t("staffOffline.form.signedTitle")}
          </h3>
          <Button variant="secondary" size="sm" onClick={() => window.open(`/staff/admissions/${admission.id}/print`, "_blank", "noopener")}>
            {t("staffOffline.form.print")}
          </Button>
        </div>

        {signed ? (
          <div className="flex flex-wrap items-center gap-2 text-body text-text">
            <span className="min-w-0 break-words">{signed.fileName}</span>
            <span className="text-meta text-muted">
              {t("staffOffline.form.uploadedOn", { date: formatDateTime(signed.uploadedAt) })}
            </span>
            <Button
              size="sm"
              variant="secondary"
              onClick={async () => {
                try {
                  await openAuthorizedFile(`/api/v1/offline-admissions/${admission.id}/signed-form`);
                } catch {
                  toast({ title: t("staffOffline.errors.generic"), tone: "danger" });
                }
              }}
            >
              {t("staffOffline.form.view")}
            </Button>
          </div>
        ) : (
          <p className="text-meta text-muted">{t("staffOffline.form.notUploaded")}</p>
        )}

        {canReview ? (
          <FileDrop
            accept="pdf-or-image"
            maxBytes={SIGNED_MAX_BYTES}
            uploading={upload.isPending}
            error={error ?? undefined}
            onFile={(file) => {
              setError(null);
              upload.mutate(file, {
                onSuccess: () => toast({ title: t("staffOffline.form.saved"), tone: "ok" }),
                onError: (caught) => setError(offlineErrorText(caught, t)),
              });
            }}
          />
        ) : null}
        <p className="text-meta text-muted">{t("staffOffline.form.optional")}</p>
      </section>

      <FormReadOnly form={admission.form} />
    </div>
  );
}
