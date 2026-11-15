"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/error-state";
import { NotFoundState } from "@/components/ui/not-found-state";
import { PageHeader } from "@/components/ui/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useLocale } from "@/i18n/locale-context";
import { isNotFoundError } from "@/lib/api/errors";
import { AdmissionFormView } from "@/components/student/admission/admission-form";
import { offlineAdmissionEnabled } from "@/lib/config";
import { useSession } from "@/lib/auth/session-context";
import type { AdmissionView } from "@/lib/api/exam/offline-admission";
import { useApplyForBatch } from "@/lib/api/exam/my-admission";
import { usePublicBatch, type PublicBatch } from "@/lib/api/exam/public-admission";
import { readApplicationToken } from "./application-token";
import { BatchCover, batchDatesLine, batchFeeLine, batchName } from "./batch-parts";
import { SentPanel } from "./sent-panel";
import { OwnApplication } from "./own-application";
import { usePublicFormApi } from "./public-form-api";

/** An empty admission for a visitor's first form: nothing is saved yet, so there is no state, id or photo. */
function emptyView(): AdmissionView {
  return { id: "", state: "AWAITING_FORM", submitCount: 0, hasPhoto: false, form: {} };
}

/** The batch at the top of the form, with "Change batch" back to the cards (plan 2A, step 2). */
function BatchStrip({ batch }: { batch: PublicBatch }) {
  const { t, locale } = useLocale();
  const name = batchName(batch, locale);
  return (
    <section
      aria-label={t("publicAdmission.apply.batchLabel")}
      className="grid gap-3 rounded-card border border-border bg-card p-4 sm:grid-cols-[minmax(0,12rem)_minmax(0,1fr)] sm:items-center"
    >
      <BatchCover
        photoUrl={batch.photoUrl}
        title={name}
        className="rounded-control sm:w-48"
        alt={t("publicAdmission.card.coverAlt", { title: name })}
        plainLabel={t("publicAdmission.card.plainLabel")}
      />
      <div className="grid min-w-0 gap-1">
        <p className="text-card-title font-bold text-text">{name}</p>
        <p className="text-meta tabular-nums text-muted">{batchDatesLine(batch, locale, t)}</p>
        <p className="text-meta font-semibold tabular-nums text-text">{batchFeeLine(batch, locale, t)}</p>
        <div className="pt-1">
          <Button variant="ghost" size="sm" href="/admission" className="px-0">
            {t("publicAdmission.apply.changeBatch")}
          </Button>
        </div>
      </div>
    </section>
  );
}

/**
 * `/admission/apply/{batchId}` (plan 2A, 2B, P1). The batch comes from the URL, so the form has no batch list: the card the visitor
 * pressed Apply on is the batch. A signed-in student's form starts from the account; a visitor's starts blank and the email starts the
 * application. After the office has the form, the confirmation replaces it.
 */
export function ApplyPage({ batchId }: { batchId: string }) {
  const { t, locale } = useLocale();
  const { session } = useSession();
  const batch = usePublicBatch(batchId);
  const [sent, setSent] = useState<AdmissionView | null>(null);

  const title = t("publicAdmission.apply.title");

  if (!offlineAdmissionEnabled) {
    return <NotFoundState title={t("common.notFound.title")} body={t("common.notFound.body")} backHref="/" backLabel={t("common.guard.goHome")} />;
  }
  if (batch.isPending) {
    return <Skeleton variant="card" count={2} />;
  }
  if (batch.isError || !batch.data) {
    return isNotFoundError(batch.error) ? (
      <NotFoundState
        title={t("publicAdmission.batch.missingTitle")}
        body={t("publicAdmission.batch.missingBody")}
        backHref="/admission"
        backLabel={t("publicAdmission.batch.back")}
      />
    ) : (
      <ErrorState error={batch.error} onRetry={() => void batch.refetch()} />
    );
  }

  const b = batch.data;
  const name = batchName(b, locale);
  const crumbs = [
    { label: t("catalog.home"), href: "/" },
    { label: t("publicAdmission.list.title"), href: "/admission" },
    { label: title },
  ];

  if (sent) {
    return (
      <div className="grid gap-6">
        <PageHeader title={title} breadcrumbs={crumbs} />
        <SentPanel batchName={name} email={sent.form.email ?? ""} signedIn={session.status === "authenticated"} />
      </div>
    );
  }

  let body;
  if (!b.admissionsOpen) {
    body = (
      <section className="grid gap-3 rounded-card border border-border bg-card p-5">
        <p className="text-body text-text">{t("publicAdmission.apply.closed")}</p>
        <div>
          <Button variant="secondary" href="/admission">
            {t("publicAdmission.apply.changeBatch")}
          </Button>
        </div>
      </section>
    );
  } else if (session.status === "loading") {
    body = <Skeleton variant="card" count={2} />;
  } else if (session.status === "authenticated" && session.user.role !== "STUDENT") {
    body = <p className="rounded-control bg-bg px-3 py-2 text-body text-text">{t("publicAdmission.apply.staffNote")}</p>;
  } else if (session.status === "authenticated") {
    body = <AccountApply batchId={batchId} onSent={setSent} />;
  } else {
    body = <VisitorApply batchId={batchId} onSent={setSent} />;
  }

  return (
    <div className="grid gap-6">
      <PageHeader title={title} breadcrumbs={crumbs} />
      <BatchStrip batch={b} />
      {body}
    </div>
  );
}

/** A visitor with no account (plan P3). The form starts blank; the application starts when the email is typed and saved. */
function VisitorApply({ batchId, onSent }: { batchId: string; onSent: (view: AdmissionView) => void }) {
  const { t } = useLocale();
  // Read once: the stored token, if this tab already started an application for the batch.
  const [initialToken] = useState(() => readApplicationToken(batchId));
  const [view, setView] = useState<AdmissionView>(emptyView);
  const [existsFor, setExistsFor] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  if (existsFor !== null) {
    return (
      <section role="status" className="grid gap-3 rounded-card border border-border bg-card p-5">
        <h2 className="text-card-title font-bold text-text">{t("publicAdmission.apply.existsTitle")}</h2>
        <p className="text-body leading-relaxed text-text">{t("publicAdmission.apply.existsBody", { email: existsFor })}</p>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" href="/admission">
            {t("publicAdmission.confirm.back")}
          </Button>
          <Button
            variant="ghost"
            onClick={() => {
              setExistsFor(null);
              setAttempt((n) => n + 1);
            }}
          >
            {t("publicAdmission.apply.otherEmail")}
          </Button>
        </div>
      </section>
    );
  }
  return <VisitorForm key={attempt} batchId={batchId} initialToken={attempt === 0 ? initialToken : null} view={view} setView={setView} onExists={setExistsFor} onSent={onSent} />;
}

function VisitorForm({
  batchId,
  initialToken,
  view,
  setView,
  onExists,
  onSent,
}: {
  batchId: string;
  initialToken: string | null;
  view: AdmissionView;
  setView: (view: AdmissionView) => void;
  onExists: (email: string) => void;
  onSent: (view: AdmissionView) => void;
}) {
  const { t } = useLocale();
  const api = usePublicFormApi({ batchId, initialToken, onView: setView, onExists });
  return (
    <div className="grid gap-4">
      <p className="text-meta text-muted">
        {t("publicAdmission.apply.haveAccount")}{" "}
        <Link href="/login" className="font-semibold text-text-accent underline underline-offset-4">
          {t("publicAdmission.apply.logIn")}
        </Link>
      </p>
      <AdmissionFormView admission={view} api={api} onSubmitted={onSent} />
    </div>
  );
}

/**
 * A signed-in student (plan B). The application starts from the account once per visit; the form then uses the student's own
 * admission, which the start made the newest one.
 */
function AccountApply({ batchId, onSent }: { batchId: string; onSent: (view: AdmissionView) => void }) {
  const { t } = useLocale();
  const apply = useApplyForBatch();
  const [state, setState] = useState<{ kind: "starting" } | { kind: "ready"; view: AdmissionView } | { kind: "failed" }>({ kind: "starting" });
  const started = useRef(false);
  const { mutateAsync } = apply;

  useEffect(() => {
    // Once per mount. Strict Mode runs effects twice; the API answers the same open application either way.
    if (started.current) return;
    started.current = true;
    mutateAsync(batchId).then(
      (view) => setState({ kind: "ready", view }),
      () => setState({ kind: "failed" }),
    );
  }, [batchId, mutateAsync]);

  if (state.kind === "starting") {
    return <Skeleton variant="card" count={2} />;
  }
  if (state.kind === "failed") {
    return (
      <p role="alert" className="rounded-control bg-bg px-3 py-2 text-body text-danger">
        {t("publicAdmission.apply.startFailed")}
      </p>
    );
  }
  // The API answers the open application for this batch when there is one. Once its form is sent there is nothing to fill in here, so
  // the student sees where it stands instead.
  if (state.view.state !== "AWAITING_FORM" && state.view.state !== "CHANGES_REQUESTED") {
    return <OwnApplication admission={state.view} />;
  }
  return <AdmissionFormView admission={state.view} onSubmitted={onSent} />;
}
