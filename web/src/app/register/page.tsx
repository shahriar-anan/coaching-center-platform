"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { FormError } from "@/components/form-error";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";

export default function RegisterPage() {
  const { t } = useLocale();
  const [fullName, setFullName] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password.length < 8 || password.length > 72) {
      setError(new Error(t("auth.passwordLength")));
      return;
    }
    setSubmitting(true);
    try {
      await api.register({ fullName, phone, email, password });
      setCreated(true);
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
        <h1 className="text-2xl font-semibold">{t("auth.registerTitle")}</h1>
        {created ? (
          <div className="mt-6 space-y-4">
            <p>{t("auth.registerSuccess")}</p>
            <p className="text-sm text-zinc-600">{t("auth.registerSuccessHint")}</p>
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
              <label className="block text-sm font-medium" htmlFor="fullName">
                {t("auth.fullName")}
              </label>
              <input
                id="fullName"
                name="fullName"
                autoComplete="name"
                required
                className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
                value={fullName}
                onChange={(event) => setFullName(event.target.value)}
              />
            </div>
            <div>
              <label className="block text-sm font-medium" htmlFor="phone">
                {t("auth.phone")}
              </label>
              <input
                id="phone"
                name="phone"
                autoComplete="tel"
                inputMode="numeric"
                required
                className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
              />
              <p className="mt-1 text-sm text-zinc-600">{t("auth.phoneHint")}</p>
            </div>
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
            <div>
              <label className="block text-sm font-medium" htmlFor="password">
                {t("auth.password")}
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
              <p className="mt-1 text-sm text-zinc-600">{t("auth.passwordHint")}</p>
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
              {submitting ? t("common.loading") : t("auth.registerSubmit")}
            </button>
          </form>
        )}
        {created ? null : (
          <p className="mt-4 text-sm">
            <Link href="/login" className="text-zinc-700 underline">
              {t("auth.backToLogin")}
            </Link>
          </p>
        )}
      </div>
    </div>
  );
}
