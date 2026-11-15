"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { AdmissionFormView } from "@/components/student/admission/admission-form";
import { pickTitle } from "@/lib/format";
import { offlineAdmissionEnabled } from "@/lib/config";
import type { AdmissionView } from "@/lib/api/exam/offline-admission";
import { loadReopenedApplication, type PublicApplication } from "@/lib/api/exam/public-admission";
import { SentPanel } from "./sent-panel";
import { usePublicFormApi } from "./public-form-api";

/**
 * `/admission/reopen/{linkToken}` (plan Q4): the form again after the office asked for changes. The link came by email and lasts 7 days.
 * The form shows the office's reason at the top, as it does for a signed-in student, and submits with the application's token.
 */
export function ReopenPage({ linkToken }: { linkToken: string }) {
  const { t } = useLocale();
  const [state, setState] = useState<{ kind: "loading" } | { kind: "ready"; application: PublicApplication } | { kind: "failed" }>({ kind: "loading" });
  const started = useRef(false);

  useEffect(() => {
    // Once per mount; Strict Mode runs effects twice, and the read is harmless to repeat.
    if (started.current) return;
    started.current = true;
    loadReopenedApplication(linkToken).then(
      (application) => setState({ kind: "ready", application }),
      () => setState({ kind: "failed" }),
    );
  }, [linkToken]);

  if (!offlineAdmissionEnabled) {
    return <NotFoundState title={t("common.notFound.title")} body={t("common.notFound.body")} backHref="/" backLabel={t("common.guard.goHome")} />;
  }
  if (state.kind === "loading") {
    return <Skeleton variant="card" count={2} />;
  }
  if (state.kind === "failed") {
    return (
      <div className="grid gap-6">
        <PageHeader title={t("publicAdmission.reopen.title")} breadcrumbs={[{ label: t("catalog.home"), href: "/" }]} />
        <section className="grid gap-3 rounded-card border border-border bg-card p-5">
          <p role="alert" className="text-body text-text">
            {t("publicAdmission.reopen.invalid")}
          </p>
          <div>
            <Button variant="secondary" href="/admission">
              {t("publicAdmission.reopen.apply")}
            </Button>
          </div>
        </section>
      </div>
    );
  }
  return <ReopenedForm application={state.application} />;
}

function ReopenedForm({ application }: { application: PublicApplication }) {
  const { t, locale } = useLocale();
  const [view, setView] = useState<AdmissionView>(application.admission);
  const [sent, setSent] = useState<AdmissionView | null>(null);
  const api = usePublicFormApi({ batchId: null, initialToken: application.token, onView: setView });
  const batch = pickTitle({ titleEn: view.batchNameEn ?? null, titleBn: view.batchNameBn ?? null }, locale);

  if (sent) {
    return (
      <div className="grid gap-6">
        <PageHeader title={t("publicAdmission.reopen.title")} breadcrumbs={[{ label: t("catalog.home"), href: "/" }]} />
        <SentPanel batchName={batch} email={sent.form.email ?? ""} signedIn={false} />
      </div>
    );
  }
  return (
    <div className="grid gap-6">
      <PageHeader title={t("publicAdmission.reopen.title")} breadcrumbs={[{ label: t("catalog.home"), href: "/" }]} />
      <AdmissionFormView admission={view} api={api} onSubmitted={setSent} />
    </div>
  );
}
