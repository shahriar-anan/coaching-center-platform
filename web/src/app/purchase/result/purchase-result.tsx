"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { FormError } from "@/components/form-error";
import { apiRequest } from "@/lib/api/client";
import { useSession } from "@/lib/auth/session-context";

type PaymentStatus = {
  status: string;
  examName: string;
};

const POLL_MS = 3000;
const WAIT_MS = 60_000;

function statusLabel(status: string): string {
  if (status === "PAID") {
    return "Paid";
  }
  if (status === "PENDING") {
    return "Pending";
  }
  if (status === "FAILED") {
    return "Failed";
  }
  if (status === "CANCELLED") {
    return "Cancelled";
  }
  return status;
}

export function PurchaseResult() {
  const params = useSearchParams();
  const tranId = params.get("tranId")?.trim() ?? "";
  const { session, login } = useSession();
  const [payment, setPayment] = useState<PaymentStatus | null>(null);
  const [view, setView] = useState<"loading" | "ready" | "missing" | "denied" | "signin">("loading");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const timer = useRef(0);

  useEffect(() => {
    if (!tranId || session.status !== "authenticated") {
      return;
    }
    let stopped = false;
    const started = Date.now();

    async function tick() {
      try {
        const body = await apiRequest<PaymentStatus>(
          `/api/v1/live-exam-orders/status?tranId=${encodeURIComponent(tranId)}`,
        );
        if (stopped) {
          return;
        }
        setPayment(body);
        setView("ready");
        if (body.status === "PENDING" && Date.now() - started < WAIT_MS) {
          timer.current = window.setTimeout(tick, POLL_MS);
        }
      } catch {
        if (!stopped) {
          setView("denied");
        }
      }
    }

    void tick();
    return () => {
      stopped = true;
      window.clearTimeout(timer.current);
    };
  }, [tranId, session.status]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(identifier, password);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  if (!tranId || view === "missing") {
    return <p className="p-8 text-center">The payment reference is missing.</p>;
  }

  if (view === "signin" || session.status === "anonymous") {
    return (
      <main className="mx-auto w-full max-w-md px-4 py-16">
        <h1 className="text-2xl font-semibold">Sign in to see this payment</h1>
        <form className="mt-6 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="block text-sm font-medium" htmlFor="identifier">
              Phone or email
            </label>
            <input
              id="identifier"
              name="identifier"
              autoComplete="username"
              required
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          {error ? <FormError error={error} /> : null}
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-zinc-900 px-4 py-2.5 font-medium text-white disabled:opacity-60"
          >
            {submitting ? "Signing in" : "Sign in"}
          </button>
        </form>
      </main>
    );
  }

  if (view === "denied") {
    return <p className="p-8 text-center">This payment could not be found.</p>;
  }

  if (view !== "ready" || !payment) {
    return <p className="p-8 text-center">Loading</p>;
  }

  return (
    <main className="mx-auto w-full max-w-lg px-4 py-16">
      <p className="text-sm text-zinc-600">Payment</p>
      <h1 className="mt-2 text-3xl font-semibold">{statusLabel(payment.status)}</h1>
      <a
        className="mt-6 inline-block text-zinc-800 underline"
        href={`/live-exams?name=${encodeURIComponent(payment.examName)}`}
      >
        {payment.examName}
      </a>
    </main>
  );
}
