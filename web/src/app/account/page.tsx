"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { FormError } from "@/components/form-error";
import { LocaleSwitcher } from "@/components/locale-switcher";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import { useSession } from "@/lib/auth/session-context";

export default function AccountPage() {
  const { t } = useLocale();
  const { session } = useSession();
  const router = useRouter();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);

  useEffect(() => {
    if (session.status === "anonymous") {
      router.replace("/login");
    }
  }, [session.status, router]);

  async function resend() {
    if (session.status !== "authenticated") {
      return;
    }
    setError(null);
    setNotice(null);
    setResending(true);
    try {
      await api.resendVerification(session.user.email);
      setNotice(t("auth.resendSent"));
    } catch (err) {
      setError(err);
    } finally {
      setResending(false);
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    if (newPassword.length < 8 || newPassword.length > 72) {
      setError(new Error(t("auth.passwordLength")));
      return;
    }
    setSubmitting(true);
    try {
      await api.changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setNotice(t("auth.passwordChanged"));
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  if (session.status !== "authenticated") {
    return <p className="p-8 text-center text-zinc-600">{t("auth.restoring")}</p>;
  }

  const verified = session.user.emailVerifiedAt != null;

  return (
    <div className="flex min-h-full flex-col items-center justify-center px-4 py-12">
      <div className="absolute right-4 top-4">
        <LocaleSwitcher />
      </div>
      <div className="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-semibold">{t("auth.accountTitle")}</h1>
        <p className="mt-4 text-sm text-zinc-600">{session.user.email}</p>
        <p className="mt-1">{verified ? t("auth.emailVerified") : t("auth.emailUnverified")}</p>
        {verified ? null : (
          <button
            type="button"
            disabled={resending}
            className="mt-4 w-full rounded-md border border-zinc-300 px-4 py-2.5 font-medium hover:bg-zinc-50 disabled:opacity-60"
            onClick={() => void resend()}
          >
            {resending ? t("common.loading") : t("auth.resendVerification")}
          </button>
        )}
        <form className="mt-8 space-y-4" onSubmit={onSubmit}>
          <h2 className="text-lg font-semibold">{t("auth.changePassword")}</h2>
          <div>
            <label className="block text-sm font-medium" htmlFor="currentPassword">
              {t("auth.currentPassword")}
            </label>
            <input
              id="currentPassword"
              name="currentPassword"
              type={showPassword ? "text" : "password"}
              autoComplete="current-password"
              required
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium" htmlFor="newPassword">
              {t("auth.newPassword")}
            </label>
            <input
              id="newPassword"
              name="newPassword"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={72}
              className="mt-1 w-full rounded-md border border-zinc-300 px-3 py-2"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
            <button
              type="button"
              className="mt-1 text-sm text-zinc-700 underline"
              onClick={() => setShowPassword((current) => !current)}
            >
              {showPassword ? t("auth.hidePassword") : t("auth.showPassword")}
            </button>
          </div>
          {notice ? <p>{notice}</p> : null}
          {error ? <FormError error={error} /> : null}
          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-zinc-900 px-4 py-2.5 font-medium text-white hover:bg-zinc-800 disabled:opacity-60"
          >
            {submitting ? t("common.loading") : t("auth.changePassword")}
          </button>
        </form>
      </div>
    </div>
  );
}
