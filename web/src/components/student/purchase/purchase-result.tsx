"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/errors";
import { usePaymentStatus, useInvalidateCatalog, type PaymentState } from "@/lib/api/exam/purchase";
import { useSession } from "@/lib/auth/session-context";
import { ORDER_MEMO_PREFIX, type OrderMemo } from "./purchase-sheet";
import { useLocale } from "@/i18n/locale-context";
import { Check, Clock, Info, X } from "lucide-react";
import { phones } from "@/content/site-info";
import { cn } from "@/components/ui/cn";
import { formatMoney } from "@/lib/format";
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

const TONE = {
  ok: "bg-ok/15 text-ok",
  warn: "bg-warn/15 text-warn",
  danger: "bg-danger/15 text-danger",
  neutral: "bg-bg text-muted",
} as const;

const ICON = { ok: Check, warn: Clock, danger: X, neutral: Info } as const;

function Outcome({
  tone = "neutral",
  title,
  body,
  details,
  actions,
  footer,
}: {
  tone?: keyof typeof TONE;
  title: string;
  body: string;
  details?: React.ReactNode;
  actions: React.ReactNode;
  footer?: React.ReactNode;
}) {
  const Icon = ICON[tone];
  return (
    <div className="mx-auto grid w-full max-w-xl justify-items-center gap-5 rounded-card border border-border bg-card p-6 text-center md:p-8">
      <span className={cn("flex size-16 items-center justify-center rounded-full", TONE[tone])}>
        <Icon aria-hidden="true" className="size-8" strokeWidth={2.5} />
      </span>
      <div className="grid gap-2">
        <h2 className="text-xl font-bold md:text-2xl">{title}</h2>
        <p className="leading-relaxed text-muted">{body}</p>
      </div>
      {details ? <dl className="grid w-full gap-2 rounded-control bg-bg p-4 text-start text-body">{details}</dl> : null}
      <div className="flex w-full flex-wrap justify-center gap-2">{actions}</div>
      {footer ? <div className="text-meta text-muted">{footer}</div> : null}
    </div>
  );
}

export function PurchaseResult() {
  const { t, locale } = useLocale();
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
        <EmptyState title={t("purchase.result.missingRef")} action={<Button variant="secondary" href="/my-modules">{t("catalog.myExams.title")}</Button>} />
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

  const memoAmount = memo?.amount;
  let body: React.ReactNode;
  if (status.isPending) {
    body = <Skeleton variant="card" count={2} />;
  } else if (notFound) {
    body = (
      <Outcome
        tone="neutral"
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
        tone="ok"
        title={t("purchase.result.paidTitle", { name: name || t("purchase.result.title") })}
        body={t("purchase.result.paidBody")}
        details={
          <>
            {memoAmount !== undefined ? (
              <div className="flex justify-between gap-3">
                <dt className="text-muted">{t("purchase.result.amountPaid")}</dt>
                <dd className="font-bold tabular-nums">{formatMoney(memoAmount, locale)}</dd>
              </div>
            ) : null}
            <div className="flex justify-between gap-3">
              <dt className="text-muted">{t("purchase.result.tranId")}</dt>
              <dd className="font-mono text-meta">{tranId}</dd>
            </div>
          </>
        }
        actions={
          <>
            {goModule ? <Button href={goModule}>{t("purchase.result.goModule")}</Button> : null}
            {goExam ? <Button variant={goModule ? "secondary" : "primary"} href={goExam}>{t("purchase.result.goExam")}</Button> : null}
            {!goModule && !goExam ? <Button href="/my-modules">{t("purchase.result.goMine")}</Button> : null}
            <Button variant="ghost" href="/account/payments">{t("purchase.result.receipts")}</Button>
          </>
        }
      />
    );
  } else if (current === "FAILED" || current === "CANCELLED") {
    const retry = memo?.examId ? examPath(memo.examId) : memo?.moduleId && memo.door ? modulePath(memo.door, memo.moduleId) : "/";
    const failed = current === "FAILED";
    body = (
      <Outcome
        tone={failed ? "danger" : "neutral"}
        title={t(failed ? "purchase.result.failedTitle" : "purchase.result.cancelledTitle")}
        body={t(failed ? "purchase.result.failedBody" : "purchase.result.cancelledBody")}
        actions={<Button href={retry}>{t("purchase.result.tryAgain")}</Button>}
        footer={failed && phones[0] ? t("purchase.result.failedHelp", { phone: phones[0].display }) : undefined}
      />
    );
  } else {
    // PENDING, still inside the first minute or past it.
    body = (
      <Outcome
        tone="warn"
        title={waiting ? t("purchase.result.pendingLateTitle") : t("purchase.result.pendingTitle")}
        body={waiting ? t("purchase.result.pendingLate") : t("purchase.result.pendingBody")}
        footer={t("purchase.result.pendingWhat")}
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
      {body}
    </>
  );
}
