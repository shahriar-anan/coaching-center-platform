"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormError } from "@/components/form-error";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import { useSession } from "@/lib/auth/session-context";

export default function ActivatePage() {
  const { t } = useLocale();
  const { session, login } = useSession();
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (session.status === "authenticated") {
      router.replace("/students");
    }
  }, [session.status, router]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== confirmPassword) {
      setError(new Error(t("auth.passwordMismatch")));
      return;
    }
    if (password.length < 8 || password.length > 72) {
      setError(new Error(t("auth.passwordLength")));
      return;
    }
    setSubmitting(true);
    try {
      await api.activate(phone, code, password);
      await login(phone, password);
    } catch (err) {
      if (err instanceof Error && err.message === "STUDENT_REJECTED") {
        setError(new Error(t("auth.studentActivated")));
      } else {
        setError(err);
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (session.status === "loading" || session.status === "authenticated") {
    return (
      <p className="p-8 text-center text-zinc-600">{t("auth.restoring")}</p>
    );
  }

  return (
    <div className="flex min-h-full flex-col items-center justify-center px-4 py-12">
      <div className="absolute right-4 top-4">
        <LocaleSwitcher />
      </div>
      <div className="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-semibold">{t("auth.activateTitle")}</h1>
        <form className="mt-6 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="block text-sm font-medium" htmlFor="phone">
              {t("auth.phone")}
            </label>
            <input
              id="phone"
              name="phone"
              autoComplete="tel"
              required
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium" htmlFor="code">
              {t("auth.code")}
            </label>
            <input
              id="code"
              name="code"
              required
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium" htmlFor="password">
              {t("auth.password")}
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={72}
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium" htmlFor="confirmPassword">
              {t("auth.confirmPassword")}
            </label>
            <input
              id="confirmPassword"
              name="confirmPassword"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={72}
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </div>
          {error ? <FormError error={error} /> : null}
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-zinc-900 px-4 py-2.5 font-medium text-white hover:bg-zinc-800 disabled:opacity-60"
          >
            {submitting ? t("common.loading") : t("auth.activateSubmit")}
          </button>
        </form>
        <p className="mt-4 text-sm">
          <Link href="/login" className="text-zinc-700 underline">
            {t("auth.loginTitle")}
          </Link>
        </p>
      </div>
    </div>
  );
}
