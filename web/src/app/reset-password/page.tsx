"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { FormError } from "@/components/form-error";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";

export default function ResetPasswordPage() {
  const { t } = useLocale();
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password.length < 8 || password.length > 72) {
      setError(new Error(t("auth.passwordLength")));
      return;
    }
    setSubmitting(true);
    try {
      await api.resetPassword(code, password);
      setDone(true);
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
        <h1 className="text-2xl font-semibold">{t("auth.resetTitle")}</h1>
        {done ? (
          <div className="mt-6 space-y-4">
            <p>{t("auth.resetSuccess")}</p>
            <Link
              href="/login"
              className="block w-full rounded-md bg-zinc-900 px-4 py-2.5 text-center font-medium text-white hover:bg-zinc-800"
            >
              {t("auth.submit")}
            </Link>
          </div>
        ) : (
          <form className="mt-6 space-y-4" onSubmit={onSubmit}>
            <div>
              <label className="block text-sm font-medium" htmlFor="code">
                {t("auth.code")}
              </label>
              <input
                id="code"
                name="code"
                autoComplete="one-time-code"
                required
                className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
                value={code}
                onChange={(event) => setCode(event.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium" htmlFor="password">
                {t("auth.newPassword")}
              </label>
              <input
                id="password"
                name="password"
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                required
                minLength={8}
                maxLength={72}
                className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <button
                type="button"
                className="mt-1 text-sm text-zinc-700 underline"
                onClick={() => setShowPassword((current) => !current)}
              >
                {showPassword ? t("auth.hidePassword") : t("auth.showPassword")}
              </button>
            </div>
            {error ? <FormError error={error} /> : null}
            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-md bg-zinc-900 px-4 py-2.5 font-medium text-white hover:bg-zinc-800 disabled:opacity-60"
            >
              {submitting ? t("common.loading") : t("auth.resetSubmit")}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
