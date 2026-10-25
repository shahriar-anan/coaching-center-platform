"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useMutation } from "@tanstack/react-query";
import { useLocale } from "@/i18n/locale-context";
import { api } from "@/lib/api/client";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { PageHeader } from "@/components/ui/page-header";
import { PasswordInput } from "@/components/ui/password-input";
import { TextInput } from "@/components/ui/text-input";
import { FormError, FormNotice, codeOf, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { isEmail, isPasswordLength, isPhone, normalizePhone } from "@/app/(auth)/_components/validate";

type Values = { fullName: string; phone: string; email: string; password: string };
type FieldErrors = Partial<Record<keyof Values, string>>;

/** Account creation. Registration does not sign the student in; the next step is the login page. */
export default function RegisterPage() {
  const { t } = useLocale();
  const [values, setValues] = useState<Values>({ fullName: "", phone: "", email: "", password: "" });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: (body: Values) =>
      api.register({ fullName: body.fullName.trim(), phone: normalizePhone(body.phone), email: body.email.trim(), password: body.password }),
    onSuccess: () => {
      // WS3's home shows a one-time welcome card when this flag is set.
      try {
        window.localStorage.setItem("exam-portal.welcome", "1");
      } catch {
        /* storage blocked: the welcome card is simply not shown */
      }
      setDone(true);
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
    return next;
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    setFormError(null);
    const order: (keyof Values)[] = ["fullName", "phone", "email", "password"];
    const first = order.find((key) => found[key]);
    if (first) {
      focusFirst([`register-${first}`]);
      return;
    }
    mutation.mutate(values);
  }

  if (done) {
    return (
      <>
        <PageHeader title={t("auth.register.successTitle")} />
        <Card padding="lg" className="grid gap-5">
          <FormNotice>{t("auth.register.successBody")}</FormNotice>
          <Button href="/login">{t("auth.register.successLogin")}</Button>
        </Card>
      </>
    );
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
          <Field label={t("auth.register.phone")} hint={t("auth.register.phoneHelp")} error={errors.phone} required>
            <TextInput
              id="register-phone"
              name="phone"
              type="tel"
              inputMode="numeric"
              autoComplete="tel-national"
              value={values.phone}
              onChange={(e) => set("phone", e.target.value)}
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
          <Field label={t("auth.register.password")} hint={t("auth.register.passwordHelp")} error={errors.password} required>
            <PasswordInput
              id="register-password"
              name="password"
              autoComplete="new-password"
              value={values.password}
              onChange={(e) => set("password", e.target.value)}
              invalid={Boolean(errors.password)}
            />
          </Field>
          {formError ? <FormError>{formError}</FormError> : null}
          <Button type="submit" fullWidth loading={mutation.isPending}>
            {t("auth.register.submit")}
          </Button>
        </form>
        <p className="text-center text-body text-muted">
          {t("auth.register.haveAccount")}{" "}
          <Link href="/login" className="font-semibold text-text-accent underline underline-offset-4">
            {t("auth.login.submit")}
          </Link>
        </p>
      </Card>
    </>
  );
}
