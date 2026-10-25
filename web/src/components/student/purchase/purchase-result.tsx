"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/errors";
import { usePaymentStatus, useInvalidateCatalog, type PaymentState } from "@/lib/api/exam/purchase";
import { useSession } from "@/lib/auth/session-context";
import { ORDER_MEMO_PREFIX, type OrderMemo } from "./purchase-sheet";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pill } from "@/components/ui/pill";
import { Skeleton } from "@/components/ui/skeleton";
import { examPath, modulePath } from "@/components/student/catalog/catalog-helpers";

const WAIT_MS = 60_000;

function readMemo(tranId: string): OrderMemo | null {
  try {
    const raw = window.sessionStorage.getItem(`${ORDER_MEMO_PREFIX}${tranId}`);
    return raw ? (JSON.parse(raw) as OrderMemo) : null;
  } catch {
    return null;
  }
}

function SignInForm() {
  const { t } = useLocale();
  const { login } = useSession();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [failed, setFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setFailed(false);
    setSubmitting(true);
    try {
      await login(identifier, password);
    } catch {
      setFailed(true);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader title={t("purchase.result.signInTitle")} />
      <form className="grid gap-3" onSubmit={onSubmit}>
        <label className="grid gap-1 text-label font-semibold">
          {t("purchase.result.identifier")}
          <input
            name="identifier"
            autoComplete="username"
            required
            className="min-h-11 rounded-control border border-border bg-card px-3 text-text"
            value={identifier}
            onChange={(event) => setIdentifier(event.target.value)}
          />
        </label>
        <label className="grid gap-1 text-label font-semibold">
          {t("purchase.result.password")}
          <input
            name="password"
            type="password"
            autoComplete="current-password"
            required
            className="min-h-11 rounded-control border border-border bg-card px-3 text-text"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        {failed ? (
          <p role="alert" className="text-body text-danger">{t("purchase.result.signInFailed")}</p>
        ) : null}
        <Button type="submit" fullWidth loading={submitting}>
          {submitting ? t("purchase.result.signingIn") : t("purchase.result.signIn")}
        </Button>
      </form>
    </>
  );
}

function Outcome({ title, body, actions }: { title: string; body: string; actions: React.ReactNode }) {
  return (
    <div className="grid gap-4 rounded-card border border-border bg-card p-6 text-center">
      <h2 className="text-section font-bold">{title}</h2>
      <p className="text-muted">{body}</p>
      <div className="flex flex-wrap justify-center gap-2">{actions}</div>
    </div>
  );
}

export function PurchaseResult() {
  const { t } = useLocale();
  const params = useSearchParams();
  const tranId = params.get("tranId")?.trim() ?? "";
  const { session } = useSession();
  const signedIn = session.status === "authenticated";
  const [startedAt, setStartedAt] = useState(() => Date.now());
  const [waiting, setWaiting] = useState(false);
  const [memo] = useState<OrderMemo | null>(() => (tranId ? readMemo(tranId) : null));
  const invalidate = useInvalidateCatalog();
  const status = usePaymentStatus(tranId, signedIn && Boolean(tranId), startedAt);
  const current: PaymentState | undefined = status.data?.status;

  useEffect(() => {
    if (current === "PAID") {
      void invalidate();
    }
  }, [current, invalidate]);

  // After one minute of polling, stop waiting quietly and offer a manual check.
  useEffect(() => {
    const id = window.setTimeout(() => setWaiting(true), WAIT_MS);
    return () => window.clearTimeout(id);
  }, [startedAt]);

  if (!tranId) {
    return (
      <>
        <PageHeader title={t("purchase.result.title")} />
        <EmptyState title={t("purchase.result.missingRef")} action={<Button variant="secondary" href="/my-modules">{t("myModules.title")}</Button>} />
      </>
    );
  }
  if (session.status === "loading") {
    return <Skeleton variant="card" count={2} />;
  }
  if (!signedIn) {
    return <SignInForm />;
  }

  const crumbs = [{ label: t("catalog.home"), href: "/" }, { label: t("purchase.result.title") }];
  const error = status.error;
  const notFound = error instanceof ApiError && (error.code === "RESOURCE_NOT_FOUND" || error.code === "ACCESS_DENIED");

  let body: React.ReactNode;
  if (status.isPending) {
    body = <Skeleton variant="card" count={2} />;
  } else if (notFound) {
    body = (
      <Outcome
        title={t("purchase.result.notFoundTitle")}
        body={t("purchase.result.notFoundBody")}
        actions={<Button variant="secondary" href="/">{t("purchase.result.home")}</Button>}
      />
    );
  } else if (status.isError && !status.data) {
    body = <ErrorState onRetry={() => void status.refetch()} />;
  } else if (current === "PAID") {
    const name = memo?.title ?? "";
    const goModule = memo?.moduleId && memo.door ? modulePath(memo.door, memo.moduleId) : null;
    const goExam = memo?.examId ? examPath(memo.examId) : null;
    body = (
      <Outcome
        title={t("purchase.result.paidTitle", { name: name || t("purchase.result.title") })}
        body={t("purchase.result.paidBody")}
        actions={
          <>
            {goModule ? <Button href={goModule}>{t("purchase.result.goModule")}</Button> : null}
            {goExam ? <Button variant={goModule ? "secondary" : "primary"} href={goExam}>{t("purchase.result.goExam")}</Button> : null}
            {!goModule && !goExam ? <Button href="/my-modules">{t("purchase.result.goMine")}</Button> : null}
          </>
        }
      />
    );
  } else if (current === "FAILED" || current === "CANCELLED") {
    const retry = memo?.examId ? examPath(memo.examId) : memo?.moduleId && memo.door ? modulePath(memo.door, memo.moduleId) : "/";
    body = (
      <Outcome
        title={t(current === "FAILED" ? "purchase.result.failedTitle" : "purchase.result.cancelledTitle")}
        body={t(current === "FAILED" ? "purchase.result.failedBody" : "purchase.result.cancelledBody")}
        actions={<Button href={retry}>{t("purchase.result.tryAgain")}</Button>}
      />
    );
  } else {
    // PENDING, still inside the first minute or past it.
    body = (
      <Outcome
        title={waiting ? t("purchase.result.pendingLateTitle") : t("purchase.result.pendingTitle")}
        body={waiting ? t("purchase.result.pendingLate") : t("purchase.result.pendingBody")}
        actions={
          waiting ? (
            <Button
              variant="secondary"
              onClick={() => {
                setWaiting(false);
                setStartedAt(Date.now());
                void status.refetch();
              }}
            >
              {t("purchase.result.checkAgain")}
            </Button>
          ) : (
            <Pill tone="warn" pulse>{t("catalog.pending")}</Pill>
          )
        }
      />
    );
  }

  return (
    <>
      <PageHeader title={t("purchase.result.title")} breadcrumbs={crumbs} />
      {status.data ? (
        <p className="mb-3 text-body text-muted">
          {t("purchase.result.tranId")}: <span className="font-mono">{tranId}</span>
        </p>
      ) : null}
      {body}
    </>
  );
}
