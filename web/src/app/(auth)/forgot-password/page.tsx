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
import { TextInput } from "@/components/ui/text-input";
import { FormError, FormNotice, codeOf, focusFirst } from "@/app/(auth)/_components/auth-ui";
import { isEmail } from "@/app/(auth)/_components/validate";

/** Always answers with the same sentence, so it never reveals whether an email has an account. */
export default function ForgotPasswordPage() {
  const { t } = useLocale();
  const [email, setEmail] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const mutation = useMutation({
    mutationFn: (value: string) => api.forgotPassword(value.trim()),
    onSuccess: () => setSent(true),
    onError: (err) => {
      if (codeOf(err) === "EMAIL_RATE_LIMITED") {
        setFormError(t("auth.forgot.rateLimited"));
      } else {
        setFormError(t("common.states.errorBody"));
      }
    },
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    if (!email.trim()) {
      setFieldError(t("auth.register.fieldRequired"));
      focusFirst(["forgot-email"]);
      return;
    }
    if (!isEmail(email)) {
      setFieldError(t("auth.forgot.invalidEmail"));
      focusFirst(["forgot-email"]);
      return;
    }
    setFieldError(null);
    mutation.mutate(email);
  }

  if (sent) {
    return (
      <>
        <PageHeader title={t("auth.forgot.title")} />
        <Card padding="lg" className="grid gap-5">
          <FormNotice>{t("auth.forgot.sent")}</FormNotice>
          <p className="text-[15px] text-muted">{t("auth.forgot.sentBody")}</p>
          <Button href="/reset-password">{t("auth.forgot.next")}</Button>
          <Link href="/login" className="text-center font-semibold text-text-accent underline underline-offset-4">
            {t("auth.forgot.back")}
          </Link>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={t("auth.forgot.title")}
        breadcrumbs={[{ label: t("common.nav.home"), href: "/" }, { label: t("auth.forgot.breadcrumb") }]}
      />
      <Card padding="lg" className="grid gap-5">
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <Field label={t("auth.forgot.email")} error={fieldError ?? undefined} required>
            <TextInput
              id="forgot-email"
              name="email"
              type="email"
              inputMode="email"
              autoComplete="email"
              autoCapitalize="none"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              invalid={Boolean(fieldError)}
            />
          </Field>
          {formError ? <FormError>{formError}</FormError> : null}
          <Button type="submit" fullWidth loading={mutation.isPending}>
            {t("auth.forgot.submit")}
          </Button>
        </form>
        <Link href="/login" className="text-center font-semibold text-text-accent underline underline-offset-4">
          {t("auth.forgot.back")}
        </Link>
      </Card>
    </>
  );
}
