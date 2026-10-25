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
import { isPasswordLength } from "@/app/(auth)/_components/validate";

export default function ResetPasswordPage() {
  const { t } = useLocale();
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<{ code?: string; password?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const mutation = useMutation({
    mutationFn: (body: { code: string; password: string }) => api.resetPassword(body.code.trim(), body.password),
    onSuccess: () => setDone(true),
    onError: (err) => {
      const cd = codeOf(err);
      if (cd && CODE_DEAD.has(cd)) {
        setFormError(t("auth.reset.invalidCode"));
        focusFirst(["reset-code"]);
      } else if (cd === "PASSWORD_NOT_ACCEPTED") {
        setErrors({ password: t("auth.register.passwordLength") });
        focusFirst(["reset-password"]);
      } else {
        setFormError(t("common.states.errorBody"));
      }
    },
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: { code?: string; password?: string } = {};
    if (!code.trim()) next.code = t("auth.register.fieldRequired");
    if (!password) next.password = t("auth.register.fieldRequired");
    else if (!isPasswordLength(password)) next.password = t("auth.register.passwordLength");
    setErrors(next);
    setFormError(null);
    if (next.code || next.password) {
      focusFirst([next.code ? "reset-code" : "reset-password"]);
      return;
    }
    mutation.mutate({ code, password });
  }

  if (done) {
    return (
      <>
        <PageHeader title={t("auth.reset.title")} />
        <Card padding="lg" className="grid gap-5">
          <FormNotice>{t("auth.reset.success")}</FormNotice>
          <Button href="/login">{t("auth.reset.successLogin")}</Button>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={t("auth.reset.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("auth.reset.breadcrumb") }]}
      />
      <Card padding="lg" className="grid gap-5">
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("auth.reset.code")} hint={t("auth.reset.codeHelp")} error={errors.code} required>
            <TextInput
              id="reset-code"
              name="code"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              invalid={Boolean(errors.code)}
            />
          </Field>
          <Field label={t("auth.reset.password")} hint={t("auth.reset.passwordHelp")} error={errors.password} required>
            <PasswordInput
              id="reset-password"
              name="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              invalid={Boolean(errors.password)}
            />
          </Field>
          {formError ? <FormError>{formError}</FormError> : null}
          <Button type="submit" fullWidth loading={mutation.isPending}>
            {t("auth.reset.submit")}
          </Button>
        </form>
        <Link href="/login" className="text-center font-semibold text-text-accent underline underline-offset-4">
          {t("auth.forgot.back")}
        </Link>
      </Card>
    </>
  );
}
