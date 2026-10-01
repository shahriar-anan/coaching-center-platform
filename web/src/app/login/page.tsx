"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormError } from "@/components/form-error";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/i18n/locale-context";
import { useSession } from "@/lib/auth/session-context";

export default function LoginPage() {
  const { t } = useLocale();
  const { session, login } = useSession();
  const router = useRouter();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
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
    setSubmitting(true);
    try {
      await login(identifier, password);
    } catch (err) {
      if (err instanceof Error && err.message === "STUDENT_REJECTED") {
        setError(new Error(t("auth.studentRejected")));
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
        <h1 className="text-2xl font-semibold">{t("auth.loginTitle")}</h1>
        <form className="mt-6 space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="block text-sm font-medium" htmlFor="identifier">
              {t("auth.identifier")}
            </label>
            <input
              id="identifier"
              name="identifier"
              autoComplete="username"
              required
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
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
              autoComplete="current-password"
              required
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {error ? <FormError error={error} /> : null}
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-zinc-900 px-4 py-2.5 font-medium text-white hover:bg-zinc-800 disabled:opacity-60"
          >
            {submitting ? t("common.loading") : t("auth.submit")}
          </button>
        </form>
        <p className="mt-4 text-sm">
          <Link href="/activate" className="text-zinc-700 underline">
            {t("auth.activateLink")}
          </Link>
        </p>
      </div>
    </div>
  );
}
