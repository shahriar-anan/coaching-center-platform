"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useRef, useState, type FormEvent, type ReactNode } from "react";
import { useLocale } from "@/i18n/locale-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { PasswordInput } from "@/components/ui/password-input";
import { Pill } from "@/components/ui/pill";
import { TextInput } from "@/components/ui/text-input";
import { useSession, WrongAudienceError } from "@/lib/auth/session-context";
import { apiUrl, googleEnabled, staffLogin, studentLogin } from "@/lib/config";
import { ApiError } from "@/lib/api/errors";
import { FormError, codeOf, focusFirst } from "@/app/(auth)/_components/auth-ui";

/**
 * Signed-out entry for students and staff. The message never says which field
 * was wrong: a bad password, an unknown account and a deactivated one look the same.
 */
export default function LoginPage() {
  const { t } = useLocale();
  const { login } = useSession();
  const params = useSearchParams();
  const next = params.get("next");
  const expired = params.get("reason") === "expired";

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ReactNode>(null);
  const [fieldError, setFieldError] = useState<{ identifier?: string; password?: string }>({});
  const identifierRef = useRef<HTMLInputElement>(null);

  function errorFor(err: unknown): ReactNode {
    if (err instanceof WrongAudienceError) {
      const onStaff = err.audience === "staff";
      return (
        <span className="grid gap-2">
          <span>{onStaff ? t("auth.login.audienceStaff") : t("auth.login.audienceStudent")}</span>
          <Link
            href={onStaff ? staffLogin : studentLogin}
            className="font-semibold text-text-accent underline underline-offset-4"
          >
            {onStaff ? t("common.guard.staffLoginHere") : t("common.guard.studentLoginHere")}
          </Link>
        </span>
      );
    }
    const code = codeOf(err);
    if (code === "LOGIN_LOCKED") {
      return t("auth.login.locked");
    }
    if (code === "INVALID_CREDENTIALS") {
      return t("auth.login.failed");
    }
    if (err instanceof ApiError && code && code !== "INTERNAL_ERROR") {
      return t("auth.login.failed");
    }
    return t("auth.login.failedUnknown");
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: { identifier?: string; password?: string } = {};
    if (!identifier.trim()) {
      nextErrors.identifier = t("common.states.required");
    }
    if (!password) {
      nextErrors.password = t("common.states.required");
    }
    setFieldError(nextErrors);
    setError(null);
    if (nextErrors.identifier || nextErrors.password) {
      focusFirst(nextErrors.identifier ? ["login-identifier"] : ["login-password"]);
      return;
    }
    setBusy(true);
    try {
      await login(identifier.trim(), password, { next });
    } catch (err) {
      // The typed values stay in place so the student can correct them.
      setError(errorFor(err));
      setBusy(false);
      identifierRef.current?.focus();
    }
  }

  return (
    <>
      <PageHeader
        title={t("auth.login.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("auth.login.title") }]}
      />
      <Card padding="lg" className="grid gap-5">
        {expired ? <p className="text-[15px] text-muted">{t("auth.login.expired")}</p> : null}
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("auth.login.identifier")} error={fieldError.identifier} required>
            <TextInput
              ref={identifierRef}
              id="login-identifier"
              name="identifier"
              type="text"
              inputMode="email"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              invalid={Boolean(fieldError.identifier)}
            />
          </Field>
          <Field label={t("auth.login.password")} error={fieldError.password} required>
            <PasswordInput
              id="login-password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              invalid={Boolean(fieldError.password)}
            />
          </Field>
          {error ? <FormError>{error}</FormError> : null}
          <Button type="submit" fullWidth loading={busy}>
            {t("auth.login.submit")}
          </Button>
        </form>

        <div className="grid gap-3">
          <p className="text-center text-sm text-muted">{t("auth.login.divider")}</p>
          {googleEnabled ? (
            <a
              href={apiUrl("/api/v1/auth/google")}
              className="inline-flex min-h-11 w-full items-center justify-center rounded-control border border-border bg-card px-5 text-[15px] font-semibold text-text hover:bg-bg"
            >
              {t("auth.login.googleButton")}
            </a>
          ) : (
            <div className="flex items-center gap-2">
              <Button variant="secondary" fullWidth disabled aria-describedby="google-soon">
                {t("auth.login.googleButton")}
              </Button>
              <span id="google-soon">
                <Pill tone="muted">{t("auth.login.googleSoon")}</Pill>
              </span>
            </div>
          )}
        </div>

        <nav aria-label={t("common.nav.main")} className="grid gap-2 text-center text-[15px]">
          <Link href="/forgot-password" className="font-semibold text-text-accent underline underline-offset-4">
            {t("auth.login.forgot")}
          </Link>
          <Link href="/register" className="font-semibold text-text-accent underline underline-offset-4">
            {t("auth.login.register")}
          </Link>
          <Link href="/activate" className="font-semibold text-text-accent underline underline-offset-4">
            {t("auth.login.activate")}
          </Link>
        </nav>
      </Card>
    </>
  );
}
