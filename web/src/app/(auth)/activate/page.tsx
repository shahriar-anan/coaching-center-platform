"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { PasswordInput } from "@/components/ui/password-input";
import { TextInput } from "@/components/ui/text-input";
import { CODE_DEAD, FormError, FormNotice, codeOf, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { destinationFor, useSession } from "@/lib/auth/session-context";
import { readCodeFromHash, scrubHash } from "@/app/(auth)/_components/code-hash";
import { isPasswordLength, isPhone, normalizePhone } from "@/app/(auth)/_components/validate";

/** A plain email check; the server checks it again. */
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** For a student an admin created. Success signs them in with the password they just set and opens their home page. */
export default function ActivatePage() {
  const { t } = useLocale();
  const router = useRouter();
  const { login } = useSession();
  const [values, setValues] = useState({ email: "", phone: "", code: "", password: "", confirmPassword: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof typeof values, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  // A link from the activation mail carries the code in the address fragment; fill it in, then remove it from the address bar.
  useEffect(() => {
    const fromLink = readCodeFromHash();
    if (fromLink) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- the address fragment is only readable in the browser, after load
      setValues((current) => ({ ...current, code: fromLink }));
      scrubHash();
    }
  }, []);

  const mutation = useMutation({
    // The email the code was sent to, or the phone. An email wins when both are typed (plan Q5).
    mutationFn: (body: typeof values) =>
      api.activate(
        body.email.trim() ? { email: body.email.trim() } : { phone: normalizePhone(body.phone) },
        body.code.trim(),
        body.password,
      ),
    onSuccess: async (_data, body) => {
      // The account is active now: sign in with the same email or phone and the new password, then open the home page.
      const identifier = body.email.trim() || normalizePhone(body.phone);
      try {
        const me = await login(identifier, body.password);
        router.replace(destinationFor(me));
      } catch {
        // The account is active even if this sign-in fails; the success screen keeps the login link.
        setDone(true);
      }
    },
    onError: (err) => {
      const cd = codeOf(err);
      if (cd && CODE_DEAD.has(cd)) {
        setFormError(t("auth.activate.invalidCode"));
        focusFirst(["activate-code"]);
      } else if (cd === "PASSWORD_NOT_ACCEPTED") {
        setErrors({ password: t("auth.register.passwordLength") });
        focusFirst(["activate-password"]);
      } else {
        setFormError(t("common.states.errorBody"));
      }
    },
  });

  function set(key: keyof typeof values, value: string) {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: undefined }));
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: typeof errors = {};
    // The email or the phone is enough; with neither, both boxes say so.
    if (!values.email.trim() && !values.phone.trim()) {
      next.email = t("auth.activate.emailOrPhone");
      next.phone = t("auth.activate.emailOrPhone");
    } else if (values.email.trim() && !EMAIL_PATTERN.test(values.email.trim())) {
      next.email = t("auth.activate.invalidEmail");
    } else if (!values.email.trim() && !isPhone(values.phone)) {
      next.phone = t("auth.register.invalidPhone");
    }
    if (!values.code.trim()) next.code = t("auth.register.fieldRequired");
    if (!values.password) next.password = t("auth.register.fieldRequired");
    else if (!isPasswordLength(values.password)) next.password = t("auth.register.passwordLength");
    // The match is checked here, in the browser: a mismatch sends no request.
    if (!values.confirmPassword) next.confirmPassword = t("auth.register.fieldRequired");
    else if (values.confirmPassword !== values.password) next.confirmPassword = t("auth.activate.passwordMismatch");
    setErrors(next);
    setFormError(null);
    const first = (["email", "phone", "code", "password", "confirmPassword"] as const).find((key) => next[key]);
    if (first) {
      focusFirst([`activate-${first}`]);
      return;
    }
    mutation.mutate(values);
  }

  if (done) {
    return (
      <>
        <PageHeader title={t("auth.activate.successTitle")} />
        <Card padding="lg" className="grid gap-5">
          <FormNotice>{t("auth.activate.successBody")}</FormNotice>
          <Button href="/login">{t("auth.activate.successLogin")}</Button>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={t("auth.activate.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("auth.activate.breadcrumb") }]}
      />
      <Card padding="lg" className="grid gap-5">
        <p className="text-body text-muted">{t("auth.activate.intro")}</p>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("auth.activate.email")} hint={t("auth.activate.eitherHint")} error={errors.email}>
            <TextInput
              id="activate-email"
              name="email"
              type="email"
              inputMode="email"
              autoComplete="email"
              value={values.email}
              onChange={(e) => set("email", e.target.value)}
              invalid={Boolean(errors.email)}
            />
          </Field>
          <Field label={t("auth.activate.phone")} error={errors.phone}>
            <TextInput
              id="activate-phone"
              name="phone"
              type="tel"
              inputMode="numeric"
              autoComplete="tel-national"
              value={values.phone}
              onChange={(e) => set("phone", e.target.value)}
              invalid={Boolean(errors.phone)}
            />
          </Field>
          <Field label={t("auth.activate.code")} error={errors.code} required>
            <TextInput
              id="activate-code"
              name="code"
              autoCapitalize="characters"
              spellCheck={false}
              autoComplete="one-time-code"
              value={values.code}
              onChange={(e) => set("code", e.target.value.toUpperCase())}
              invalid={Boolean(errors.code)}
            />
          </Field>
          <Field label={t("auth.activate.password")} hint={t("auth.activate.passwordHelp")} error={errors.password} required>
            <PasswordInput
              id="activate-password"
              name="password"
              autoComplete="new-password"
              value={values.password}
              onChange={(e) => set("password", e.target.value)}
              invalid={Boolean(errors.password)}
            />
          </Field>
          <Field label={t("auth.activate.confirmPassword")} error={errors.confirmPassword} required>
            <PasswordInput
              id="activate-confirmPassword"
              name="confirmPassword"
              autoComplete="new-password"
              value={values.confirmPassword}
              onChange={(e) => set("confirmPassword", e.target.value)}
              invalid={Boolean(errors.confirmPassword)}
            />
          </Field>
          {formError ? <FormError>{formError}</FormError> : null}
          <Button type="submit" fullWidth loading={mutation.isPending}>
            {t("auth.activate.submit")}
          </Button>
        </form>
        <Link href="/login" className="text-center font-semibold text-text-accent underline underline-offset-4">
          {t("auth.forgot.back")}
        </Link>
      </Card>
    </>
  );
}
