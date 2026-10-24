"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api/errors";
import { apiRequest } from "@/lib/api/client";
import { useSession } from "@/lib/auth/session-context";

const STORAGE_KEY = "sandbox-order";

type Page<T> = { content?: T[] };

type PublicModule = { id: string };

type PublicExam = {
  id: string;
  titleEn: string;
  price: number | null;
};

type PricedExam = PublicExam & { price: number };

function isPriced(exam: PublicExam): exam is PricedExam {
  return exam.price != null;
}

type StoredOrder = {
  orderId: string;
  tranId: string;
  examId: string;
  examName: string;
};

type StartedOrder = {
  orderId: string;
  tranId: string;
  redirectUrl?: string;
};

function readStoredOrder(): StoredOrder | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as StoredOrder;
    if (!parsed.orderId || !parsed.examId) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function remember(order: StoredOrder) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(order));
}

function forget() {
  sessionStorage.removeItem(STORAGE_KEY);
}

async function loadPricedExams(): Promise<PricedExam[]> {
  const modules = await apiRequest<Page<PublicModule>>("/api/v1/public/live-exam-modules?size=100");
  const exams: PricedExam[] = [];
  for (const row of modules.content ?? []) {
    const page = await apiRequest<Page<PublicExam>>(
      `/api/v1/public/live-exam-modules/${row.id}/exams?size=100`,
    );
    for (const exam of page.content ?? []) {
      if (isPriced(exam)) {
        exams.push(exam);
      }
    }
  }
  return exams;
}

function priceText(price: number): string {
  return Number(price).toFixed(2);
}

export default function SandboxPage() {
  const { session, login } = useSession();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [exams, setExams] = useState<PricedExam[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [listError, setListError] = useState(false);
  const [listGeneration, setListGeneration] = useState(0);
  const [pending, setPending] = useState<StoredOrder | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [buying, setBuying] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const buyingRef = useRef(false);

  const student =
    session.status === "authenticated" && session.user.role === "STUDENT" ? session.user : null;

  useEffect(() => {
    if (!student) {
      return;
    }
    let cancelled = false;

    async function refreshStoredOrder() {
      const snapshot = sessionStorage.getItem(STORAGE_KEY);
      const stored = readStoredOrder();
      if (!stored) {
        if (!cancelled) {
          setPending(null);
        }
        return;
      }
      try {
        const order = await apiRequest<{ status: string }>(`/api/v1/live-exam-orders/${stored.orderId}`);
        if (cancelled || sessionStorage.getItem(STORAGE_KEY) !== snapshot) {
          return;
        }
        if (order.status === "INITIATED") {
          setPending(stored);
          return;
        }
        if (order.status === "SUCCEEDED" || order.status === "FAILED" || order.status === "CANCELLED") {
          forget();
          setPending(null);
        }
      } catch (err) {
        if (cancelled || sessionStorage.getItem(STORAGE_KEY) !== snapshot) {
          return;
        }
        if (err instanceof ApiError && err.code === "RESOURCE_NOT_FOUND") {
          forget();
          setPending(null);
        }
      }
    }

    void refreshStoredOrder();
    function onPageShow() {
      void refreshStoredOrder();
    }
    window.addEventListener("pageshow", onPageShow);
    return () => {
      cancelled = true;
      window.removeEventListener("pageshow", onPageShow);
    };
  }, [student]);

  useEffect(() => {
    if (!student) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const rows = await loadPricedExams();
        if (!cancelled) {
          setExams(rows);
          setLoaded(true);
          setListError(false);
        }
      } catch {
        if (!cancelled) {
          setLoaded(true);
          setListError(true);
          setNotice("The exam list could not be loaded.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [student, listGeneration]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    setSubmitting(true);
    try {
      await login(identifier, password);
    } catch (err) {
      if (err instanceof Error && err.message === "STUDENT_REJECTED") {
        setNotice("This page is for a student account.");
      } else {
        setNotice("Sign-in failed.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function buy(exam: PricedExam) {
    if (buyingRef.current) {
      return;
    }
    buyingRef.current = true;
    setBuying(true);
    setNotice(null);
    try {
      const order = await apiRequest<StartedOrder>("/api/v1/live-exam-orders", {
        method: "POST",
        body: { examId: exam.id },
      });
      if (order.redirectUrl) {
        remember({
          orderId: order.orderId,
          tranId: order.tranId,
          examId: exam.id,
          examName: exam.titleEn,
        });
        window.location.assign(order.redirectUrl);
      }
    } catch (err) {
      if (!(err instanceof ApiError)) {
        setNotice("The payment page did not open.");
        return;
      }
      if (err.code === "PAYMENT_GATEWAY_UNAVAILABLE" && err.orderId) {
        const stored = {
          orderId: err.orderId,
          tranId: err.tranId ?? "",
          examId: exam.id,
          examName: exam.titleEn,
        };
        remember(stored);
        setPending(stored);
        setNotice("The payment page did not open.");
        return;
      }
      if (err.code === "PAYMENT_GATEWAY_UNAVAILABLE") {
        setNotice("Payments are not configured on this API.");
        return;
      }
      if (err.code === "ALREADY_PURCHASED") {
        setNotice("This account already has the exam.");
        return;
      }
      if (err.code === "PHONE_REQUIRED") {
        setNotice("The account needs a phone number.");
        return;
      }
      if (err.code === "RESOURCE_NOT_FOUND" || err.code === "PRICE_REQUIRED") {
        setNotice("The exam is not on sale any more.");
        setListGeneration((value) => value + 1);
      }
    } finally {
      buyingRef.current = false;
      setBuying(false);
    }
  }

  async function cancel() {
    if (!pending || cancelling) {
      return;
    }
    setCancelling(true);
    try {
      await apiRequest(`/api/v1/live-exam-orders/${pending.orderId}/cancel`, { method: "POST" });
      forget();
      setPending(null);
      setNotice(null);
    } catch (err) {
      if (err instanceof ApiError && err.code === "PAYMENT_NOT_PENDING") {
        forget();
        setPending(null);
        setNotice("The order is no longer pending.");
      }
    } finally {
      setCancelling(false);
    }
  }

  if (session.status === "loading") {
    return <p className="p-8 text-center">Loading</p>;
  }

  if (session.status === "authenticated" && session.user.role !== "STUDENT") {
    return (
      <main className="mx-auto w-full max-w-lg px-4 py-16">
        <h1 className="text-2xl font-semibold">Sandbox</h1>
        <p className="mt-4">This page is for a student account.</p>
      </main>
    );
  }

  if (!student) {
    return (
      <main className="mx-auto w-full max-w-md px-4 py-16">
        <h1 className="text-2xl font-semibold">Sandbox</h1>
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
          {notice ? <p>{notice}</p> : null}
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-zinc-900 px-4 py-2 text-white disabled:opacity-50"
          >
            Sign in
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-lg px-4 py-16">
      <h1 className="text-2xl font-semibold">Sandbox</h1>
      {notice ? <p className="mt-4">{notice}</p> : null}
      {pending ? (
        <div className="mt-4">
          <p>{pending.examName}</p>
          <button
            type="button"
            onClick={() => void cancel()}
            disabled={cancelling}
            className="mt-2 rounded-md border border-zinc-300 px-4 py-2"
          >
            Cancel
          </button>
        </div>
      ) : null}
      {loaded && !listError && exams.length === 0 ? (
        <p className="mt-4">No priced exam is published yet.</p>
      ) : null}
      <ul className="mt-6 space-y-4">
        {exams.map((exam) => (
          <li key={exam.id}>
            <p>
              {exam.titleEn} {priceText(exam.price)}
            </p>
            <button
              type="button"
              disabled={buying}
              onClick={() => void buy(exam)}
              className="mt-2 rounded-md bg-zinc-900 px-4 py-2 text-white disabled:opacity-50"
            >
              Buy
            </button>
          </li>
        ))}
      </ul>
    </main>
  );
}
