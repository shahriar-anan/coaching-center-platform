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
import { CODE_DEAD, FormError, FormNotice, codeOf, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { isPasswordLength, isPhone, normalizePhone } from "@/app/(auth)/_components/validate";

/** For a student an admin created. Success does not sign them in; they go to the login page. */
export default function ActivatePage() {
  const { t } = useLocale();
  const [values, setValues] = useState({ phone: "", code: "", password: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof typeof values, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: (body: typeof values) => api.activate(normalizePhone(body.phone), body.code.trim(), body.password),
    onSuccess: () => setDone(true),
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
    if (!values.phone.trim()) next.phone = t("auth.register.fieldRequired");
    else if (!isPhone(values.phone)) next.phone = t("auth.register.invalidPhone");
    if (!values.code.trim()) next.code = t("auth.register.fieldRequired");
    if (!values.password) next.password = t("auth.register.fieldRequired");
    else if (!isPasswordLength(values.password)) next.password = t("auth.register.passwordLength");
    setErrors(next);
    setFormError(null);
    const first = (["phone", "code", "password"] as const).find((key) => next[key]);
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
        <p className="text-[15px] text-muted">{t("auth.activate.intro")}</p>
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("auth.activate.phone")} error={errors.phone} required>
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
              inputMode="numeric"
              autoComplete="one-time-code"
              value={values.code}
              onChange={(e) => set("code", e.target.value)}
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
