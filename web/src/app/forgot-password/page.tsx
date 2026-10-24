"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { FormError } from "@/components/form-error";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";

export default function ForgotPasswordPage() {
  const { t } = useLocale();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.forgotPassword(email);
      setSent(true);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-full flex-col items-center justify-center px-4 py-12">
      <div className="absolute right-4 top-4">
        <LocaleSwitcher />
      </div>
      <div className="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-semibold">{t("auth.forgotTitle")}</h1>
        {sent ? (
          <p className="mt-6">{t("auth.forgotSent")}</p>
        ) : (
          <form className="mt-6 space-y-4" onSubmit={onSubmit}>
            <div>
              <label className="block text-sm font-medium" htmlFor="email">
                {t("auth.email")}
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>
            {error ? <FormError error={error} /> : null}
            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-md bg-zinc-900 px-4 py-2.5 font-medium text-white hover:bg-zinc-800 disabled:opacity-60"
            >
              {submitting ? t("common.loading") : t("auth.forgotSubmit")}
            </button>
          </form>
        )}
        <p className="mt-4 text-sm">
          <Link href="/login" className="text-zinc-700 underline">
            {t("auth.backToLogin")}
          </Link>
        </p>
      </div>
    </div>
  );
}
