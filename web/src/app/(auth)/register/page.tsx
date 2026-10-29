"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import { useResendEmailVerification, useVerifyEmail } from "@/lib/api/exam/account";
import type { Me } from "@/lib/api/exam/types";
import { destinationFor, useSession } from "@/lib/auth/session-context";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { PasswordInput } from "@/components/ui/password-input";
import { TextInput } from "@/components/ui/text-input";
import { CODE_DEAD, FormError, FormNotice, codeOf, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { Skeleton } from "@/components/ui/skeleton";
import { formatNumber } from "@/lib/format";
import {
  formatPhone,
  isEmail,
  isPasswordLength,
  isPhone,
  normalizePhone,
  passwordStatus,
} from "@/app/(auth)/_components/validate";

/** The form reads the URL (?next=), so it sits inside a Suspense boundary for the static build. */
export default function RegisterPage() {
  return (
    <Suspense fallback={<Skeleton variant="card" />}>
      <RegisterForm />
    </Suspense>
  );
}

/** The confirm value stays in the form. The request sends only the four account fields (see mutationFn). */
type Values = { fullName: string; phone: string; email: string; password: string; confirmPassword: string };
type FieldErrors = Partial<Record<keyof Values, string>>;

/**
 * Account creation. After the account is made the student is signed in with the
 * same details and asked for the emailed code, then sent home (or back to `next`).
 * If that sign-in fails, the page falls back to a link to the login page.
 */
function RegisterForm() {
  const { t, locale } = useLocale();
  const { login } = useSession();
  // Keep the buy path: the student returns to `next`, and the login links carry it on.
  const next = useSearchParams().get("next");
  const nextQuery = next ? `?next=${encodeURIComponent(next)}` : "";

  function passwordHint(value: string): string {
    const status = passwordStatus(value);
    if (!value || status.length > 72) return t("auth.register.passwordHelp");
    if (status.ok) return t("auth.register.passwordOk");
    return t("auth.register.passwordShort", { n: formatNumber(status.short, locale) });
  }
  const [values, setValues] = useState<Values>({ fullName: "", phone: "", email: "", password: "", confirmPassword: "" });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  /** The signed-in student after registration, or "manual" when the automatic sign-in failed. */
  const [created, setCreated] = useState<Me | "manual" | null>(null);

  const mutation = useMutation({
    mutationFn: (body: Values) =>
      api.register({ fullName: body.fullName.trim(), phone: normalizePhone(body.phone), email: body.email.trim(), password: body.password }),
    onSuccess: async (_data, body) => {
      // WS3's home shows a one-time welcome card when this flag is set.
      try {
        window.localStorage.setItem("exam-portal.welcome", "1");
      } catch {
        /* storage blocked: the welcome card is simply not shown */
      }
      try {
        setCreated(await login(normalizePhone(body.phone), body.password, { redirect: false }));
      } catch {
        setCreated("manual");
      }
    },
    onError: (err) => {
      setFormError(null);
      const code = codeOf(err);
      if (code === "DUPLICATE_PHONE") {
        setErrors({ phone: t("auth.register.duplicatePhone") });
        focusFirst(["register-phone"]);
      } else if (code === "DUPLICATE_EMAIL") {
        setErrors({ email: t("auth.register.duplicateEmail") });
        focusFirst(["register-email"]);
      } else if (code === "EMAIL_RATE_LIMITED") {
        setFormError(t("auth.register.rateLimited"));
      } else {
        setFormError(t("common.states.errorBody"));
      }
    },
  });

  function set<K extends keyof Values>(key: K, value: string) {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  }

  function validate(): FieldErrors {
    const next: FieldErrors = {};
    if (!values.fullName.trim()) next.fullName = t("auth.register.fieldRequired");
    if (!values.phone.trim()) next.phone = t("auth.register.fieldRequired");
    else if (!isPhone(values.phone)) next.phone = t("auth.register.invalidPhone");
    if (!values.email.trim()) next.email = t("auth.register.fieldRequired");
    else if (!isEmail(values.email)) next.email = t("auth.register.invalidEmail");
    if (!values.password) next.password = t("auth.register.fieldRequired");
    else if (!isPasswordLength(values.password)) next.password = t("auth.register.passwordLength");
    // The match is checked here, in the browser: a mismatch sends no request.
    if (!values.confirmPassword) next.confirmPassword = t("auth.register.fieldRequired");
    else if (values.confirmPassword !== values.password) next.confirmPassword = t("auth.register.passwordMismatch");
    return next;
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    setFormError(null);
    const order: (keyof Values)[] = ["fullName", "phone", "email", "password", "confirmPassword"];
    const first = order.find((key) => found[key]);
    if (first) {
      focusFirst([`register-${first}`]);
      return;
    }
    mutation.mutate(values);
  }

  if (created === "manual") {
    return (
      <>
        <PageHeader title={t("auth.register.successTitle")} />
        <Card padding="lg" className="grid gap-5">
          <FormNotice>{t("auth.register.successBody")}</FormNotice>
          <Button href={`/login${nextQuery}`}>{t("auth.register.successLogin")}</Button>
        </Card>
      </>
    );
  }
  if (created) {
    return <VerifyEmailStep user={created} email={values.email.trim()} next={next} />;
  }

  return (
    <>
      <PageHeader
        title={t("auth.register.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("auth.register.breadcrumb") }]}
      />
      <Card padding="lg" className="grid gap-5">
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("auth.register.fullName")} error={errors.fullName} required>
            <TextInput
              id="register-fullName"
              name="fullName"
              autoComplete="name"
              value={values.fullName}
              onChange={(e) => set("fullName", e.target.value)}
              invalid={Boolean(errors.fullName)}
            />
          </Field>
          <Field
            label={t("auth.register.phone")}
            hint={isPhone(values.phone) ? t("auth.register.phoneOk") : t("auth.register.phoneHelp")}
            error={errors.phone}
            required
          >
            <TextInput
              id="register-phone"
              name="phone"
              type="tel"
              inputMode="numeric"
              autoComplete="tel-national"
              value={values.phone}
              onChange={(e) => set("phone", formatPhone(e.target.value))}
              invalid={Boolean(errors.phone)}
            />
          </Field>
          <Field label={t("auth.register.email")} error={errors.email} required>
            <TextInput
              id="register-email"
              name="email"
              type="email"
              inputMode="email"
              autoComplete="email"
              autoCapitalize="none"
              value={values.email}
              onChange={(e) => set("email", e.target.value)}
              invalid={Boolean(errors.email)}
            />
          </Field>
          <Field
            label={t("auth.register.password")}
            hint={passwordHint(values.password)}
            error={errors.password}
            required
          >
            <PasswordInput
              id="register-password"
              name="password"
              autoComplete="new-password"
              value={values.password}
              onChange={(e) => set("password", e.target.value)}
              invalid={Boolean(errors.password)}
            />
          </Field>
          <Field label={t("auth.register.confirmPassword")} error={errors.confirmPassword} required>
            <PasswordInput
              id="register-confirmPassword"
              name="confirmPassword"
              autoComplete="new-password"
              value={values.confirmPassword}
              onChange={(e) => set("confirmPassword", e.target.value)}
              invalid={Boolean(errors.confirmPassword)}
            />
          </Field>
          {formError ? <FormError>{formError}</FormError> : null}
          <Button type="submit" fullWidth loading={mutation.isPending}>
            {t("auth.register.submit")}
          </Button>
        </form>
        <p className="text-center text-body text-muted">
          {t("auth.register.haveAccount")}{" "}
          <Link href={`/login${nextQuery}`} className="font-semibold text-text-accent underline underline-offset-4">
            {t("auth.login.submit")}
          </Link>
        </p>
      </Card>
    </>
  );
}

/**
 * The second step: the code the API emailed at registration. Verifying is not
 * required to use the site, so the student may also skip it and verify later
 * from the account page.
 */
function VerifyEmailStep({ user, email, next }: { user: Me; email: string; next: string | null }) {
  const { t } = useLocale();
  const router = useRouter();
  const { refreshMe } = useSession();
  const verify = useVerifyEmail();
  const resend = useResendEmailVerification();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const destination = destinationFor(user, next);

  function goOn() {
    if (/^https?:\/\//.test(destination)) window.location.assign(destination);
    else router.replace(destination);
  }

  async function onVerify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    if (!code.trim()) {
      setError(t("auth.register.fieldRequired"));
      focusFirst(["register-code"]);
      return;
    }
    try {
      await verify.mutateAsync({ code: code.trim() });
      await refreshMe();
      goOn();
    } catch (err) {
      const cd = codeOf(err);
      setError(cd && CODE_DEAD.has(cd) ? t("auth.reset.invalidCode") : t("common.states.errorBody"));
      focusFirst(["register-code"]);
    }
  }

  async function onResend() {
    setError(null);
    setNotice(null);
    try {
      await resend.mutateAsync({ email: user.email ?? email });
      setNotice(t("auth.register.verifyResent"));
    } catch (err) {
      setError(codeOf(err) === "EMAIL_RATE_LIMITED" ? t("auth.register.rateLimited") : t("common.states.errorBody"));
    }
  }

  return (
    <>
      <PageHeader title={t("auth.register.verifyTitle")} />
      <Card padding="lg" className="grid gap-5">
        <FormNotice>{t("auth.register.verifyBody", { email: user.email ?? email })}</FormNotice>
        <form onSubmit={onVerify} noValidate className="grid gap-4">
          <Field label={t("auth.register.verifyCode")} error={error ?? undefined} required>
            <TextInput
              id="register-code"
              name="verificationCode"
              autoCapitalize="characters"
              spellCheck={false}
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              invalid={Boolean(error)}
            />
          </Field>
          {notice ? <FormNotice>{notice}</FormNotice> : null}
          <Button type="submit" fullWidth loading={verify.isPending}>
            {t("auth.register.verifySubmit")}
          </Button>
          <Button type="button" variant="secondary" fullWidth loading={resend.isPending} onClick={onResend}>
            {t("auth.register.verifyResend")}
          </Button>
        </form>
        <div className="grid gap-2 border-t border-border pt-4 text-center">
          <p className="text-body text-muted">{t("auth.register.verifySkipNote")}</p>
          <Button type="button" variant="ghost" onClick={goOn}>
            {t("auth.register.verifySkip")}
          </Button>
        </div>
      </Card>
    </>
  );
}
